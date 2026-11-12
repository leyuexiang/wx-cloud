"""校验 OSS 分钟级计划与文档，不运行业务测试或连接服务器。"""

import json
import pathlib
import re
import subprocess
import unicodedata
from urllib.parse import unquote


ROOT = pathlib.Path(__file__).resolve().parents[3]
EVIDENCE = pathlib.Path(__file__).resolve().parent
DOCS = [
    "docs/00_执行总览.md",
    "docs/01_项目总体设计.md",
    "docs/02_里程碑与交付计划.md",
    "docs/03_近期执行任务.md",
    "docs/README.md",
    "docs/10_专项设计/11_对象存储服务设计.md",
    "contracts/editor/README.md",
]


def heading_id(title):
    """按本轮使用的普通 Markdown 标题检查本地锚点。"""
    title = title.lower().replace("`", "").replace("*", "")
    title = "".join(
        char for char in title
        if char in "-_" or char.isspace() or unicodedata.category(char)[0] not in "PS"
    )
    return re.sub(r"\s", "-", title)


def main():
    errors = []
    link_count = anchor_count = 0
    for relative in DOCS:
        path = ROOT / relative
        raw = path.read_bytes()
        content = raw.decode("utf-8", errors="strict")
        if raw.startswith(b"\xef\xbb\xbf") or "\ufffd" in content:
            errors.append({"file": relative, "issue": "编码标记或替代字符"})
        # 已跟踪文件只检查当前新增行；新设计文件检查全文，保留旧文档无关问题。
        tracked = subprocess.run(
            ["git", "ls-files", "--error-unmatch", "--", relative],
            cwd=ROOT, capture_output=True,
        ).returncode == 0
        if tracked:
            diff = subprocess.run(
                ["git", "diff", "--unified=0", "--", relative],
                cwd=ROOT, capture_output=True, encoding="utf-8", check=True,
            ).stdout
            content = "\n".join(
                line[1:] for line in diff.splitlines()
                if line.startswith("+") and not line.startswith("+++")
            )
        for link in re.findall(r"\[[^\]]+\]\(([^)]+)\)", content):
            if re.match(r"^[a-zA-Z]+://", link):
                continue
            location, _, anchor = unquote(link).partition("#")
            target = (path.parent / location).resolve() if location else path
            link_count += 1
            if not target.is_file():
                errors.append({"file": relative, "link": link, "issue": "本地文件不存在"})
                continue
            if anchor:
                anchor_count += 1
                headings = re.findall(
                    r"^#{1,6}\s+(.+)$", target.read_text(encoding="utf-8"), re.M,
                )
                if anchor not in {heading_id(value) for value in headings}:
                    errors.append({"file": relative, "link": link, "issue": "标题锚点不存在"})

    plan = (ROOT / DOCS[2]).read_text(encoding="utf-8").split("## 9. 对象存储改造专项", 1)[1]
    ledger = (ROOT / DOCS[3]).read_text(encoding="utf-8")
    nodes = []
    seen = {"OSS-EVAL-01"}
    phase_totals = {}
    for line in plan.splitlines():
        cells = [cell.strip() for cell in line.strip("|").split("|")]
        if not cells or not re.fullmatch(r"OSS-0[1-5]-\d{2}", cells[0]):
            continue
        if len(cells) != 6 or not all(cells):
            errors.append({"node": cells[0], "issue": "节点列数或必需内容错误"})
            continue
        node_id = cells[0]
        minutes = int(cells[1].removesuffix(" 分钟"))
        if node_id in seen or minutes < 10 or minutes > 12:
            errors.append({"node": node_id, "issue": "编号重复或时间盒越界"})
        dependencies = re.findall(r"OSS-(?:EVAL-01|0[1-5]-\d{2})", cells[2])
        if not dependencies or any(value not in seen for value in dependencies):
            errors.append({"node": node_id, "issue": "前置缺失、未知或顺序成环"})
        seen.add(node_id)
        phase = node_id.rsplit("-", 1)[0]
        phase_totals.setdefault(phase, {"nodes": 0, "minutes": 0})
        phase_totals[phase]["nodes"] += 1
        phase_totals[phase]["minutes"] += minutes
        nodes.append({"id": node_id, "minutes": minutes, "dependencies": dependencies})

    for phase, totals in phase_totals.items():
        expected_ids = [f"{phase}-{index:02d}" for index in range(1, totals["nodes"] + 1)]
        actual_ids = [node["id"] for node in nodes if node["id"].startswith(phase + "-")]
        if actual_ids != expected_ids:
            errors.append({"phase": phase, "issue": "节点编号不连续"})
        summary = re.search(
            rf"^\| {phase} [^\n]+? \| (\d+) \| (\d+) 分钟 \|", plan, re.M,
        )
        if not summary or (int(summary[1]), int(summary[2])) != (totals["nodes"], totals["minutes"]):
            errors.append({"phase": phase, "issue": "里程碑汇总不一致"})
        status_range = f"{phase}-01～{phase}-{totals['nodes']:02d} | {totals['nodes']} | 未开始"
        if status_range not in ledger:
            errors.append({"phase": phase, "issue": "03 未覆盖全部初始节点默认状态"})

    total_minutes = sum(node["minutes"] for node in nodes)
    if len(phase_totals) != 5 or len(nodes) != 104 or total_minutes != 1152:
        errors.append({"issue": "总里程碑/节点数/时间盒汇总不一致"})
    if f"{len(nodes)} 个初始节点，首轮时间盒合计 {total_minutes} 分钟" not in plan:
        errors.append({"issue": "计划总计文字不一致"})
    for check_file in [pathlib.Path(__file__), EVIDENCE / "validation.json"]:
        check_file.read_bytes().decode("utf-8", errors="strict")
    whitespace = subprocess.run(["git", "diff", "--check"], cwd=ROOT, capture_output=True)
    business = subprocess.run(
        ["git", "diff", "--exit-code", "--", "pom.xml", "wx-dependencies/pom.xml",
         "wx-module-infra", "wx-module-3d"], cwd=ROOT, capture_output=True,
    )
    if whitespace.returncode or business.returncode:
        errors.append({"issue": "差异空白错误或业务文件发生变化"})
    result = {
        "task": "OSS-PLAN-01", "date": "2026-11-10",
        "document_files": len(DOCS), "local_links_checked": link_count,
        "heading_anchors_checked": anchor_count, "milestones": phase_totals,
        "nodes": len(nodes), "first_pass_minutes": total_minutes,
        "node_minutes_range": [min(node["minutes"] for node in nodes), max(node["minutes"] for node in nodes)],
        "dependencies_ordered": True, "status_ranges_covered_in_03": True,
        "business_files_unchanged": business.returncode == 0,
        "git_diff_check_passed": whitespace.returncode == 0,
        "scope": "静态文档与计划结构检查；不重跑历史探针、不连接真实依赖、不执行迁移部署",
        "errors": errors, "passed": not errors,
    }
    (EVIDENCE / "validation.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
    )
    print(json.dumps(result, ensure_ascii=True))
    return int(bool(errors))


if __name__ == "__main__":
    raise SystemExit(main())
