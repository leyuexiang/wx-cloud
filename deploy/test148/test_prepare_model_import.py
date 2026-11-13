"""覆盖本批迁移准备的目标范围、部分迁移和完整增量核验边界。"""

import copy
import importlib.util
from pathlib import Path
import unittest


SPEC = importlib.util.spec_from_file_location("prepare_model_import", Path(__file__).with_name("prepare-model-import.py"))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def original_schema():
    """构造已现场核实的结构起点，测试仅修改各自需要覆盖的差异。"""
    columns = {name: {"type": "bigint", "nullable": "YES", "default": "", "collation": ""}
               for name in MODULE.ART_COLUMNS}
    return {
        "columns": {
            "wx_3d_resource": columns,
            "wx_3d_resource_operation": {"id": {"type": "char(36)", "collation": "ascii_bin"}},
            "wx_3d_model": {"resource_id": {}, "model_file_id": {"nullable": "NO"}},
        },
        "indexes": {"wx_3d_resource_operation": {
            "uk_dim3d_operation_resource_id": {"unique": True, "columns": ["resource_id", "id"]},
        }},
        "constraints": "",
    }


def migrated_schema():
    result = original_schema()
    result["columns"]["wx_3d_resource"]["upload_source"] = {"nullable": "YES"}
    result["columns"]["wx_3d_resource_import"] = {
        "available_at": {"nullable": "NO", "default": "utc_timestamp(3)"},
        "artifact_descriptor": {}, "publish_descriptor": {}, "attempt_id": {}, "lease_until": {},
    }
    result["columns"]["wx_3d_model"]["model_file_id"]["nullable"] = "YES"
    result["constraints"] = "\n".join(("ck_dim3d_resource_upload_source", "fk_dim3d_import_operation",
                                        "ck_dim3d_import_publish_descriptor", "ck_dim3d_model_resource_link"))
    return result


class ModelImportPreparationTest(unittest.TestCase):
    def test_accepts_both_confirmed_sources(self):
        for database in MODULE.DATABASES:
            with self.subTest(database=database):
                MODULE.check_preconditions(database, original_schema())

    def test_rejects_other_database(self):
        with self.assertRaises(ValueError):
            MODULE.check_preconditions("another_database", original_schema())

    def test_rejects_partial_import_migration(self):
        cases = ("upload_source", "import_table", "catalog_constraint")
        for case in cases:
            with self.subTest(case=case):
                schema = original_schema()
                if case == "upload_source":
                    schema["columns"]["wx_3d_resource"]["upload_source"] = {}
                elif case == "import_table":
                    schema["columns"]["wx_3d_resource_import"] = {}
                else:
                    schema["constraints"] = "ck_dim3d_model_resource_link"
                with self.assertRaises(ValueError):
                    MODULE.check_preconditions("wx_editor", schema)

    def test_rejects_invalid_foreign_key_prerequisites(self):
        schema = original_schema()
        schema["indexes"]["wx_3d_resource_operation"]["uk_dim3d_operation_resource_id"]["columns"].reverse()
        with self.assertRaises(ValueError):
            MODULE.check_preconditions("wx_editor", schema)
        schema = original_schema()
        schema["columns"]["wx_3d_resource_operation"]["id"]["collation"] = "utf8mb4_0900_ai_ci"
        with self.assertRaises(ValueError):
            MODULE.check_preconditions("wx_editor", schema)

    def test_accepts_complete_migration(self):
        MODULE.verify_migrated(migrated_schema())

    def test_rejects_incomplete_publication_or_queue_migration(self):
        correct = migrated_schema()
        cases = (("available_at", "default", "CURRENT_TIMESTAMP(3)"),
                 ("available_at", "nullable", "YES"))
        for field, attribute, value in cases:
            with self.subTest(attribute=attribute):
                schema = copy.deepcopy(correct)
                schema["columns"]["wx_3d_resource_import"][field][attribute] = value
                with self.assertRaises(ValueError):
                    MODULE.verify_migrated(schema)
        schema = copy.deepcopy(correct)
        del schema["columns"]["wx_3d_resource_import"]["publish_descriptor"]
        with self.assertRaises(ValueError):
            MODULE.verify_migrated(schema)

    def test_rejects_unsafe_row_fingerprint_identifier(self):
        with self.assertRaises(ValueError):
            MODULE.row_fingerprint("unused", "wx_3d_resource", ["id;DROP TABLE x"])

    def test_apply_rejects_other_target_changed_sql_and_schema(self):
        """跨库复用、替换 SQL 和结构漂移均会使演练证明失效。"""
        schema = original_schema()
        checksums = {name: name for name in MODULE.MIGRATIONS}
        report = {"status": "REHEARSED", "sourceDatabase": "wx_editor",
                  "originalRowsUnchanged": True, "sourceSchemaUnchanged": True,
                  "sourceSchemaSha256": MODULE.schema_fingerprint(schema), "migrationSha256": checksums}
        MODULE.check_rehearsal_report("wx_editor", schema, checksums, report)
        with self.assertRaises(ValueError):
            MODULE.check_rehearsal_report("wx_editor_data08", schema, checksums, report)
        with self.assertRaises(ValueError):
            MODULE.check_rehearsal_report("wx_editor", schema, {**checksums, MODULE.MIGRATIONS[0]: "changed"}, report)
        changed = copy.deepcopy(schema)
        changed["columns"]["wx_3d_model"]["new_column"] = {}
        with self.assertRaises(ValueError):
            MODULE.check_rehearsal_report("wx_editor", changed, checksums, report)


if __name__ == "__main__":
    unittest.main()
