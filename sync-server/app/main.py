from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from .database import init_db
from .routes import health, devices, sync
import os

@asynccontextmanager
async def lifespan(app: FastAPI):
    os.makedirs(os.path.dirname(os.environ.get("DATABASE_PATH", "/app/data/sync.db")), exist_ok=True)
    init_db()
    yield

app = FastAPI(title="MediaNest Sync Server", version="1.0.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router, tags=["health"])
app.include_router(devices.router, prefix="/device", tags=["devices"])
app.include_router(sync.router, prefix="/sync", tags=["sync"])
