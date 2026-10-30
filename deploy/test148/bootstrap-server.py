"""在测试服务器备份旧服务并初始化独立库；不停止旧服务，不切换入口。"""

from pathlib import Path
import datetime
import json
import os
import secrets
import shutil
import subprocess


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

mysql_cmd = ["docker", "exec", "-i", "mysql8", "sh", "-c",
             'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot "$@"', "--"]
with (backup / "metabase_3d.sql").open("wb") as output:
    run(["docker", "exec", "mysql8", "sh", "-c",
         'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --single-transaction --no-tablespaces metabase_3d'], stdout=output)
(backup / "metabase_3d.sql").chmod(0o600)

env_path = ROOT / ".env"
if env_path.exists():
    env = dict(line.split("=", 1) for line in env_path.read_text().splitlines() if line and not line.startswith("#"))
else:
    old = inspect_env("metabase-backend")
    minio = inspect_env("smartlens-minio")
    env = {
        "WX_DB_PASSWORD": secrets.token_urlsafe(32),
        "WX_REDIS_PASSWORD": secrets.token_urlsafe(32),
        "WX_REDIS_PORT": "16380",
        "WX_MINIO_ACCESS_KEY": minio["MINIO_ROOT_USER"],
        "WX_MINIO_SECRET_KEY": minio["MINIO_ROOT_PASSWORD"],
        "WX_RUNTIME_SECRET": secrets.token_urlsafe(32),
        "WX_GATEWAY_PORT": "12532",
    }
    env_path.write_text("".join(f"{key}={value}\n" for key, value in env.items()))
    env_path.chmod(0o600)

# 使用专属 Redis，避免测试登录与权限缓存混入其他应用的缓存空间。
if "WX_REDIS_PORT" not in env:
    env["WX_REDIS_PORT"] = "16380"
    env["WX_REDIS_PASSWORD"] = secrets.token_urlsafe(32)
    env_path.write_text("".join(f"{key}={value}\n" for key, value in env.items()))
    env_path.chmod(0o600)

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
uid = int(subprocess.check_output(["docker", "exec", "metabase-backend", "id", "-u"]).decode())
gid = int(subprocess.check_output(["docker", "exec", "metabase-backend", "id", "-g"]).decode())
os.chown(data, uid, gid)
print(f"旧服务与数据库已备份：{backup}")
print("新数据库已准备；旧服务仍运行，尚未切换入口。")
