import importlib.util
import pathlib
import tempfile
import unittest
from argparse import Namespace
from unittest.mock import patch


MODULE_PATH = pathlib.Path(__file__).with_name("apply-data08-retention-schema.py")
SPEC = importlib.util.spec_from_file_location("apply_data08_retention_schema", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class Data08RetentionSchemaTest(unittest.TestCase):
    def test_preflight_is_read_only_and_requires_clean_base(self):
        args = Namespace(database="wx_editor_data08", apply=False)
        with patch.object(MODULE.VERIFY, "mysql", return_value=[
            ["wx_3d_scene"], ["wx_3d_scene_revision"], ["wx_3d_scene_content"]
        ]):
            report = MODULE.preflight(args)
        self.assertTrue(report["ready"])
        self.assertTrue(report["readOnly"])
        self.assertEqual(["wx_3d_scene_bookmark", "wx_3d_scene_request"], report["tables"])

    def test_existing_reference_table_stops_without_guessing(self):
        args = Namespace(database="wx_editor_data08", apply=False)
        with patch.object(MODULE.VERIFY, "mysql", return_value=[
            ["wx_3d_scene"], ["wx_3d_scene_revision"], ["wx_3d_scene_content"], ["wx_3d_scene_request"]
        ]), self.assertRaises(RuntimeError):
            MODULE.preflight(args)

    def test_apply_requires_confirmation_and_backup_marker(self):
        args = Namespace(confirm="", backup_marker=None, apply=True)
        with self.assertRaises(RuntimeError):
            MODULE.apply(args, {})


if __name__ == "__main__":
    unittest.main()
