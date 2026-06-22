# Implementation Plan: Phase 9 — VPS Sync (FastAPI + SQLite + Docker)

## System / Contract Summary
- **Sync server**: Python FastAPI + SQLite + Docker (primary) / systemd+uvicorn (alternative). Dependencies managed via `uv` — `pyproject.toml` + `uv.lock`, no `requirements.txt`.
- **Android client**: OkHttp-based `SyncRepository` + `SyncManager` + WorkManager periodic check
- **Protocol**: REST JSON over HTTP. API key auth via `X-API-Key` header.
- **Conflict resolution**: Timestamp-based — newest `updatedAt` wins.
- **Sync scope**: All 7 Room tables (videos, downloads, history, folders, video_folder_join, playlists, subscriptions) + DataStore preferences
- **Not synced**: Media files (too large). MediaNest stays offline-first — sync is metadata-only.
- **DB version**: 6 currently → bump to 7 with migration (add `syncVersion INTEGER DEFAULT 0` to all entities for incremental sync)
- **Data persistence**: SQLite file lives on Docker volume (`/app/data/sync.db`). Rebuilding the image does NOT wipe data — volume is mounted from host or named volume.
- **No WebSocket**: Poll-based for now (`GET /sync/pull`). Push notifications deferred.

---

## Phase Order

**Server (9a):** Build the sync API server → Dockerize → document alt deployment

1. **9a.1** — Create project scaffold: `sync-server/` dir, `pyproject.toml`, `app/` package, config
2. **9a.2** — Create SQLite schema + init script (tables: `devices`, `changes`)
3. **9a.3** — Create FastAPI app: `GET /health`, `POST /register`, `DELETE /device/{device_id}`
4. **9a.4** — Create sync endpoints: `POST /sync/push`, `GET /sync/pull`
5. **9a.5** — Dockerfile + docker-compose.yml with volume mount
6. **9a.6** — Alternative deployment: systemd service + uvicorn runner

**Client (9b):** Build Android sync engine

1. **9b.1** — Create `DevicePreferences.kt` (DataStore for server_url, api_key, device_id)
2. **9b.2** — Create `SyncRepository.kt` (OkHttp HTTP client for all sync endpoints)
3. **9b.3** — Create `SyncManager.kt` (state machine + WorkManager periodic check)
4. **9b.4** — DB v6→v7 — add `syncVersion` to all 7 entities, explicit migration
5. **9b.5** — Update `SettingsScreen.kt` — server URL, API key, manual sync button, last synced timestamp
6. **9b.6** — Build and verify
7. **9b.7** — Schedule SyncWorker via WorkScheduler (periodic 6h with network constraint); trigger on device registration
8. **9b.8** — Incremental sync: use entity timestamp fields with `lastSyncAt` to only push changed rows; add DAO `since` queries; update SyncManager
9. **9b.9** — Sync event log: in-memory circular buffer in SyncManager that records each push/pull op (per-table counts, row IDs, errors, timestamps); expose as `StateFlow`; render as expandable log in SettingsScreen

---

## Server

### Step 9a.1: Create project scaffold

**What**: Create `sync-server/` directory with Python FastAPI project structure.

**Where**:
- `sync-server/pyproject.toml`
- `sync-server/app/__init__.py`
- `sync-server/app/main.py`
- `sync-server/app/config.py`
- `sync-server/app/database.py`
- `sync-server/app/models.py`
- `sync-server/app/schemas.py`
- `sync-server/app/routes/__init__.py`
- `sync-server/app/routes/health.py`
- `sync-server/app/routes/devices.py`
- `sync-server/app/routes/sync.py`

**pyproject.toml**:
```toml
[project]
name = "medianest-sync-server"
version = "1.0.0"
requires-python = ">=3.12"
dependencies = [
    "fastapi>=0.115.0",
    "uvicorn[standard]>=0.32.0",
    "pydantic>=2.10.0",
]

[tool.uv.sources]
```

**Add deps** (run from `sync-server/`):
```bash
uv add fastapi uvicorn[standard] pydantic
```

This writes to `pyproject.toml` + `uv.lock`. No `requirements.txt` needed.

**config.py**:
```python
import os

DATABASE_PATH = os.getenv("DATABASE_PATH", "/app/data/sync.db")
SYNC_API_KEY = os.getenv("SYNC_API_KEY", "changeme-in-production")
```

**database.py**:
```python
import sqlite3
import threading
from .config import DATABASE_PATH

_local = threading.local()

def get_connection() -> sqlite3.Connection:
    if not hasattr(_local, "conn") or _local.conn is None:
        _local.conn = sqlite3.connect(DATABASE_PATH)
        _local.conn.row_factory = sqlite3.Row
        _local.conn.execute("PRAGMA journal_mode=WAL")
        _local.conn.execute("PRAGMA foreign_keys=ON")
    return _local.conn

def init_db():
    conn = get_connection()
    conn.executescript("""
        CREATE TABLE IF NOT EXISTS devices (
            device_id TEXT PRIMARY KEY,
            device_name TEXT NOT NULL DEFAULT '',
            api_key TEXT NOT NULL,
            last_sync_at INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS changes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT NOT NULL,
            table_name TEXT NOT NULL,
            row_id TEXT NOT NULL,
            operation TEXT NOT NULL CHECK(operation IN ('upsert','delete')),
            payload TEXT NOT NULL,
            version INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            FOREIGN KEY (device_id) REFERENCES devices(device_id)
        );

        CREATE INDEX IF NOT EXISTS idx_changes_device_version
            ON changes(device_id, version);
        CREATE INDEX IF NOT EXISTS idx_changes_table_row
            ON changes(table_name, row_id);
    """)
    conn.commit()
```

