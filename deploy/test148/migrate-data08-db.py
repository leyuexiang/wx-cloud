"""DATA-08 共享正文回填工具；默认 dry-run，禁止直接改写线上数据。"""
from __future__ import annotations

import argparse
import importlib.util
import json
import pathlib
import sys
from collections import defaultdict


VERIFY_PATH = pathlib.Path(__file__).with_name("verify-data08-db.py")
VERIFY_SPEC = importlib.util.spec_from_file_location("verify_data08_db", VERIFY_PATH)
VERIFY = importlib.util.module_from_spec(VERIFY_SPEC)
VERIFY_SPEC.loader.exec_module(VERIFY)


def sql_number(value: int) -> str:
    return str(int(value))


def plan(args: argparse.Namespace) -> tuple[dict, str]:
    inventory = VERIFY.column_inventory(args)
    revision_columns = inventory.get("wx_3d_scene_revision", set())
    content_columns = inventory.get("wx_3d_scene_content", set())
    required_content_columns = {"id", "tenant_id", "schema_version", "scene_json", "three_d_json",
                                "two_d_json", "dependencies_json", "content_hash", "content_bytes", "deleted"}
    if not required_content_columns.issubset(content_columns) or not {"content_id", "content_hash", "content_bytes"}.issubset(revision_columns):
        raise RuntimeError("共享正文表或修订引用列尚未完成结构迁移")

    revisions = VERIFY.read_legacy_revisions(args, revision_columns)
    contents = VERIFY.read_contents(args)
    by_identity = {}
    for content_id, content in contents.items():
        identity = (content["tenantId"], content["contentHash"], content["schemaVersion"])
        if identity in by_identity and by_identity[identity] != content_id:
            raise RuntimeError("共享正文唯一键出现重复行")
        by_identity[identity] = content_id

    next_id = max(contents.keys(), default=0) + 1
    new_contents = {}
    revive_ids = set()
    updates = []
    for revision in revisions:
        identity = (revision["tenantId"], revision["contentHashExpected"], revision["schemaVersion"])
        content_id = by_identity.get(identity)
        if content_id is None:
            content_id = next_id
            next_id += 1
            new_contents[identity] = (content_id, revision)
            by_identity[identity] = content_id
        elif identity not in new_contents:
            content = contents[content_id]
            if content["packages"] != revision["packages"] or content["contentBytes"] != revision["contentBytesExpected"]:
                raise RuntimeError(f"共享正文内容不一致：content_id={content_id}")
            if content["deleted"]:
                revive_ids.add(content_id)
        if revision.get("content_id") is None:
            updates.append((revision["tenantId"], revision["sceneId"], revision["revisionNo"],
                            content_id, revision["contentHashExpected"], revision["contentBytesExpected"]))
        elif int(revision["content_id"]) != content_id or revision.get("content_hash") != revision["contentHashExpected"]:
            raise RuntimeError(f"已有修订引用与正文摘要不一致：scene_id={revision['sceneId']},revision_no={revision['revisionNo']}")

    statements = ["START TRANSACTION;"]
    for (tenant_id, digest, schema), (content_id, revision) in new_contents.items():
        packages = revision["packages"]
        statements.append(
            "INSERT INTO wx_3d_scene_content(id,tenant_id,content_hash,schema_version,scene_json,three_d_json,two_d_json,dependencies_json,content_bytes,last_referenced_time,deleted) "
            f"VALUES({sql_number(content_id)},{sql_number(tenant_id)},'{digest}',{sql_number(schema)},"
            f"UNHEX('{packages[0].hex()}'),UNHEX('{packages[1].hex()}'),UNHEX('{packages[2].hex()}'),UNHEX('{packages[3].hex()}'),"
            f"CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);"
        )
    for content_id in sorted(revive_ids):
        statements.append(f"UPDATE wx_3d_scene_content SET deleted=0,last_referenced_time=CURRENT_TIMESTAMP WHERE id={sql_number(content_id)};")
    for tenant_id, scene_id, revision_no, content_id, digest, byte_count in updates:
        statements.append(
            "UPDATE wx_3d_scene_revision SET "
            f"content_id={sql_number(content_id)},content_hash='{digest}',content_bytes={sql_number(byte_count)} "
            f"WHERE tenant_id={sql_number(tenant_id)} AND scene_id={sql_number(scene_id)} AND revision_no={sql_number(revision_no)} AND content_id IS NULL;"
        )
    statements.append("COMMIT;")
    report = {
        "revisionCount": len(revisions),
        "newContentRows": len(new_contents),
        "revivedContentRows": len(revive_ids),
        "revisionReferencesToWrite": len(updates),
        "dryRun": not args.apply,
        "databaseWrite": False,
    }
    return report, "\n".join(statements) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="DATA-08 共享正文回填")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--database", default="wx_editor")
    parser.add_argument("--password-env", default="WX_MYSQL_ROOT_PASSWORD")
    parser.add_argument("--mysql-container")
    parser.add_argument("--apply", action="store_true", help="执行事务写入；默认只生成计划")
    parser.add_argument("--confirm", default="", help="执行写入时必须为 DATA08-APPLY")
    parser.add_argument("--sql-output", type=pathlib.Path, help="把事务SQL写入本地文件，默认不落盘")
    args = parser.parse_args()
    try:
        report, sql = plan(args)
        if args.apply:
            if args.confirm != "DATA08-APPLY":
                raise RuntimeError("写入操作必须显式传入 --confirm DATA08-APPLY")
            VERIFY.mysql(args, sql)
            report["dryRun"] = False
            report["databaseWrite"] = True
        elif args.sql_output:
            args.sql_output.write_text(sql, encoding="utf-8")
            report["sqlOutput"] = str(args.sql_output)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0
    except Exception as error:
        print(json.dumps({"passed": False, "error": str(error)}, ensure_ascii=False))
        return 2


if __name__ == "__main__":
    sys.exit(main())
