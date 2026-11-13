"""仅在本批演练副本验证模型接收、签名传输及权限，不发布目录或启动转换。"""

import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import ssl
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path("/opt/wx/editor-test148")
ORIGIN = "http://192.168.10.247:5180"
FRONTEND_ORIGINS = (ORIGIN, "http://localhost:5181", "http://127.0.0.1:5173", "https://192.168.10.148:9447")
CERTIFICATE = "/home/wx-editor-web148/releases/iot-web-20261111/server.crt"


def source_payload(kind):
    """使用可实际转换的输入；FBX 复用现场样本，3DS 由现有工具从最小几何导出。"""
    if kind == "fbx":
        file = Path("/root/fbx2gltf-docker/models/12(2).zip")
        if not 4 <= file.stat().st_size <= 16 * 1024 * 1024:
            raise ValueError("现场样本大小超出本批验证范围")
        return file.read_bytes(), "现场既有 FBX 源压缩包"
    geometry = "o probe\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n"
    raw = geometry.encode()
    if kind == "3ds":
        # 子进程只读出自建临时目录中的真实导出文件，退出前清理该目录。
        program = """const fs=require('fs'),os=require('os'),path=require('path'),cp=require('child_process');
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'model03-'));
try { const input=path.join(dir,'probe.obj'),output=path.join(dir,'probe.3ds');
fs.writeFileSync(input,process.argv[1]);
cp.execFileSync('assimp',['export',input,output],{stdio:'ignore',timeout:30000});
process.stdout.write(fs.readFileSync(output));
} finally { fs.rmSync(dir,{recursive:true,force:true}); }"""
        generated = subprocess.run(["docker", "exec", "market04-scene-o2hxr7nz-converter", "node", "-e", program, geometry],
                                   capture_output=True, check=True, timeout=45)
        raw = generated.stdout
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("probe." + kind, raw)
    return buffer.getvalue(), "最小三角形 " + kind.upper() + " 压缩包"


