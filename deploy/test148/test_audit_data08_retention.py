import importlib.util
import pathlib
import unittest
from argparse import Namespace
from unittest.mock import patch


MODULE_PATH = pathlib.Path(__file__).with_name("audit-data08-retention.py")
SPEC = importlib.util.spec_from_file_location("audit_data08_retention", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class Data08RetentionAuditTest(unittest.TestCase):
    def test_audit_marks_current_template_deleted_and_unknown_expiry_references(self):
        args = Namespace()
        responses = {
            "information_schema": [["1"]],
            "wx_3d_scene": [["10", "2", "2", "0", "NULL"], ["20", "2", "1", "1", "NULL"]],
            "wx_3d_scene_revision": [["2", "10", "1", "req-1"], ["2", "10", "2", "req-2"], ["2", "20", "1", "req-3"]],
            "wx_3d_scene_template": [["2", "20", "ONLINE", "0"]],
        }

        def fake_mysql(_args, sql):
            if "information_schema.TABLES" in sql:
                return responses["information_schema"]
            if "FROM wx_3d_scene_revision" in sql:
                return responses["wx_3d_scene_revision"]
            if "FROM wx_3d_scene_template" in sql:
                return responses["wx_3d_scene_template"]
            if "FROM wx_3d_scene" in sql:
                return responses["wx_3d_scene"]
            return [["0"]]

        with patch.object(MODULE.VERIFY, "mysql", side_effect=fake_mysql):
            report = MODULE.audit(args)
        self.assertEqual(3, report["revisionCount"])
        self.assertEqual(0, report["reclaimCandidateCount"])
        self.assertIn("current_pointer", report["rows"][1]["reasons"])
        self.assertIn("template_first_revision", report["rows"][2]["reasons"])
        self.assertIn("deleted_scene_audit_window", report["rows"][2]["reasons"])
        self.assertIn("request_id_expiry_unmodeled", report["rows"][0]["reasons"])


if __name__ == "__main__":
    unittest.main()
