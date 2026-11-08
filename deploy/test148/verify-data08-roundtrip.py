"""DATA-08 迁移后共享正文双读回读校验，只读不修改数据库。"""
from __future__ import annotations

import argparse
import importlib.util
import json
import pathlib
import sys


VERIFY_PATH = pathlib.Path(__file__).with_name("verify-data08-db.py")
VERIFY_SPEC = importlib.util.spec_from_file_location("verify_data08_db", VERIFY_PATH)
VERIFY = importlib.util.module_from_spec(VERIFY_SPEC)
VERIFY_SPEC.loader.exec_module(VERIFY)


def check(args: argparse.Namespace) -> dict:
    inventory = VERIFY.column_inventory(args)
    revision_columns = inventory.get("wx_3d_scene_revision", set())
    content_columns = inventory.get("wx_3d_scene_content", set())
    required_content_columns = {"id", "tenant_id", "schema_version", "scene_json", "three_d_json",
                                "two_d_json", "dependencies_json", "content_hash", "content_bytes", "deleted"}
    if not required_content_columns.issubset(content_columns) or not {"content_id", "content_hash", "content_bytes"}.issubset(revision_columns):
        return {"passed": False, "ready": False, "readOnly": True,
                "message": "共享正文表或修订引用列尚未完成结构迁移"}

    revisions = VERIFY.read_legacy_revisions(args, revision_columns)
    contents = VERIFY.read_contents(args)
    missing = []
    tenant_violations = []
    schema_mismatches = []
    hash_mismatches = []
    byte_mismatches = []
    package_mismatches = []
    for revision in revisions:
        identity = f"{revision['tenantId']}/{revision['sceneId']}/{revision['revisionNo']}"
        content_id = revision.get("content_id")
        if content_id is None or int(content_id) not in contents:
            missing.append(identity)
            continue
        content = contents[int(content_id)]
        if content["deleted"]:
            missing.append(identity)
            continue
        if content["tenantId"] != revision["tenantId"]:
            tenant_violations.append(identity)
        if content["schemaVersion"] != revision["schemaVersion"]:
            schema_mismatches.append(identity)
        if content["packages"] != revision["packages"]:
            package_mismatches.append(identity)
        if revision.get("content_hash") != revision["contentHashExpected"] or content["contentHash"] != revision["contentHashExpected"]:
            hash_mismatches.append(identity)
        if revision.get("content_bytes") is None or int(revision["content_bytes"]) != revision["contentBytesExpected"] or content["contentBytes"] != revision["contentBytesExpected"]:
            byte_mismatches.append(identity)

    errors = missing + tenant_violations + schema_mismatches + hash_mismatches + byte_mismatches + package_mismatches
    return {
        "passed": not errors, "ready": True, "readOnly": True, "revisionCount": len(revisions),
        "contentRowCount": len(contents), "missingReferences": len(missing),
        "tenantIsolationViolations": len(tenant_violations), "schemaMismatches": len(schema_mismatches),
        "hashMismatches": len(hash_mismatches), "byteMismatches": len(byte_mismatches),
        "packageMismatches": len(package_mismatches), "errorIdentities": errors[:100],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="DATA-08 迁移后共享正文回读校验")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--database", default="wx_editor")
    parser.add_argument("--password-env", default="WX_MYSQL_ROOT_PASSWORD")
    parser.add_argument("--mysql-container")
    args = parser.parse_args()
    try:
        report = check(args)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0 if report["passed"] else 1
    except Exception as error:
        print(json.dumps({"passed": False, "error": str(error)}, ensure_ascii=False))
        return 2


if __name__ == "__main__":
    sys.exit(main())
