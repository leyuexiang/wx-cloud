"""比对RVT引擎导出结果与独立基准；本程序不解析RVT，也不证明完整验收通过。"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import struct
import sys
from pathlib import Path


class InputError(ValueError):
    """输入缺失或契约不正确时停止，避免把无效输入当成比对通过。"""


def reject_constant(value: str) -> None:
    raise InputError(f"JSON中不允许非有限数值：{value}")


def read_json(path: Path):
    with path.open(encoding="utf-8-sig") as stream:
        return json.load(stream, parse_constant=reject_constant)


def required_text(data: dict, key: str) -> str:
    value = data.get(key)
    if not isinstance(value, str) or not value.strip() or "REPLACE_WITH" in value:
        raise InputError(f"{key}必须填写真实的非空字符串")
    return value


def contained_file(root: Path, name: str) -> Path:
    # 所有导出资源必须位于同一结果目录，禁止清单引用目录外文件。
    if not isinstance(name, str) or not name:
        raise InputError("导出文件名必须是非空字符串")
    relative = Path(name)
    path = (root / relative).resolve()
    if relative.is_absolute() or not path.is_relative_to(root) or not path.is_file():
        raise InputError(f"结果文件缺失或超出结果目录：{name}")
    return path


def vector(value, label: str) -> list:
    if not isinstance(value, list) or len(value) != 3:
        raise InputError(f"{label}必须包含三个有限坐标")
    if any(type(item) not in (int, float) or not math.isfinite(item) for item in value):
        raise InputError(f"{label}包含无效坐标")
    return value


def load_elements(path: Path, actual: bool) -> dict:
    elements = {}
    with path.open(encoding="utf-8-sig") as stream:
        for line_no, line in enumerate(stream, 1):
            if not line.strip():
                continue
            row = json.loads(line, parse_constant=reject_constant)
            if not isinstance(row, dict):
                raise InputError(f"{path.name}第{line_no}行不是JSON对象")
            key = (required_text(row, "occurrencePath"), required_text(row, "sourceUniqueId"))
            if key in elements:
                raise InputError(f"{path.name}出现重复构件：{key}")
            required_text(row, "sourceElementId")
            if type(row.get("hasGeometry")) is not bool:
                raise InputError(f"{key}缺少布尔类型hasGeometry")
            props = row.get("properties")
            if not isinstance(props, dict) or not {"name", "category"}.issubset(props):
                raise InputError(f"{key}属性必须至少包含name和category")
            if "positionM" in row:
                vector(row["positionM"], "positionM")
            if "bboxM" in row:
                bbox = row["bboxM"]
                if not isinstance(bbox, dict):
                    raise InputError(f"{key}包围盒必须是对象")
                low, high = vector(bbox.get("min"), "bboxM.min"), vector(bbox.get("max"), "bboxM.max")
                if any(a > b for a, b in zip(low, high)):
                    raise InputError(f"{key}包围盒min不得大于max")
            if actual:
                refs = row.get("geometryRefs", [])
                if not isinstance(refs, list) or any(not isinstance(ref, str) or not ref for ref in refs):
                    raise InputError(f"{key}几何引用必须是字符串列表")
            elements[key] = row
    if not elements:
        raise InputError(f"构件清单不能为空：{path}")
    return elements


def sha256(path: Path) -> str:
    # 大文件分块计算摘要，避免按源模型大小分配内存。
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def compare(case_path: Path, result_dir: Path, output: Path) -> dict:
    case, manifest = read_json(case_path), read_json(result_dir / "manifest.json")
    if not isinstance(case, dict) or not isinstance(manifest, dict):
        raise InputError("任务配置和输出清单必须是JSON对象")
    if case.get("schemaVersion") != 1 or manifest.get("schemaVersion") != 1:
        raise InputError("仅支持schemaVersion=1")
    source = (case_path.parent / required_text(case, "sourcePath")).resolve()
    baseline_path = (case_path.parent / required_text(case, "baselinePath")).resolve()
    if source.suffix.lower() != ".rvt" or not source.is_file():
        raise InputError("sourcePath必须指向存在的原始RVT文件")
    tolerance = case.get("coordinateToleranceM")
    if type(tolerance) not in (int, float) or not math.isfinite(tolerance) or tolerance < 0:
        raise InputError("coordinateToleranceM必须是非负有限数值")
    engine = manifest.get("engine")
    if not isinstance(engine, dict):
        raise InputError("清单缺少engine对象")
    for field in ("name", "version"):
        required_text(engine, field)
    if engine.get("runMode") not in ("PRIVATE", "CLOUD"):
        raise InputError("engine.runMode必须是PRIVATE或CLOUD")
    actual_path = contained_file(result_dir, required_text(manifest, "elementsFile"))
    errors_path = contained_file(result_dir, required_text(manifest, "errorsFile"))
    assets = manifest.get("artifacts")
    if not isinstance(assets, list):
        raise InputError("清单artifacts必须是文件名列表")
    asset_paths = {contained_file(result_dir, name) for name in assets}
    # 报告只能写新路径，避免命令行参数误覆盖源文件、基准或引擎产物。
    inputs = {case_path, source, baseline_path, result_dir / "manifest.json", actual_path, errors_path} | asset_paths
    if output in inputs:
        raise InputError("报告路径不能覆盖输入或引擎产物")
    baseline, actual = load_elements(baseline_path, False), load_elements(actual_path, True)
    errors = read_json(errors_path)
    if not isinstance(errors, list):
        raise InputError("errors.json必须是列表")
    issues, issue_count = [], 0

    def issue(code: str, detail: str) -> None:
        nonlocal issue_count
        issue_count += 1
        # 报告限制详细项数量，大批缺失时仍保留总数，避免输出无界增长。
        if len(issues) < 100:
            issues.append({"code": code, "detail": detail})

    for field in ("caseId", "sourceRevitVersion", "filterRevision", "coordinateFrame"):
        expected = required_text(case, field)
        if manifest.get(field) != expected:
            issue("CONTEXT_MISMATCH", field)
    source_hash = sha256(source)
    declared_hash = manifest.get("sourceSha256", "")
    if not isinstance(declared_hash, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", declared_hash):
        raise InputError("sourceSha256必须填写原始RVT的64位十六进制摘要")
    if source_hash != declared_hash.lower():
        issue("SOURCE_HASH_MISMATCH", "输入RVT与导出清单摘要不同")
    if manifest.get("status") != "SUCCEEDED" or manifest.get("unit") != "m":
        issue("MANIFEST_STATUS_OR_UNIT", "要求SUCCEEDED且坐标单位为m")
    if errors:
        issue("UNRESOLVED_ERRORS", f"存在{len(errors)}条未解决错误")
    for asset in sorted(asset_paths):
        size = asset.stat().st_size
        if not size:
            issue("EMPTY_ASSET", asset.name)
        elif asset.suffix.lower() == ".glb":
            with asset.open("rb") as stream:
                header = stream.read(12)
            if size < 20 or len(header) != 12 or struct.unpack("<4sII", header) != (b"glTF", 2, size):
                issue("INVALID_GLB_HEADER", asset.name)
    missing, unexpected = baseline.keys() - actual.keys(), actual.keys() - baseline.keys()
    for key in sorted(missing):
        issue("MISSING_ELEMENT", str(key))
    for key in sorted(unexpected):
        issue("UNEXPECTED_ELEMENT", str(key))
    for key, row in actual.items():
        refs = row.get("geometryRefs", [])
        if row["hasGeometry"] and not refs:
            issue("MISSING_GEOMETRY_REF", str(key))
        for ref in refs:
            if contained_file(result_dir, ref) not in asset_paths:
                issue("UNDECLARED_GEOMETRY_REF", f"{key}: {ref}")
    for key in sorted(baseline.keys() & actual.keys()):
        expected, observed = baseline[key], actual[key]
        for field in ("sourceElementId", "hasGeometry"):
            if expected[field] != observed[field]:
                issue("ELEMENT_FIELD_MISMATCH", f"{key}: {field}")
        for name, value in expected["properties"].items():
            if name not in observed["properties"] or observed["properties"][name] != value:
                issue("PROPERTY_MISMATCH", f"{key}: {name}")
        for field in ("positionM", "bboxM"):
            if field not in expected:
                continue
            if field not in observed:
                issue("MISSING_COORDINATE", f"{key}: {field}")
                continue
            pairs = [(expected[field], observed[field])] if field == "positionM" else [
                (expected[field][corner], observed[field][corner]) for corner in ("min", "max")
            ]
            if any(math.dist(left, right) > tolerance for left, right in pairs):
                issue("COORDINATE_MISMATCH", f"{key}: {field}")
    return {
        "schemaVersion": 1,
        "caseId": case["caseId"],
        "status": "PASSED_EXPORT_COMPARISON" if not issue_count else "FAILED_EXPORT_COMPARISON",
        "scope": "仅检查引擎导出结果与独立基准，不读取RVT内部结构",
        "engine": {key: engine[key] for key in ("name", "version", "runMode")},
        "sourceSha256": source_hash,
        "counts": {"baseline": len(baseline), "actual": len(actual), "missing": len(missing), "unexpected": len(unexpected)},
        "coordinateToleranceM": tolerance,
        "issueCount": issue_count,
        "issues": issues,
        "excludedChecks": ["原生几何正确性", "浏览器交互和构件拾取映射", "无人值守和故障恢复", "15秒首屏", "现场5cm精度"],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", type=Path, required=True, help="验证任务JSON路径")
    parser.add_argument("--result-dir", type=Path, required=True, help="真实引擎导出结果目录")
    parser.add_argument("--output", type=Path, help="报告路径，默认结果目录内comparison-report.json")
    args = parser.parse_args()
    result_dir = args.result_dir.resolve()
    output = (args.output or result_dir / "comparison-report.json").resolve()
    try:
        report = compare(args.case.resolve(), result_dir, output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    except (OSError, ValueError, TypeError, OverflowError) as exc:
        print(f"输入或配置无效：{exc}", file=sys.stderr)
        return 1
    print(f"{report['status']}；问题数：{report['issueCount']}；报告：{output}")
    return 0 if not report["issueCount"] else 2


if __name__ == "__main__":
    sys.exit(main())
