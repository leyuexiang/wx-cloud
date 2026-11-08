"""DATA-08 隔离库应用联验，直连隔离三维服务，不经过业务网关。"""
from __future__ import annotations

import argparse
import json
import pathlib
import urllib.error
import urllib.parse
import urllib.request
import uuid


def call(base: str, path: str, token: str | None, tenant: int, method: str = "GET", body=None):
    headers = {"Content-Type": "application/json", "tenant-id": str(tenant)}
    if token:
        headers["Authorization"] = "Bearer " + token
    payload = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    request = urllib.request.Request(base + path, method=method, headers=headers, data=payload)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as error:
        return json.loads(error.read())


def main() -> int:
    parser = argparse.ArgumentParser(description="DATA-08 隔离库应用联验")
    parser.add_argument("--dimensions-base", default="http://127.0.0.1:48093/admin-api")
    parser.add_argument("--system-base", default="http://127.0.0.1:48181/admin-api")
    parser.add_argument("--identity-file", default="/opt/wx/editor-test148/test-identities.json")
    parser.add_argument("--tenant", type=int, default=2)
    args = parser.parse_args()
    identities = json.loads(pathlib.Path(args.identity_file).read_text(encoding="utf-8"))
    identity = next(item for item in identities if item["role"] == "tenant_admin")
    login = call(args.system_base, "/system/auth/login", None, args.tenant, "POST",
                 {"username": identity["username"], "password": identity["password"]})
    if login.get("code") != 200:
        print(json.dumps({"passed": False, "stage": "login", "response": login}, ensure_ascii=False))
        return 1
    token = login["data"]["accessToken"]
    scene_id = None
    client = "data08-isolation-" + uuid.uuid4().hex
    checks = []

    def check(name, condition):
        checks.append({"name": name, "passed": bool(condition)})
        if not condition:
            raise RuntimeError(name)

    try:
        created = call(args.dimensions_base, "/dim3d/scene/add", token, args.tenant, "POST",
                       {"name": "DATA08隔离联验-" + uuid.uuid4().hex[:8], "sceneType": "local3d", "sceneCategory": "DATA08"})
        check("创建隔离场景", created.get("code") == 200)
        scene_id = created["data"]["sceneId"]
        info = call(args.dimensions_base, "/dim3d/scene/info/" + scene_id, token, args.tenant)
        check("读取隔离场景", info.get("code") == 200)
        revision = info["data"]["currentRevision"]
        keys = ["sceneJson", "threeDJson", "twoDJson", "dependenciesJson"]
        body = {key: revision[key] for key in keys}
        scene_json = json.loads(body["sceneJson"])
        scene_json.setdefault("metadata", {})["data08Isolation"] = "dual-write"
        body["sceneJson"] = json.dumps(scene_json, ensure_ascii=False, separators=(",", ":"))
        lock = call(args.dimensions_base, "/dim3d/scene/lock/" + scene_id + "?clientKey=" + client, token, args.tenant, "POST")
        check("取得隔离编辑锁", lock.get("code") == 200)
        save_body = {**body, "sceneId": scene_id, "lockToken": lock["data"]["lockToken"],
                     "requestId": uuid.uuid4().hex, "baseRevisionNo": 1, "schemaVersion": 1}
        saved = call(args.dimensions_base, "/dim3d/scene/saveAll", token, args.tenant, "PUT", save_body)
        check("隔离双写保存第二版", saved.get("code") == 200 and saved["data"]["revisionNo"] == 2)
        replay = call(args.dimensions_base, "/dim3d/scene/saveAll", token, args.tenant, "PUT", save_body)
        check("隔离保存幂等", replay.get("data", {}).get("revisionId") == saved["data"].get("revisionId"))
        chunks = call(args.dimensions_base, "/dim3d/scene/chunks/" + scene_id + "?revisionNo=2&pageNum=1&pageSize=20&includeRelations=true", token, args.tenant)
        check("隔离分片读取", chunks.get("code") == 200)
        return_code = 0
    except Exception as error:
        checks.append({"name": str(error), "passed": False})
        return_code = 1
    finally:
        if scene_id:
            call(args.dimensions_base, "/dim3d/scene/unlock/" + scene_id + "?clientKey=" + client, token, args.tenant, "DELETE")
            call(args.dimensions_base, "/dim3d/scene/remove/" + scene_id, token, args.tenant, "DELETE")
    print(json.dumps({"passed": return_code == 0 and all(item["passed"] for item in checks), "sceneId": scene_id, "checks": checks}, ensure_ascii=False, indent=2))
    return return_code


if __name__ == "__main__":
    raise SystemExit(main())
