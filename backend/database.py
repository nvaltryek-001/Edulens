"""Dual SQLite/PostgreSQL database adapter for EduLens.

SQLite remains the default for local development. Set DATABASE_URL to use PostgreSQL.
Uses psycopg 3 when DATABASE_URL is configured.
"""
from __future__ import annotations

import os
import sqlite3

DATABASE_URL = os.environ.get("DATABASE_URL", "").strip()
DB_PATH = os.environ.get(
    "EDULENS_DB_PATH",
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "edulens.db"),
)


class _PostgresCursor:
    def __init__(self, cursor, inserted_id=None):
        self._cursor = cursor
        self.lastrowid = inserted_id

    def fetchone(self):
        return self._cursor.fetchone()

    def fetchall(self):
        return self._cursor.fetchall()

    @property
    def rowcount(self):
        return self._cursor.rowcount

    def __iter__(self):
        return iter(self._cursor)


class _PostgresConnection:
    """Small compatibility layer for the SQLite-style API used by app.py."""
    def __init__(self, connection):
        self._connection = connection

    @staticmethod
    def _sql(sql):
        # Current app SQL uses qmark placeholders; psycopg uses %s.
        return sql.replace("?", "%s")

    def execute(self, sql, params=()):
        clean = sql.strip().rstrip(";")
        wants_id = (
            clean.lower().startswith("insert into attendance_sessions")
            and " returning " not in clean.lower()
        )
        if wants_id:
            clean += " RETURNING session_id"
        cursor = self._connection.cursor()
        cursor.execute(self._sql(clean), params)
        inserted_id = None
        if wants_id:
            row = cursor.fetchone()
            inserted_id = row["session_id"] if row else None
            # Keep the returned cursor usable for rowcount/fetch calls.
            cursor = self._connection.cursor()
        return _PostgresCursor(cursor, inserted_id)

    def cursor(self):
        return _PostgresCursor(self._connection.cursor())

    def commit(self):
        self._connection.commit()

    def rollback(self):
        self._connection.rollback()

    def close(self):
        self._connection.close()


def get_db_connection():
    if DATABASE_URL:
        try:
            import psycopg
            from psycopg.rows import dict_row
        except ImportError as exc:
            raise RuntimeError(
                "DATABASE_URL is set but psycopg[binary] is not installed"
            ) from exc
        raw = psycopg.connect(
            DATABASE_URL,
            row_factory=dict_row,
            connect_timeout=10,
        )
        return _PostgresConnection(raw)

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    conn = get_db_connection()
    try:
        c = conn.cursor()
        if DATABASE_URL:
            statements = [
                """CREATE TABLE IF NOT EXISTS students (
                    student_id TEXT PRIMARY KEY, name TEXT, usn TEXT,
                    department TEXT, semester TEXT, section TEXT,
                    email TEXT, phone_number TEXT
                )""",
                """CREATE TABLE IF NOT EXISTS attendance_sessions (
                    session_id BIGSERIAL PRIMARY KEY, subject TEXT NOT NULL,
                    faculty TEXT NOT NULL, department TEXT NOT NULL,
                    semester TEXT NOT NULL, section TEXT NOT NULL,
                    start_time TEXT NOT NULL, end_time TEXT,
                    status TEXT NOT NULL DEFAULT 'ACTIVE'
                )""",
                """CREATE TABLE IF NOT EXISTS attendance (
                    attendance_id BIGSERIAL PRIMARY KEY, student_id TEXT,
                    date TEXT, time TEXT, status TEXT, subject TEXT,
                    faculty TEXT, session_id BIGINT,
                    FOREIGN KEY(student_id) REFERENCES students(student_id)
                )""",
                """CREATE TABLE IF NOT EXISTS logs (
                    log_id BIGSERIAL PRIMARY KEY, event TEXT,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    sync_status INTEGER DEFAULT 0
                )""",
                """CREATE TABLE IF NOT EXISTS users (
                    user_id BIGSERIAL PRIMARY KEY, username TEXT UNIQUE,
                    password_hash TEXT, role TEXT
                )""",
                "ALTER TABLE attendance ADD COLUMN IF NOT EXISTS session_id BIGINT",
            ]
            for statement in statements:
                c._cursor.execute(statement)
        else:
            c.execute("""CREATE TABLE IF NOT EXISTS students (
                student_id TEXT PRIMARY KEY, name TEXT, usn TEXT,
                department TEXT, semester TEXT, section TEXT,
                email TEXT, phone_number TEXT
            )""")
            c.execute("""CREATE TABLE IF NOT EXISTS attendance_sessions (
                session_id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject TEXT NOT NULL, faculty TEXT NOT NULL,
                department TEXT NOT NULL, semester TEXT NOT NULL,
                section TEXT NOT NULL, start_time TEXT NOT NULL,
                end_time TEXT, status TEXT NOT NULL DEFAULT 'ACTIVE'
            )""")
            c.execute("""CREATE TABLE IF NOT EXISTS attendance (
                attendance_id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_id TEXT, date TEXT, time TEXT, status TEXT,
                subject TEXT, faculty TEXT,
                FOREIGN KEY(student_id) REFERENCES students(student_id)
            )""")
            cols = [row["name"] for row in conn.execute(
                "PRAGMA table_info(attendance)"
            ).fetchall()]
            if "session_id" not in cols:
                c.execute("ALTER TABLE attendance ADD COLUMN session_id INTEGER")
            c.execute("""CREATE TABLE IF NOT EXISTS logs (
                log_id INTEGER PRIMARY KEY AUTOINCREMENT, event TEXT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                sync_status INTEGER DEFAULT 0
            )""")
            c.execute("""CREATE TABLE IF NOT EXISTS users (
                user_id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE, password_hash TEXT, role TEXT
            )""")
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    init_db()
