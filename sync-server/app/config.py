import os

DATABASE_PATH = os.getenv("DATABASE_PATH", "/app/data/sync.db")
SYNC_API_KEY = os.getenv("SYNC_API_KEY", "changeme-in-production")