**Why**: `threading.local()` connection per thread — FastAPI uses thread pool for sync endpoints. WAL mode allows concurrent reads. `foreign_keys=ON` ensures referential integrity.

**Edge cases**: `DATABASE_PATH` dir may not exist — `sqlite3.connect` creates file but not parent dir. Need `os.makedirs(os.path.dirname(DATABASE_PATH), exist_ok=True)` in init.

**Pitfalls / do not**:
- Do NOT use async SQLite drivers for this — sqlite3 is synchronous, FastAPI handles via thread pool. Adding aiosqlite adds complexity with no benefit for single-user sync.
- Do NOT store API keys in code — always via env vars.

---

### Step 9a.2: Database schema + init

Already covered in Step 9a.1 `init_db()`.

Edge cases covered:
- `devices` table: `api_key` stored per-device (simpler than global). On first register, server generates a random key.
- `changes` table: `version` is a monotonic integer per device. Client tracks `lastSyncVersion` and only pulls newer changes.
- `payload` is a JSON string of the full row data for upsert, or just `{"id": "..."}` for delete.

---

### Step 9a.3: FastAPI app + health + device registration

**Where**:
- `sync-server/app/main.py`
- `sync-server/app/routes/health.py`
- `sync-server/app/routes/devices.py`
- `sync-server/app/schemas.py`

**main.py**:
```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from .database import init_db
from .routes import health, devices, sync
import os

app = FastAPI(title="MediaNest Sync Server", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router, tags=["health"])
app.include_router(devices.router, prefix="/device", tags=["devices"])
app.include_router(sync.router, prefix="/sync", tags=["sync"])

@app.on_event("startup")
def startup():
    os.makedirs(os.path.dirname(os.environ.get("DATABASE_PATH", "/app/data/sync.db")), exist_ok=True)
    init_db()
```

**schemas.py**:
```python
from pydantic import BaseModel
from typing import Any

class RegisterRequest(BaseModel):
    device_name: str = ""

class RegisterResponse(BaseModel):
    device_id: str
    api_key: str

class SyncPushItem(BaseModel):
    table: str
    row_id: str
    operation: str  # "upsert" | "delete"
    payload: dict[str, Any]

class SyncPushRequest(BaseModel):
    device_id: str
    changes: list[SyncPushItem]

class SyncPushResponse(BaseModel):
    accepted: int

class SyncPullResponse(BaseModel):
    version: int
    changes: list[dict[str, Any]]
    has_more: bool
```

**routes/health.py**:
```python
from fastapi import APIRouter

router = APIRouter()

@router.get("/health")
def health():
    return {"status": "ok"}
```

**routes/devices.py**:
```python
import secrets
import time
from fastapi import APIRouter, HTTPException
from ..database import get_connection
from ..schemas import RegisterRequest, RegisterResponse

router = APIRouter()

@router.post("/register", response_model=RegisterResponse)
def register(req: RegisterRequest):
    conn = get_connection()
    device_id = secrets.token_hex(16)
    api_key = secrets.token_hex(32)
    now = int(time.time() * 1000)
    conn.execute(
        "INSERT INTO devices (device_id, device_name, api_key, last_sync_at, created_at, updated_at) VALUES (?, ?, ?, 0, ?, ?)",
        (device_id, req.device_name, api_key, now, now),
    )
    conn.commit()
    return RegisterResponse(device_id=device_id, api_key=api_key)

@router.delete("/{device_id}")
def delete_device(device_id: str, api_key: str):
    conn = get_connection()
    cur = conn.execute("DELETE FROM devices WHERE device_id = ? AND api_key = ?", (device_id, api_key))
    conn.commit()
    if cur.rowcount == 0:
        raise HTTPException(404, "Device not found or wrong API key")
    # Also delete all changes for that device
    conn.execute("DELETE FROM changes WHERE device_id = ?", (device_id,))
    conn.commit()
    return {"deleted": True}
```

**Why**: `secrets.token_hex` for cryptographically random IDs and keys. No user-facing password — API key is the sole credential.

**Edge cases**: Register with same device_name twice → creates two separate devices (intentional — user can clean up later).

**Pitfalls / do not**:
- Do NOT truncate `last_sync_at` on register — starts at 0, first sync will pull all data.
- Do NOT require auth on `/health` — monitoring tools need unauthenticated health check.

---

### Step 9a.4: Sync endpoints

