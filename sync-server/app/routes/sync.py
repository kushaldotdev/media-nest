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

    conn.execute("BEGIN EXCLUSIVE")
    try:
        accepted = 0
        for item in req.changes:
            try:
                conn.execute(
                    "INSERT INTO changes (device_id, table_name, row_id, operation, payload, version, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    (req.device_id, item.table, item.row_id, item.operation, json.dumps(item.payload), 0, now),
                )
                accepted += 1
            except Exception:
                continue

        conn.execute("UPDATE devices SET last_sync_at = ?, updated_at = ? WHERE device_id = ?", (now, now, req.device_id))
        conn.commit()
    except Exception:
        conn.rollback()
        raise

    return SyncPushResponse(accepted=accepted)

@router.get("/pull", response_model=SyncPullResponse)
def pull(device_id: str, after_version: int = 0, limit: int = 100, x_api_key: str = Header(...)):
    if not verify_device(device_id, x_api_key):
        raise HTTPException(401, "Invalid device or API key")

    conn = get_connection()
    conn.execute("BEGIN IMMEDIATE")
    try:
        cur = conn.execute(
            "SELECT * FROM changes WHERE device_id != ? AND id > ? ORDER BY id ASC LIMIT ?",
            (device_id, after_version, limit + 1),
        )
        rows = cur.fetchall()
        has_more = len(rows) > limit
        items = [dict(r) for r in rows[:limit]]
        max_version = max((r["id"] for r in rows[:limit]), default=after_version)

        for item in items:
            item["payload"] = json.loads(item["payload"])
            item["version"] = item["id"]

        now = int(time.time() * 1000)
        conn.execute("UPDATE devices SET last_sync_at = ?, updated_at = ? WHERE device_id = ?", (now, now, device_id))
        conn.commit()
    except Exception:
        conn.rollback()
        raise

    return SyncPullResponse(version=max_version, changes=items, has_more=has_more)
