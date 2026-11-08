"""DATA-08 结构迁移工具，默认 dry-run，写入必须显式确认和提供备份标记。"""
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
CONTENT_UP = ROOT / "migrations" / "data08" / "001-content-table.up.sql"
REVISION_UP = ROOT / "migrations" / "data08" / "002-revision-columns.up.sql"


def preflight(args: argparse.Namespace) -> dict:
    inventory = VERIFY.column_inventory(args)
    required = {"wx_3d_scene", "wx_3d_scene_revision"}
    if not required.issubset(inventory):
        raise RuntimeError("当前数据库缺少 wx_3d_scene 或 wx_3d_scene_revision，停止结构迁移")
    if "wx_3d_scene_content" in inventory:
        raise RuntimeError("wx_3d_scene_content 已存在，禁止重复执行结构迁移")
    revision_columns = inventory["wx_3d_scene_revision"]
    existing = {"content_id", "content_hash", "content_bytes"} & revision_columns
    if existing:
        raise RuntimeError("修订引用列已有部分存在，不能自动猜测现场迁移状态：" + ",".join(sorted(existing)))
    if not CONTENT_UP.is_file() or not REVISION_UP.is_file():
        raise RuntimeError("DATA-08 结构SQL文件缺失")
    return {
        "ready": True,
        "readOnly": not args.apply,
        "database": args.database,
        "migrationOrder": ["001-content-table.up.sql", "002-revision-columns.up.sql"],
        "sqlFiles": [str(CONTENT_UP), str(REVISION_UP)],
    }


def apply(args: argparse.Namespace, report: dict) -> dict:
    if args.confirm != "DATA08-SCHEMA-APPLY":
        raise RuntimeError("结构写入必须显式传入 --confirm DATA08-SCHEMA-APPLY")
    if not args.backup_marker:
        raise RuntimeError("结构写入必须提供 --backup-marker，指向已完成备份的记录文件")
    marker = pathlib.Path(args.backup_marker)
    if not marker.is_file():
        raise RuntimeError("备份标记文件不存在：" + str(marker))
    statements = [CONTENT_UP.read_text(encoding="utf-8"), REVISION_UP.read_text(encoding="utf-8")]
    for statement in statements:
        VERIFY.mysql(args, statement)
    report.update({"readOnly": False, "databaseWrite": True, "backupMarker": str(marker)})
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description="DATA-08 结构迁移")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--database", default="wx_editor")
    parser.add_argument("--password-env", default="WX_MYSQL_ROOT_PASSWORD")
    parser.add_argument("--mysql-container")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--confirm", default="")
    parser.add_argument("--backup-marker")
    args = parser.parse_args()
    try:
        report = preflight(args)
        if args.apply:
            report = apply(args, report)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0
    except Exception as error:
        print(json.dumps({"passed": False, "error": str(error)}, ensure_ascii=False))
        return 2


if __name__ == "__main__":
    sys.exit(main())
