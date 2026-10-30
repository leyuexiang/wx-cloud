"""将测试入口恢复到旧服务；不删除新数据库和新服务的调试数据。"""

from pathlib import Path
import subprocess

ROOT = Path("/opt/wx/editor-test148")
if Path.cwd().resolve() != ROOT:
    raise SystemExit("必须从 /opt/wx/editor-test148 执行")
env_path = ROOT / ".env"
content = env_path.read_text()
if "WX_GATEWAY_PORT=12522" not in content:
    raise SystemExit("当前并非新网关占用 12522，请先核查入口状态")
env_path.write_text(content.replace("WX_GATEWAY_PORT=12522", "WX_GATEWAY_PORT=12532"))
env_path.chmod(0o600)
subprocess.run(["docker", "compose", "-f", "compose.yaml", "up", "-d", "--force-recreate", "gateway"], check=True)
subprocess.run(["docker", "start", "metabase-backend"], check=True)
print("旧服务已恢复启动；新网关退回 12532，新库与新服务数据保留。")