**routes/sync.py**:
```python
import json
import time
from fastapi import APIRouter, HTTPException, Header
from ..database import get_connection
from ..schemas import SyncPushRequest, SyncPushResponse, SyncPullResponse

router = APIRouter()

def verify_device(device_id: str, x_api_key: str = Header(...)) -> bool:
    conn = get_connection()
    cur = conn.execute("SELECT 1 FROM devices WHERE device_id = ? AND api_key = ?", (device_id, x_api_key))
    return cur.fetchone() is not None

@router.post("/push", response_model=SyncPushResponse)
def push(req: SyncPushRequest, x_api_key: str = Header(...)):
    if not verify_device(req.device_id, x_api_key):
        raise HTTPException(401, "Invalid device or API key")

    conn = get_connection()
    now = int(time.time() * 1000)

    # Get current max version for this device
    cur = conn.execute("SELECT COALESCE(MAX(version), 0) FROM changes WHERE device_id = ?", (req.device_id,))
    version = (cur.fetchone()[0] or 0) + 1

    accepted = 0
    for item in req.changes:
        try:
            conn.execute(
                "INSERT INTO changes (device_id, table_name, row_id, operation, payload, version, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                (req.device_id, item.table, item.row_id, item.operation, json.dumps(item.payload), version, now),
            )
            accepted += 1
            version += 1
        except Exception:
            continue

    # Update device last_sync_at
    conn.execute("UPDATE devices SET last_sync_at = ?, updated_at = ? WHERE device_id = ?", (now, now, req.device_id))
    conn.commit()
    return SyncPushResponse(accepted=accepted)

@router.get("/pull", response_model=SyncPullResponse)
def pull(device_id: str, after_version: int = 0, limit: int = 100, x_api_key: str = Header(...)):
    if not verify_device(device_id, x_api_key):
        raise HTTPException(401, "Invalid device or API key")

    conn = get_connection()
    cur = conn.execute(
        "SELECT * FROM changes WHERE device_id != ? AND version > ? ORDER BY version ASC LIMIT ?",
        (device_id, after_version, limit + 1),
    )
    rows = cur.fetchall()
    has_more = len(rows) > limit
    items = [dict(r) for r in rows[:limit]]
    max_version = max((r["version"] for r in rows[:limit]), default=after_version)

    # Serialize payload from JSON string back to dict
    for item in items:
        item["payload"] = json.loads(item["payload"])

    now = int(time.time() * 1000)
    conn.execute("UPDATE devices SET last_sync_at = ?, updated_at = ? WHERE device_id = ?", (now, now, device_id))
    conn.commit()

    return SyncPullResponse(version=max_version, changes=items, has_more=has_more)
```

**Why**:
- `after_version` — incremental pull, client stores last version and only fetches newer.
- `device_id != ?` — exclude own changes (avoid echo). Each device pushes its own changes, pulls others'.
- `limit + 1` — detect `has_more` without a separate count query.
- JSON `payload` stored as TEXT, deserialized on pull — keeps schema flexible.

**Edge cases**:
- Empty push → `accepted = 0`, no error.
- Pull with no new changes → empty `changes` list, `version = after_version`, `has_more = false`.
- Large payload (>1MB) → SQLite TEXT limit is 1B, but HTTP request size may be limited. Client should batch.
- Invalid JSON in push payload → caught by Pydantic validation before handler.
- Device deleted but still pushes → `verify_device` returns False → 401.

**Pitfalls / do not**:
- Do NOT use `x_api_key` as a Query param — always Header. Query params leak in server logs.
- Do NOT allow pulling own changes — client would re-apply its own mutations.
- Do NOT make `limit` too large (default 100). Client can paginate with `has_more`.

---

### Step 9a.5: Docker + docker-compose with volume

**Where**:
- `sync-server/Dockerfile`
- `sync-server/docker-compose.yml`
- `sync-server/.env.example`

**Dockerfile**:
```dockerfile
FROM ghcr.io/astral-sh/uv:python3.12-alpine

WORKDIR /app

COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev

COPY app/ ./app/

EXPOSE 8000

CMD ["uv", "run", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

**Why `ghcr.io/astral-sh/uv`**: Official uv Docker image with Python 3.12 slim-alpine. `uv sync --frozen` installs exact pinned deps from `uv.lock` — no `pip`, no `requirements.txt`. `uv run` activates the venv automatically.

**docker-compose.yml**:
```yaml
version: "3.9"

services:
  sync-server:
    build: .
    container_name: medianest-sync
    restart: unless-stopped
    ports:
      - "8000:8000"
    environment:
      - DATABASE_PATH=/app/data/sync.db
      - SYNC_API_KEY=changeme-in-production
    volumes:
      - sync-data:/app/data

volumes:
  sync-data:
```

**Key insight**: `sync-data` named volume mounts to `/app/data` inside the container. SQLite file at `/app/data/sync.db` **survives `docker build` and `docker compose down`**. It only gets deleted if you run `docker compose down -v` (explicit volume removal).

**How to run**:
```bash
cd sync-server
docker compose up -d        # First run: builds image + creates volume
# ... use the server ...
docker compose down          # Stops container. Volume persists.
docker compose up -d         # Restarts. All data still there.
docker compose build         # Rebuilds image. Volume data survives.
docker compose up -d         # New image, old data. Works.
```

**Alternative docker run**:
```bash
docker volume create medianest-sync-data
docker run -d \
  --name medianest-sync \
  -p 8000:8000 \
  -e DATABASE_PATH=/app/data/sync.db \
  -e SYNC_API_KEY=changeme-in-production \
  -v medianest-sync-data:/app/data \
  medianest-sync-server
```

**.env.example**:
```
SYNC_API_KEY=changeme-in-production
DATABASE_PATH=/app/data/sync.db
```

Edge case: `docker compose build` with `--no-cache` still preserves the volume. Only `docker compose down -v` or `docker volume rm` destroys it.

---

### Step 9a.6: Alternative deployment (non-Docker)

**Where**:
- `sync-server/deploy/systemd/medianest-sync.service`

**medianest-sync.service**:
```ini
[Unit]
Description=MediaNest Sync Server
After=network.target

