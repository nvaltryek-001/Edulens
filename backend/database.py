import sqlite3
import os

DB_PATH = os.environ.get(
    'EDULENS_DB_PATH',
    os.path.join(os.path.dirname(os.path.abspath(__file__)), 'edulens.db')
)


def get_db_connection():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():

    conn = get_db_connection()
    c = conn.cursor()

    # ============================================================
    # STUDENTS
    # ============================================================

    c.execute('''
        CREATE TABLE IF NOT EXISTS students (
            student_id TEXT PRIMARY KEY,
            name TEXT,
            usn TEXT,
            department TEXT,
            semester TEXT,
            section TEXT,
            email TEXT,
            phone_number TEXT
        )
    ''')

    # ============================================================
    # A8 — ATTENDANCE SESSIONS
    # ============================================================

    c.execute('''
        CREATE TABLE IF NOT EXISTS attendance_sessions (
            session_id INTEGER PRIMARY KEY AUTOINCREMENT,

            subject TEXT NOT NULL,

            faculty TEXT NOT NULL,

            department TEXT NOT NULL,

            semester TEXT NOT NULL,

            section TEXT NOT NULL,

            start_time TEXT NOT NULL,

            end_time TEXT,

            status TEXT NOT NULL DEFAULT 'ACTIVE'
        )
    ''')

    # ============================================================
    # ATTENDANCE
    # ============================================================

    c.execute('''
        CREATE TABLE IF NOT EXISTS attendance (
            attendance_id INTEGER PRIMARY KEY AUTOINCREMENT,

            student_id TEXT,

            date TEXT,

            time TEXT,

            status TEXT,

            subject TEXT,

            faculty TEXT,

            FOREIGN KEY(student_id)
                REFERENCES students(student_id)
        )
    ''')

    # ============================================================
    # A8 — ADD SESSION ID TO EXISTING ATTENDANCE TABLE
    # ============================================================

    attendance_columns = [
        row['name']
        for row in conn.execute(
            "PRAGMA table_info(attendance)"
        ).fetchall()
    ]

    if 'session_id' not in attendance_columns:

        c.execute('''
            ALTER TABLE attendance
            ADD COLUMN session_id INTEGER
        ''')

    # ============================================================
    # LOGS
    # ============================================================

    c.execute('''
        CREATE TABLE IF NOT EXISTS logs (
            log_id INTEGER PRIMARY KEY AUTOINCREMENT,

            event TEXT,

            timestamp DATETIME
                DEFAULT CURRENT_TIMESTAMP,

            sync_status INTEGER
                DEFAULT 0
        )
    ''')

    # ============================================================
    # USERS
    # ============================================================

    c.execute('''
        CREATE TABLE IF NOT EXISTS users (
            user_id INTEGER PRIMARY KEY AUTOINCREMENT,

            username TEXT UNIQUE,

            password_hash TEXT,

            role TEXT
        )
    ''')

    # ============================================================
    # SAVE DATABASE CHANGES
    # ============================================================

    conn.commit()
    conn.close()


# ================================================================
# INITIALIZE DATABASE
# ================================================================

if __name__ == '__main__':
    init_db()