import getpass
import re
import sys

from werkzeug.security import generate_password_hash
from database import get_db_connection, init_db


def main():
    print("EduLens - First Administrator Setup")
    print("Use this only against the intended database.")
    init_db()

    username = input("Administrator username: ").strip()
    if not username or len(username) > 100:
        print("Invalid username.")
        return 1

    password = getpass.getpass("New password (minimum 12 characters): ")
    confirm = getpass.getpass("Confirm password: ")

    if len(password) < 12:
        print("Password must be at least 12 characters.")
        return 1
    if password != confirm:
        print("Passwords do not match.")
        return 1

    conn = get_db_connection()
    try:
        count = conn.execute(
            "SELECT COUNT(*) FROM users"
        ).fetchone()[0]

        if count != 0:
            print(
                "Refusing bootstrap: the users table is not empty. "
                "Use an approved account recovery process instead."
            )
            return 1

        conn.execute(
            "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)",
            (username, generate_password_hash(password, method="pbkdf2:sha256"),
             "administrator")
        )
        conn.commit()
        print("Administrator created successfully.")
        print("Sign in with the username and password you just set.")
        return 0
    except Exception:
        conn.rollback()
        print("Administrator creation failed. Check the database and logs.")
        return 1
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
