import importlib.util
import pathlib
import unittest
from argparse import Namespace
from unittest.mock import patch


MODULE_PATH = pathlib.Path(__file__).with_name("verify-data08-roundtrip.py")
SPEC = importlib.util.spec_from_file_location("verify_data08_roundtrip", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class Data08RoundtripTest(unittest.TestCase):
    def test_structure_not_ready_is_explicit(self):
        with patch.object(MODULE.VERIFY, "column_inventory", return_value={"wx_3d_scene_revision": set()}):
            result = MODULE.check(Namespace())
        self.assertFalse(result["passed"])
        self.assertFalse(result["ready"])

    def test_matching_shared_content_passes(self):
        packages = [b"{}", b"{}", b"{}", b"{}"]
        digest = MODULE.VERIFY.content_hash(1, packages)
        revision = {"tenantId": 2, "sceneId": 10, "revisionNo": 1, "schemaVersion": 1,
                    "packages": packages, "contentHashExpected": digest,
                    "contentBytesExpected": 8, "content_id": "7", "content_hash": digest, "content_bytes": "8"}
        content = {7: {"tenantId": 2, "schemaVersion": 1, "packages": packages,
                       "contentHash": digest, "contentBytes": 8, "deleted": 0}}
        with patch.object(MODULE.VERIFY, "column_inventory", return_value={
            "wx_3d_scene_revision": {"content_id", "content_hash", "content_bytes"},
            "wx_3d_scene_content": {"id", "tenant_id", "schema_version", "scene_json", "three_d_json",
                                     "two_d_json", "dependencies_json", "content_hash", "content_bytes", "deleted"},
        }), patch.object(MODULE.VERIFY, "read_legacy_revisions", return_value=[revision]), \
                patch.object(MODULE.VERIFY, "read_contents", return_value=content):
            result = MODULE.check(Namespace())
        self.assertTrue(result["passed"])
        self.assertEqual(0, result["packageMismatches"])


if __name__ == "__main__":
    unittest.main()