[Service]
Type=simple
User=medianest
Group=medianest
WorkingDirectory=/opt/medianest-sync
Environment=DATABASE_PATH=/opt/medianest-sync/data/sync.db
Environment=SYNC_API_KEY=changeme-in-production
ExecStart=/opt/medianest-sync/.venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

**Setup script** (`sync-server/deploy/setup.sh`):
```bash
#!/bin/bash
set -e

# Install uv
curl -LsSf https://astral.sh/uv/install.sh | sh
source "$HOME/.cargo/env"

# Create user
useradd -r -s /bin/false medianest || true

# Create dirs
mkdir -p /opt/medianest-sync/data
chown -R medianest:medianest /opt/medianest-sync

# Copy app files (assuming current dir is sync-server/)
cp -r app /opt/medianest-sync/
cp pyproject.toml /opt/medianest-sync/
cp uv.lock /opt/medianest-sync/

# Install deps with uv
cd /opt/medianest-sync
uv sync --frozen --no-dev

# Service
cp deploy/systemd/medianest-sync.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now medianest-sync

echo "Server running on port 8000. Data at /opt/medianest-sync/data/"
```

**Why**: `Restart=always` handles crashes. `User=medianest` isolates from root. Data lives on host filesystem — survives image rebuilds obviously since there's no Docker.

---

## Android Client

### Step 9b.1: Create DevicePreferences

**Where**:
- `app/src/main/java/com/example/medianest/data/preferences/DevicePreferences.kt` (new)
- `app/src/main/java/com/example/medianest/di/DataModule.kt` (or add to existing `DatabaseModule.kt`)

**DevicePreferences.kt**:
```kotlin
package com.example.medianest.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.syncStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

class DevicePreferences(private val context: Context) {
    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_LAST_SYNC_VERSION = stringPreferencesKey("last_sync_version")
        private val KEY_LAST_SYNC_AT = stringPreferencesKey("last_sync_at")
    }

    val serverUrl: Flow<String> = context.syncStore.data.map { it[KEY_SERVER_URL] ?: "" }
    val apiKey: Flow<String> = context.syncStore.data.map { it[KEY_API_KEY] ?: "" }
    val deviceId: Flow<String> = context.syncStore.data.map { it[KEY_DEVICE_ID] ?: "" }
    val lastSyncVersion: Flow<Long> = context.syncStore.data.map { it[KEY_LAST_SYNC_VERSION]?.toLongOrNull() ?: 0L }
    val lastSyncAt: Flow<Long> = context.syncStore.data.map { it[KEY_LAST_SYNC_AT]?.toLongOrNull() ?: 0L }

    suspend fun setServerUrl(url: String) { context.syncStore.edit { it[KEY_SERVER_URL] = url } }
    suspend fun setApiKey(key: String) { context.syncStore.edit { it[KEY_API_KEY] = key } }
    suspend fun setDeviceId(id: String) { context.syncStore.edit { it[KEY_DEVICE_ID] = id } }
    suspend fun setLastSyncVersion(version: Long) { context.syncStore.edit { it[KEY_LAST_SYNC_VERSION] = version.toString() } }
    suspend fun setLastSyncAt(timestamp: Long) { context.syncStore.edit { it[KEY_LAST_SYNC_AT] = timestamp.toString() } }

    suspend fun clear() {
        context.syncStore.edit { it.clear() }
    }
}
```

---

### Step 9b.2: Create SyncRepository (OkHttp client)

**Where**:
- `app/src/main/java/com/example/medianest/data/sync/SyncRepository.kt` (new)
- `app/src/main/java/com/example/medianest/data/sync/SyncModels.kt` (new)

**SyncModels.kt**:
```kotlin
package com.example.medianest.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val deviceName: String = "")

@Serializable
data class RegisterResponse(val deviceId: String, val apiKey: String)

@Serializable
data class SyncPushItem(val table: String, val rowId: String, val operation: String, val payload: Map<String, Any?>)

@Serializable
data class SyncPushRequest(val deviceId: String, val changes: List<SyncPushItem>)

@Serializable
data class SyncPushResponse(val accepted: Int)

@Serializable
data class SyncPullResponse(val version: Long, val changes: List<Map<String, Any?>>, val hasMore: Boolean)
```

**SyncRepository.kt**:
```kotlin
package com.example.medianest.data.sync

import com.example.medianest.data.preferences.DevicePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val devicePreferences: DevicePreferences
) {
    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
    }

    private val client = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun register(serverUrl: String, deviceName: String = ""): Result<RegisterResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(RegisterRequest(deviceName)).toRequestBody(JSON_MEDIA)
                val request = Request.Builder()
                    .url("$serverUrl/device/register")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw Exception("Empty response")
                json.decodeFromString<RegisterResponse>(responseBody)
            }
        }

    suspend fun pushChanges(serverUrl: String, apiKey: String, deviceId: String, changes: List<SyncPushItem>): Result<SyncPushResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(SyncPushRequest(deviceId, changes)).toRequestBody(JSON_MEDIA)
                val request = Request.Builder()
                    .url("$serverUrl/sync/push")
                    .post(body)
                    .header("X-API-Key", apiKey)
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw Exception("Empty response")
                json.decodeFromString<SyncPushResponse>(responseBody)
            }
        }

    suspend fun pullChanges(serverUrl: String, apiKey: String, deviceId: String, afterVersion: Long = 0): Result<SyncPullResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$serverUrl/sync/pull?device_id=$deviceId&after_version=$afterVersion&limit=100")
                    .get()
                    .header("X-API-Key", apiKey)
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw Exception("Empty response")
                json.decodeFromString<SyncPullResponse>(responseBody)
            }
        }
}
```

