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
        self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self' https://oapi.map.naver.com; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self' https:; object-src 'none'; base-uri 'none'; frame-ancestors 'self'")

    def _send_json(self, status: int, payload: dict, extra_headers: dict[str, str] | None = None):
        data = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self._headers("application/json; charset=utf-8")
        for name, value in (extra_headers or {}).items():
            self.send_header(name, value)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _error(self, error: ApiError):
        headers = {"Retry-After": str(JOIN_RATE_WINDOW_SECONDS)} if error.status == 429 else None
        self._send_json(error.status, {"error": {"code": error.code, "message": error.message, "requestId": str(uuid.uuid4())}}, headers)

    def _limit_join_attempts(self):
        address = self.client_address[0]
        now = time.monotonic()
        with _join_rate_lock:
            attempts = _join_rate_buckets.setdefault(address, deque())
            while attempts and now - attempts[0] >= JOIN_RATE_WINDOW_SECONDS:
                attempts.popleft()
            if len(attempts) >= JOIN_RATE_LIMIT:
                raise ApiError(429, "RATE_LIMITED", "요청이 잠시 많습니다. 잠시 뒤 다시 시도해 주세요")
            attempts.append(now)
            _join_rate_buckets.move_to_end(address)
            if len(_join_rate_buckets) > MAX_JOIN_RATE_BUCKETS:
                for stale_address, bucket in list(_join_rate_buckets.items()):
                    if not bucket or now - bucket[-1] >= JOIN_RATE_WINDOW_SECONDS:
                        del _join_rate_buckets[stale_address]
                while len(_join_rate_buckets) > MAX_JOIN_RATE_BUCKETS:
                    _join_rate_buckets.popitem(last=False)

    def _body(self) -> dict:
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError as exc:
            raise ApiError(400, "INVALID_CONTENT_LENGTH", "요청 내용을 읽을 수 없습니다") from exc
        if length > MAX_BODY:
            raise ApiError(413, "BODY_TOO_LARGE", "요청 내용이 너무 큽니다")
        try:
            value = json.loads(self.rfile.read(length) or b"{}")
        except (json.JSONDecodeError, UnicodeDecodeError) as exc:
            raise ApiError(400, "INVALID_JSON", "요청 형식이 올바르지 않습니다") from exc
        if not isinstance(value, dict):
            raise ApiError(400, "INVALID_JSON", "요청 형식이 올바르지 않습니다")
        return value

    def _path(self) -> str:
        return unquote(urlsplit(self.path).path)

    def _driver(self, db: sqlite3.Connection, session_id: str):
        row = _session_row(db, session_id)
        if row is None:
            raise ApiError(404, "SESSION_NOT_FOUND", "이동 정보를 찾을 수 없습니다")
        token = self.headers.get("Authorization", "")
        token = token[7:] if token.startswith("Bearer ") else ""
        if not token or not _constant_time_hash_equal(token, row["driver_hash"]):
            raise ApiError(401, "UNAUTHORIZED", "차량 인증이 필요합니다")
        if row["state"] == "EXPIRED":
            raise ApiError(410, "SESSION_EXPIRED", "이동 초대가 만료되었습니다")
        return row

    def _expected_version(self, body: dict) -> int:
        value = body.get("expectedVersion")
        if value is None:
            value = self.headers.get("If-Match", "").strip('"')
        try:
            return int(value)
        except (TypeError, ValueError) as exc:
            raise ApiError(400, "VERSION_REQUIRED", "최신 화면 버전을 확인해 주세요") from exc

    def _check_version(self, row, expected: int):
        if row["version"] != expected:
            raise ApiError(409, "VERSION_CONFLICT", "다른 변경이 있어 최신 상태를 다시 불러왔습니다")

    def _passengers(self, db, session_id):
        return [
            {"id": p["id"], "displayName": p["display_name"], "point": _json(p["point"])}
            for p in db.execute("SELECT id,display_name,point FROM passengers WHERE session_id=? ORDER BY created_at,id", (session_id,)).fetchall()
        ]

    def do_GET(self):
        try:
            path = self._path()
            if path == "/api/health":
                return self._send_json(200, {"status": "ok", "mode": routing_provider()})
            if path == "/api/config":
                map_client_id = os.environ.get("MOAGIL_NAVER_CLIENT_ID", "").strip()
                return self._send_json(200, {
                    "routingProvider": routing_provider(),
                    "mapProvider": "naver" if map_client_id else "osm",
                    "naverMapClientId": map_client_id,
                })
            if path.startswith("/api/sessions/"):
                parts = path.split("/")
                if len(parts) != 4:
                    raise ApiError(404, "NOT_FOUND", "요청 경로를 찾을 수 없습니다")
                with _db_connection() as db:
                    row = self._driver(db, parts[3])
                    return self._send_json(200, _snapshot(db, row))
            self._serve_static(path)
        except ApiError as error:
            self._error(error)
        except BrokenPipeError:
            return
        except Exception:
            self._error(ApiError(500, "INTERNAL_ERROR", "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요"))

    def do_POST(self):
        try:
            path = self._path()
            body = self._body()
            parts = path.split("/")
            if len(parts) == 5 and parts[1:3] == ["api", "sessions"] and parts[4] in ("join-info", "passengers"):
                self._limit_join_attempts()
            if path == "/api/sessions":
                base = self._join_base()
                with _db_connection() as db:
                    session_id, driver_token, invite_token, expires = _new_session(db)
                return self._send_json(201, {"sessionId": session_id, "driverToken": driver_token, "inviteToken": invite_token, "expiresAt": expires, "version": 1, "joinBaseUrl": base})
            if path == "/api/sessions/demo":
                base = self._join_base()
                with _db_connection() as db:
                    db.execute("BEGIN IMMEDIATE")
                    session_id, driver_token, invite_token, expires = _new_session(db)
                    for fixture in FIXTURE["passengers"]:
                        db.execute("INSERT INTO passengers VALUES(?,?,?,?,?,?,?)", (str(uuid.uuid4()), session_id, fixture["name"], json.dumps(fixture["point"], ensure_ascii=False), utc_now(), _hash(secrets.token_urlsafe(18)), _hash(fixture["name"])))
                    db.execute("UPDATE sessions SET final_point=?,version=version+1 WHERE id=?", (json.dumps(FIXTURE["finalPoint"], ensure_ascii=False), session_id))
                    row = _session_row(db, session_id)
                    snapshot = _snapshot(db, row)
                return self._send_json(201, {"sessionId": session_id, "driverToken": driver_token, "inviteToken": invite_token, "expiresAt": expires, "version": snapshot["version"], "joinBaseUrl": base, "session": snapshot})

            if path.startswith("/api/sessions/"):
                parts = path.split("/")
                if len(parts) == 5 and parts[4] == "join-info":
                    session_id = parts[3]
                    with _db_connection() as db:
                        row = _session_row(db, session_id)
                        self._verify_invite(row, body.get("inviteToken"))
                        count = db.execute("SELECT COUNT(*) FROM passengers WHERE session_id=?", (session_id,)).fetchone()[0]
                        if row["state"] not in ("COLLECTING", "PLANNED"):
                            raise ApiError(409, "SESSION_CLOSED", "이 이동은 더 이상 참가자를 받지 않습니다")
                        return self._send_json(200, {"state": row["state"], "remaining": max(0, 4-count), "expiresAt": row["expires_at"]})
                if len(parts) == 5 and parts[4] == "plan":
                    return self._plan(parts[3], body)
                if len(parts) == 5 and parts[4] == "start":
                    return self._start(parts[3], body)
                if len(parts) == 5 and parts[4] == "complete-trip":
                    return self._complete_trip(parts[3], body)
                if len(parts) == 5 and parts[4] == "cancel":
                    return self._cancel(parts[3], body)
                if len(parts) == 5 and parts[4] == "complete-stop":
                    return self._complete_stop(parts[3], body)
                if len(parts) == 5 and parts[4] == "select-order":
                    return self._select_order(parts[3], body)
                if len(parts) == 5 and parts[4] == "passengers":
                    return self._add_passenger(parts[3], body)
            raise ApiError(404, "NOT_FOUND", "요청 경로를 찾을 수 없습니다")
        except ApiError as error:
            self._error(error)
        except RouteError as error:
            self._error(ApiError(422, "INVALID_ROUTE", str(error)))
        except RoutingError as error:
            self._error(ApiError(503, "ROUTING_UNAVAILABLE", str(error)))
        except BrokenPipeError:
            return
        except Exception:
            self._error(ApiError(500, "INTERNAL_ERROR", "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요"))

    def do_PUT(self):
        try:
            parts = self._path().split("/")
            body = self._body()
            if len(parts) != 5 or parts[4] != "settings":
                raise ApiError(404, "NOT_FOUND", "요청 경로를 찾을 수 없습니다")
            session_id = parts[3]
            with _db_connection() as db:
                db.execute("BEGIN IMMEDIATE")
                row = self._driver(db, session_id)
                self._check_version(row, self._expected_version(body))
                if row["state"] not in ("COLLECTING", "PLANNED"):
                    raise ApiError(409, "SESSION_ACTIVE", "이동을 시작한 뒤에는 설정을 바꿀 수 없습니다")
                start = validate_point(body.get("startPoint"), "startPoint")
                final = validate_point(body["finalPoint"], "finalPoint") if body.get("finalPoint") else None
                priority = body.get("priorityPassengerId")
                ids = {p["id"] for p in self._passengers(db, session_id)}
                if priority and priority not in ids:
                    raise ApiError(422, "INVALID_PRIORITY", "먼저 내릴 동승자를 목록에서 선택해 주세요")
                db.execute("UPDATE sessions SET state='COLLECTING',version=version+1,start_point=?,final_point=?,priority_id=?,selected_order=NULL,route_snapshot=NULL,active_index=0 WHERE id=?", (json.dumps(start, ensure_ascii=False), json.dumps(final, ensure_ascii=False) if final else None, priority, session_id))
                row = _session_row(db, session_id)
                return self._send_json(200, _snapshot(db, row))
        except ApiError as error:
            self._error(error)
        except RouteError as error:
            self._error(ApiError(422, "INVALID_POINT", str(error)))
        except Exception:
            self._error(ApiError(500, "INTERNAL_ERROR", "설정을 저장하지 못했습니다"))

    def do_DELETE(self):
        try:
            parts = self._path().split("/")
            if len(parts) != 6 or parts[4] != "passengers":
                raise ApiError(404, "NOT_FOUND", "요청 경로를 찾을 수 없습니다")
            session_id, passenger_id = parts[3], parts[5]
            with _db_connection() as db:
                db.execute("BEGIN IMMEDIATE")
                row = self._driver(db, session_id)
                expected = self.headers.get("If-Match", "").strip('"')
                self._check_version(row, int(expected))
                if row["state"] not in ("COLLECTING", "PLANNED"):
                    raise ApiError(409, "SESSION_ACTIVE", "이동을 시작한 뒤에는 동승자를 삭제할 수 없습니다")
                db.execute("DELETE FROM passengers WHERE id=? AND session_id=?", (passenger_id, session_id))
                if db.total_changes == 0:
                    raise ApiError(404, "PASSENGER_NOT_FOUND", "동승자를 찾을 수 없습니다")
                db.execute("UPDATE sessions SET state='COLLECTING',version=version+1,priority_id=CASE WHEN priority_id=? THEN NULL ELSE priority_id END,selected_order=NULL,route_snapshot=NULL WHERE id=?", (passenger_id, session_id))
                row = _session_row(db, session_id)
                return self._send_json(200, _snapshot(db, row))
        except ApiError as error:
            self._error(error)
        except (TypeError, ValueError):
            self._error(ApiError(400, "VERSION_REQUIRED", "최신 화면 버전을 확인해 주세요"))
        except Exception:
            self._error(ApiError(500, "INTERNAL_ERROR", "동승자를 삭제하지 못했습니다"))

    def _verify_invite(self, row, token):
        if row is None:
            raise ApiError(404, "SESSION_NOT_FOUND", "이동 정보를 찾을 수 없습니다")
        if not isinstance(token, str) or not _constant_time_hash_equal(token, row["invite_hash"]):
            raise ApiError(401, "INVALID_INVITE", "초대 링크를 확인할 수 없습니다")
        if row["state"] == "EXPIRED":
            raise ApiError(410, "SESSION_EXPIRED", "초대 링크가 만료되었습니다")

    def _add_passenger(self, session_id: str, body: dict):
        name = str(body.get("displayName", "")).strip()
        if not 1 <= len(name) <= 20:
            raise ApiError(422, "INVALID_NAME", "별명은 1~20자로 입력해 주세요")
        point = validate_point(body.get("point"))
        invite = body.get("inviteToken")
        idem = body.get("idempotencyKey")
        if not isinstance(idem, str) or not 16 <= len(idem) <= 128:
            raise ApiError(422, "INVALID_IDEMPOTENCY_KEY", "요청을 다시 시도할 수 없습니다. 화면을 새로 열어 주세요")
        payload_hash = _hash(json.dumps({"displayName": name, "point": point}, sort_keys=True, ensure_ascii=False))
        with _db_connection() as db:
            db.execute("BEGIN IMMEDIATE")
            row = _session_row(db, session_id)
            self._verify_invite(row, invite)
            existing = db.execute("SELECT id,display_name,point,payload_hash FROM passengers WHERE session_id=? AND idempotency_hash=?", (session_id, _hash(idem))).fetchone()
            if existing:
                if not hmac.compare_digest(existing["payload_hash"], payload_hash):
                    raise ApiError(409, "IDEMPOTENCY_CONFLICT", "다른 내용으로 이미 접수된 요청입니다")
                return self._send_json(200, {"passengerId": existing["id"], "displayName": existing["display_name"], "point": _json(existing["point"]), "duplicate": True})
            if row["state"] != "COLLECTING":
                raise ApiError(409, "SESSION_CLOSED", "참가 등록이 마감되었습니다")
            count = db.execute("SELECT COUNT(*) FROM passengers WHERE session_id=?", (session_id,)).fetchone()[0]
            if count >= 4:
                raise ApiError(409, "SESSION_FULL", "동승자는 최대 4명까지 등록할 수 있습니다")
            passenger_id = str(uuid.uuid4())
            db.execute("INSERT INTO passengers VALUES(?,?,?,?,?,?,?)", (passenger_id, session_id, name, json.dumps(point, ensure_ascii=False), utc_now(), _hash(idem), payload_hash))
            db.execute("UPDATE sessions SET state='COLLECTING',version=version+1,selected_order=NULL,route_snapshot=NULL WHERE id=?", (session_id,))
            return self._send_json(201, {"passengerId": passenger_id, "displayName": name, "point": point})

    def _plan(self, session_id: str, body: dict):
        with _db_connection() as db:
            row = self._driver(db, session_id)
            expected_version = self._expected_version(body)
            self._check_version(row, expected_version)
            start = _json(row["start_point"])
            passengers = self._passengers(db, session_id)
            final = _json(row["final_point"])
            priority_id = row["priority_id"]
            basis_version = row["version"]

        provider = routing_provider()
        if provider == "osrm":
            matrix_points = [start, *[person["point"] for person in passengers]]
            if final:
                matrix_points.append(final)
            evaluator = matrix_evaluator(matrix_points)
        elif provider == "naver":
            evaluator = naver_route_evaluator()
        else:
            evaluator = evaluate_route
        results = plan_routes(
            start,
            passengers,
            final,
            priority_id,
            evaluator,
            max_workers=2 if provider == "naver" else 1,
            timeout_seconds=120 if provider == "naver" else None,
        )
        chosen = results[0]
        if provider == "osrm":
            by_id = {person["id"]: person for person in passengers}
            route_points = [start, *[by_id[item]["point"] for item in chosen["order"]]]
            if final:
                route_points.append(final)
            chosen = {**chosen, **fetch_route(route_points)}
        note = NAVER_NOTE if provider == "naver" else OSRM_NOTE if provider == "osrm" else "시연용 직선거리 추정 · 실제 도로/교통 아님"
        provider_version = NAVER_PROVIDER_VERSION if provider == "naver" else OSRM_PROVIDER_VERSION if provider == "osrm" else "mock-haversine-v1"
        snapshot = {
            **chosen,
            "evaluatedCount": len(results),
            "basisVersion": basis_version,
            "staleAt": (datetime.now(timezone.utc) + timedelta(minutes=5)).isoformat(timespec="seconds").replace("+00:00", "Z"),
            "providerVersion": provider_version,
            "note": note,
            "attribution": NAVER_ATTRIBUTION if provider == "naver" else OSRM_ATTRIBUTION if provider == "osrm" else None,
        }
        with _db_connection() as db:
            db.execute("BEGIN IMMEDIATE")
            row = self._driver(db, session_id)
            self._check_version(row, expected_version)
            db.execute("UPDATE sessions SET state='PLANNED',version=version+1,selected_order=?,route_snapshot=? WHERE id=?", (json.dumps(chosen["order"]), json.dumps(snapshot, ensure_ascii=False), session_id))
            updated = _session_row(db, session_id)
            return self._send_json(200, _snapshot(db, updated))

    def _select_order(self, session_id: str, body: dict):
        order = body.get("order")
        if not isinstance(order, list):
            raise ApiError(422, "INVALID_ORDER", "순서를 다시 확인해 주세요")
        with _db_connection() as db:
            row = self._driver(db, session_id)
            expected_version = self._expected_version(body)
            self._check_version(row, expected_version)
            passengers = self._passengers(db, session_id)
            expected = {p["id"] for p in passengers}
            if len(order) != len(passengers) or set(order) != expected:
                raise ApiError(422, "INVALID_ORDER", "모든 동승자를 한 번씩 포함해야 합니다")
            if row["priority_id"] and order[0] != row["priority_id"]:
                raise ApiError(422, "PRIORITY_VIOLATION", "먼저 내릴 동승자는 첫 번째에 두어야 합니다")
            by_id = {p["id"]: p for p in passengers}
            points = [_json(row["start_point"]), *[by_id[item]["point"] for item in order]]
            final = _json(row["final_point"])
            if final:
                points.append(final)
            old = _json(row["route_snapshot"], {})
            basis_version = row["version"]

        provider = routing_provider()
        estimate = fetch_naver_route(points) if provider == "naver" else fetch_route(points) if provider == "osrm" else evaluate_route(points)
        note = NAVER_NOTE if provider == "naver" else OSRM_NOTE if provider == "osrm" else "시연용 직선거리 추정 · 실제 도로/교통 아님"
        provider_version = NAVER_PROVIDER_VERSION if provider == "naver" else OSRM_PROVIDER_VERSION if provider == "osrm" else "mock-haversine-v1"
        snapshot = {
            **estimate,
            "order": order,
            "evaluatedCount": old.get("evaluatedCount", 1),
            "basisVersion": basis_version,
            "staleAt": (datetime.now(timezone.utc) + timedelta(minutes=5)).isoformat(timespec="seconds").replace("+00:00", "Z"),
            "providerVersion": provider_version,
            "note": note,
            "attribution": NAVER_ATTRIBUTION if provider == "naver" else OSRM_ATTRIBUTION if provider == "osrm" else None,
        }
        with _db_connection() as db:
            db.execute("BEGIN IMMEDIATE")
            row = self._driver(db, session_id)
            self._check_version(row, expected_version)
            db.execute("UPDATE sessions SET state='PLANNED',version=version+1,selected_order=?,route_snapshot=? WHERE id=?", (json.dumps(order), json.dumps(snapshot, ensure_ascii=False), session_id))
            return self._send_json(200, _snapshot(db, _session_row(db, session_id)))

    def _start(self, session_id: str, body: dict):
        with _db_connection() as db:
            db.execute("BEGIN IMMEDIATE")
            row = self._driver(db, session_id)
            self._check_version(row, self._expected_version(body))
            route = _json(row["route_snapshot"])
            if row["state"] != "PLANNED" or not route:
                raise ApiError(409, "PLAN_REQUIRED", "경로를 먼저 계산해 주세요")
            if _parse_time(route["staleAt"]) <= datetime.now(timezone.utc):
                raise ApiError(409, "PLAN_STALE", "계산 결과가 오래되어 다시 계산해야 합니다")
            db.execute("UPDATE sessions SET state='ACTIVE',version=version+1,active_index=0 WHERE id=?", (session_id,))
            return self._send_json(200, _snapshot(db, _session_row(db, session_id)))

    def _complete_stop(self, session_id: str, body: dict):
        with _db_connection() as db:
            db.execute("BEGIN IMMEDIATE")
            row = self._driver(db, session_id)
            self._check_version(row, self._expected_version(body))
            order = _json(row["selected_order"], [])
            if row["state"] != "ACTIVE" or row["active_index"] >= len(order) or body.get("passengerId") != order[row["active_index"]]:
                raise ApiError(409, "STOP_ORDER_CONFLICT", "현재 안내 중인 동승자를 확인해 주세요")
            db.execute("UPDATE sessions SET version=version+1,active_index=active_index+1 WHERE id=?", (session_id,))
            return self._send_json(200, _snapshot(db, _session_row(db, session_id)))

    def _complete_trip(self, session_id: str, body: dict):
        with _db_connection() as db:
            db.execute("BEGIN IMMEDIATE")
            row = self._driver(db, session_id)
            self._check_version(row, self._expected_version(body))
            order = _json(row["selected_order"], [])
            if row["state"] != "ACTIVE" or row["active_index"] < len(order):
                raise ApiError(409, "TRIP_INCOMPLETE", "모든 하차 단계를 마친 뒤 종료할 수 있습니다")
            db.execute("UPDATE sessions SET state='COMPLETED',version=version+1 WHERE id=?", (session_id,))
            return self._send_json(200, _snapshot(db, _session_row(db, session_id)))

    def _cancel(self, session_id: str, body: dict):
        with _db_connection() as db:
            db.execute("BEGIN IMMEDIATE")
            row = self._driver(db, session_id)
            self._check_version(row, self._expected_version(body))
            if row["state"] in ("COMPLETED", "CANCELLED", "EXPIRED"):
                raise ApiError(409, "SESSION_CLOSED", "이미 종료된 이동입니다")
            db.execute("UPDATE sessions SET state='CANCELLED',version=version+1,route_snapshot=NULL WHERE id=?", (session_id,))
            return self._send_json(200, _snapshot(db, _session_row(db, session_id)))

    def _join_base(self):
        configured = os.environ.get("MOAGIL_PUBLIC_BASE_URL", "").strip()
        if configured:
            parsed = urlsplit(configured)
            host = (parsed.hostname or "").lower().rstrip(".")
            if parsed.scheme not in ("http", "https") or not host or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
                raise ApiError(500, "PUBLIC_URL_INVALID", "MOAGIL_PUBLIC_BASE_URL은 공개 가능한 http(s) 주소의 호스트여야 합니다")
            try:
                address = ipaddress.ip_address(host)
                if address.is_loopback or address.is_unspecified or str(address) == "10.0.2.2":
                    raise ApiError(500, "PUBLIC_URL_INVALID", "MOAGIL_PUBLIC_BASE_URL에 localhost, loopback, unspecified, Android 에뮬레이터 전용 주소를 사용할 수 없습니다")
            except ValueError:
                if host == "localhost" or host.endswith(".localhost") or "." not in host:
                    raise ApiError(500, "PUBLIC_URL_INVALID", "MOAGIL_PUBLIC_BASE_URL 주소를 확인해 주세요")
            try:
                port = parsed.port
            except ValueError as error:
                raise ApiError(500, "PUBLIC_URL_INVALID", "MOAGIL_PUBLIC_BASE_URL 포트를 확인해 주세요") from error
            if port is not None and not 1 <= port <= 65535:
                raise ApiError(500, "PUBLIC_URL_INVALID", "MOAGIL_PUBLIC_BASE_URL 포트를 확인해 주세요")
            return f"{parsed.scheme}://{parsed.netloc}"
        host_header = self.headers.get("Host", "localhost").strip()
        try:
            parsed_host = urlsplit("//" + host_header)
            host = (parsed_host.hostname or "").lower().rstrip(".")
            port = parsed_host.port
            safe_authority = bool(host) and not (parsed_host.username or parsed_host.password or parsed_host.path or parsed_host.query or parsed_host.fragment)
        except ValueError:
            host, port, safe_authority = "", None, False
        try:
            address = ipaddress.ip_address(host)
            needs_lan_fallback = address.is_loopback or address.is_unspecified or str(address) == "10.0.2.2"
        except ValueError:
            needs_lan_fallback = not host or host == "localhost" or host.endswith(".localhost") or "." not in host
        if safe_authority and not needs_lan_fallback:
            authority = f"[{host}]" if ":" in host else host
            if port is not None:
                authority += ":" + str(port)
            return "http://" + authority
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
                sock.connect(("192.0.2.1", 80))
                address = sock.getsockname()[0]
        except OSError:
            raise ApiError(500, "PUBLIC_URL_REQUIRED", "휴대폰이 접근할 서버 LAN 주소를 찾지 못했습니다. MOAGIL_PUBLIC_BASE_URL을 설정해 주세요")
        if ipaddress.ip_address(address).is_loopback or ipaddress.ip_address(address).is_unspecified:
            raise ApiError(500, "PUBLIC_URL_REQUIRED", "휴대폰이 접근할 서버 LAN 주소를 확인하지 못했습니다. MOAGIL_PUBLIC_BASE_URL을 설정해 주세요")
        port = self.server.server_port
        return "http://" + address + ":" + str(port)

    def _serve_static(self, path: str):
        if path == "/" or path.startswith("/join/"):
            target = WEB_ROOT / "index.html"
        else:
            target = (WEB_ROOT / path.lstrip("/")).resolve()
            if WEB_ROOT.resolve() not in target.parents:
                raise ApiError(404, "NOT_FOUND", "요청 경로를 찾을 수 없습니다")
        if not target.is_file():
            raise ApiError(404, "NOT_FOUND", "요청 경로를 찾을 수 없습니다")
        content_type = {".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8", ".svg": "image/svg+xml", ".png": "image/png"}.get(target.suffix, "application/octet-stream")
        data = target.read_bytes()
        self.send_response(200)
        self._headers(content_type, static_page=target.suffix == ".html")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main() -> None:
    _init_db()
    provider = routing_provider()
    port = int(os.environ.get("PORT", "8080"))
    server = ThreadingHTTPServer((os.environ.get("HOST", "0.0.0.0"), port), Handler)
    print("MOAGIL 시연 서버가 준비되었습니다.")
    print("차량 화면: http://localhost:" + str(port))
    print("휴대폰은 QR 초대 링크로 인터넷을 통해 접속할 수 있습니다.")
    print("경로 계산: " + ("네이버 Directions 5 (실시간 교통)" if provider == "naver" else "OSM 실제 도로망 (실시간 교통 없음)" if provider == "osrm" else "모의 직선거리"))
    print("종료: Ctrl+C")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nMOAGIL 서버를 종료합니다.")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
