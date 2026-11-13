"""在已通过迁移演练的副本启动模型接收或转换候选，不改正式容器及业务库。"""

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import uuid
from urllib.parse import urlsplit

from server_support import file_sha256, mysql_command, runtime_identity

ROOT = Path("/opt/wx/editor-test148")
TARGETS = {
    "main": ("wx_editor", "wx-editor-dimensions", "dimensions.yaml", 49092),
    "data08": ("wx_editor_data08", "wx-editor-dimensions-data08", "dimensions-data08.yaml", 49093),
}


def candidate_config(text, source, clone):
    """只替换唯一主数据源库名；拒绝错误来源和未知副本，保留现场其他配置原文。"""
    if source not in {item[0] for item in TARGETS.values()}:
        raise ValueError("不是本批目标库")
    if not re.fullmatch(r"wx_model02_rehearsal_(main|data08)_[0-9a-f]{8}", clone):
        raise ValueError("不是本批独立演练库")
    expected = "main" if source == "wx_editor" else "data08"
    if not clone.startswith("wx_model02_rehearsal_" + expected + "_"):
        raise ValueError("演练副本和目标不匹配")
    pattern = r"(jdbc:mysql://[^\s/]+/)" + re.escape(source) + r"(?=[?\s])"
    result, count = re.subn(pattern, lambda match: match[1] + clone, text)
    if count != 1:
        raise ValueError("主数据源地址数量不为一，停止自动调整")
    return result


def candidate_runtime(target, converter_url, publication_check=False):
    """接收、转换和目录事务验收使用不同端口，只有显式事务验收使用测试兼容标识。"""
    port = TARGETS[target][3]
    arguments = ["--dim3d.model-import.enabled=true"]
    if converter_url is None:
        if publication_check:
            raise ValueError("目录事务验收需要实际转换接口")
        return {"phase": "model02", "port": port,
                "arguments": arguments + ["--dim3d.model-import.worker-enabled=false"]}
    endpoint = urlsplit(converter_url)
    if (endpoint.scheme != "http" or endpoint.hostname != "127.0.0.1" or not endpoint.port
            or endpoint.path != "/convert" or endpoint.username or endpoint.password
            or endpoint.query or endpoint.fragment):
        raise ValueError("转换候选仅允许本机已验证的完整转换接口")
    phase = "model04" if publication_check else "model03"
    compatibility = "model04-transaction-check" if publication_check else ""
    return {"phase": phase, "port": port + (20 if publication_check else 10),
            "arguments": arguments + ["--dim3d.model-import.worker-enabled=true",
                                       "--dim3d.model-import.converter-url=" + converter_url,
                                       "--dim3d.model-import.app-compatibility=" + compatibility]}