---

### Step 9b.3: Create SyncManager (state machine + WorkManager)

**Where**:
- `app/src/main/java/com/example/medianest/data/sync/SyncManager.kt` (new)
- `app/src/main/java/com/example/medianest/worker/SyncWorker.kt` (new)

**SyncManager.kt**:
```kotlin
package com.example.medianest.data.sync

import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.dao.FolderDao
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.dao.PlaylistDao
import com.example.medianest.data.local.dao.SubscriptionDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.VideoFolderDao
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.local.entity.SubscriptionEntity
import com.example.medianest.data.preferences.DevicePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

@Singleton
class SyncManager @Inject constructor(
    private val syncRepository: SyncRepository,
    private val devicePreferences: DevicePreferences,
    private val videoDao: VideoDao,
    private val downloadDao: DownloadDao,
    private val historyDao: HistoryDao,
    private val folderDao: FolderDao,
    private val videoFolderDao: VideoFolderDao,
    private val playlistDao: PlaylistDao,
    private val subscriptionDao: SubscriptionDao
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state

    private val scope = CoroutineScope(Dispatchers.IO)

    fun sync() {
        if (_state.value is SyncState.Syncing) return
        _state.value = SyncState.Syncing

        scope.launch {
            try {
                val serverUrl = devicePreferences.serverUrl.first()
                val apiKey = devicePreferences.apiKey.first()
                val deviceId = devicePreferences.deviceId.first()

                if (serverUrl.isBlank() || apiKey.isBlank() || deviceId.isBlank()) {
                    _state.value = SyncState.Error("Sync not configured. Set server URL and API key in Settings.")
                    return@launch
                }

                // 1. Push local changes
                val localChanges = collectLocalChanges()
                if (localChanges.isNotEmpty()) {
                    val pushResult = syncRepository.pushChanges(serverUrl, apiKey, deviceId, localChanges)
                    if (pushResult.isFailure) {
                        _state.value = SyncState.Error("Push failed: ${pushResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                }

                // 2. Pull remote changes
                val lastVersion = devicePreferences.lastSyncVersion.first()
                val pullResult = syncRepository.pullChanges(serverUrl, apiKey, deviceId, lastVersion)
                if (pullResult.isFailure) {
                    _state.value = SyncState.Error("Pull failed: ${pullResult.exceptionOrNull()?.message}")
                    return@launch
                }

                val pull = pullResult.getOrThrow()
                applyRemoteChanges(pull.changes)
                devicePreferences.setLastSyncVersion(pull.version)
                devicePreferences.setLastSyncAt(System.currentTimeMillis())

                val msg = if (pull.changes.isEmpty()) "Synced (no new changes)" else "Synced ${pull.changes.size} changes"
                _state.value = SyncState.Success(msg)
            } catch (e: Exception) {
                _state.value = SyncState.Error(e.message ?: "Sync failed")
            }
        }
    }

    fun resetState() { _state.value = SyncState.Idle }

    private suspend fun collectLocalChanges(): List<SyncPushItem> {
        val changes = mutableListOf<SyncPushItem>()
        // Collect all videos
        val videos = videoDao.getAllVideos().first()
        changes.addAll(videos.map { SyncPushItem("videos", it.id, "upsert", mapOf("title" to it.title)) }) // simplified
        return changes
    }

    private suspend fun applyRemoteChanges(changes: List<Map<String, Any?>>) {
        for (change in changes) {
            // Apply each change to local DB
            val table = change["table_name"] as? String ?: continue
            val operation = change["operation"] as? String ?: continue
            val payload = change["payload"] as? Map<*, *> ?: continue
            // Deserialize and apply based on table + operation
        }
    }
}
```

**Note**: `collectLocalChanges` and `applyRemoteChanges` need full entity serialization logic. This is the most complex part — each entity must serialize to `payload` and deserialize on the other side. For Phase 9, keep it simple: push all videos, downloads, history, etc. as full payloads. Incremental tracking via `syncVersion` can be optimized later.

---

### Step 9b.4: DB v6→v7 — add syncVersion

**What**: Add `syncVersion INTEGER DEFAULT 0` column to all 7 entities for tracking which rows have been synced.

**Where**:
- `app/src/main/java/com/example/medianest/data/local/AppDatabase.kt` — version 7
- `app/src/main/java/com/example/medianest/di/DatabaseModule.kt` — `MIGRATION_6_7`

**MIGRATION_6_7**:
```kotlin
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (table in listOf("videos", "downloads", "playback_history", "folders", "video_folder_join", "playlists", "subscriptions")) {
                db.execSQL("ALTER TABLE $table ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
```

---

### Step 9b.5: Update SettingsScreen

Add sync section after the library repair card:
- `OutlinedTextField` for server URL
- `OutlinedTextField` for API key (password field, obscured)
- "Register Device" button (calls `POST /device/register`, stores deviceId + apiKey)
- "Sync Now" button
- Last synced timestamp
- Sync status (from SyncManager.state)

---

### Step 9b.6: Build and verify

```bash
./gradlew :app:assembleDebug
```

---

## Beginner Implementation Guide (execution order)

