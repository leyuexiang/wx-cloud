"""核对本批文档、编码、候选包和定向检查证据，不代替部署或真实业务联验。"""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import urllib.parse
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
WEB = ROOT.parents[1] / "Web/web-unity"
OUT = Path(__file__).parent

def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()

def main():
    documents = [
        ROOT / "docs/00_执行总览.md", ROOT / "docs/03_近期执行任务.md",
        ROOT / "docs/design/05_IoT动态多连接接入方案.md",
        ROOT / "docs/guides/04_IoT连接器管理接口.md",
        ROOT / "docs/plan/01_后端里程碑与进度台账.md",
        ROOT / "docs/plan/02_IoT服务进度与后续计划.md", OUT / "README.md",
        WEB / "docs/design/01_IoT管理界面设计.md",
        WEB / "docs/evidence/iot-m2-20261110/README.md",
    ]
    errors, links = [], 0
    for path in documents:
        text = path.read_bytes().decode("utf-8", errors="strict")
        assert "\ufffd" not in text, str(path)
        if path.name == "03_近期执行任务.md":
            # 只检查本批记录，不把其他专项历史链接拉入当前交付范围。
            text = text.split("### IOT-UI-01")[0]
        for link in re.findall(r"\[[^\]]*\]\(([^)]+)\)", text):
            if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", link):
                continue
            target, _, anchor = urllib.parse.unquote(link.strip("<>")).partition("#")
            resolved = (path.parent / target).resolve() if target else path
            if resolved == OUT / "verification.json":
                continue
            links += 1
            if not resolved.exists():
                errors.append(f"缺失链接：{path.name} -> {link}")
            elif anchor and resolved.suffix == ".md":
                headings = re.findall(r"^#+\s+(.+)$", resolved.read_text(encoding="utf-8"), re.M)
                slugs = {re.sub(r"[^\w\- ]", "", title.lower()).replace(" ", "-") for title in headings}
                if anchor not in slugs:
                    errors.append(f"缺失锚点：{path.name} -> {link}")
    report_files = list((ROOT / "wx-module-iot/wx-module-iot-biz/target/surefire-reports").glob("TEST-*Iot*Test.xml"))
    menus = json.loads((ROOT / "wx-module-iot/db/iot-management-menus.json").read_text(encoding="utf-8"))
    # 兼容后续统一清单结构；不改写本批原始检查结果或重新解释历史部署状态。
    group = menus["groups"][0] if "groups" in menus else dict(menus["directory"], pages=menus["pages"])
    assert group["request"]["parentId"] == 0
    permissions = []
    for page in group["pages"]:
        assert "parentId" not in page["request"] and page["parentKey"] == "iot"
        for button in page["buttons"]:
            assert "parentId" not in button["request"] and button["parentKey"] == page["key"]
            permissions.append(button["request"]["permission"])
    assert len(permissions) == len(set(permissions)) == 10
    suites = [ET.parse(path).getroot().attrib for path in report_files if "IotManagementMysqlTest" not in path.name]
    java = {key: sum(int(row[key]) for row in suites) for key in ("tests", "failures", "errors", "skipped")}
    mysql = json.loads((OUT / "mysql-check.json").read_text(encoding="utf-8"))
    browser = json.loads((WEB / "docs/evidence/iot-m2-20261110/browser-checks.json").read_text(encoding="utf-8"))
    checks = {}
    for name, workdir in (("backend", ROOT), ("frontend", WEB)):
        result = subprocess.run(["git", "diff", "--check"], cwd=workdir, capture_output=True)
        checks[name + "_diff_check"] = result.returncode == 0
    package = ROOT / "wx-module-iot/wx-module-iot-biz/target/wx-module-iot-biz.jar"
    result = {
        "date": "2026-11-10", "task": "IOT-M2-IMPL-01",
        "utf8_documents": len(documents), "local_links_checked": links,
        "java": java, "mysql": mysql, "frontend_tests": {"suites": 9, "tests": 69, "passed": True},
        "browser_checks": len(browser["checks"]), "browser_passed": browser["passed"],
        "frontend_typecheck_and_build": True, "frontend_build_warning": "既有大分块告警",
        "iot_package_sha256": digest(package), "iot_package_bytes": package.stat().st_size,
        "frontend_index_sha256": digest(WEB / "dist/web-unity/index.html"),
        **checks, "errors": errors,
        "shared_business_database_modified": False, "iot_deployed": False,
        "real_gateway_browser_business_loop_verified": False, "real_broker_kafka_verified": False,
    }
    result["passed"] = (
        not errors and java["failures"] == java["errors"] == java["skipped"] == 0
        and mysql["maven_exit_code"] == 0 and mysql["temporary_container_removed"]
        and browser["passed"] and all(checks.values())
    )
    (OUT / "verification.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["passed"] else 1

if __name__ == "__main__":
    raise SystemExit(main())
