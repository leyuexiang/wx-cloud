"""DATA-08 结构回退工具，默认只读，禁止删除已有共享正文或修订引用。"""
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

ROOT = pathlib.Path(__file__).parent
CONTENT_DOWN = ROOT / "migrations" / "data08" / "001-content-table.down.sql"
REVISION_DOWN = ROOT / "migrations" / "data08" / "002-revision-columns.down.sql"


def prepare(args: argparse.Namespace) -> dict:
    inventory = VERIFY.column_inventory(args)
    if "wx_3d_scene_content" not in inventory or "wx_3d_scene_revision" not in inventory:
        raise RuntimeError("DATA-08 目标结构不存在，停止回退")
    revision_columns = inventory["wx_3d_scene_revision"]
    required = {"content_id", "content_hash", "content_bytes"}
    if not required.issubset(revision_columns):
        raise RuntimeError("修订引用列不完整，停止自动回退")
    rows = VERIFY.mysql(args, "SELECT COUNT(*) FROM wx_3d_scene_content")
    content_count = int(rows[0][0]) if rows else -1
    rows = VERIFY.mysql(args, "SELECT COUNT(*) FROM wx_3d_scene_revision WHERE content_id IS NOT NULL OR content_hash IS NOT NULL OR content_bytes IS NOT NULL")
    reference_count = int(rows[0][0]) if rows else -1
    if content_count != 0 or reference_count != 0:
        raise RuntimeError(f"共享正文或修订引用仍有数据，禁止回退：content={content_count},references={reference_count}")
    if not CONTENT_DOWN.is_file() or not REVISION_DOWN.is_file():
        raise RuntimeError("DATA-08 回退SQL文件缺失")
    return {
        "ready": True, "readOnly": not args.apply, "contentRows": content_count,
        "revisionReferences": reference_count,
        "rollbackOrder": ["002-revision-columns.down.sql", "001-content-table.down.sql"],
        "sqlFiles": [str(REVISION_DOWN), str(CONTENT_DOWN)],
    }


def apply(args: argparse.Namespace, report: dict) -> dict:
    if args.confirm != "DATA08-SCHEMA-ROLLBACK":
        raise RuntimeError("结构回退必须显式传入 --confirm DATA08-SCHEMA-ROLLBACK")
    statements = [REVISION_DOWN.read_text(encoding="utf-8"), CONTENT_DOWN.read_text(encoding="utf-8")]
    for statement in statements:
        VERIFY.mysql(args, statement)
    report.update({"readOnly": False, "databaseWrite": True})
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description="DATA-08 结构回退")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--database", default="wx_editor")
    parser.add_argument("--password-env", default="WX_MYSQL_ROOT_PASSWORD")
    parser.add_argument("--mysql-container")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--confirm", default="")
    args = parser.parse_args()
    try:
        report = prepare(args)
        if args.apply:
            report = apply(args, report)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0
    except Exception as error:
        print(json.dumps({"passed": False, "error": str(error)}, ensure_ascii=False))
        return 2


if __name__ == "__main__":
    sys.exit(main())
