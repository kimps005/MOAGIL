import io
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch
from urllib.parse import parse_qs, urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import server
from server import ApiError, Handler


class PhotonSearchTests(unittest.TestCase):
    def setUp(self):
        with server._geocode_lock:
            server._geocode_cache.clear()
            server._geocode_last_request = 0.0

    @staticmethod
    def response(features):
        return io.BytesIO(json.dumps({"features": features}).encode("utf-8"))

    def test_search_limits_results_and_sends_korean_language_preferences(self):
        features = [{"properties": {"name": f"place-{index}"}} for index in range(8)]
        with patch("server.urlopen", return_value=self.response(features)) as open_url, \
                patch("server.time.monotonic", side_effect=[100, 100, 100]):
            result = server._photon_search("  오이도역  ")

        self.assertEqual(len(result["features"]), 6)
        request = open_url.call_args.args[0]
        query = parse_qs(urlsplit(request.full_url).query)
        self.assertEqual(query["q"], ["오이도역"])
        self.assertEqual(query["limit"], ["6"])
        self.assertEqual(request.get_header("Accept-language"), "ko-KR,ko;q=0.9,en;q=0.8")
        self.assertIn("MOAGIL-Prototype", request.get_header("User-agent"))

    def test_normalized_queries_use_the_six_hour_cache(self):
        features = [{"properties": {"name": "오이도역"}}]
        with patch("server.urlopen", return_value=self.response(features)) as open_url, \
                patch("server.time.monotonic", side_effect=[100, 100, 100, 101]):
            first = server._photon_search("오이도역")
            second = server._photon_search("  오이도역  ")

        self.assertEqual(first, second)
        open_url.assert_called_once()

    def test_provider_failure_becomes_a_user_facing_api_error(self):
        with patch("server.urlopen", side_effect=OSError("network unavailable")), \
                patch("server.time.monotonic", side_effect=[100, 100]):
            with self.assertRaises(ApiError) as error:
                server._photon_search("오이도역")

        self.assertEqual(error.exception.status, 502)
        self.assertEqual(error.exception.code, "GEOCODER_UNAVAILABLE")


class GeocodeEndpointTests(unittest.TestCase):
    def handler(self, query):
        value = object.__new__(Handler)
        value.path = f"/api/geocode?q={query}"
        value._path = lambda: "/api/geocode"
        value._send_json = MagicMock()
        value._error = MagicMock()
        return value

    def test_endpoint_decodes_query_and_returns_provider_results(self):
        handler = self.handler("%EC%98%A4%EC%9D%B4%EB%8F%84%EC%97%AD")
        expected = {"features": [{"properties": {"name": "오이도역"}}]}
        with patch("server._photon_search", return_value=expected) as search:
            handler.do_GET()

        search.assert_called_once_with("오이도역")
        handler._send_json.assert_called_once_with(200, expected)
        handler._error.assert_not_called()

    def test_endpoint_rejects_queries_shorter_than_two_characters(self):
        handler = self.handler("a")
        handler.do_GET()

        handler._send_json.assert_not_called()
        handler._error.assert_called_once()
        error = handler._error.call_args.args[0]
        self.assertEqual(error.code, "QUERY_TOO_SHORT")


if __name__ == "__main__":
    unittest.main()
