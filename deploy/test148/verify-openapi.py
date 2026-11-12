"""只读检查文档页面、聚合、原始规范及相邻业务鉴权，不记录业务响应内容。"""

import argparse
import json
import re
import urllib.error
import urllib.request
from pathlib import Path


def verify(base):
    """不能仅按 HTTP 200 判成功：同时检查规范结构与业务错误码。"""
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    checks = []

    def request(path, headers=None):
        try:
            response = opener.open(urllib.request.Request(base + path, headers=headers or {}), timeout=25)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read().decode("utf-8")
            return response.status, raw

    def check(name, passed, **details):
        checks.append(dict(name=name, passed=bool(passed), **details))

    status, html = request("/doc.html")
    check("文档页面", status == 200 and "<html" in html.lower())
    # 读取页面真实引用的脚本，避免空 HTML 壳被误判为可用页面。
    scripts = re.findall(r'<script[^>]+src=["\']?([^\s"\'>]+)', html)
    check("页面包含脚本", bool(scripts))
    for path in scripts:
        status, body = request("/" + path.lstrip("/"))
        check("页面脚本 " + path, status == 200 and len(body) > 100)
    # 当前 Knife4j 4.5 使用 OpenAPI 3 聚合端点，旧 /swagger-resources 不再提供服务。
    status, raw = request("/v3/api-docs/swagger-config")
    aggregation = json.loads(raw)
    resources = aggregation.get("urls", [])
    expected = {"/admin-api/" + name + "/v3/api-docs" for name in ("system", "infra", "dimensions")}
    check("三个已部署模块聚合", isinstance(resources, list)
          and {item.get("url") for item in resources} == expected, resources=resources)
    for name in ("system", "infra", "dimensions"):
        status, raw = request("/admin-api/" + name + "/v3/api-docs")
        document = json.loads(raw)
        paths = document.get("paths", {})
        check(name + " 原始规范", status == 200 and str(document.get("openapi", "")).startswith("3.")
              and bool(paths), pathCount=len(paths))
        # 原规范同时含内部翻译代理和许可接口；只核对网关承载的业务路径没有重复模块前缀。
        business = [path for path in paths if path.startswith("/admin-api/")]
        check(name + " 调试路径保留业务前缀", bool(business)
              and all("/admin-api/admin-api/" not in path for path in business), pathSample=business[:2])
    protected = ["/admin-api/system/user/profile/get", "/admin-api/infra/file-config/page",
                 "/admin-api/dim3d/model/page", "/admin-api/system/v3/api-docs-unrelated"]
    for path in protected:
        for forged in (False, True):
            headers = {"tenant-id": "1"}
            if forged:
                headers.update({"login-user": '{"id":1,"userType":2,"tenantId":1}', "x-bot-user": "3"})
            status, raw = request(path, headers)
            code = json.loads(raw).get("code", status)
            check(path + (" 伪造身份拒绝" if forged else " 匿名拒绝"), code in (401, 403), code=code)
    return {"base": base, "passed": all(item["passed"] for item in checks), "checks": checks}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:12522")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    report = verify(args.base)
    Path(args.output).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    raise SystemExit(0 if report["passed"] else 1)
