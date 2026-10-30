import os
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import server
from server import ApiError, Handler


class FakeSocket:
    def __init__(self, address=None, error=None):
        self.address = address
        self.error = error

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def connect(self, *_args):
        if self.error:
            raise self.error

    def getsockname(self):
        return self.address, 0


class ServerGuardTests(unittest.TestCase):
    def setUp(self):
        with server._join_rate_lock:
            server._join_rate_buckets.clear()

    def handler(self, address="192.0.2.10"):
        value = object.__new__(Handler)
        value.client_address = (address, 12345)
        value.headers = {"Host": "10.0.2.2:8080"}
        value.server = SimpleNamespace(server_port=8080)
        return value

    def test_mobile_join_host_accepts_a_configured_lan_or_domain_address(self):
        for base in ("http://192.168.0.10:8080", "https://moagil.example/"):
            with self.subTest(base=base), patch.dict(os.environ, {"MOAGIL_PUBLIC_BASE_URL": base}):
                self.assertEqual(base.rstrip("/"), self.handler()._join_base())

    def test_mobile_join_host_rejects_non_routable_and_malformed_addresses(self):
        for base in (
            "http://localhost:8080",
            "http://localhost.:8080",
            "http://preview.localhost:8080",
            "http://127.0.0.1:8080",
            "http://0.0.0.0:8080",
            "http://10.0.2.2:8080",
            "http://[::]:8080",
            "https://user:secret@moagil.example",
            "https://moagil.example/path",
            "https://moagil.example:99999",
        ):
            with self.subTest(base=base), patch.dict(os.environ, {"MOAGIL_PUBLIC_BASE_URL": base}):
                with self.assertRaises(ApiError) as raised:
                    self.handler()._join_base()
                self.assertEqual("PUBLIC_URL_INVALID", raised.exception.code)

    def test_emulator_api_host_uses_the_detected_lan_address_for_mobile_qr(self):
        routed_socket = FakeSocket("192.168.1.22")
        with patch.dict(os.environ, {"MOAGIL_PUBLIC_BASE_URL": ""}), patch("server.socket.socket", return_value=routed_socket):
            self.assertEqual("http://192.168.1.22:8080", self.handler()._join_base())

    def test_refuses_to_return_a_loopback_qr_when_lan_address_detection_fails(self):
        failed_socket = FakeSocket(error=OSError("no network route"))
        with patch.dict(os.environ, {"MOAGIL_PUBLIC_BASE_URL": ""}), patch("server.socket.socket", return_value=failed_socket):
            with self.assertRaises(ApiError) as raised:
                self.handler("localhost:8080")._join_base()
            self.assertEqual("PUBLIC_URL_REQUIRED", raised.exception.code)

    def test_join_attempts_are_limited_per_source_ip_and_expire_after_window(self):
        handler = self.handler("198.51.100.8")
        times = [0.0] * (server.JOIN_RATE_LIMIT + 1) + [server.JOIN_RATE_WINDOW_SECONDS + 0.1]
        with patch("server.time.monotonic", side_effect=times):
            for _ in range(server.JOIN_RATE_LIMIT):
                handler._limit_join_attempts()
            with self.assertRaises(ApiError) as raised:
                handler._limit_join_attempts()
            self.assertEqual(429, raised.exception.status)
            self.assertEqual("RATE_LIMITED", raised.exception.code)
            handler._limit_join_attempts()

    def test_new_passenger_reopens_a_planned_session_and_invalidates_its_route(self):
        class ResponseHandler(Handler):
            def _send_json(self, status, payload, extra_headers=None):
                return status, payload

        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[2]) as temporary, patch.object(server, "DATA_DIR", Path(temporary)), patch.object(server, "DB_PATH", Path(temporary) / "sessions.db"):
            server._init_db()
            with server._db_connection() as db:
                session_id, _driver_token, invite_token, _expires = server._new_session(db)
                db.execute("UPDATE sessions SET state='PLANNED',version=version+1,selected_order='[]',route_snapshot='{}' WHERE id=?", (session_id,))

            handler = object.__new__(ResponseHandler)
            status, payload = handler._add_passenger(session_id, {
                "inviteToken": invite_token,
                "displayName": "다음 참가자",
                "point": {"lat": 37.51, "lon": 127.02, "label": "등록 위치"},
                "idempotencyKey": "0123456789abcdef0123456789abcdef",
            })

            self.assertEqual(201, status)
            self.assertEqual("다음 참가자", payload["displayName"])
            with server._db_connection() as db:
                row = server._session_row(db, session_id)
                snapshot = server._snapshot(db, row)
            self.assertEqual("COLLECTING", snapshot["state"])
            self.assertIsNone(snapshot["routeSnapshot"])
            self.assertEqual(1, len(snapshot["passengers"]))


if __name__ == "__main__":
    unittest.main()
