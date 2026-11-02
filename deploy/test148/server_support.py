"""部署环境兼容层：支持宿主 MySQL 和原有容器，不把凭据放入命令参数。"""

import hashlib
import secrets
import subprocess
from pathlib import Path


ROOT = Path("/opt/wx/editor-test148")


def mysql_command(database=None, *, dump=False, defaults=None):
    """存在私有配置时调用宿主客户端，否则复用 mysql8 容器内的凭据。"""
    config = Path(defaults) if defaults is not None else ROOT / "bootstrap/mysql.cnf"
    program = "mysqldump" if dump else "mysql"
    options = (["--single-transaction", "--no-tablespaces", "--routines", "--triggers"]
               if dump else ["--default-character-set=utf8mb4"])
    if config.is_file():
        # MySQL 规定 defaults-extra-file 必须紧跟可执行文件，密码仅存于权限 600 的文件。
        command = [program, "--defaults-extra-file=" + str(config), *options]
    else:
        # 容器首次初始化会启动仅支持本地套接字的临时实例；强制 TCP 可避免误判其已就绪。
        command = ["docker", "exec", "-i", "mysql8", "sh", "-c",
                   'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec ' + program
                   + ' -uroot --protocol=TCP -h127.0.0.1 "$@"', "--", *options]
    if database:
        command.append(database)
    return command


def file_sha256(path):
    """按固定大小分块计算摘要，兼容 Python 3.9，避免整包驻留内存。"""
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def deployment_environment(existing, minio):
    """补齐首次部署参数；已有密码及端口原样保留，避免重跑导致失配。"""
    environment = dict(existing)
    for key in ("WX_MYSQL_ROOT_PASSWORD", "WX_DB_PASSWORD", "WX_REDIS_PASSWORD", "WX_RUNTIME_SECRET"):
        if not environment.get(key):
            environment[key] = secrets.token_urlsafe(32)
    defaults = {"WX_GATEWAY_PORT": "12532", "WX_SYSTEM_PORT": "48181",
                "WX_INFRA_PORT": "48182", "WX_DIMENSIONS_PORT": "48192",
                "WX_DB_PORT": "13306", "WX_REDIS_PORT": "16380"}
    for key, value in defaults.items():
        environment.setdefault(key, value)
    for key, source in (("WX_MINIO_ACCESS_KEY", "MINIO_ROOT_USER"), ("WX_MINIO_SECRET_KEY", "MINIO_ROOT_PASSWORD")):
        if not environment.get(key):
            environment[key] = minio[source]
    return environment


def recover_legacy(rebuild_gateway):
    """即使新网关重建失败，也独立释放入口并启动旧容器，避免二次失败阻断恢复。"""
    try:
        rebuild_gateway()
    except Exception:
        subprocess.run(["docker", "stop", "wx-editor-gateway"], check=False)
        raise
    finally:
        subprocess.run(["docker", "start", "metabase-backend"], check=True)


def runtime_identity(image):
    """从镜像实际默认用户读取 UID/GID，避免旧容器覆盖用户后误将目录交给 root。"""
    value = subprocess.check_output([
        "docker", "run", "--rm", "--entrypoint", "sh", image,
        "-c", 'printf "%s:%s" "$(id -u)" "$(id -g)"',
    ]).decode().strip()
    uid, gid = value.split(":")
    return int(uid), int(gid)