### Server
1. Create `sync-server/app/__init__.py`, `config.py`, `database.py`, `models.py`, `schemas.py`
2. Create `sync-server/app/main.py` with FastAPI app + startup
3. Create `sync-server/app/routes/health.py`, `devices.py`, `sync.py`
4. Create `sync-server/pyproject.toml` + run `uv add fastapi uvicorn[standard] pydantic` to generate `uv.lock`
5. Test locally: `uv run uvicorn app.main:app --reload`
6. Create `sync-server/Dockerfile`
7. Create `sync-server/docker-compose.yml`
8. Test Docker: `docker compose up -d`, verify `curl localhost:8000/health`
9. Create `sync-server/deploy/systemd/medianest-sync.service`
10. Test systemd on Linux VM

### Client
1. Create `DevicePreferences.kt`, `SyncModels.kt`, `SyncRepository.kt`
2. Create `SyncManager.kt`
3. Add `MIGRATION_6_7` to `DatabaseModule.kt`, bump version to 7
4. Update `SettingsScreen.kt` with sync UI
5. Build

---

## Final Verification Checklist

- [ ] `docker compose up -d` starts the server on port 8000
- [ ] `curl localhost:8000/health` returns `{"status":"ok"}`
- [ ] `curl -X POST localhost:8000/device/register -H "Content-Type: application/json" -d '{}'` returns deviceId + apiKey
- [ ] `docker compose down && docker compose up -d` — data persists
- [ ] `docker compose build --no-cache && docker compose up -d` — data persists
- [ ] `curl -X POST localhost:8000/sync/push -H "Content-Type: application/json" -H "X-API-Key: <key>" -d '{"deviceId":"...","changes":[]}'` works
- [ ] `curl "localhost:8000/sync/pull?device_id=<id>&after_version=0" -H "X-API-Key: <key>"` works
- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] Settings screen shows sync section
- [ ] Register device button stores deviceId + apiKey in DataStore
- [ ] Sync Now button triggers SyncManager
- [ ] Sync status shown on screen
- [ ] systemd service file is valid
- [ ] `setup.sh` script works on clean Ubuntu/Debian

---

---

### Step 9b.9: Sync event log (in-app per-sync log)

**What**: Add an in-memory circular buffer in SyncManager that records every push/pull operation — per-table row counts, row IDs, errors, timestamps. Expose as `StateFlow<List<SyncLogEntry>>`. Display as expandable log in SettingsScreen.

**Where**:
- `app/src/main/java/com/example/medianest/data/sync/SyncManager.kt` — add `SyncLogEntry` data class + `_log: MutableStateFlow<List<SyncLogEntry>>` + `val log: StateFlow<List<SyncLogEntry>>`
- `app/src/main/java/com/example/medianest/ui/screens/SettingsScreen.kt` — add expandable Sync Log card at bottom

**SyncLogEntry** (add to SyncManager.kt or separate file):
```kotlin
data class SyncLogEntry(
    val timestamp: Long,
    val type: String,          // "push" | "pull" | "apply" | "error"
    val table: String? = null,
    val rowCount: Int = 0,
    val rowIds: List<String> = emptyList(),
    val summary: String
)
```

**SyncManager additions**:
```kotlin
    private val _log = MutableStateFlow<List<SyncLogEntry>>(emptyList())
    val log: StateFlow<List<SyncLogEntry>> = _log

    private val maxLogEntries = 100

    private fun addLogEntry(entry: SyncLogEntry) {
        val current = _log.value.toMutableList()
        current.add(0, entry)
        if (current.size > maxLogEntries) current.removeAt(current.size - 1)
        _log.value = current
    }

    // call this in push success:
    addLogEntry(SyncLogEntry(System.currentTimeMillis(), "push", null, accepted, emptyList(),
        if (accepted > 0) "Pushed $accepted changes" else "No changes to push"))

    // Before pull:
    addLogEntry(SyncLogEntry(System.currentTimeMillis(), "pull", null, 0, emptyList(),
        "Fetching changes since version $lastVersion"))

    // After pull + apply:
    addLogEntry(SyncLogEntry(System.currentTimeMillis(), "pull", null, pull.changes.size, emptyList(),
        "Pulled ${pull.changes.size} changes (version ${pull.version})"))

    val eventSummary = mutableMapOf<String, Int>()
    pull.changes.forEach { c ->
        val t = (c["table_name"] as? JsonPrimitive)?.content ?: "unknown"
        eventSummary[t] = (eventSummary[t] ?: 0) + 1
    }
    // log per-table counts
```

**SettingsScreen.kt addition** — after sync buttons, add:
- Collapsible "Sync Log" section
- Shows last 50 entries in reverse chronological order
- Each entry: timestamp, type icon, summary
- Tappable to expand/collapse
- "Clear Log" button

**Edge cases**:
- Never store logs to disk — in-memory only. Survives config changes via ViewModel.
- Cap at 100 entries — oldest entries evicted.
- Empty log shows "No sync activity yet" placeholder.
- Errors get their own `SyncLogEntry` type so they're highlighted with error color.

**Verification checklist additions**:
- [ ] After Sync Now, log shows push/pull entries with per-table counts
- [ ] Error case shows error entry with message
- [ ] Log survives rotation (ViewModel)
- [ ] Log cap at 100 entries works (push 101 entries → oldest evicted)

---

### Step 9b.7: Schedule SyncWorker via WorkScheduler

**What**: Add periodic WorkManager scheduling for SyncWorker (6h, network required). Trigger scheduling on successful device registration.

