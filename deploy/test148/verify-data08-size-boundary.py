"""DATA-08 隔离网关及三维服务的四包大小边界验证。"""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import uuid


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


API = load("data08_api", "verify-data08-isolation-service.py")
BASE = "http://127.0.0.1:12525/admin-api"
SYSTEM = "http://127.0.0.1:48081/admin-api"
LIMIT = 4 * 1024 * 1024


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tenant", type=int, default=2)
    args = parser.parse_args()
    identities = json.loads(Path("/opt/wx/editor-test148/test-identities.json").read_text())
    identity = next(item for item in identities if item["username"] == "tenantadmin")
    login = API.call(SYSTEM, "/system/auth/login", None, args.tenant, "POST",
                     {"username": identity["username"], "password": identity["password"]})
    require(login.get("code") == 200, "大小边界验证登录失败")
    token = login["data"]["accessToken"]
    scene_id = None
    client = "data08-size-" + uuid.uuid4().hex
    report = {"passed": False, "servicePackageLimitBytes": LIMIT, "cases": []}
    try:
        created = API.call(BASE, "/dim3d/scene/add", token, args.tenant, "POST",
                           {"name": "DATA08大小边界-" + uuid.uuid4().hex[:8], "sceneType": "local3d"})
        require(created.get("code") == 200, "大小边界场景创建失败")
        scene_id = created["data"]["sceneId"]
        revision = API.call(BASE, "/dim3d/scene/info/" + scene_id, token, args.tenant)["data"]["currentRevision"]
        lock = API.call(BASE, "/dim3d/scene/lock/" + scene_id + "?clientKey=" + client, token, args.tenant, "POST")
        require(lock.get("code") == 200, "大小边界场景加锁失败")
        base = {key: revision[key] for key in ("sceneJson", "twoDJson", "dependenciesJson")}
        cases = (("under-limit", LIMIT - 64 * 1024, True), ("over-limit", LIMIT + 64 * 1024, False))
        current_revision = 1
        for label, padding_bytes, expected_success in cases:
            three_d = {
                "baseConfig": {"coordinateSystem": {"kind": "local"}, "gisLayers": [], "scene": {}, "camera": {}, "weather": {}, "settings": {}},
                "objects": [], "components": [], "layers": [], "cameraPaths": [],
                "streaming": {"groups": [], "chunks": [], "chunkTargets": []},
                "metadata": {"padding": "x" * padding_bytes},
            }
            body = {**base, "threeDJson": json.dumps(three_d, ensure_ascii=False, separators=(",", ":")),
                    "sceneId": scene_id, "lockToken": lock["data"]["lockToken"], "schemaVersion": 1,
                    "baseRevisionNo": current_revision, "requestId": "data08-size-" + uuid.uuid4().hex}
            payload_bytes = len(body["threeDJson"].encode())
            response = API.call(BASE, "/dim3d/scene/saveAll", token, args.tenant, "PUT", body)
            data = response.get("data") or {}
            case = {"label": label, "payloadBytes": payload_bytes, "code": response.get("code"),
                    "message": response.get("msg"), "conflict": data.get("conflict"),
                    "revisionNo": data.get("revisionNo")}
            report["cases"].append(case)
            if expected_success:
                require(response.get("code") == 200 and data.get("revisionNo") == current_revision + 1,
                        "低于单包限制的请求未成功")
                current_revision += 1
            else:
                require(response.get("code") != 200 or data.get("revisionNo") != current_revision + 1,
                        "超过单包限制的请求被错误接受")
        report["passed"] = True
    finally:
        if scene_id:
            API.call(BASE, "/dim3d/scene/unlock/" + scene_id + "?clientKey=" + client, token, args.tenant, "DELETE")
            API.call(BASE, "/dim3d/scene/remove/" + scene_id, token, args.tenant, "DELETE")
        API.call(SYSTEM, "/system/auth/logout", token, args.tenant, "POST")
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
