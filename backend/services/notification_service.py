import os
import firebase_admin
from firebase_admin import credentials, messaging
from config.config import Config
from pathlib import Path

class NotificationService:
    def __init__(self):
        if not firebase_admin._apps:
            cred_path = os.getenv('FIREBASE_CREDENTIALS', Config.FIREBASE_CREDENTIALS)
            
            if not os.path.exists(cred_path):
                # Search the repository automatically and use the detected path if it differs
                repo_root = Path(__file__).resolve().parent.parent.parent
                found_paths = list(repo_root.rglob("firebase-service-account.json"))
                if found_paths:
                    cred_path = str(found_paths[0])
                else:
                    cred_path = "backend/config/firebase-service-account.json"

            try:
                cred = credentials.Certificate(cred_path)
                firebase_admin.initialize_app(cred)
            except Exception as e:
                print(f"Warning: Failed to initialize Firebase Admin SDK with {cred_path}: {e}")

    def send_notification(self, token, title, body, data=None):
        if not firebase_admin._apps:
            print("Firebase Admin SDK not initialized.")
            return False

        message = messaging.Message(
            notification=messaging.Notification(
                title=title,
                body=body,
            ),
            data=data or {},
            token=token,
        )

        try:
            response = messaging.send(message)
            print('Successfully sent message:', response)
            return True
        except Exception as e:
            print('Error sending message:', e)
            return False