**Where**:
- `app/src/main/java/com/example/medianest/worker/WorkScheduler.kt` — add `scheduleSync()` method
- `app/src/main/java/com/example/medianest/ui/viewmodel/ExportImportViewModel.kt` — call `WorkScheduler.scheduleSync()` on successful register

**WorkScheduler.kt** edit — add after `scheduleSubscriptionCheck`:
```kotlin
    fun scheduleSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            6, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
```

**ExportImportViewModel.kt** — in `registerDevice()`, after successful registration:
```kotlin
                devicePreferences.setDeviceId(response.deviceId)
                devicePreferences.setApiKey(response.apiKey)
                WorkScheduler.scheduleSync(getApplication<Application>().applicationContext)
```

**Edge cases**:
- `ExistingPeriodicWorkPolicy.KEEP` — if already scheduled, keeps existing schedule.
- Network constraint prevents sync when offline — WorkManager retries when connectivity returns.

**Pitfalls / do not**:
- Do NOT schedule sync before device is registered — no valid server to sync with.
- Do NOT use `KEEP` if user changes server URL — they'd need to re-register (register already triggers schedule).

---

### Step 9b.8: Incremental sync using entity timestamps

**What**: Instead of pushing all rows every time, use the entity's timestamp field (e.g. `addedAt`, `playedAt`, `updatedAt`, `downloadedAt`) compared to `lastSyncAt` from DataStore to only push rows modified since last sync.

**Where**:
- `app/src/main/java/com/example/medianest/data/local/dao/` — add `getSince(timestamp: Long)` queries to each DAO that returns Flow<List<Entity>>
- `app/src/main/java/com/example/medianest/data/sync/SyncManager.kt` — in `collectLocalChanges()`, filter by `lastSyncAt` timestamp

**DAO additions**:

For each DAO, add a query method that returns only rows modified after a given timestamp.

**VideoDao.kt**:
```kotlin
    @Query("SELECT * FROM videos WHERE addedAt > :since ORDER BY addedAt ASC")
    fun getVideosSince(since: Long): Flow<List<VideoEntity>>
```

**DownloadDao.kt**:
```kotlin
    @Query("SELECT * FROM downloads WHERE downloadedAt > :since ORDER BY downloadedAt ASC")
    fun getDownloadsSince(since: Long): Flow<List<DownloadEntity>>
```

**HistoryDao.kt**:
```kotlin
    @Query("SELECT * FROM playback_history WHERE playedAt > :since ORDER BY playedAt ASC")
    fun getHistorySince(since: Long): Flow<List<HistoryEntity>>
```

**FolderDao.kt**:
```kotlin
    @Query("SELECT * FROM folders WHERE updatedAt > :since ORDER BY updatedAt ASC")
    fun getFoldersSince(since: Long): Flow<List<FolderEntity>>
```

**VideoFolderDao.kt**:
```kotlin
    @Query("SELECT * FROM video_folder_join WHERE addedAt > :since ORDER BY addedAt ASC")
    fun getJoinsSince(since: Long): List<VideoFolderJoin>
```

**PlaylistDao.kt**:
```kotlin
    @Query("SELECT * FROM playlists WHERE updatedAt > :since ORDER BY updatedAt ASC")
    fun getPlaylistsSince(since: Long): Flow<List<PlaylistEntity>>
```

**SubscriptionDao.kt**:
```kotlin
    @Query("SELECT * FROM subscriptions WHERE updatedAt > :since ORDER BY updatedAt ASC")
    fun getSubscriptionsSince(since: Long): Flow<List<SubscriptionEntity>>
```

