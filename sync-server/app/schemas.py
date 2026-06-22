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
    operation: str
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
