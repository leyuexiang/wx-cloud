import importlib.util
import pathlib
import tempfile
import unittest
from argparse import Namespace
from unittest.mock import patch


MODULE_PATH = pathlib.Path(__file__).with_name("apply-data08-schema.py")
SPEC = importlib.util.spec_from_file_location("apply_data08_schema", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class Data08SchemaApplyTest(unittest.TestCase):
    def test_preflight_accepts_clean_schema_in_dry_run(self):
        args = Namespace(database="wx_editor", apply=False)
        with patch.object(MODULE.VERIFY, "column_inventory", return_value={
            "wx_3d_scene": {"id"}, "wx_3d_scene_revision": {"id", "scene_json"}
        }):
            report = MODULE.preflight(args)
        self.assertTrue(report["ready"])
        self.assertTrue(report["readOnly"])

    def test_partial_revision_columns_are_rejected(self):
        args = Namespace(database="wx_editor", apply=False)
        with patch.object(MODULE.VERIFY, "column_inventory", return_value={
            "wx_3d_scene": {"id"}, "wx_3d_scene_revision": {"id", "content_hash"}
        }), self.assertRaises(RuntimeError):
            MODULE.preflight(args)

    def test_apply_requires_confirmation_and_backup_marker(self):
        args = Namespace(confirm="", backup_marker=None, apply=True)
        with self.assertRaises(RuntimeError):
            MODULE.apply(args, {})


if __name__ == "__main__":
    unittest.main()
