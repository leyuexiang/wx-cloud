"""在隔离验证通过后切换测试入口，失败时自动恢复旧服务。"""

from pathlib import Path
import json
import os
import subprocess
import time
import urllib.request

ROOT = Path("/opt/wx/editor-test148")
if Path.cwd().resolve() != ROOT:
    raise SystemExit("必须从 /opt/wx/editor-test148 执行")
staging = ROOT / "verification-12532.json"
if not staging.exists() or not json.loads(staging.read_text())["passed"]:
    raise SystemExit("隔离接口验证未通过，禁止切换")

env_path = ROOT / ".env"
original = env_path.read_text()
if "WX_GATEWAY_PORT=12532" not in original:
    raise SystemExit("当前入口状态不符合首次切换预期，请先核查")
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def run(*args, **kwargs):
    subprocess.run(list(args), check=True, **kwargs)


def gateway():
    run("docker", "compose", "-f", "compose.yaml", "up", "-d", "--force-recreate", "gateway")


def wait_health():
    for _ in range(20):
        try:
            with opener.open("http://127.0.0.1:12522/actuator/health", timeout=2) as response:
                if json.load(response).get("status") == "UP":
                    return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError("新入口未在等待时间内就绪")


try:
    run("docker", "stop", "metabase-backend")
    env_path.write_text(original.replace("WX_GATEWAY_PORT=12532", "WX_GATEWAY_PORT=12522"))
    env_path.chmod(0o600)
    gateway()
    wait_health()
    run("python3", "verify-api.py", env={**os.environ, "WX_VERIFY_PORT": "12522"})
except Exception:
    # 回退会重建网关容器，先保留失败现场，避免启动期异常日志随容器丢失。
    diagnostics = ROOT / "backups/cutover-failure.log"
    with diagnostics.open("wb") as output:
        subprocess.run(["docker", "logs", "--tail", "350", "wx-editor-gateway"],
                       stdout=output, stderr=subprocess.STDOUT, check=False)
    diagnostics.chmod(0o600)
    env_path.write_text(original)
    env_path.chmod(0o600)
    gateway()
    run("docker", "start", "metabase-backend")
    print("切换失败，已恢复旧服务和原入口；新库保留用于排查。")
    raise

print("测试入口 12522 已切换到 jtyf-cloud，原容器已停止但仍保留可恢复。")
