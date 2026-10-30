"""验证 ART-01 合同示例、输入边界和本地引用；不调用尚未实现的服务。"""

from pathlib import Path
import argparse
import copy
import hashlib
import json
import re
import jsonschema
import yaml


ROOT = Path(__file__).resolve().parent
SCHEMA = json.loads((ROOT / "resource-contract.schema.json").read_text(encoding="utf-8"))
EXAMPLES = json.loads((ROOT / "examples.json").read_text(encoding="utf-8"))
CHECKER = jsonschema.FormatChecker()


@CHECKER.checks("positive-int64-string")
def positive_int64(value):
    return isinstance(value, str) and value.isascii() and value.isdigit() and 0 < int(value) <= 9223372036854775807


@CHECKER.checks("nonnegative-int64-string")
def nonnegative_int64(value):
    return isinstance(value, str) and value.isascii() and value.isdigit() and 0 <= int(value) <= 9223372036854775807


@CHECKER.checks("positive-file-size-string")
def positive_file_size(value):
    return isinstance(value, str) and value.isascii() and value.isdigit() and 0 < int(value) <= 2147483648


def content_hash(manifest):
    """摘要输入只有确定性字符串与对象，灯光浮点值由子文件字节摘要保护。"""
    payload = copy.deepcopy(manifest)
    payload.pop("contentHash", None)
    payload["files"].sort(key=lambda value: value["path"])
    canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return canonical, hashlib.sha256(canonical).hexdigest()


def valid_manifest(manifest):
    validator = jsonschema.Draft202012Validator({**SCHEMA, "$ref": "#/$defs/resourceManifest"}, format_checker=CHECKER)
    if not validator.is_valid(manifest): return False
    paths = [row["path"] for row in manifest["files"]]
    if len(set(paths)) != len(paths) or "manifest.json" in paths: return False
    if any(value not in paths for key, value in manifest["entry"].items() if key.endswith("Path")): return False
    return content_hash(manifest)[1] == manifest["contentHash"]


