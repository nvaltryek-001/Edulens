import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent


class Config:
    FIREBASE_CREDENTIALS = os.getenv(
        "FIREBASE_CREDENTIALS",
        str(BASE_DIR / "config" / "firebase-service-account.json")
    )

    JWT_SECRET = os.getenv(
        "JWT_SECRET",
        "super_secret_local_key_change_me"
    )