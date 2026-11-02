"""Vercel entry point for the existing MOAGIL HTTP API."""

from __future__ import annotations

import sys
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

BACKEND_DIR = Path(__file__).resolve().parents[1] / "backend"
sys.path.insert(0, str(BACKEND_DIR))

import server as moagil_server  # noqa: E402


class handler(moagil_server.Handler):
    """Route rewritten Vercel API paths through the local request handler."""

    def _path(self) -> str:
        routed_path = parse_qs(urlsplit(self.path).query).get("__moagil_path", [""])[0]
        if routed_path.startswith("/api/") or routed_path == "/api":
            return routed_path
        return super()._path()
