"""148 文档配置发布：复用现有网关包，先隔离验证，按本批配置备份回退。"""

import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import runpy
import shutil
import subprocess
import time
import urllib.request

import yaml

from openapi_support import enable_openapi


ROOT = Path("/opt/wx/editor-test148")
RELEASE = ROOT / "releases/openapi-20261110"
ISOLATION = "wx-editor-openapi-check"


def digest(path):
    """流式摘要适用于服务器旧版 Python，避免加载完整服务包。"""
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def run(*args):
    """部署命令不输出可能含环境值的标准输出和标准错误。"""
    return subprocess.run(args, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def healthy(port, container=None):
    """有界轮询只检查当前发布的网关；原业务服务不重启。"""
    for _ in range(75):
        if container:
            state = run("docker", "inspect", "--format", "{{.State.Running}}", container).stdout.decode().strip()
            if state != "true":
                raise RuntimeError("候选网关容器已退出")
        try:
            with urllib.request.urlopen("http://127.0.0.1:" + str(port) + "/actuator/health", timeout=2) as response:
                if json.load(response).get("status") == "UP":
                    return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError("网关健康检查超时")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("isolate", "apply", "rollback"))
    args = parser.parse_args()
    if Path(__file__).resolve().parent != RELEASE or not (ROOT / ".env").is_file():
        raise RuntimeError("发布目录或部署环境不匹配")
    os.umask(0o077)
    target = ROOT / "config/gateway.yaml"
    candidate = RELEASE / "gateway.candidate.yaml"
    report_path = RELEASE / "deployment.json"
    verify = runpy.run_path(str(RELEASE / "verify-openapi.py"))["verify"]

    def restart():
        # 现场容器来自此编排，只重建 gateway，保持其他容器和 12525 隔离入口不变。
        run("docker", "compose", "--project-directory", str(ROOT), "-f", str(ROOT / "compose.yaml"),
            "up", "-d", "--no-deps", "--force-recreate", "gateway")
        healthy(12522)

    def replace_config(source):
        # 文件挂载依赖旧 inode，替换后必须重建网关，禁止仅重启 Java 进程。
        pending = target.with_suffix(".openapi-ready")
        shutil.copy2(source, pending)
        pending.replace(target)

    if args.action == "isolate":
        previous = json.loads(report_path.read_text(encoding="utf-8")) if report_path.exists() else None
        if previous and (previous["status"] not in ("PREPARED", "ISOLATION_FAILED")
                         or digest(target) != previous["previousConfigSha256"]):
            raise RuntimeError("发布已验证或现场改变，不能覆盖已有记录")
        stamp = previous["time"] if previous else datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        backup = Path(previous["backup"]) if previous else ROOT / "backups" / ("openapi-" + stamp)
        if not previous:
            backup.mkdir(mode=0o700)
            shutil.copy2(target, backup / "gateway.yaml")
        original = yaml.safe_load(target.read_text(encoding="utf-8"))
        enabled = enable_openapi(original)
        # 宿主 PyYAML 为旧版，不支持 sort_keys；映射顺序不影响网关路由数组优先级。
        candidate.write_text(yaml.safe_dump(enabled, allow_unicode=True), encoding="utf-8")
        # 运行镜像采用非 root 用户；候选沿用现场配置权限，环境密钥仍仅留在 .env。
        candidate.chmod(target.stat().st_mode & 0o777)
        report = {"status": "PREPARED", "time": stamp, "backup": str(backup),
                  "previousConfigSha256": digest(target), "candidateConfigSha256": digest(candidate),
                  "gatewayJarSha256": digest(ROOT / "jars/gateway.jar"),
                  "databaseChanged": False, "serviceJarsChanged": False, "isolationPort": 12534}
        report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
        try:
            # 临时容器仅绑定回环地址，使用相同包与环境文件，不接管业务入口。
            run("docker", "run", "-d", "--name", ISOLATION, "--network", "host", "--memory", "768m",
                "--env-file", str(ROOT / ".env"), "-e", "TZ=Asia/Shanghai",
                "-e", "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom",
                "-v", str(ROOT / "jars/gateway.jar") + ":/opt/wx/app.jar:ro",
                "-v", str(candidate) + ":/opt/wx/application.yaml:ro", "--entrypoint", "java",
                "wx-cloud-runtime:test148", "-Xms64m", "-Xmx384m", "-jar", "/opt/wx/app.jar",
                "--spring.config.location=file:/opt/wx/application.yaml", "--server.port=12534", "--server.address=127.0.0.1")
            healthy(12534, ISOLATION)
            result = verify("http://127.0.0.1:12534")
            (RELEASE / "isolation.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
            if not result["passed"]:
                raise RuntimeError("隔离文档验证未通过")
            report["status"] = "ISOLATION_PASSED"
        except Exception:
            report["status"] = "ISOLATION_FAILED"
            raise
        finally:
            run("docker", "rm", "-f", ISOLATION)
            report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    else:
        report = json.loads(report_path.read_text(encoding="utf-8"))
        backup = Path(report["backup"]) / "gateway.yaml"
        if backup.parent.parent != ROOT / "backups" or digest(backup) != report["previousConfigSha256"]:
            raise RuntimeError("本批备份路径或摘要不匹配")
        if args.action == "apply":
            if report["status"] != "ISOLATION_PASSED" or digest(target) != report["previousConfigSha256"]:
                raise RuntimeError("隔离未通过或现场配置被并发修改")
            if digest(candidate) != report["candidateConfigSha256"] or digest(ROOT / "jars/gateway.jar") != report["gatewayJarSha256"]:
                raise RuntimeError("候选配置或网关包摘要发生变化")
            try:
                replace_config(candidate)
                restart()
                result = verify("http://127.0.0.1:12522")
                (RELEASE / "postflight.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
                if not result["passed"]:
                    raise RuntimeError("业务入口文档验证未通过")
                report["status"] = "DEPLOYED"
            except Exception:
                replace_config(backup)
                restart()
                report["status"] = "ROLLED_BACK"
                report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
                raise
        else:
            if digest(target) != report["candidateConfigSha256"]:
                raise RuntimeError("现场配置已改变，不能覆盖其他批次")
            replace_config(backup)
            restart()
            report["status"] = "ROLLED_BACK"
        report["activeConfigSha256"] = digest(target)
        report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