def run(arguments):
    """参数数组执行，失败不回显可能含私有环境值的外部诊断。"""
    result = subprocess.run(arguments, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError("候选准备命令失败，退出码 " + str(result.returncode))
    return result.stdout.strip()


def stage(target, report_path, artifact, expected, converter_url=None, publication_check=False):
    """核对报告和包后准备只监听回环的实例，接收与转换候选使用不同端口。"""
    os.umask(0o077)
    source, container, filename, port = TARGETS[target]
    runtime = candidate_runtime(target, converter_url, publication_check)
    port = runtime["port"]
    report = json.loads(report_path.read_text(encoding="utf-8"))
    if (report.get("status") != "REHEARSED" or report.get("sourceDatabase") != source
            or not report.get("originalRowsUnchanged") or not report.get("sourceSchemaUnchanged")):
        raise ValueError("需要目标库已通过的迁移演练报告")
    clone = report["rehearsalDatabase"]
    config = candidate_config((ROOT / "config" / filename).read_text(encoding="utf-8"), source, clone)
    if file_sha256(artifact) != expected:
        raise ValueError("候选包摘要不匹配")
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", port))
    original = json.loads(run(["docker", "inspect", container]))[0]
    directory = ROOT / "releases" / (runtime["phase"] + "-stage-" + target + "-" + expected[:12] + "-" + uuid.uuid4().hex[:8])
    directory.mkdir(mode=0o700)
    # 容器原环境仅保存在受限目录；报告和终端只记录非秘密的实例、库及摘要。
    (directory / "original-container.json").write_text(json.dumps(original), encoding="utf-8")
    env = directory / ".env"
    config_path = directory / "dimensions.yaml"
    config_path.write_text(config, encoding="utf-8")
    config_path.chmod(0o644)
    package = directory / "dimensions.jar"
    shutil.copyfile(artifact, package)
    package.chmod(0o644)
    if file_sha256(package) != expected:
        raise ValueError("复制后候选包摘要不匹配")
    logs = directory / "logs"
    logs.mkdir()
    image = original["Config"]["Image"]
    uid, gid = runtime_identity(image)
    os.chown(logs, uid, gid)
    # 同一应用用户只在其原数据副本获得增删改查，不创建身份或授予结构管理权限。
    command = mysql_command() + ["-N", "-B"]
    if publication_check:
        # 早期副本只含模型及资源表。目录快照和封面事务还需要同结构的空支持表。
        # 只在已核对名称的演练副本创建结构，不复制正式文件记录或配置凭据。
        tables = {"wx_infra_file": source, "wx_3d_icon": source, "wx_3d_template": source,
                  "wx_3d_scene_publication_resource": "wx_editor_data08"}
        # 删除保护结构取自已应用 DATA-09 的库，副本没有场景数据，用于回收本批样例。
        # 主库缺少该结构的正式增量另记部署依赖，不能据副本准备认定正式迁移已完成。
        sql = "\n".join("CREATE TABLE IF NOT EXISTS `" + clone + "`.`" + table + "` LIKE `" + schema + "`.`" + table + "`;"
                        for table, schema in tables.items())
        subprocess.run(command, input=sql, text=True, check=True, capture_output=True)
    sql = "GRANT SELECT,INSERT,UPDATE,DELETE ON `" + clone + "`.* TO 'wx_editor_test148'@'%';"
    subprocess.run(command, input=sql, text=True, check=True, capture_output=True)
    name = "wx-editor-" + runtime["phase"] + "-" + target
    args = ["docker", "run", "-d", "--name", name, "--network", "host", "--restart", "no",
            "--env-file", str(env), "--entrypoint", "java", "-w", original["Config"]["WorkingDir"] or "/app"]
    if original["Config"].get("User"):
        args.extend(["--user", original["Config"]["User"]])
    for mount in original["Mounts"]:
        # 共享授权数据只读，候选日志、配置和程序全部落在本批独立目录。
        if mount["Destination"] == "/data":
            args.extend(["-v", mount["Source"] + ":/data:ro"])
    for host, guest in ((package, "/opt/wx/app.jar"), (config_path, "/opt/wx/application.yaml")):
        args.extend(["-v", str(host) + ":" + guest + ":ro"])
    args.extend(["-v", str(logs) + ":/app/logs", image, "-Xms128m", "-Xmx768m", "-jar", "/opt/wx/app.jar",
                 "--spring.config.location=file:/opt/wx/application.yaml", "--server.address=127.0.0.1",
                 "--server.port=" + str(port), *runtime["arguments"]])
    paused = None
    # 所有结构和包检查成功后才生成短暂环境文件，启动或回退结束立即清理。
    env.write_text("\n".join(original["Config"]["Env"]) + "\n", encoding="utf-8")
    try:
        if publication_check:
            # 同一副本只留一个活动工作线程，避免上一阶段实例抢领本批待发布任务。
            old_name = "wx-editor-model03-" + target
            old = subprocess.run(["docker", "inspect", old_name], text=True, capture_output=True)
            if old.returncode == 0 and json.loads(old.stdout)[0]["State"]["Running"]:
                run(["docker", "stop", old_name])
                paused = old_name
        run(args)
    except Exception:
        if paused:
            run(["docker", "start", paused])
        raise
    finally:
        # 容器启动后已接收环境副本，不继续保留临时环境文件。
        env.unlink(missing_ok=True)
    result = {"container": name, "port": port, "database": clone, "sourceDatabase": source,
              "artifactSha256": expected, "workerEnabled": converter_url is not None,
              "publicationEnabled": publication_check, "runtimeCompatibilityVerified": False,
              "pausedConversionCandidate": paused, "releaseDirectory": str(directory)}
    (directory / "stage.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=TARGETS, required=True)
    parser.add_argument("--rehearsal-report", type=Path, required=True)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--converter-url", help="仅用于转换候选：本机已验证的完整 /convert 地址")
    parser.add_argument("--publication-check", action="store_true", help="使用独立演练库及测试兼容标识核验目录发布事务")
    args = parser.parse_args()
    if Path.cwd().resolve() != ROOT or not re.fullmatch(r"[0-9a-f]{64}", args.sha256):
        parser.error("工作目录或候选包摘要错误")
    print(json.dumps(stage(args.target, args.rehearsal_report, args.artifact, args.sha256,
                           args.converter_url, args.publication_check)))


if __name__ == "__main__":
    main()
