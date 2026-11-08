import importlib.util
import pathlib
import unittest
from argparse import Namespace
from unittest.mock import patch


MODULE_PATH = pathlib.Path(__file__).with_name("rollback-data08-schema.py")
SPEC = importlib.util.spec_from_file_location("rollback_data08_schema", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class Data08SchemaRollbackTest(unittest.TestCase):
    def test_empty_shared_layer_can_prepare_rollback(self):
        args = Namespace(apply=False)

        def fake_mysql(_args, sql):
            return [["0"]] if "COUNT" in sql else []

        with patch.object(MODULE.VERIFY, "column_inventory", return_value={
            "wx_3d_scene_revision": {"content_id", "content_hash", "content_bytes"},
            "wx_3d_scene_content": {"id"},
        }), patch.object(MODULE.VERIFY, "mysql", side_effect=fake_mysql):
            result = MODULE.prepare(args)
        self.assertTrue(result["ready"])
        self.assertTrue(result["readOnly"])
        self.assertEqual(["002-revision-columns.down.sql", "001-content-table.down.sql"], result["rollbackOrder"])

    def test_non_empty_shared_layer_is_blocked(self):
        args = Namespace(apply=False)

        def fake_mysql(_args, sql):
            return [["1"]] if "wx_3d_scene_content" in sql else [["0"]]

        with patch.object(MODULE.VERIFY, "column_inventory", return_value={
            "wx_3d_scene_revision": {"content_id", "content_hash", "content_bytes"},
            "wx_3d_scene_content": {"id"},
        }), patch.object(MODULE.VERIFY, "mysql", side_effect=fake_mysql), self.assertRaises(RuntimeError):
            MODULE.prepare(args)


if __name__ == "__main__":
    unittest.main()
