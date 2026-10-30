import io
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import server


class MapTileCacheTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(dir=Path(__file__).parent, prefix=".tile-cache-test-")
        self.addCleanup(self.temp_dir.cleanup)
        self.cache_patch = patch.object(server, "MAP_TILE_CACHE_DIR", Path(self.temp_dir.name))
        self.cache_patch.start()
        self.addCleanup(self.cache_patch.stop)

    def test_tile_is_identified_cached_and_reused(self):
        response = io.BytesIO(b"png-tile")
        response.headers = {
            "Content-Type": "image/png",
            "Cache-Control": "public, max-age=86400",
            "ETag": '"tile-v1"',
        }
        with patch("server.time.time", return_value=1000), patch("server.urlopen", return_value=response) as open_url:
            first = server._load_map_tile(12, 3546, 1616)
            second = server._load_map_tile(12, 3546, 1616)

        self.assertEqual(first[0], b"png-tile")
        self.assertEqual(second, first)
        self.assertEqual(first[1]["cacheControl"], "public, max-age=86400")
        self.assertEqual(first[1]["expiresAt"], 87400)
        request = open_url.call_args.args[0]
        self.assertEqual(request.full_url, "https://tile.openstreetmap.org/12/3546/1616.png")
        self.assertIn("MOAGIL-Prototype", request.get_header("User-agent"))
        open_url.assert_called_once()

    def test_missing_upstream_cache_headers_use_seven_day_cache(self):
        response = io.BytesIO(b"png-tile")
        response.headers = {"Content-Type": "image/png"}
        with patch("server.time.time", return_value=1000), patch("server.urlopen", return_value=response):
            _, metadata = server._load_map_tile(12, 3546, 1616)

        self.assertEqual(metadata["expiresAt"], 1000 + server.MAP_TILE_DEFAULT_TTL_SECONDS)
        self.assertEqual(metadata["cacheControl"], f"public, max-age={server.MAP_TILE_DEFAULT_TTL_SECONDS}")

    def test_tile_response_keeps_the_upstream_cache_policy_without_no_store(self):
        class RecordingHandler:
            def __init__(self):
                self.headers = []
                self.body = io.BytesIO()
                self.wfile = self.body

            def send_response(self, status):
                self.status = status

            def send_header(self, name, value):
                self.headers.append((name, value))

            def _headers(self, content_type, **kwargs):
                server.Handler._headers(self, content_type, **kwargs)

            def end_headers(self):
                pass

        handler = RecordingHandler()
        server.Handler._send_map_tile(handler, b"png-tile", {
            "contentType": "image/png",
            "cacheControl": "public, max-age=86400",
        })

        cache_headers = [value for name, value in handler.headers if name.casefold() == "cache-control"]
        self.assertEqual(handler.status, 200)
        self.assertEqual(cache_headers, ["public, max-age=86400"])
        self.assertEqual(handler.body.getvalue(), b"png-tile")


if __name__ == "__main__":
    unittest.main()