def verify():
    jsonschema.Draft202012Validator.check_schema(SCHEMA)
    results = []

    def check(name, definition, value, valid):
        validator = jsonschema.Draft202012Validator({**SCHEMA, "$ref": "#/$defs/" + definition}, format_checker=CHECKER)
        actual = validator.is_valid(value)
        results.append({"name": name, "passed": actual == valid})
        if actual != valid:
            raise AssertionError(name + ": " + str(list(validator.iter_errors(value))))

    check("全局首次创建", "operationRequest", EXAMPLES["create"], True)
    tenant = copy.deepcopy(EXAMPLES["create"])
    tenant["resourceScope"] = "TENANT_SHARED"
    check("租户共享首次创建", "operationRequest", tenant, True)
    check("单资源替换", "operationRequest", EXAMPLES["replace"], True)
    for kind in ("DELETE", "RESTORE"):
        value = {**EXAMPLES["replace"], "operationType": kind}
        if kind == "RESTORE": value["snapshotOperationId"] = EXAMPLES["create"]["operationId"]
        check(kind, "operationRequest", value, True)
    check("内容提交", "contentCommit", EXAMPLES["commit"], True)
    check("删除恢复空提交", "emptyCommit", {}, True)
    check("当前描述", "current", EXAMPLES["current"], True)
    for field in ("tenantId", "tenant_id", "operatorId", "projectId", "role", "resourceId"):
        check("创建拒绝客户端归属或ID:" + field, "operationRequest", {**EXAMPLES["create"], field: "1"}, False)
    for field in ("tenantId", "resourceScope", "storagePrefix"):
        check("替换拒绝改范围或前缀:" + field, "operationRequest", {**EXAMPLES["replace"], field: "GLOBAL_SYSTEM"}, False)
    for value in (1, "0", "01", "9223372036854775808", "１", "-1"):
        check("资源ID边界:" + str(value), "operationRequest", {**EXAMPLES["replace"], "resourceId": value}, False)
    missing = dict(EXAMPLES["replace"])
    del missing["expectedRevision"]
    check("替换必须声明预期修订", "operationRequest", missing, False)
    for value in (0, "-1", "01", "9223372036854775808"):
        check("修订边界:" + str(value), "operationRequest", {**EXAMPLES["replace"], "expectedRevision": value}, False)
    for value in ("../manifest.json", "a/../manifest.json", "./manifest.json", "/manifest.json",
                  "a//manifest.json", "a\\manifest.json", "%2e%2e/manifest.json", "https://evil.invalid/m", "a?sig=x", "a#x"):
        check("清单路径边界:" + value, "contentCommit", {**EXAMPLES["commit"], "manifestPath": value}, False)
    for value in ("A" * 64, "a" * 63, "g" * 64):
        check("摘要格式:" + value[:4], "contentCommit", {**EXAMPLES["commit"], "contentHash": value}, False)
    check("拒绝错误平台名", "contentCommit", {**EXAMPLES["commit"], "platform": "WebGPU"}, False)
    check("拒绝空兼容标识", "contentCommit", {**EXAMPLES["commit"], "appCompatibility": ""}, False)
    check("拒绝空白兼容标识", "contentCommit", {**EXAMPLES["commit"], "appCompatibility": " "}, False)
    blank_name = copy.deepcopy(EXAMPLES["create"])
    blank_name["metadata"]["resourceName"] = " "
    check("拒绝空白资源名称", "operationRequest", blank_name, False)
    check("拒绝HTTP下载", "current", {**EXAMPLES["current"], "manifestUrl": "http://evil.invalid/m"}, False)
    check("当前内容修订不能为零", "current", {**EXAMPLES["current"], "currentRevision": "0"}, False)
    check("上传文件", "uploadTargetsRequest", {"files": [{"path": "packs/car.bundle", "size": 123, "sha256": "a" * 64}]}, True)
    check("拒绝空文件列表", "uploadTargetsRequest", {"files": []}, False)
    operation = {"operationId": EXAMPLES["create"]["operationId"], "resourceId": EXAMPLES["replace"]["resourceId"],
                 "operationType": "REPLACE", "status": "COMMITTED", "expectedRevision": "1", "expiresAt": "2026-10-29T10:00:00Z"}
    check("已提交必须有提交修订", "operation", operation, False)
    check("已提交操作示例", "operation", {**operation, "committedRevision": "2"}, True)
    page = {"list": [{key: EXAMPLES["current"][key] for key in ("resourceId", "resourceName", "resourceScope", "currentRevision", "contentHash")}], "total": 1}
    page["list"][0]["resourceType"] = "MODEL"
    check("资源分页示例", "resourcePage", page, True)
    check("分页拒绝负总数", "resourcePage", {**page, "total": -1}, False)
    target = {"operationId": EXAMPLES["create"]["operationId"], "expiresAt": "2026-10-29T10:00:00Z", "targets": [
        {"path": "packs/car.bundle", "url": "https://resources.example.invalid/object", "method": "PUT", "requiredHeaders": {"Content-Type": "application/octet-stream"}}]}
    check("受限上传目标示例", "uploadTargetsResponse", target, True)
    unsafe = copy.deepcopy(target)
    unsafe["targets"][0]["url"] = "http://evil.invalid/object"
    check("上传目标必须为HTTPS", "uploadTargetsResponse", unsafe, False)
    fixture = json.loads((ROOT / "content-hash-fixture.json").read_text(encoding="utf-8"))
    check("主清单结构", "resourceManifest", fixture["manifest"], True)
    canonical, digest = content_hash(fixture["manifest"])
    assert canonical.hex() == fixture["canonicalUtf8Hex"] and digest == fixture["contentHash"]
    results.append({"name": "跨语言UTF-8摘要夹具", "passed": True})
    assert valid_manifest(fixture["manifest"])
    reordered = copy.deepcopy(fixture["manifest"])
    reordered["files"].reverse()
    assert valid_manifest(reordered)
    results.append({"name": "文件顺序变化保持规范内容摘要", "passed": True})
    for name, changed in (("摘要错误", lambda m: m.update(contentHash="a" * 64)),
                          ("悬空入口", lambda m: m["entry"].update(packagePath="packs/missing.bundle")),
                          ("同路径不同摘要", lambda m: m["files"].append({**m["files"][0], "sha256": "f" * 64})),
                          ("主清单不能成为自身文件", lambda m: m["files"].append({"path": "manifest.json", "size": "1", "sha256": "a" * 64})),
                          ("未声明行为字段", lambda m: m.update(projectId="1"))):
        broken = copy.deepcopy(fixture["manifest"])
        changed(broken)
        assert not valid_manifest(broken), name
        results.append({"name": "主清单拒绝:" + name, "passed": True})
    errors = json.loads((ROOT / "errors.json").read_text(encoding="utf-8"))
    assert len(errors) == len({item["code"] for item in errors}) == 19
    results.append({"name": "领域错误码唯一", "passed": True})
    spec = yaml.safe_load((ROOT / "openapi.yaml").read_text(encoding="utf-8"))
    operation_ids = []

    def visit(value):
        if isinstance(value, dict):
            if "$ref" in value:
                match = re.fullmatch(r"\./resource-contract\.schema\.json#/\$defs/(\w+)", value["$ref"])
                assert match and match[1] in SCHEMA["$defs"], "OpenAPI包含失效或远程引用"
            if "operationId" in value: operation_ids.append(value["operationId"])
            for item in value.values(): visit(item)
        elif isinstance(value, list):
            for item in value: visit(item)

    visit(spec)
    assert spec["openapi"] == "3.1.0" and spec["security"] == [{"bearerAuth": []}]
    assert len(operation_ids) == len(set(operation_ids)) == 8
    # 逐文件签发必须携带固定身份，不能退回仅查询当前资源的旧合同。
    file_route = spec["paths"]["/admin-api/dim3d/resources/{resourceId}/file"]
    assert {item["name"] for item in file_route["parameters"] if item.get("required")} == {"resourceId", "revision", "contentHash", "path"}
    results.append({"name": "OpenAPI语法、本地引用及操作身份唯一", "passed": True})
    return {"scope": "ART-01离线合同验证，不是接口或权限验收", "passed": len(results), "failed": 0, "checks": results}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    result = verify()
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"passed": result["passed"], "failed": result["failed"]}))
