import os
import sys
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


if __name__ == "__main__":
    unittest.main()
