import sqlite3
import os

DB_PATH = os.environ.get(
    'EDULENS_DB_PATH',
    os.path.join(os.path.dirname(os.path.abspath(__file__)), 'edulens.db')
)


def get_db_connection():
    # timeout + WAL let several faculty phones write at the same time
    conn = sqlite3.connect(DB_PATH, timeout=15)
    conn.row_factory = sqlite3.Row
    conn.execute('PRAGMA journal_mode=WAL')
    conn.execute('PRAGMA foreign_keys=ON')
    return conn


def _add_column_if_missing(conn, table, column, ddl):
    cols = [r['name'] for r in conn.execute(f'PRAGMA table_info({table})').fetchall()]
    if column not in cols:
        conn.execute(f'ALTER TABLE {table} ADD COLUMN {column} {ddl}')


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
    # REAL-DEPLOYMENT ADDITIONS (parents, ownership, review flow)
    # ============================================================

    _add_column_if_missing(conn, 'students', 'parent_name', 'TEXT')
    _add_column_if_missing(conn, 'students', 'parent_phone', 'TEXT')
    _add_column_if_missing(conn, 'students', 'parent_email', 'TEXT')
    _add_column_if_missing(conn, 'attendance_sessions', 'created_by', 'TEXT')
    _add_column_if_missing(conn, 'attendance_sessions', 'finalized_by', 'TEXT')
    _add_column_if_missing(conn, 'attendance', 'source', "TEXT DEFAULT 'AI'")
    _add_column_if_missing(conn, 'attendance', 'confidence', 'REAL')

    # AI matches wait here until the teacher confirms the session
    c.execute('''
        CREATE TABLE IF NOT EXISTS session_candidates (
            session_id INTEGER NOT NULL,
            student_id TEXT NOT NULL,
            confidence REAL,
            detected_at TEXT,
            PRIMARY KEY (session_id, student_id)
        )
    ''')

    # One row per message, so a parent is never alerted twice for the same event
    c.execute('''
        CREATE TABLE IF NOT EXISTS parent_notifications (
            notification_id INTEGER PRIMARY KEY AUTOINCREMENT,
            student_id TEXT NOT NULL,
            event_key TEXT NOT NULL,
            kind TEXT NOT NULL,
            channel TEXT NOT NULL,
            recipient TEXT,
            message TEXT,
            status TEXT NOT NULL,
            error TEXT,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP,
            UNIQUE (student_id, event_key, channel)
        )
    ''')

    # Remove duplicate session rows left by older builds, then enforce uniqueness
    c.execute('''
        DELETE FROM attendance
        WHERE session_id IS NOT NULL
          AND attendance_id NOT IN (
              SELECT MIN(attendance_id) FROM attendance
              WHERE session_id IS NOT NULL
              GROUP BY session_id, student_id
          )
    ''')
    c.execute('''
        CREATE UNIQUE INDEX IF NOT EXISTS ux_attendance_session_student
        ON attendance(session_id, student_id)
        WHERE session_id IS NOT NULL
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