"""Dependency-free HTTP/SQLite server for the MOAGIL P0 demonstration."""

from __future__ import annotations

import hashlib
import hmac
import json
import os
import secrets
import socket
import sqlite3
import sys
import uuid
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit

from domain import RouteError, plan_routes, validate_point, utc_now, evaluate_route

ROOT = Path(__file__).resolve().parents[1]
WEB_ROOT = ROOT / "web"
DATA_DIR = Path(__file__).resolve().parent / "data"
DB_PATH = Path(os.environ.get("MOAGIL_DB", DATA_DIR / "moagil.db"))
MAX_BODY = 128 * 1024

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

    def _headers(self, content_type: str):
        self.send_header("Content-Type", content_type)
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'self'")

    def _send_json(self, status: int, payload: dict):
        data = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self._headers("application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _error(self, error: ApiError):
        self._send_json(error.status, {"error": {"code": error.code, "message": error.message, "requestId": str(uuid.uuid4())}})

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
                return self._send_json(200, {"status": "ok", "mode": "mock"})
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
            if path == "/api/sessions":
                with _db_connection() as db:
                    session_id, driver_token, invite_token, expires = _new_session(db)
                base = self._join_base()
                return self._send_json(201, {"sessionId": session_id, "driverToken": driver_token, "inviteToken": invite_token, "expiresAt": expires, "version": 1, "joinBaseUrl": base})
            if path == "/api/sessions/demo":
                with _db_connection() as db:
                    db.execute("BEGIN IMMEDIATE")
                    session_id, driver_token, invite_token, expires = _new_session(db)
                    for fixture in FIXTURE["passengers"]:
                        db.execute("INSERT INTO passengers VALUES(?,?,?,?,?,?,?)", (str(uuid.uuid4()), session_id, fixture["name"], json.dumps(fixture["point"], ensure_ascii=False), utc_now(), _hash(secrets.token_urlsafe(18)), _hash(fixture["name"])))
                    db.execute("UPDATE sessions SET final_point=?,version=version+1 WHERE id=?", (json.dumps(FIXTURE["finalPoint"], ensure_ascii=False), session_id))
                    row = _session_row(db, session_id)
                    snapshot = _snapshot(db, row)
                base = self._join_base()
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
            db.execute("BEGIN IMMEDIATE")
            row = self._driver(db, session_id)
            self._check_version(row, self._expected_version(body))
            passengers = self._passengers(db, session_id)
            final = _json(row["final_point"])
            results = plan_routes(_json(row["start_point"]), passengers, final, row["priority_id"])
            chosen = results[0]
            snapshot = {**chosen, "evaluatedCount": len(results), "basisVersion": row["version"], "staleAt": (datetime.now(timezone.utc) + timedelta(minutes=5)).isoformat(timespec="seconds").replace("+00:00", "Z"), "providerVersion": "mock-haversine-v1", "note": "시연용 직선거리 추정 · 실제 도로/교통 아님"}
            db.execute("UPDATE sessions SET state='PLANNED',version=version+1,selected_order=?,route_snapshot=? WHERE id=?", (json.dumps(chosen["order"]), json.dumps(snapshot, ensure_ascii=False), session_id))
            updated = _session_row(db, session_id)
            return self._send_json(200, _snapshot(db, updated))

    def _select_order(self, session_id: str, body: dict):
        order = body.get("order")
        if not isinstance(order, list):
            raise ApiError(422, "INVALID_ORDER", "순서를 다시 확인해 주세요")
        with _db_connection() as db:
            db.execute("BEGIN IMMEDIATE")
            row = self._driver(db, session_id)
            self._check_version(row, self._expected_version(body))
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
            estimate = evaluate_route(points)
            old = _json(row["route_snapshot"], {})
            snapshot = {**estimate, "order": order, "evaluatedCount": old.get("evaluatedCount", 1), "basisVersion": row["version"], "staleAt": estimate["fetchedAt"], "providerVersion": "mock-haversine-v1", "note": "시연용 직선거리 추정 · 실제 도로/교통 아님"}
            snapshot["staleAt"] = (datetime.now(timezone.utc) + timedelta(minutes=5)).isoformat(timespec="seconds").replace("+00:00", "Z")
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
        host = self.headers.get("Host", "localhost").split(":")[0]
        if host not in ("localhost", "127.0.0.1", "0.0.0.0", "[::1]"):
            return "http://" + self.headers.get("Host", "localhost")
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.connect(("192.0.2.1", 80))
            address = sock.getsockname()[0]
            sock.close()
        except OSError:
            address = "127.0.0.1"
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
        content_type = {".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8", ".svg": "image/svg+xml"}.get(target.suffix, "application/octet-stream")
        data = target.read_bytes()
        self.send_response(200)
        self._headers(content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main() -> None:
    _init_db()
    port = int(os.environ.get("PORT", "8080"))
    server = ThreadingHTTPServer((os.environ.get("HOST", "0.0.0.0"), port), Handler)
    print("MOAGIL 시연 서버가 준비되었습니다.")
    print("차량 화면: http://localhost:" + str(port))
    print("휴대폰은 차량과 같은 Wi-Fi에서 차량 화면의 초대 링크를 사용하세요.")
    print("종료: Ctrl+C")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nMOAGIL 서버를 종료합니다.")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
