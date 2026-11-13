"""仅在本批演练副本验证模型接收、签名传输及权限，不发布目录或启动转换。"""

import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import ssl
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

ROOT = Path("/opt/wx/editor-test148")
ORIGIN = "http://192.168.10.247:5180"
CERTIFICATE = "/home/wx-editor-web148/releases/iot-web-20261111/server.crt"


def run(target):
    """使用现有受控测试身份；签名和令牌仅在内存，不进入结果或错误文本。"""
    port = 49092 if target == "main" else 49093
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    secure = urllib.request.build_opener(urllib.request.ProxyHandler({}),
                                        urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=CERTIFICATE)))
    checks = []
    operation = str(uuid.uuid4())
    created = False
    uploaded = False
    result = {"target": target, "port": port, "operationId": operation, "passed": False, "checks": checks}

    def check(name, passed):
        checks.append({"name": name, "passed": bool(passed)})
        if not passed:
            raise RuntimeError(name)

    def request(path, actor=None, body=None, method="GET", gateway=False):
        headers = {"Content-Type": "application/json", "ClientId": "admin-api"}
        if actor:
            headers["tenant-id"] = str(actor["tenantId"])
            if actor.get("token"):
                headers["Authorization"] = "Bearer " + actor["token"]
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request("http://127.0.0.1:" + str(12522 if gateway else port) + path,
                                     data=data, headers=headers, method=method)
        try:
            response = opener.open(req, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return json.load(response)

    spec = importlib.util.spec_from_file_location("source_s3", ROOT / "art-s3-test-support.py")
    s3 = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(s3)
    identities = json.loads((ROOT / "test-identities.json").read_text(encoding="utf-8"))
    actor = next(item for item in identities if item["role"] == "tenant_admin")
    viewer = next(item for item in identities if item["role"] == "viewer")
    path = "/admin-api/dim3d/model-imports/" + operation
    object_path = "/wx-art-test148/resources/staging/model-imports/" + operation + "/source.zip"
    # 使用可实际转换的最小三角形源模型；不伪造转换响应，也不替代用户模型验收。
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("probe.obj", "o probe\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n")
    payload = buffer.getvalue()
    digest = hashlib.sha256(payload).hexdigest()
    result.update(sourceSize=len(payload), sourceSha256=digest, sample="最小三角形 OBJ 压缩包")
    try:
        for identity in (actor, viewer):
            login = request("/admin-api/system/auth/login", identity,
                            {"username": identity["username"], "password": identity["password"]}, "POST", gateway=True)
            check(identity["role"] + "真实登录", login.get("code") == 200)
            identity["token"] = login["data"]["accessToken"]
        body = {"operationId": operation, "operationType": "CREATE", "resourceScope": "TENANT_SHARED",
                "metadata": {"resourceName": "MODEL02-接收验证-" + operation[:8], "assetName": "probe"},
                "targetFormat": "glb", "source": {"size": str(len(payload)), "sha256": digest}}
        check("匿名创建拒绝", request("/admin-api/dim3d/model-imports", body=body, method="POST").get("code") == 401)
        check("普通用户不能创建", request("/admin-api/dim3d/model-imports", viewer, body, "POST").get("code") == 2030001002)
        response = request("/admin-api/dim3d/model-imports", actor, body, "POST")
        check("管理员创建接收任务", response.get("code") == 200)
        created = True
        resource = response["data"]["resourceId"]
        result["resourceId"] = resource
        check("创建状态和字符串身份", response["data"]["importStatus"] == "RECEIVING" and isinstance(resource, str))
        replay = request("/admin-api/dim3d/model-imports", actor, body, "POST")
        check("同号重放复用资源", replay.get("code") == 200 and replay["data"]["resourceId"] == resource)
        check("其他操作者不能查询", request(path, viewer).get("code") == 2030001001)
        changed = {**body, "source": {"size": str(len(payload) + 1), "sha256": digest}}
        check("同号不同源文件拒绝", request("/admin-api/dim3d/model-imports", actor, changed, "POST").get("code") == 2030001004)
        target_response = request(path + "/source-target", actor, {}, "POST")
        check("取得绑定签名目标", target_response.get("code") == 200)
        upload = target_response["data"]
        address = urllib.parse.urlsplit(upload["uploadUrl"])
        check("目标绑定地址及摘要", address.scheme == "https" and address.netloc == "192.168.10.148:9445"
              and address.path == object_path and upload["sourceSha256"] == digest and upload["sourceSize"] == str(len(payload)))
        preflight = urllib.request.Request(urllib.parse.urlunsplit(address._replace(query="")), method="OPTIONS",
                                          headers={"Origin": ORIGIN, "Access-Control-Request-Method": "PUT"})
        with secure.open(preflight, timeout=15) as response:
            check("当前网页跨域预检", response.status == 204 and response.headers.get("Access-Control-Allow-Origin") == ORIGIN)
        put = urllib.request.Request(upload["uploadUrl"], data=payload, method="PUT",
                                     headers={"Content-Type": "application/zip", "Origin": ORIGIN})
        with secure.open(put, timeout=30) as response:
            uploaded = True
            check("真实签名上传与跨域响应", response.status == 200 and response.headers.get("Access-Control-Allow-Origin") == ORIGIN)
        status, stored = s3.request("GET", object_path)
        check("私有桶回读字节摘要", status == 200 and len(stored) == len(payload) and hashlib.sha256(stored).hexdigest() == digest)
        queued = request(path + "/submit", actor, {}, "POST")
        check("持久化入队", queued.get("code") == 200 and queued["data"]["importStatus"] == "QUEUED")
        check("工作线程关闭时不假报成功", request(path, actor)["data"]["importStatus"] == "QUEUED")
        result["passed"] = True
    except Exception as error:
        # 网络异常可能携带完整地址，报告只写类型和已脱敏的检查名。
        result["errorType"] = type(error).__name__
    finally:
        if created:
            try:
                cancelled = request("/admin-api/dim3d/resource-operations/" + operation + "/cancel", actor, {}, "POST")
                result["cancelled"] = cancelled.get("code") == 200 and request(path, actor)["data"]["operationStatus"] == "CANCELLED"
            except Exception:
                result["cancelled"] = False
        if uploaded:
            try:
                result["temporaryObjectRemoved"] = s3.request("DELETE", object_path)[0] in (200, 204)
            except Exception:
                result["temporaryObjectRemoved"] = False
        result["passed"] = result["passed"] and result.get("cancelled", False) and result.get("temporaryObjectRemoved", False)
    destination = ROOT / "releases/model02-5e0a22ac" / ("source-verification-" + target + ".json")
    destination.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))
    return result["passed"]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=("main", "data08"), required=True)
    raise SystemExit(0 if run(parser.parse_args().target) else 1)
