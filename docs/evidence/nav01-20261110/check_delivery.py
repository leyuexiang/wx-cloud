"""检查NAV-01本地交付记录，不将隔离页面测试解释为真实后端联验。"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.parse

ROOT = Path(__file__).resolve().parents[3]
WEB = ROOT.parents[1] / "Web/web-unity"
OUT = Path(__file__).parent

def main():
    documents = [
        ROOT / "docs/00_执行总览.md", ROOT / "docs/01_项目总体设计.md",
        ROOT / "docs/03_近期执行任务.md", ROOT / "docs/README.md",
        ROOT / "docs/design/05_IoT动态多连接接入方案.md",
        ROOT / "docs/guides/04_IoT连接器管理接口.md",
        WEB / "README.md", WEB / "docs/design/01_IoT管理界面设计.md",
        WEB / "docs/design/02_管理导航统一设计.md",
        WEB / "docs/api/05-独立登录鉴权设计与环境切换.md",
        WEB / "docs/evidence/nav01-20261110/README.md",
    ]
    errors, links = [], 0
    for path in documents:
        text = path.read_bytes().decode("utf-8", errors="strict")
        assert "\ufffd" not in text
        if path.name == "03_近期执行任务.md":
            text = text.split("### IOT-UI-02")[0]
        for value in re.findall(r"\[[^\]]*\]\(([^)]+)\)", text):
            if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", value):
                continue
            target, _, anchor = urllib.parse.unquote(value.strip("<>")).partition("#")
            resolved = (path.parent / target).resolve() if target else path
            if resolved == OUT / "verification.json":
                continue
            links += 1
            if not resolved.exists():
                errors.append(f"{path.name}: {value}")
            elif anchor and resolved.suffix == ".md":
                titles = re.findall(r"^#+\s+(.+)$", resolved.read_text(encoding="utf-8"), re.M)
                slugs = {re.sub(r"[^\w\- ]", "", title.lower()).replace(" ", "-") for title in titles}
                if anchor not in slugs:
                    errors.append(f"缺失锚点 {path.name}: {value}")
    manifest = json.loads((ROOT / "db/system/editor-management-menus.json").read_text(encoding="utf-8"))
    pages = [page for group in manifest["groups"] for page in group["pages"]]
    assert len(pages) == 4
    source = (WEB / "src/auth/managementNavigation.ts").read_text(encoding="utf-8")
    for page in pages:
        assert page["request"]["component"] in source and "parentId" not in page["request"]
    header = (WEB / "src/layout/Header.vue").read_text(encoding="utf-8")
    assert '<ManagementNavigationMenu :nodes="managementNavigation"' in header
    assert '<el-menu-item index="modelManage"' not in header
    assert 'getManagementFallbackRoute' in (WEB / "src/router/index.ts").read_text(encoding="utf-8")
    browser = json.loads((WEB / "docs/evidence/nav01-20261110/browser-checks.json").read_text(encoding="utf-8"))
    twod = json.loads((WEB / "docs/evidence/nav01-20261110/two-d-regression.json").read_text(encoding="utf-8"))
    checks = {}
    for name, directory in (("backend", ROOT), ("frontend", WEB)):
        checks[name] = subprocess.run(["git", "diff", "--check"], cwd=directory, capture_output=True).returncode == 0
    logs = Path(os.environ["TEMP"])
    tests = (logs / "nav01-tests.log").read_text(encoding="utf-8", errors="replace")
    build = (logs / "nav01-build.log").read_text(encoding="utf-8", errors="replace")
    types = (logs / "nav01-types.log").read_text(encoding="utf-8", errors="replace")
    tests_passed = bool(re.search(r"Tests\s+76 passed", tests))
    result = {
        "date": "2026-11-10", "task": "NAV-01",
        "utf8_documents": len(documents), "local_links_checked": links,
        "unit_tests": 76, "unit_tests_passed": tests_passed,
        "browser_checks": len(browser["checks"]), "browser_passed": browser["passed"],
        "two_d_regression_passed": twod["passed"], "two_d_component_count": twod["registryCount"],
        "typecheck_passed": "error TS" not in types and "vue-tsc -b" in types,
        "build_passed": "built in" in build, "diff_checks": checks, "errors": errors,
        "frontend_index_sha256": hashlib.sha256((WEB / "dist/web-unity/index.html").read_bytes()).hexdigest(),
        "menu_manifest_applied": False, "deployed": False, "real_gateway_verified": False,
    }
    result["passed"] = (not errors and tests_passed and browser["passed"] and twod["passed"]
                        and result["typecheck_passed"] and result["build_passed"] and all(checks.values()))
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "verification.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["passed"] else 1

if __name__ == "__main__":
    raise SystemExit(main())
