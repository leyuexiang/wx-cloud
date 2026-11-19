"""DATA-08 修订保留引用审计，只读输出，不执行删除。"""
from __future__ import annotations

import argparse
import importlib.util
import json
import pathlib
import sys
from datetime import datetime, timezone


VERIFY_PATH = pathlib.Path(__file__).with_name("verify-data08-db.py")
VERIFY_SPEC = importlib.util.spec_from_file_location("verify_data08_db", VERIFY_PATH)
VERIFY = importlib.util.module_from_spec(VERIFY_SPEC)
VERIFY_SPEC.loader.exec_module(VERIFY)


def table_exists(args: argparse.Namespace, table: str) -> bool:
    rows = VERIFY.mysql(args, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='" + table + "'")
    return bool(rows and rows[0] and int(rows[0][0]) == 1)


def audit(args: argparse.Namespace) -> dict:
    required = {"wx_3d_scene", "wx_3d_scene_revision"}
    tables = {table: table_exists(args, table) for table in required | {"wx_3d_scene_template", "wx_3d_scene_publication", "wx_3d_scene_bookmark", "wx_3d_scene_request"}}
    if not all(tables[name] for name in required):
        raise RuntimeError("场景主表或修订表不存在")

    scenes = VERIFY.mysql(args, "SELECT id,tenant_id,current_revision,deleted,lock_expire FROM wx_3d_scene")
    revisions = VERIFY.mysql(args, "SELECT tenant_id,scene_id,revision_no,request_id FROM wx_3d_scene_revision ORDER BY tenant_id,scene_id,revision_no")
    now = datetime.now(timezone.utc)
    scene_state = {}
    for scene_id, tenant_id, current_revision, deleted, lock_expire in scenes:
        active_lock = False
        if lock_expire and lock_expire != "NULL":
            try:
                active_lock = datetime.fromisoformat(lock_expire.replace(" ", "T").replace("Z", "+00:00")).astimezone(timezone.utc) > now
            except ValueError:
                active_lock = True
        scene_state[(int(tenant_id), int(scene_id))] = {
            "currentRevision": int(current_revision), "deleted": int(deleted), "activeLock": active_lock,
        }

    template_first_revisions = set()
    if tables["wx_3d_scene_template"]:
        template_rows = VERIFY.mysql(args, "SELECT tenant_id,template_scene_id,status,deleted FROM wx_3d_scene_template")
        for tenant_id, scene_id, status, deleted in template_rows:
            if str(status) == "ONLINE" or int(deleted) == 0:
                template_first_revisions.add((int(tenant_id), int(scene_id), 1))

    publication_table_available = tables["wx_3d_scene_publication"]
    publication_refs = set()
    if publication_table_available:
        # 发布历史本身仍是不可变审计依据；无论当前指针是否撤销，都不能回收对应场景修订。
        publication_rows = VERIFY.mysql(args, "SELECT tenant_id,scene_id,revision_no FROM wx_3d_scene_publication")
        publication_refs = {(int(tenant_id), int(scene_id), int(revision_no)) for tenant_id, scene_id, revision_no in publication_rows}
    bookmark_table_available = tables["wx_3d_scene_bookmark"]
    bookmark_refs = set()
    if bookmark_table_available:
        bookmark_rows = VERIFY.mysql(args, "SELECT tenant_id,scene_id,revision_no,status,expires_time FROM wx_3d_scene_bookmark")
        for tenant_id, scene_id, revision_no, status, expires_time in bookmark_rows:
            if str(status) != "ACTIVE":
                continue
            if expires_time and expires_time != "NULL":
                try:
                    expiry = datetime.fromisoformat(expires_time.replace(" ", "T").replace("Z", "+00:00")).astimezone(timezone.utc)
                    if expiry <= now:
                        continue
                except ValueError:
                    pass
            bookmark_refs.add((int(tenant_id), int(scene_id), int(revision_no)))
    request_table_available = tables["wx_3d_scene_request"]
    request_refs = {}
    if request_table_available:
        request_rows = VERIFY.mysql(args, "SELECT tenant_id,scene_id,request_id,request_hash,revision_no,expires_time FROM wx_3d_scene_request")
        request_refs = {(int(tenant_id), int(scene_id), request_id): (request_hash, int(revision_no), expires_time)
                        for tenant_id, scene_id, request_id, request_hash, revision_no, expires_time in request_rows}
    rows = []
    reason_counts = {}
    for tenant_id, scene_id, revision_no, request_id in revisions:
        key = (int(tenant_id), int(scene_id))
        state = scene_state.get(key, {"currentRevision": None, "deleted": 1, "activeLock": False})
        reasons = []
        if state["currentRevision"] == int(revision_no):
            reasons.append("current_pointer")
        if state["activeLock"]:
            reasons.append("active_lock")
        if (int(tenant_id), int(scene_id), int(revision_no)) in template_first_revisions:
            reasons.append("template_first_revision")
        if state["deleted"]:
            reasons.append("deleted_scene_audit_window")
        if publication_table_available:
            if (int(tenant_id), int(scene_id), int(revision_no)) in publication_refs:
                reasons.append("publication_reference")
            else:
                reasons.append("publication_reference_checked_none")
        else:
            reasons.append("publication_reference_table_absent")
        if bookmark_table_available:
            if (int(tenant_id), int(scene_id), int(revision_no)) in bookmark_refs:
                reasons.append("persistent_bookmark_reference")
            else:
                reasons.append("persistent_bookmark_checked_none")
        else:
            reasons.append("persistent_bookmark_table_absent")
        if request_table_available:
            request_ref = request_refs.get((int(tenant_id), int(scene_id), request_id))
            if request_ref is None:
                reasons.append("request_retention_record_missing")
            elif request_ref[1] != int(revision_no):
                reasons.append("request_retention_revision_mismatch")
            else:
                expiry = request_ref[2]
                try:
                    expires_at = datetime.fromisoformat(expiry.replace(" ", "T").replace("Z", "+00:00")).astimezone(timezone.utc)
                    reasons.append("request_retention_expired" if expires_at <= now else "request_retention_active")
                except (AttributeError, ValueError):
                    reasons.append("request_retention_expiry_invalid")
        else:
            reasons.append("request_id_expiry_unmodeled")
        for reason in reasons:
            reason_counts[reason] = reason_counts.get(reason, 0) + 1
        rows.append({"tenantId": int(tenant_id), "sceneId": int(scene_id), "revisionNo": int(revision_no),
                     "requestIdPresent": bool(request_id), "reclaimCandidate": False, "reasons": reasons})

    return {
        "passed": True,
        "readOnly": True,
        "sceneCount": len(scenes),
        "revisionCount": len(revisions),
        "tables": tables,
        "reasonCounts": reason_counts,
        "publicationReferenceCount": len(publication_refs),
        "reclaimCandidateCount": 0,
        "rows": rows,
        "decision": "当前仅输出引用审计，不允许据此直接删除修订或共享正文",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="DATA-08 修订保留引用审计")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--database", default="wx_editor")
    parser.add_argument("--password-env", default="WX_MYSQL_ROOT_PASSWORD")
    parser.add_argument("--mysql-container")
    parser.add_argument("--json-output", type=pathlib.Path)
    args = parser.parse_args()
    try:
        report = audit(args)
        payload = json.dumps(report, ensure_ascii=False, indent=2)
        if args.json_output:
            args.json_output.write_text(payload, encoding="utf-8")
        print(payload)
        return 0
    except Exception as error:
        print(json.dumps({"passed": False, "error": str(error)}, ensure_ascii=False))
        return 2


if __name__ == "__main__":
    sys.exit(main())
