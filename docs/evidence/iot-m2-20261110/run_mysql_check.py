"""使用服务器已有镜像启动一次性MySQL，仅执行隔离库检查，退出删除容器及内存数据。"""
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
HOST = os.environ.get("IOT_CHECK_SSH_HOST", "192.168.10.148")
NAME = "iot-m2-check-" + secrets.token_hex(4)
SSH = ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=8", HOST]

REMOTE = r'''
import json, os, secrets, subprocess, sys, time
name = sys.argv[1]
password = secrets.token_urlsafe(32)
env = dict(os.environ, MYSQL_ROOT_PASSWORD=password, MYSQL_DATABASE="iot_m2_check", MYSQL_PWD=password)
subprocess.run(["docker", "run", "-d", "--pull=never", "--name", name,
    "--memory=768m", "--cpus=1", "--pids-limit=512", "--tmpfs", "/var/lib/mysql:rw,size=384m",
    "-e", "MYSQL_ROOT_PASSWORD", "-e", "MYSQL_DATABASE", "-p", "127.0.0.1::3306",
    "mysql:8.0", "--innodb-buffer-pool-size=64M", "--performance-schema=OFF"],
    env=env, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
for attempt in range(90):
    check = subprocess.run(["docker", "exec", "-e", "MYSQL_PWD", name, "mysql", "-uroot",
        "--protocol=TCP", "-h127.0.0.1", "-Nse", "SELECT 1"], env=env,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if check.returncode == 0:
        port = subprocess.check_output(["docker", "port", name, "3306/tcp"]).decode().strip().rsplit(":", 1)[1]
        print(json.dumps({"password": password, "port": int(port)}))
        break
    time.sleep(1)
else:
    raise RuntimeError("临时MySQL未就绪")
'''

def run():
    """凭据仅经受信SSH标准输出进入本进程内存，不写命令、仓库或验收记录。"""
    tunnel = None
    result = {"database": "一次性MySQL 8.0容器/iot_m2_check", "shared_database_modified": False}
    try:
        remote = subprocess.run([*SSH, "python3", "-", NAME], input=REMOTE, text=True, encoding="utf-8",
                                capture_output=True)
        if remote.returncode:
            # 远端程序不输出秘密，错误仅含容器启动命令或就绪阶段的固定说明。
            print(remote.stderr[-1800:])
            raise RuntimeError("隔离数据库启动失败")
        private = json.loads(remote.stdout)
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            port = listener.getsockname()[1]
        tunnel = subprocess.Popen(["ssh", "-o", "BatchMode=yes", "-o", "ExitOnForwardFailure=yes",
            "-N", "-L", f"127.0.0.1:{port}:127.0.0.1:{private['port']}", HOST],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for attempt in range(40):
            try:
                with socket.create_connection(("127.0.0.1", port), timeout=1):
                    break
            except OSError:
                time.sleep(0.25)
        else:
            raise RuntimeError("隔离数据库隧道未就绪")
        print("隔离MySQL已就绪，正在执行真实Mapper及租户分页检查", flush=True)
        env = dict(os.environ, IOT_MYSQL_TEST_PASSWORD=private["password"],
                   IOT_MYSQL_TEST_URL=f"jdbc:mysql://127.0.0.1:{port}/iot_m2_check?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai")
        command = [shutil.which("mvn.cmd") or "mvn", "-o", "-pl", "wx-module-iot/wx-module-iot-biz", "-am",
                   "-Dtest=IotManagementMysqlTest", "-Dsurefire.failIfNoSpecifiedTests=false", "test", "-q"]
        if os.name == "nt":
            # 当前Windows JDK拒绝外部自附加；只为测试JVM开启自身附加，不改业务进程参数。
            command.insert(-2, "-DargLine=-Djdk.attach.allowAttachSelf=true")
        # 完整输出留在系统临时目录，交付记录只保存测试数量与明确边界。
        with tempfile.TemporaryFile(mode="w+b") as output:
            process = subprocess.run(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
            report = ROOT / "wx-module-iot/wx-module-iot-biz/target/surefire-reports/TEST-cn.iocoder.wx.module.iot.service.IotManagementMysqlTest.xml"
            if report.is_file():
                suite = ET.parse(report).getroot()
                result.update({key: int(suite.attrib[key]) for key in ("tests", "failures", "errors", "skipped")})
            result["maven_exit_code"] = process.returncode
            if process.returncode:
                output.seek(0)
                # 仅提取编译/失败摘要，不输出连接参数或完整异常堆栈。
                lines = output.read().decode("utf-8", errors="replace").splitlines()
                print("\n".join(line for line in lines if line.startswith("[ERROR]") and
                    not any(word in line.lower() for word in ("password", "jdbc:", "mysql://")))[:5000])
    finally:
        if tunnel:
            tunnel.terminate()
            tunnel.wait(timeout=10)
        cleanup = subprocess.run([*SSH, "docker", "rm", "-f", NAME], capture_output=True)
        result["temporary_container_removed"] = cleanup.returncode == 0
        # 新批次可指定独立证据文件，避免覆盖历史验收数据。
        Path(os.environ.get("IOT_MYSQL_CHECK_REPORT", Path(__file__).parent / "mysql-check.json")).write_text(
            json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))
    return result.get("maven_exit_code", 1)

if __name__ == "__main__":
    raise SystemExit(run())
