"""DATA-08 隔离服务并发读写基线。"""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
import argparse
import importlib.util
import json
from pathlib import Path
import statistics
import time
import uuid


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


API = load("data08_api", "verify-data08-isolation-service.py")
BASE = "http://127.0.0.1:48093/admin-api"
SYSTEM = "http://127.0.0.1:48081/admin-api"


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tenant", type=int, default=2)
    parser.add_argument("--read-workers", type=int, default=8)
    parser.add_argument("--write-workers", type=int, default=4)
    args = parser.parse_args()
    identities = json.loads(Path("/opt/wx/editor-test148/test-identities.json").read_text())
    identity = next(item for item in identities if item["username"] == "tenantadmin")
    login = API.call(SYSTEM, "/system/auth/login", None, args.tenant, "POST",
                     {"username": identity["username"], "password": identity["password"]})
    require(login.get("code") == 200, "并发基线登录失败")
    token = login["data"]["accessToken"]
    scene_id = None
    client = "data08-concurrency-" + uuid.uuid4().hex
    report = {"passed": False, "readWorkers": args.read_workers, "writeWorkers": args.write_workers}
    try:
        created = API.call(BASE, "/dim3d/scene/add", token, args.tenant, "POST",
                           {"name": "DATA08并发基线-" + uuid.uuid4().hex[:8], "sceneType": "local3d"})
        require(created.get("code") == 200, "并发基线场景创建失败")
        scene_id = created["data"]["sceneId"]
        info = API.call(BASE, "/dim3d/scene/info/" + scene_id, token, args.tenant)
        require(info.get("code") == 200, "并发基线详情读取失败")
        revision = info["data"]["currentRevision"]
        lock = API.call(BASE, "/dim3d/scene/lock/" + scene_id + "?clientKey=" + client,
                        token, args.tenant, "POST")
        require(lock.get("code") == 200, "并发基线加锁失败")
        lock_token = lock["data"]["lockToken"]
        read_start = time.perf_counter()

        def read_once(_):
            start = time.perf_counter()
            response = API.call(BASE, "/dim3d/scene/info/" + scene_id, token, args.tenant)
            return {"code": response.get("code"), "revisionNo": response.get("data", {}).get("currentRevision", {}).get("revisionNo"),
                    "elapsedMs": round((time.perf_counter() - start) * 1000, 3)}

        with ThreadPoolExecutor(max_workers=args.read_workers) as pool:
            reads = list(pool.map(read_once, range(args.read_workers)))
        require(all(item["code"] == 200 and item["revisionNo"] == 1 for item in reads),
                "并发固定修订读取出现错误")
        report["parallelReads"] = {"count": len(reads), "elapsedMs": round((time.perf_counter() - read_start) * 1000, 3),
                                    "medianRequestMs": round(statistics.median(item["elapsedMs"] for item in reads), 3),
                                    "maxRequestMs": max(item["elapsedMs"] for item in reads)}

        body = {key: revision[key] for key in ("sceneJson", "threeDJson", "twoDJson", "dependenciesJson")}

        def save_once(index):
            start = time.perf_counter()
            response = API.call(BASE, "/dim3d/scene/saveAll", token, args.tenant, "PUT", {
                **body, "sceneId": scene_id, "lockToken": lock_token, "schemaVersion": 1,
                "baseRevisionNo": 1, "requestId": "data08-concurrency-" + uuid.uuid4().hex,
            })
            return {"index": index, "code": response.get("code"), "data": response.get("data"),
                    "elapsedMs": round((time.perf_counter() - start) * 1000, 3)}

        with ThreadPoolExecutor(max_workers=args.write_workers) as pool:
            writes = list(pool.map(save_once, range(args.write_workers)))
        successes = [item for item in writes if item["code"] == 200 and item["data"].get("conflict") is False]
        conflicts = [item for item in writes if item["code"] == 200 and item["data"].get("conflict") is True]
        require(len(successes) == 1 and len(conflicts) == args.write_workers - 1, "并发保存未形成一成功多冲突")
        final = API.call(BASE, "/dim3d/scene/info/" + scene_id, token, args.tenant)
        require(final.get("code") == 200 and final["data"]["currentRevision"]["revisionNo"] == 2,
                "并发保存后的修订号异常")
        report["parallelWrites"] = {"results": writes, "successCount": len(successes), "conflictCount": len(conflicts),
                                     "finalRevisionNo": final["data"]["currentRevision"]["revisionNo"]}
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
