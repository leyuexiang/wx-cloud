"""DATA-08 迁移前只读预检；默认不写数据库、不创建库、不执行DDL。"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import struct
import subprocess
import sys
from collections import defaultdict


PACKAGE_COUNT = 4
ENCODING_VERSION = 1


def content_bytes(packages: list[bytes]) -> int:
    if len(packages) != PACKAGE_COUNT:
        raise ValueError("场景修订必须包含四个正文包")
    return sum(len(value) for value in packages)


def content_hash(schema_version: int, packages: list[bytes]) -> str:
    if schema_version < 1 or len(packages) != PACKAGE_COUNT:
        raise ValueError("结构版本或四包数量无效")
    framed = struct.pack(">iii", ENCODING_VERSION, schema_version, PACKAGE_COUNT)
    framed += b"".join(struct.pack(">i", len(value)) + value for value in packages)
    return hashlib.sha256(framed).hexdigest()


def mysql(args: argparse.Namespace, sql: str) -> list[list[str]]:
    env = os.environ.copy()
    if args.password_env:
        env["MYSQL_PWD"] = os.environ.get(args.password_env, "")
    # 四包回填的十六进制 SQL 可能超过单个进程参数上限，统一从标准输入传给客户端。
    mysql_args = ["--protocol=TCP", "-h", args.host, "-P", str(args.port), "-u", args.user,
                  "--batch", "--raw", "--skip-column-names", args.database]
    if args.mysql_container:
        command = ["docker", "exec", "-i", args.mysql_container, "sh", "-c",
                   'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql "$@"', "--", *mysql_args]
    else:
        command = ["mysql", *mysql_args]
    result = subprocess.run(command, env=env, input=sql, capture_output=True, text=True, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or "mysql命令执行失败")
    return [line.split("\t") for line in result.stdout.splitlines() if line]


def column_inventory(args: argparse.Namespace) -> dict[str, set[str]]:
    rows = mysql(args, "SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS "
                     "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN "
                     "('wx_3d_scene','wx_3d_scene_revision','wx_3d_scene_content','wx_3d_scene_template','wx_3d_scene_publication','wx_3d_scene_bookmark')")
    inventory: dict[str, set[str]] = defaultdict(set)
    for table, column in rows:
        inventory[table].add(column)
    return dict(inventory)


def read_legacy_revisions(args: argparse.Namespace, columns: set[str]) -> list[dict]:
    optional = [column for column in ("content_id", "content_hash", "content_bytes") if column in columns]
    projection = ["tenant_id", "scene_id", "revision_no", "schema_version", "HEX(scene_json)",
                  "HEX(three_d_json)", "HEX(two_d_json)", "HEX(dependencies_json)"] + optional
    rows = mysql(args, "SELECT " + ",".join(projection) + " FROM wx_3d_scene_revision ORDER BY tenant_id,scene_id,revision_no")
    revisions = []
    for row in rows:
        packages = [bytes.fromhex(value) for value in row[4:8]]
        offset = 8
        item = {
            "tenantId": int(row[0]), "sceneId": int(row[1]), "revisionNo": int(row[2]),
            "schemaVersion": int(row[3]), "packages": packages,
            "contentHashExpected": content_hash(int(row[3]), packages),
            "contentBytesExpected": content_bytes(packages),
        }
        for column in optional:
            item[column] = row[offset] if row[offset] != "NULL" else None
            offset += 1
        revisions.append(item)
    return revisions


def read_contents(args: argparse.Namespace) -> dict[int, dict]:
    rows = mysql(args, "SELECT id,tenant_id,schema_version,HEX(scene_json),HEX(three_d_json),HEX(two_d_json),"
                     "HEX(dependencies_json),content_hash,content_bytes,deleted FROM wx_3d_scene_content ORDER BY id")
    contents = {}
    for row in rows:
        packages = [bytes.fromhex(value) for value in row[3:7]]
        contents[int(row[0])] = {
            "tenantId": int(row[1]), "schemaVersion": int(row[2]), "packages": packages,
            "contentHash": row[7], "contentBytes": int(row[8]), "deleted": int(row[9]),
        }
    return contents


def inspect(args: argparse.Namespace) -> dict:
    inventory = column_inventory(args)
    revision_columns = inventory.get("wx_3d_scene_revision", set())
    content_columns = inventory.get("wx_3d_scene_content", set())
    required_content_columns = {"id", "tenant_id", "schema_version", "scene_json", "three_d_json",
                                "two_d_json", "dependencies_json", "content_hash", "content_bytes", "deleted"}
    structure_ready = required_content_columns.issubset(content_columns) and {"content_id", "content_hash", "content_bytes"}.issubset(revision_columns)
    revisions = read_legacy_revisions(args, revision_columns)
    grouped: dict[tuple[int, str, int], int] = defaultdict(int)
    raw_bytes = 0
    for revision in revisions:
        grouped[(revision["tenantId"], revision["contentHashExpected"], revision["schemaVersion"])] += 1
        raw_bytes += revision["contentBytesExpected"]
    unique_bytes = 0
    for tenant_id, digest, schema in grouped:
        revision = next(item for item in revisions if (item["tenantId"], item["contentHashExpected"], item["schemaVersion"]) == (tenant_id, digest, schema))
        unique_bytes += revision["contentBytesExpected"]
    report = {
        "passed": False,
        "structureReady": structure_ready,
        "tables": sorted(inventory),
        "revisionCount": len(revisions),
        "expectedUniqueContentCount": len(grouped),
        "rawBytes": raw_bytes,
        "expectedUniqueBytes": unique_bytes,
        "estimatedSavingsBytes": raw_bytes - unique_bytes,
        "estimatedSavingsRatio": round((raw_bytes - unique_bytes) / raw_bytes, 6) if raw_bytes else 0,
    }
    if not structure_ready:
        report["message"] = "共享正文表或修订引用列尚未完成结构迁移"
        return report
    contents = read_contents(args)
    missing_references = 0
    hash_mismatches = 0
    byte_mismatches = 0
    tenant_violations = 0
    for revision in revisions:
        if revision.get("content_id") is None:
            missing_references += 1
            continue
        content = contents.get(int(revision["content_id"]))
        if content is None or content["deleted"]:
            missing_references += 1
            continue
        if content["tenantId"] != revision["tenantId"]:
            tenant_violations += 1
        if revision.get("content_hash") != revision["contentHashExpected"] or content["contentHash"] != revision["contentHashExpected"]:
            hash_mismatches += 1
        if revision.get("content_bytes") is None or int(revision["content_bytes"]) != revision["contentBytesExpected"] or content["contentBytes"] != revision["contentBytesExpected"]:
            byte_mismatches += 1
    report.update({"contentRowCount": len(contents), "missingReferences": missing_references,
                   "hashMismatches": hash_mismatches, "byteMismatches": byte_mismatches,
                   "tenantIsolationViolations": tenant_violations})
    report["passed"] = not any((missing_references, hash_mismatches, byte_mismatches, tenant_violations))
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description="DATA-08 只读迁移预检")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--database", default="wx_editor")
    parser.add_argument("--password-env", default="WX_MYSQL_ROOT_PASSWORD")
    parser.add_argument("--mysql-container", help="在远端Docker MySQL容器内执行只读查询")
    parser.add_argument("--require-ready", action="store_true", help="结构未完成或校验失败时返回非零")
    args = parser.parse_args()
    try:
        report = inspect(args)
    except Exception as error:
        print(json.dumps({"passed": False, "error": str(error)}, ensure_ascii=False))
        return 2
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["passed"] or not args.require_ready else 1


if __name__ == "__main__":
    sys.exit(main())
