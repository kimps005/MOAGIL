import os
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import server


class FakeCursor:
    rowcount = 1


class FakePostgresConnection:
    def __init__(self):
        self.statements = []
        self.closed = False

    def execute(self, statement, parameters=()):
        self.statements.append((statement, parameters))
        return FakeCursor()

    def close(self):
        self.closed = True


class HostedStorageTests(unittest.TestCase):
    def postgres_modules(self, connection, captured_kwargs):
        psycopg = types.ModuleType("psycopg")
        psycopg.__path__ = []

        def connect(*args, **kwargs):
            captured_kwargs.update(kwargs)
            return connection

        psycopg.connect = connect
        rows = types.ModuleType("psycopg.rows")
        rows.dict_row = object()
        return {"psycopg": psycopg, "psycopg.rows": rows}

    def test_supabase_connection_uses_dict_rows_and_transaction_pooling_safe_lock(self):
        raw = FakePostgresConnection()
        connect_kwargs = {}
        modules = self.postgres_modules(raw, connect_kwargs)
        with patch.dict(sys.modules, modules), patch.dict(os.environ, {
            "MOAGIL_DATABASE_URL": "postgresql://supabase.example/db?sslmode=require",
            "VERCEL": "1",
        }, clear=False):
            database = server._connect()
            database.execute("BEGIN IMMEDIATE")
            database.execute("UPDATE sessions SET state=? WHERE id=?", ("ACTIVE", "session-1"))
            database.commit()
            database.close()

        self.assertEqual(raw.statements, [
            ("BEGIN", ()),
            ("SELECT pg_advisory_xact_lock(%s)", (781122409,)),
            ("UPDATE sessions SET state=%s WHERE id=%s", ("ACTIVE", "session-1")),
            ("COMMIT", ()),
        ])
        self.assertTrue(raw.closed)
        self.assertTrue(connect_kwargs["autocommit"])
        self.assertIsNone(connect_kwargs["prepare_threshold"])
        self.assertEqual(connect_kwargs["sslmode"], "require")

    def test_vercel_refuses_ephemeral_sqlite_when_supabase_url_is_missing(self):
        with patch.dict(os.environ, {"VERCEL": "1"}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "MOAGIL_DATABASE_URL"):
                server._connect()


if __name__ == "__main__":
    unittest.main()
