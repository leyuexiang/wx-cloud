"""在测试服务器备份旧服务并初始化独立库；不停止旧服务，不切换入口。"""

from pathlib import Path
import datetime
import json
import os
import shutil
import subprocess
import time
from server_support import mysql_command, deployment_environment, runtime_identity


def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def inspect_env(container):
    info = json.loads(subprocess.check_output(["docker", "inspect", container]))[0]
    return dict(item.split("=", 1) for item in info["Config"]["Env"] if "=" in item)


ROOT = Path("/opt/wx/editor-test148")
if Path.cwd().resolve() != ROOT:
    raise SystemExit("必须从 /opt/wx/editor-test148 运行")

backup = ROOT / "backups" / datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
backup.mkdir(parents=True, mode=0o700)
backup.chmod(0o700)
for filename in ["docker-compose.yml", ".env", "Dockerfile.local"]:
    source = Path("/root/metabase-backend") / filename
    if source.exists():
        shutil.copy2(source, backup / filename)
        (backup / filename).chmod(0o600)
info = subprocess.check_output(["docker", "inspect", "metabase-backend"])
(backup / "old-container.json").write_bytes(info)
(backup / "old-container.json").chmod(0o600)
old_image = json.loads(info)[0]["Image"]
run(["docker", "tag", old_image, "wx-cloud-runtime:test148"])
run(["docker", "tag", old_image, "metabase-backend:before-wx-replacement"])

# 支持宿主数据库；管理员凭据需预先存入 bootstrap/mysql.cnf，权限为 600。
mysql_cmd = mysql_command()
with (backup / "metabase_3d.sql").open("wb") as output:
    # 旧服务使用宿主 MariaDB，新实例没有旧库；通过旧账号只读备份，密码仅传入子进程环境。
    old = inspect_env("metabase-backend")
    if "DB_PASSWORD" in old and "DB_USERNAME" in old:
        run(["mysqldump", "-h127.0.0.1", "-u" + old["DB_USERNAME"],
             "--single-transaction", "--no-tablespaces", "metabase_3d"],
            env={**os.environ, "MYSQL_PWD": old["DB_PASSWORD"]}, stdout=output)
    else:
        run(mysql_command("metabase_3d", dump=True), stdout=output)
(backup / "metabase_3d.sql").chmod(0o600)

env_path = ROOT / ".env"
if env_path.exists():
    env = dict(line.split("=", 1) for line in env_path.read_text().splitlines() if line and not line.startswith("#"))
else:
    env = {}

# 先生成完整环境，再校验编排并启动独立依赖；不能在 MySQL 就绪前执行初始化 SQL。
env = deployment_environment(env, inspect_env(os.environ.get("WX_MINIO_CONTAINER", "minio")))
env_path.write_text("".join(f"{key}={value}\n" for key, value in env.items()), encoding="utf-8")
env_path.chmod(0o600)
run(["docker", "compose", "-f", "compose.yaml", "config", "-q"])
run(["docker", "compose", "-f", "compose.yaml", "up", "-d", "mysql", "redis"])
for attempt in range(90):
    result = subprocess.run(mysql_cmd + ["-NBe", "SELECT 1"], capture_output=True)
    if result.returncode == 0:
        break
    time.sleep(2)
else:
    raise RuntimeError("独立数据库未在 180 秒内就绪；旧服务仍运行")

# 账号仅能访问新测试库，旧库及其他应用库保持独立。
password = env["WX_DB_PASSWORD"]
sql = (
    "CREATE DATABASE IF NOT EXISTS wx_editor_test148 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n"
    f"CREATE USER IF NOT EXISTS 'wx_editor_test148'@'%' IDENTIFIED BY '{password}';\n"
    f"ALTER USER 'wx_editor_test148'@'%' IDENTIFIED BY '{password}';\n"
    "GRANT ALL ON wx_editor_test148.* TO 'wx_editor_test148'@'%';\n"
)
run(mysql_cmd, input=sql.encode())

check_sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='wx_editor_test148';"
count = subprocess.check_output(mysql_cmd + ["-NBe", check_sql], input=b"").decode().strip()
# CREATE TABLE IF NOT EXISTS 支持首次初始化失败后继续补齐，不删除任何已建表或记录。
run(mysql_cmd + ["wx_editor_test148"], input=(ROOT / "schema-core.sql").read_bytes())
expected = set(json.loads((ROOT / "table-map.json").read_text()).values())
actual = set(subprocess.check_output(mysql_cmd + ["-NBe",
    "SELECT table_name FROM information_schema.tables WHERE table_schema='wx_editor_test148';"],
    input=b"").decode().splitlines())
if expected - actual:
    raise RuntimeError("测试库仍有缺表：" + ",".join(sorted(expected - actual)))
print(f"独立库已核对 {len(actual)} 张表，所有目标表均存在")

data = ROOT / "data"
data.mkdir(exist_ok=True)
# 旧容器显式使用 root，但复用镜像默认使用普通用户；目录必须与新镜像身份一致。
uid, gid = runtime_identity("wx-cloud-runtime:test148")
os.chown(data, uid, gid)
print(f"旧服务与数据库已备份：{backup}")
print("新数据库已准备；旧服务仍运行，尚未切换入口。")
