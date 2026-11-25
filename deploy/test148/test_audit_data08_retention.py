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
            "wx_3d_scene_revision": [["2", "10", "1", "req-1", "a" * 64], ["2", "10", "2", "req-2", "a" * 64], ["2", "20", "1", "req-3", "a" * 64]],
            "wx_3d_scene_template": [["2", "20", "ONLINE", "0"]],
            "wx_3d_scene_publication": [["2", "10", "1"]],
        }

        def fake_mysql(_args, sql):
            if sql == "SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP)":
                return [["1800000000"]]
            if "information_schema.TABLES" in sql:
                if "TABLE_NAME='wx_3d_scene'" in sql or "TABLE_NAME='wx_3d_scene_revision'" in sql or "TABLE_NAME='wx_3d_scene_template'" in sql or "TABLE_NAME='wx_3d_scene_publication'" in sql:
                    return responses["information_schema"]
                return [["0"]]
            if "FROM wx_3d_scene_revision" in sql:
                return responses["wx_3d_scene_revision"]
            if "FROM wx_3d_scene_template" in sql:
                return responses["wx_3d_scene_template"]
            if "FROM wx_3d_scene_publication" in sql:
                return responses["wx_3d_scene_publication"]
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
        self.assertIn("publication_reference", report["rows"][0]["reasons"])
        self.assertIn("publication_reference_checked_none", report["rows"][1]["reasons"])
        self.assertIn("persistent_bookmark_table_absent", report["rows"][0]["reasons"])

    def test_audit_reads_active_bookmark_and_request_retention(self):
        args = Namespace()
        def fake_mysql(_args, sql):
            if sql == "SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP)":
                return [["1800000000"]]
            if "information_schema.TABLES" in sql:
                return [["1"]]
            if "FROM wx_3d_scene_revision" in sql:
                return [["2", "10", "1", "req-1", "a" * 64]]
            if "FROM wx_3d_scene_publication" in sql:
                return []
            if "FROM wx_3d_scene_template" in sql:
                return []
            if "FROM wx_3d_scene_bookmark" in sql:
                return [["2", "10", "1", "ACTIVE", "4070908800"]]
            if "FROM wx_3d_scene_request" in sql:
                return [["2", "10", "req-1", "a" * 64, "1", "4070908800"]]
            if "FROM wx_3d_scene" in sql:
                return [["10", "2", "1", "0", "NULL"]]
            return []

        with patch.object(MODULE.VERIFY, "mysql", side_effect=fake_mysql):
            report = MODULE.audit(args)
        reasons = report["rows"][0]["reasons"]
        self.assertIn("persistent_bookmark_reference", reasons)
        self.assertIn("request_retention_active", reasons)

    def test_request_corruption_expiry_and_tenant_isolation_remain_conservative(self):
        cases = [
            ([], "request_retention_record_missing"),
            ([["3", "10", "req", "a" * 64, "1", "4070908800"]], "request_retention_record_missing"),
            ([["2", "10", "req", "a" * 64, "2", "4070908800"]], "request_retention_revision_mismatch"),
            ([["2", "10", "req", "b" * 64, "1", "4070908800"]], "request_retention_hash_mismatch"),
            ([["2", "10", "req", "a" * 64, "1", "bad"]], "request_retention_expiry_invalid"),
            ([["2", "10", "req", "a" * 64, "1", "NaN"]], "request_retention_expiry_invalid"),
            ([["2", "10", "req", "a" * 64, "1", "1799999999"]], "request_retention_expired"),
        ]
        for requests, reason in cases:
            with self.subTest(reason=reason):
                def mysql(_args, sql):
                    if "information_schema.TABLES" in sql: return [["1"]]
                    if sql == "SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP)": return [["1800000000"]]
                    if "FROM wx_3d_scene_revision" in sql: return [["2", "10", "1", "req", "a" * 64]]
                    if "FROM wx_3d_scene_request" in sql: return requests
                    if "FROM wx_3d_scene_bookmark" in sql: return [["2", "10", "1", "UNKNOWN", "0"]]
                    if sql.endswith("FROM wx_3d_scene"): return [["10", "2", "2", "0", "1800000001"]]
                    return []
                with patch.object(MODULE.VERIFY, "mysql", side_effect=mysql):
                    report = MODULE.audit(Namespace())
                self.assertIn(reason, report["rows"][0]["reasons"])
                self.assertIn("persistent_bookmark_reference", report["rows"][0]["reasons"])
                self.assertIn("active_lock", report["rows"][0]["reasons"])
                self.assertEqual(0, report["reclaimCandidateCount"])


if __name__ == "__main__":
    unittest.main()