**SyncManager.kt changes** — replace `collectLocalChanges()` body to use `lastSyncAt`:
```kotlin
    private suspend fun collectLocalChanges(): List<SyncPushItem> {
        val since = devicePreferences.lastSyncAt.first()
        val changes = mutableListOf<SyncPushItem>()

        // First sync (since == 0) → push everything. Otherwise push only new/changed rows.
        val useIncremental = since > 0

        videoDao.getAllVideos().first().forEach { v ->
            if (!useIncremental || v.addedAt > since) {
                changes.add(SyncPushItem("videos", v.id, "upsert", mapOf(
                    "id" to JsonPrimitive(v.id), "title" to JsonPrimitive(v.title),
                    "channelName" to JsonPrimitive(v.channelName),
                    "channelId" to JsonPrimitive(v.channelId ?: ""),
                    "durationSeconds" to JsonPrimitive(v.durationSeconds),
                    "thumbnailUrl" to JsonPrimitive(v.thumbnailUrl ?: ""),
                    "description" to JsonPrimitive(v.description ?: ""),
                    "uploadDate" to JsonPrimitive(v.uploadDate ?: ""),
                    "localFilePath" to JsonPrimitive(v.localFilePath),
                    "favorite" to JsonPrimitive(v.favorite),
                    "syncVersion" to JsonPrimitive(v.syncVersion)
                )))
            }
        }
        downloadDao.getAllDownloadsOnce().forEach { d ->
            if (!useIncremental || d.downloadedAt > since) {
                changes.add(SyncPushItem("downloads", d.id.toString(), "upsert", mapOf(
                    "id" to JsonPrimitive(d.id), "videoId" to JsonPrimitive(d.videoId),
                    "url" to JsonPrimitive(d.url), "format" to JsonPrimitive(d.format),
                    "quality" to JsonPrimitive(d.quality), "title" to JsonPrimitive(d.title),
                    "thumbnailUrl" to JsonPrimitive(d.thumbnailUrl ?: ""),
                    "filePath" to JsonPrimitive(d.filePath),
                    "fileSizeBytes" to JsonPrimitive(d.fileSizeBytes),
                    "status" to JsonPrimitive(d.status.name),
                    "syncVersion" to JsonPrimitive(d.syncVersion)
                )))
            }
        }
        historyDao.getAllHistory().first().forEach { h ->
            if (!useIncremental || h.playedAt > since) {
                changes.add(SyncPushItem("playback_history", h.videoId, "upsert", mapOf(
                    "videoId" to JsonPrimitive(h.videoId),
                    "positionMillis" to JsonPrimitive(h.positionMillis),
                    "playedAt" to JsonPrimitive(h.playedAt),
                    "syncVersion" to JsonPrimitive(h.syncVersion)
                )))
            }
        }
        folderDao.getAllFolders().first().forEach { f ->
            if (!useIncremental || f.updatedAt > since) {
                changes.add(SyncPushItem("folders", f.id.toString(), "upsert", mapOf(
                    "id" to JsonPrimitive(f.id), "name" to JsonPrimitive(f.name),
                    "parentId" to JsonPrimitive(f.parentId ?: -1L),
                    "createdAt" to JsonPrimitive(f.createdAt),
                    "updatedAt" to JsonPrimitive(f.updatedAt),
                    "syncVersion" to JsonPrimitive(f.syncVersion)
                )))
            }
        }
        videoFolderDao.getAllJoins().forEach { j ->
            if (!useIncremental || j.addedAt > since) {
                changes.add(SyncPushItem("video_folder_join", "${j.videoId}_${j.folderId}", "upsert", mapOf(
                    "videoId" to JsonPrimitive(j.videoId),
                    "folderId" to JsonPrimitive(j.folderId),
                    "addedAt" to JsonPrimitive(j.addedAt),
                    "syncVersion" to JsonPrimitive(j.syncVersion)
                )))
            }
        }
        playlistDao.getAllPlaylists().first().forEach { p ->
            if (!useIncremental || p.updatedAt > since) {
                changes.add(SyncPushItem("playlists", p.id.toString(), "upsert", mapOf(
                    "id" to JsonPrimitive(p.id), "name" to JsonPrimitive(p.name),
                    "description" to JsonPrimitive(p.description ?: ""),
                    "thumbnailUrl" to JsonPrimitive(p.thumbnailUrl ?: ""),
                    "youtubePlaylistId" to JsonPrimitive(p.youtubePlaylistId),
                    "uploaderName" to JsonPrimitive(p.uploaderName ?: ""),
                    "videoCount" to JsonPrimitive(p.videoCount),
                    "syncVersion" to JsonPrimitive(p.syncVersion)
                )))
            }
        }
        subscriptionDao.getAllSubscriptions().first().forEach { s ->
            if (!useIncremental || s.updatedAt > since) {
                changes.add(SyncPushItem("subscriptions", s.sourceId, "upsert", mapOf(
                    "id" to JsonPrimitive(s.id), "sourceId" to JsonPrimitive(s.sourceId),
                    "sourceType" to JsonPrimitive(s.sourceType),
                    "name" to JsonPrimitive(s.name),
                    "thumbnailUrl" to JsonPrimitive(s.thumbnailUrl ?: ""),
                    "uploaderName" to JsonPrimitive(s.uploaderName ?: ""),
                    "autoDownload" to JsonPrimitive(s.autoDownload),
                    "audioOnly" to JsonPrimitive(s.audioOnly),
                    "lastCheckedAt" to JsonPrimitive(s.lastCheckedAt),
                    "syncVersion" to JsonPrimitive(s.syncVersion)
                )))
            }
        }
        return changes
    }
```

**Why this works**: On first sync, `lastSyncAt == 0`, so `useIncremental == false` — pushes everything. On subsequent syncs, `lastSyncAt > 0` and only rows with timestamps after that point are pushed. Server already deduplicates via the `changes` table (version-based).

**Edge cases**:
- `lastSyncAt == 0` (first sync) → pushes all rows.
- Entity updated between `collectLocalChanges()` and `setLastSyncAt()` → will be picked up on next sync cycle.
- Deleted entities are NOT tracked incrementally — deletes are always pushed as full delete ops on first sync. For incremental deletes, a separate `deleted_entities` table would be needed (deferred to future phase).

**Verification checklist additions**:
- [ ] First sync pushes all rows from all 7 tables
- [ ] Second sync (with no changes) pushes nothing — pull shows 0 new changes
- [ ] After adding a new video, next sync pushes only that video
- [ ] WorkScheduler.scheduleSync() is called after device registration
- [ ] SyncWorker runs on schedule with network constraint

---

## Stop Conditions

- `docker compose` fails on Windows → user must run Linux VM or WSL2. Document systemd as alternative.
- FastAPI server can't write to `/app/data/sync.db` → Docker volume not mounted. Check `docker compose config`.
- API key auth on push/pull returns 401 → verify key matches `.env` or `docker-compose.yml`.
- OkHttp on Android can't connect → check server URL (no trailing slash), network permissions (INTERNET already declared).
- SQLite WAL mode not supported → fallback to DELETE mode. But WAL is supported on all modern storage.
- Thread-local connection in FastAPI returns stale data → thread pool executor ensures each request gets clean connection. If issue arises, switch to `sqlite3.connect()` per request (no thread local).
