"""在回环候选端口验证保留请求和书签；只修改本轮创建的独立场景，不清理正文。"""
from __future__ import annotations

import argparse
from argparse import Namespace
from concurrent.futures import ThreadPoolExecutor
import importlib.util
import json
from pathlib import Path
import uuid


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


API = load("retention_api", "verify-data08-isolation-service.py")
DB = load("retention_db", "verify-data08-db.py")
AUDIT = load("retention_audit", "audit-data08-retention.py")
DB_ARGS = Namespace(host="127.0.0.1", port=3306, user="root", database="wx_editor_data08",
                    password_env=None, mysql_container="mysql8")
BASE = "http://127.0.0.1:49143/admin-api"
AUTH = "http://127.0.0.1:12525/admin-api"
KEYS = ("sceneJson", "threeDJson", "twoDJson", "dependenciesJson")


def main():
    global BASE
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=("candidate", "data08"), default="candidate")
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--confirm", default="")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    confirmation = "DATA08-RETENTION-CANDIDATE" if args.target == "candidate" else "DATA08-RETENTION-DATA08"
    if not args.execute or args.confirm != confirmation:
        raise RuntimeError("需要显式确认对应的隔离入口")
    BASE = "http://127.0.0.1:" + ("49143" if args.target == "candidate" else "12525") + "/admin-api"
    checks, cleanup = [], []
    report = {"passed": False, "database": "wx_editor_data08", "base": BASE,
              "checks": checks, "cleanup": cleanup}
    scene_id = token = bookmark_id = None
    client = "retention-" + uuid.uuid4().hex

    def check(name, condition):
        checks.append({"name": name, "passed": bool(condition)})
        if not condition:
            raise RuntimeError(name)

    def sql(statement):
        return DB.mysql(DB_ARGS, statement)

    def call(path, method="GET", body=None, tenant=2):
        return API.call(BASE, path, token, tenant, method, body)

    def packages():
        data = call("/dim3d/scene/info/" + scene_id)["data"]["currentRevision"]
        return {key: data[key] for key in KEYS}, int(data["revisionNo"])

    try:
        identities = json.loads(Path("/opt/wx/editor-test148/test-identities.json").read_text(encoding="utf-8"))
        identity = next(item for item in identities if item["username"] == "tenantadmin" and item["tenantId"] == 2)
        login = API.call(AUTH, "/system/auth/login", None, 2, "POST",
                         {"username": identity["username"], "password": identity["password"]})
        check("真实账号登录", login.get("code") == 200)
        token = login["data"]["accessToken"]
        check("书签匿名写入拒绝", API.call(BASE, "/dim3d/scene/bookmark/1", None, 2, "POST",
              {"revisionNo": 1, "bookmarkType": "EDITOR", "bookmarkKey": client, "expiresInSeconds": 300}).get("code") != 200)
        created = call("/dim3d/scene/add", "POST", {"name": "DATA08-保留候选-" + client, "sceneType": "local3d"})
        check("创建独立候选场景", created.get("code") == 200)
        scene_id = created["data"]["sceneId"]
        # 后续管理SQL始终同时限定可信租户和本轮创建的场景，不接受任意外部编号。
        sid = int(scene_id)
        report["sceneId"] = scene_id
        where = f"tenant_id=2 AND scene_id={sid}"
        check("首版和请求记录同事务写入", sql(f"SELECT COUNT(*) FROM wx_3d_scene_request WHERE {where} AND revision_no=1")[0][0] == "1")
        mark_body = {"revisionNo": 1, "bookmarkType": "EDITOR", "bookmarkKey": client, "expiresInSeconds": 300}
        mark = call("/dim3d/scene/bookmark/" + scene_id, "POST", mark_body)
        check("创建固定首版书签", mark.get("code") == 200 and mark["data"]["revisionNo"] == 1)
        bookmark_id = mark["data"]["bookmarkId"]
        again = call("/dim3d/scene/bookmark/" + scene_id, "POST", mark_body)
        check("书签同键重试不续期", again.get("data") == mark["data"])
        check("错租户请求拒绝", call("/dim3d/scene/bookmark/" + scene_id, "POST", mark_body, tenant=3).get("code") != 200)
        lock = call("/dim3d/scene/lock/" + scene_id + "?clientKey=" + client, "POST")
        check("取得本轮编辑锁", lock.get("code") == 200)
        frozen, number = packages()
        request = uuid.uuid4().hex
        body = {**frozen, "sceneId": scene_id, "baseRevisionNo": number, "schemaVersion": 1,
                "lockToken": lock["data"]["lockToken"], "requestId": request}
        saved = call("/dim3d/scene/saveAll", "PUT", body)
        check("保存第二版", saved.get("code") == 200 and saved["data"]["revisionNo"] == 2)
        row = sql(f"SELECT request_hash,expires_time FROM wx_3d_scene_request WHERE {where} AND request_id='{request}'")[0]
        replay = call("/dim3d/scene/saveAll", "PUT", body)
        check("原请求回放固定修订", replay.get("code") == 200 and replay["data"]["revisionId"] == saved["data"]["revisionId"])
        check("请求回放不续期", sql(f"SELECT request_hash,expires_time FROM wx_3d_scene_request WHERE {where} AND request_id='{request}'")[0] == row)
        try:
            sql(f"UPDATE wx_3d_scene_request SET request_hash=REPEAT('f',64) WHERE {where} AND request_id='{request}'")
            check("请求摘要错配拒绝", call("/dim3d/scene/saveAll", "PUT", body).get("code") == 500)
        finally:
            sql(f"UPDATE wx_3d_scene_request SET request_hash='{row[0]}' WHERE {where} AND request_id='{request}'")
        sql(f"UPDATE wx_3d_scene_request SET expires_time='2000-01-01 00:00:00' WHERE {where} AND request_id='{request}'")
        check("过期请求拒绝重放", call("/dim3d/scene/saveAll", "PUT", body).get("code") == 409)
        check("过期请求没有追加修订", sql(f"SELECT COUNT(*) FROM wx_3d_scene_revision WHERE {where}")[0][0] == "2")
        frozen2, number2 = packages()
        payloads = [{**body, **frozen2, "baseRevisionNo": number2, "requestId": uuid.uuid4().hex} for _ in range(4)]
        with ThreadPoolExecutor(max_workers=4) as pool:
            writes = list(pool.map(lambda item: call("/dim3d/scene/saveAll", "PUT", item), payloads))
        successes = [item for item in writes if item.get("code") == 200 and not item.get("data", {}).get("conflict")]
        conflicts = [item for item in writes if item.get("data", {}).get("conflict")]
        check("四路同基线保存一成功三冲突", len(successes) == 1 and len(conflicts) == 3)
        check("成功保存才新增请求记录", sql(f"SELECT COUNT(*) FROM wx_3d_scene_request WHERE {where}")[0][0] == "3")
        check("正文四包保持", packages()[0] == frozen)
        report["concurrent"] = {"successes": len(successes), "conflicts": len(conflicts)}
        mark_conflict = call("/dim3d/scene/bookmark/" + scene_id, "POST", {**mark_body, "revisionNo": 2})
        check("书签禁止同键换版", mark_conflict.get("code") == 409)
        audit = AUDIT.audit(DB_ARGS)
        relevant = [item for item in audit["rows"] if item["tenantId"] == 2 and item["sceneId"] == sid]
        check("审计看到旧版书签及过期请求", any("persistent_bookmark_reference" in item["reasons"] for item in relevant)
              and any("request_retention_expired" in item["reasons"] for item in relevant))
        report["audit"] = relevant
        endpoint = "/dim3d/scene/bookmark/" + scene_id + "/" + bookmark_id
        check("正常撤销书签", call(endpoint, "DELETE").get("code") == 200)
        first_revoke = sql(f"SELECT status,revoked_time FROM wx_3d_scene_bookmark WHERE {where} AND id={int(bookmark_id)}")
        check("撤销幂等且保留审计", call(endpoint, "DELETE").get("code") == 200 and first_revoke == sql(f"SELECT status,revoked_time FROM wx_3d_scene_bookmark WHERE {where} AND id={int(bookmark_id)}"))
        check("撤销后迟到重试不复活", call("/dim3d/scene/bookmark/" + scene_id, "POST", mark_body).get("code") == 409)
        report["passed"] = True
    except Exception as error:
        report["error"] = str(error)
    finally:
        # 每一项收尾单独尝试，单个失败不能阻止后续解锁、软删除和退出。
        actions = []
        if scene_id and token:
            if bookmark_id:
                actions.append(("撤销书签", lambda: call("/dim3d/scene/bookmark/" + scene_id + "/" + bookmark_id, "DELETE")))
            actions.extend([
                ("解除本轮租约", lambda: call("/dim3d/scene/unlock/" + scene_id + "?clientKey=" + client, "DELETE")),
                ("软删除本轮场景", lambda: call("/dim3d/scene/remove/" + scene_id, "DELETE")),
            ])
        if token:
            actions.append(("退出会话", lambda: API.call(AUTH, "/system/auth/logout", token, 2, "POST")))
        for name, action in actions:
            try:
                cleanup.append({"name": name, "passed": action().get("code") == 200})
            except Exception:
                cleanup.append({"name": name, "passed": False})
        report["passed"] = report["passed"] and all(item["passed"] for item in cleanup)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