def run(target, conversion=False, source_kind="obj", output_format="glb"):
    """使用现有受控测试身份；签名和令牌仅在内存，不进入结果或错误文本。"""
    port = 49092 if target == "main" else 49093
    if conversion:
        port += 10
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
    # 样本构造不伪造转换响应，也不替代用户原始模型的最终画面验收。
    payload, sample = source_payload(source_kind)
    digest = hashlib.sha256(payload).hexdigest()
    result.update(sourceSize=len(payload), sourceSha256=digest, sample=sample, conversion=conversion, targetFormat=output_format)
    try:
        for identity in (actor, viewer):
            login = request("/admin-api/system/auth/login", identity,
                            {"username": identity["username"], "password": identity["password"]}, "POST", gateway=True)
            check(identity["role"] + "真实登录", login.get("code") == 200)
            identity["token"] = login["data"]["accessToken"]
        body = {"operationId": operation, "operationType": "CREATE", "resourceScope": "TENANT_SHARED",
                "metadata": {"resourceName": "MODEL02-接收验证-" + operation[:8], "assetName": "probe"},
                "targetFormat": output_format, "source": {"size": str(len(payload)), "sha256": digest}}
        check("匿名创建拒绝", request("/admin-api/dim3d/model-imports", body=body, method="POST").get("code") == 401)
        invalid = {**actor, "token": "model02-invalid-" + uuid.uuid4().hex}
        check("无效登录令牌创建拒绝", request("/admin-api/dim3d/model-imports", invalid, body, "POST").get("code") == 401)
        wrong_tenant = {**actor, "tenantId": 1 if str(actor["tenantId"]) != "1" else 2}
        check("有效令牌不能篡改租户", request("/admin-api/dim3d/model-imports", wrong_tenant, body, "POST").get("code") == 403)
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
        check("无效登录令牌不能取得签名", request(path + "/source-target", invalid, {}, "POST").get("code") == 401)
        check("其他操作者不能取得签名", request(path + "/source-target", viewer, {}, "POST").get("code") == 2030001001)
        changed = {**body, "source": {"size": str(len(payload) + 1), "sha256": digest}}
        check("同号不同源文件拒绝", request("/admin-api/dim3d/model-imports", actor, changed, "POST").get("code") == 2030001004)
        target_response = request(path + "/source-target", actor, {}, "POST")
        check("取得绑定签名目标", target_response.get("code") == 200)
        upload = target_response["data"]
        address = urllib.parse.urlsplit(upload["uploadUrl"])
        check("目标绑定地址及摘要", address.scheme == "https" and address.netloc == "192.168.10.148:9445"
              and address.path == object_path and upload["sourceSha256"] == digest and upload["sourceSize"] == str(len(payload)))
        unsigned = urllib.parse.urlunsplit(address._replace(query=""))
        for origin in FRONTEND_ORIGINS:
            preflight = urllib.request.Request(unsigned, method="OPTIONS",
                                              headers={"Origin": origin, "Access-Control-Request-Method": "PUT"})
            with secure.open(preflight, timeout=15) as response:
                check("多前端来源预检:" + origin, response.status == 204
                      and response.headers.get("Access-Control-Allow-Origin") == "*"
                      and response.headers.get("Access-Control-Allow-Credentials") is None)
        try:
            with secure.open(urllib.request.Request(unsigned, data=payload, method="PUT"), timeout=15) as response:
                unsigned_status = response.status
        except urllib.error.HTTPError as error:
            unsigned_status = error.code
            error.close()
        check("取消来源限制后无签名上传仍拒绝", unsigned_status == 403)
        # 主候选模拟浏览器，另一候选不带来源头，验证接口调试工具的实际传输方式。
        headers = {"Content-Type": "application/zip"}
        if target == "main":
            headers["Origin"] = ORIGIN
        result["uploadHasOriginHeader"] = "Origin" in headers
        put = urllib.request.Request(upload["uploadUrl"], data=payload, method="PUT",
                                     headers=headers)
        with secure.open(put, timeout=30) as response:
            uploaded = True
            check("真实签名上传与跨域响应", response.status == 200 and response.headers.get("Access-Control-Allow-Origin") == "*"
                  and response.headers.get("Access-Control-Allow-Credentials") is None)
        status, stored = s3.request("GET", object_path)
        check("私有桶回读字节摘要", status == 200 and len(stored) == len(payload) and hashlib.sha256(stored).hexdigest() == digest)
        queued = request(path + "/submit", actor, {}, "POST")
        check("持久化入队", queued.get("code") == 200 and queued["data"]["importStatus"] == "QUEUED")
        if conversion:
            deadline = time.monotonic() + 120
            while True:
                state = request(path, actor)["data"]
                if state["importStatus"] in {"PUBLISHING", "FAILED", "CANCELLED", "EXPIRED", "CONFLICT", "COMMITTED"}:
                    break
                if time.monotonic() >= deadline:
                    break
                time.sleep(1)
            result["conversionState"] = state
            check("冻结原包及真实转换闭包校验", state["importStatus"] == "PUBLISHING" and state["errorCode"] is None)
            check("待确认阶段未假报最终提交", state["committedRevision"] is None and state["operationStatus"] == "UPLOADING")
        else:
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
        if conversion and result.get("cancelled"):
            try:
                # 仅清理本次操作的后台材料；从未提交目录或资源修订，不影响业务文件。
                prefix = "resources/model-imports/" + operation + "/"
                query = urllib.parse.urlencode({"list-type": "2", "prefix": prefix})
                status, listing = s3.request("GET", "/wx-art-test148", query=query)
                tree = ET.fromstring(listing)
                keys = [item.text for item in tree.findall("{*}Contents/{*}Key")]
                safe = status == 200 and len(keys) <= 16 and tree.findtext("{*}IsTruncated") == "false" and all(key.startswith(prefix) for key in keys)
                result["conversionObjectsRemoved"] = safe and all(s3.request("DELETE", "/wx-art-test148/" + key)[0] in (200, 204) for key in keys)
            except Exception:
                result["conversionObjectsRemoved"] = False
            result["passed"] = result["passed"] and result["conversionObjectsRemoved"]
        # 使用正式注销接口结束本次登录，复验旧令牌失效；不遗留测试会话。
        for identity in (actor, viewer):
            if not identity.get("token"):
                continue
            try:
                logout = request("/admin-api/system/auth/logout", identity, {}, "POST", gateway=True)
                revoked = logout.get("code") == 200 and request(path, identity).get("code") == 401
            except Exception:
                revoked = False
            checks.append({"name": identity["role"] + "注销后旧令牌拒绝", "passed": revoked})
            result["passed"] = result["passed"] and revoked
        result["passed"] = result["passed"] and result.get("cancelled", False) and result.get("temporaryObjectRemoved", False)
    name = "conversion-" + source_kind + "-" + output_format if conversion else "source-verification"
    destination = ROOT / "releases/model02-5e0a22ac" / (name + "-" + target + ".json")
    destination.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))
    return result["passed"]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=("main", "data08"), required=True)
    parser.add_argument("--conversion", action="store_true", help="使用转换候选验证到等待确认阶段，不发布目录")
    parser.add_argument("--source-kind", choices=("obj", "fbx", "3ds"), default="obj")
    parser.add_argument("--format", choices=("glb", "gltf"), default="glb")
    args = parser.parse_args()
    raise SystemExit(0 if run(args.target, args.conversion, args.source_kind, args.format) else 1)
