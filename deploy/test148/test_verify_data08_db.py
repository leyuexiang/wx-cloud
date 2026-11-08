import importlib.util
import pathlib
import unittest
from argparse import Namespace
from unittest.mock import patch

MODULE_PATH = pathlib.Path(__file__).with_name("verify-data08-db.py")
SPEC = importlib.util.spec_from_file_location("verify_data08_db", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
content_bytes = MODULE.content_bytes
content_hash = MODULE.content_hash

MIGRATE_PATH = pathlib.Path(__file__).with_name("migrate-data08-db.py")
MIGRATE_SPEC = importlib.util.spec_from_file_location("migrate_data08_db", MIGRATE_PATH)
MIGRATE = importlib.util.module_from_spec(MIGRATE_SPEC)
MIGRATE_SPEC.loader.exec_module(MIGRATE)


class Data08DigestContractTest(unittest.TestCase):
    def test_java_framing_is_stable_and_utf8_aware(self):
        packages = ["园区".encode(), b"{}", b"[]", b"\xe2\x9c\x93"]
        self.assertEqual(13, content_bytes(packages))
        self.assertEqual(content_hash(1, packages), content_hash(1, packages))
        self.assertNotEqual(content_hash(1, [b"ab", b"c", b"d", b"e"]), content_hash(1, [b"a", b"bc", b"d", b"e"]))
        self.assertNotEqual(content_hash(1, packages), content_hash(2, packages))

    def test_mysql_streams_large_backfill_to_standard_input(self):
        # 大四包生成的十六进制 SQL 超过常见进程参数限制，连接参数仍单独传递。
        sql = "SELECT UNHEX('" + "61" * (256 * 1024) + "');\n"
        for container in (None, "mysql8"):
            with self.subTest(container=container), patch.object(MODULE.subprocess, "run") as run:
                run.return_value.returncode = 0
                run.return_value.stdout = "1\t2\n"
                args = Namespace(password_env=None, host="127.0.0.1", port=3306,
                                 user="root", database="wx_editor_data08", mysql_container=container)
                self.assertEqual([["1", "2"]], MODULE.mysql(args, sql))
                command = run.call_args.args[0]
                self.assertTrue("-e" not in command and sql not in command)
                self.assertTrue(run.call_args.kwargs.get("input") == sql)

    def test_migration_plan_groups_same_tenant_content_without_writing(self):
        packages = [b'{"sceneType":"gis"}', b'{"nodes":[]}', b'{"layers":[]}', b'{"resources":[]}']
        revision = {
            "tenantId": 2, "sceneId": 10, "revisionNo": 1, "schemaVersion": 1,
            "packages": packages, "contentHashExpected": content_hash(1, packages),
            "contentBytesExpected": content_bytes(packages), "content_id": None,
        }
        second = {**revision, "revisionNo": 2}
        args = Namespace(apply=False)
        with patch.object(MIGRATE.VERIFY, "column_inventory", return_value={
            "wx_3d_scene_revision": {"content_id", "content_hash", "content_bytes"},
            "wx_3d_scene_content": {"id", "tenant_id", "schema_version", "scene_json", "three_d_json",
                                     "two_d_json", "dependencies_json", "content_hash", "content_bytes", "deleted"},
        }), patch.object(MIGRATE.VERIFY, "read_legacy_revisions", return_value=[revision, second]), \
                patch.object(MIGRATE.VERIFY, "read_contents", return_value={}):
            report, sql = MIGRATE.plan(args)
        self.assertEqual(1, report["newContentRows"])
        self.assertEqual(2, report["revisionReferencesToWrite"])
        self.assertEqual(1, sql.count("INSERT INTO wx_3d_scene_content"))
        self.assertIn(f",{revision['contentBytesExpected']},CURRENT_TIMESTAMP,0);", sql)
        self.assertEqual(2, sql.count("UPDATE wx_3d_scene_revision SET"))
        self.assertTrue(report["dryRun"])


if __name__ == "__main__":
    unittest.main()
