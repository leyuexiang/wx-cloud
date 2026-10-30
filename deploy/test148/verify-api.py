"""验证测试入口的登录、核心表读取和权限拒绝路径，不记录密码与令牌。"""

from pathlib import Path
import datetime
import json
import os
import urllib.error
import urllib.request

ROOT = Path("/opt/wx/editor-test148")
BASE = "http://127.0.0.1:" + os.environ.get("WX_VERIFY_PORT", "12532")
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
results = []


def request(path, tenant=1, token=None, method="GET", body=None, extra=None):
    headers = {"tenant-id": str(tenant), "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    headers.update(extra or {})
    req = urllib.request.Request(BASE + path, headers=headers, method=method,
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        response = opener.open(req, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    raw = response.read().decode()
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return {"code": response.status, "msg": raw[:150]}


def check(name, response, expected):
    code = response.get("code", 0 if response.get("status") == "UP" else None)
    passed = code in expected
    item = {"name": name, "passed": passed, "code": code}
    if not passed:
        item["message"] = response.get("msg", "")
    results.append(item)
    print(json.dumps(item, ensure_ascii=False))
    return passed


check("网关健康", request("/actuator/health"), {0})
tokens = {}
for identity in json.loads((ROOT / "test-identities.json").read_text()):
    response = request("/admin-api/system/auth/login", identity["tenantId"], method="POST",
                       body={"username": identity["username"], "password": identity["password"]})
    if check(identity["role"] + " 登录", response, {200}):
        tokens[identity["role"]] = response["data"]["accessToken"]

admin = tokens.get("super_admin")
tenant_admin = tokens.get("tenant_admin")
viewer = tokens.get("viewer")
if admin:
    check("平台管理员读取权限", request("/admin-api/system/auth/get-permission-info", token=admin), {200})
    check("平台管理员读取模型目录", request("/admin-api/dim3d/model/page?pageNum=1&pageSize=10", token=admin), {200})
    check("平台管理员读取场景目录", request("/admin-api/dimensions/project-engineering/get-scene-list?platformType=U3D_WEB", token=admin), {200})
if tenant_admin:
    check("租户管理员读取模型目录", request("/admin-api/dim3d/model/page?pageNum=1&pageSize=10", 2, tenant_admin), {200})
    check("拒绝跨租户请求头", request("/admin-api/dim3d/model/page", 1, tenant_admin), {403})
if viewer:
    check("普通用户读取模型目录", request("/admin-api/dim3d/model/page?pageNum=1&pageSize=10", 2, viewer), {200})
    check("普通用户不能修改模型", request("/admin-api/dim3d/model/edit/9223372036854775807", 2, viewer,
          method="PUT", body={"modelName": "权限拒绝验证", "category": "TEST", "modelFileId": 1}), {403})
check("拒绝匿名资源查询", request("/admin-api/dim3d/model/page"), {401, 403})
check("拒绝伪造内部身份头", request("/admin-api/dim3d/model/page", extra={
    "login-user": '{"id":1,"userType":2,"tenantId":1}', "x-bot-user": "3"}), {401, 403})
check("未标注方法权限的场景接口仍要求登录", request(
    "/admin-api/dimensions/project-engineering/get-scene-info?platformType=U3D_WEB"), {401})
if viewer:
    check("普通用户令牌不能借伪造身份提权", request(
        "/admin-api/dim3d/model/edit/9223372036854775807", 2, viewer, method="PUT",
        body={"modelName": "权限拒绝验证", "category": "TEST", "modelFileId": 1},
        extra={"login-user": '{"id":1,"userType":2,"tenantId":2}', "x-bot-user": "3"}), {403})

if tenant_admin:
    # 验证真实 API 写入及请求体归属篡改；最后通过同一业务接口删除测试记录。
    response = request("/admin-api/dim3d/model/add", 2, tenant_admin, method="POST", body={
        "modelName": "测试148归属校验", "category": "TEST", "modelFileId": 1,
        "resourceBelong": "MINE", "tenantId": "1",
    })
    if check("模型元数据新增", response, {200}):
        model_id = response["data"]
        try:
            read = request(f"/admin-api/dim3d/model/info/{model_id}", 2, tenant_admin)
            check("模型元数据回读", read, {200})
            actual_tenant = str((read.get("data") or {}).get("tenantId"))
            check("忽略请求体伪造的租户归属", {"code": 200 if actual_tenant == "2" else 500}, {200})
            other = request(f"/admin-api/dim3d/model/info/{model_id}", 1, admin)
            denied = other.get("code") == 404 and other.get("data") is None
            check("其他租户无法读取该模型", {"code": 403 if denied else 200}, {403})
        finally:
            check("删除验收模型记录", request(f"/admin-api/dim3d/model/remove/{model_id}", 2,
                  tenant_admin, method="DELETE"), {200})

report = {"time": datetime.datetime.now().isoformat(), "base": BASE,
          "passed": all(item["passed"] for item in results), "checks": results}
(ROOT / ("verification-" + BASE.rsplit(":", 1)[1] + ".json")).write_text(
    json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
raise SystemExit(0 if report["passed"] else 1)
