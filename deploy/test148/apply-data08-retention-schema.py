"""DATA-08 保留引用结构迁移；默认 dry-run，写入必须显式确认并提供备份标记。"""
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
UP = ROOT / "migrations" / "data08" / "003-retention-references.up.sql"
DOWN = ROOT / "migrations" / "data08" / "003-retention-references.down.sql"
TABLES = {"wx_3d_scene_bookmark", "wx_3d_scene_request"}


def table_inventory(args: argparse.Namespace) -> set[str]:
    rows = VERIFY.mysql(args, "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")
    return {row[0] for row in rows}


def preflight(args: argparse.Namespace) -> dict:
    inventory = table_inventory(args)
    required = {"wx_3d_scene", "wx_3d_scene_revision", "wx_3d_scene_content"}
    if not required.issubset(inventory):
        raise RuntimeError("DATA-08 基础表未完成，停止保留引用迁移")
    existing = sorted(TABLES & inventory)
    if existing:
        raise RuntimeError("保留引用表已有部分存在，不能自动猜测现场迁移状态：" + ",".join(existing))
    if not UP.is_file() or not DOWN.is_file():
        raise RuntimeError("保留引用结构 SQL 文件缺失")
    return {
        "ready": True,
        "readOnly": not args.apply,
        "database": args.database,
        "migrationOrder": [UP.name],
        "sqlFiles": [str(UP), str(DOWN)],
        "tables": sorted(TABLES),
    }


def apply(args: argparse.Namespace, report: dict) -> dict:
    if args.confirm != "DATA08-RETENTION-SCHEMA-APPLY":
        raise RuntimeError("保留引用结构写入必须显式传入 --confirm DATA08-RETENTION-SCHEMA-APPLY")
    if not args.backup_marker:
        raise RuntimeError("保留引用结构写入必须提供 --backup-marker")
    marker = pathlib.Path(args.backup_marker)
    if not marker.is_file():
        raise RuntimeError("备份标记文件不存在：" + str(marker))
    VERIFY.mysql(args, UP.read_text(encoding="utf-8"))
    report.update({"readOnly": False, "databaseWrite": True, "backupMarker": str(marker)})
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description="DATA-08 保留引用结构迁移")
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
