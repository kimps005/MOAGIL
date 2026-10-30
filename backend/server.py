"""Dependency-free HTTP/SQLite server for the MOAGIL P0 demonstration."""

from __future__ import annotations

import hashlib
import hmac
import ipaddress
import json
import os
import secrets
import socket
import sqlite3
import sys
import threading
import time
import uuid
from collections import OrderedDict, deque
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit

from domain import RouteError, plan_routes, validate_point, utc_now, evaluate_route
from routing import (
    OSRM_ATTRIBUTION,
    OSRM_NOTE,
    OSRM_PROVIDER_VERSION,
    NAVER_ATTRIBUTION,
    NAVER_NOTE,
    NAVER_PROVIDER_VERSION,
    RoutingError,
    fetch_naver_route,
    fetch_route,
    matrix_evaluator,
    naver_route_evaluator,
    routing_provider,
)

ROOT = Path(__file__).resolve().parents[1]


def _load_env_file() -> None:
    env_path = ROOT / ".env"
    if not env_path.is_file():
        return
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        name, value = name.strip(), value.strip()
        if name and name.replace("_", "").isalnum() and name[0].isalpha() and name not in os.environ:
            if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
                value = value[1:-1]
            os.environ[name] = value


_load_env_file()
WEB_ROOT = ROOT / "web"
DATA_DIR = Path(__file__).resolve().parent / "data"
DB_PATH = Path(os.environ.get("MOAGIL_DB", DATA_DIR / "moagil.db"))
MAX_BODY = 128 * 1024
JOIN_RATE_LIMIT = 30
JOIN_RATE_WINDOW_SECONDS = 60
MAX_JOIN_RATE_BUCKETS = 4096
_join_rate_lock = threading.Lock()
_join_rate_buckets: OrderedDict[str, deque[float]] = OrderedDict()

FIXTURE = {
    "startPoint": {"lat": 37.525, "lon": 127.000, "label": "시연 출발지"},
    "passengers": [
        {"name": "동승자 A", "point": {"lat": 37.545, "lon": 126.970, "label": "시연 지점 A"}},
        {"name": "동승자 B", "point": {"lat": 37.550, "lon": 127.030, "label": "시연 지점 B"}},
        {"name": "동승자 C", "point": {"lat": 37.500, "lon": 127.050, "label": "시연 지점 C"}},
        {"name": "동승자 D", "point": {"lat": 37.480, "lon": 126.980, "label": "시연 지점 D"}},
    ],
    "finalPoint": {"lat": 37.510, "lon": 126.950, "label": "운전자 최종 목적지"},
}


def _parse_time(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def _hash(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _constant_time_hash_equal(candidate: str, stored_hash: str) -> bool:
    return hmac.compare_digest(_hash(candidate), stored_hash)


def _connect() -> sqlite3.Connection:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH, timeout=8, isolation_level=None)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys=ON")
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA busy_timeout=8000")
    return conn


@contextmanager
def _db_connection():
    db = _connect()
    try:
        yield db
    except Exception:
        db.rollback()
        raise
    else:
        db.commit()
    finally:
        db.close()


def _init_db() -> None:
    with _db_connection() as db:
        db.executescript("""
        CREATE TABLE IF NOT EXISTS sessions (
            id TEXT PRIMARY KEY,
            state TEXT NOT NULL,
            version INTEGER NOT NULL,
            created_at TEXT NOT NULL,
            expires_at TEXT NOT NULL,
            driver_hash TEXT NOT NULL,
            invite_hash TEXT NOT NULL,
            start_point TEXT NOT NULL,
            final_point TEXT,
            priority_id TEXT,
            selected_order TEXT,
            active_index INTEGER NOT NULL DEFAULT 0,
            route_snapshot TEXT
        );
        CREATE TABLE IF NOT EXISTS passengers (
            id TEXT PRIMARY KEY,
            session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
            display_name TEXT NOT NULL,
            point TEXT NOT NULL,
            created_at TEXT NOT NULL,
            idempotency_hash TEXT NOT NULL,
            payload_hash TEXT NOT NULL,
            UNIQUE(session_id, idempotency_hash)
        );
        CREATE INDEX IF NOT EXISTS passengers_session_idx ON passengers(session_id, created_at);
        """)
        db.execute("DELETE FROM sessions WHERE expires_at < ?", ((datetime.now(timezone.utc) - timedelta(hours=24)).isoformat(),))


def _json(value: str | None, fallback=None):
    return json.loads(value) if value else fallback


def _session_row(db: sqlite3.Connection, session_id: str):
    row = db.execute("SELECT * FROM sessions WHERE id=?", (session_id,)).fetchone()
    if row and row["state"] not in ("COMPLETED", "CANCELLED", "EXPIRED") and _parse_time(row["expires_at"]) <= datetime.now(timezone.utc):
        db.execute("UPDATE sessions SET state='EXPIRED',version=version+1,route_snapshot=NULL WHERE id=?", (session_id,))
        row = db.execute("SELECT * FROM sessions WHERE id=?", (session_id,)).fetchone()
    return row


def _snapshot(db: sqlite3.Connection, row: sqlite3.Row) -> dict:
    passengers = db.execute(
        "SELECT id,display_name,point,created_at FROM passengers WHERE session_id=? ORDER BY created_at,id",
        (row["id"],),
    ).fetchall()
    return {
        "sessionId": row["id"],
        "state": row["state"],
        "version": row["version"],
        "createdAt": row["created_at"],
        "expiresAt": row["expires_at"],
        "startPoint": _json(row["start_point"]),
        "finalPoint": _json(row["final_point"]),
        "priorityPassengerId": row["priority_id"],
        "passengers": [
            {"id": p["id"], "displayName": p["display_name"], "point": _json(p["point"]), "createdAt": p["created_at"]}
            for p in passengers
        ],
        "selectedOrder": _json(row["selected_order"], []),
        "activeIndex": row["active_index"],
        "routeSnapshot": _json(row["route_snapshot"]),
    }


def _new_session(db: sqlite3.Connection) -> tuple[str, str, str, str]:
    session_id = str(uuid.uuid4())
    driver_token = secrets.token_urlsafe(32)
    invite_token = secrets.token_urlsafe(32)
    created = datetime.now(timezone.utc)
    expires = created + timedelta(hours=2)
    db.execute(
        "INSERT INTO sessions(id,state,version,created_at,expires_at,driver_hash,invite_hash,start_point) VALUES(?,?,?,?,?,?,?,?)",
        (session_id, "COLLECTING", 1, created.isoformat(timespec="seconds").replace("+00:00", "Z"), expires.isoformat(timespec="seconds").replace("+00:00", "Z"), _hash(driver_token), _hash(invite_token), json.dumps(FIXTURE["startPoint"], ensure_ascii=False)),
    )
    return session_id, driver_token, invite_token, expires.isoformat(timespec="seconds").replace("+00:00", "Z")


class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str):
        self.status = status
        self.code = code
        self.message = message


class Handler(BaseHTTPRequestHandler):
    server_version = "MOAGIL-Demo/1.0"

    def log_message(self, _format, *args):
        # Request paths can contain invitation tokens. Keep access logs free of them.
        return

    def _headers(self, content_type: str, *, static_page: bool = False):
        self.send_header("Content-Type", content_type)
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "strict-origin-when-cross-origin" if static_page else "no-referrer")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self' https://oapi.map.naver.com; style-src 'self' 'un