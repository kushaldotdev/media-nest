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
    conn.execute("DELETE FROM changes WHERE device_id = ?", (device_id,))
    conn.commit()
    return {"deleted": True}
