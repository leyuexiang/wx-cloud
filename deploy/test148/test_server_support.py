"""部署前回归：验证宿主数据库适配、密钥保护和资源配置生成。"""

import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import yaml


class ServerSupportTest(unittest.TestCase):
    """使用临时文件验证命令生成，不连接或修改真实数据库。"""

    def test_native_mysql_keeps_password_out_of_command(self):
        """原生数据库使用私有配置文件，命令参数不携带密码。"""
        from server_support import mysql_command
        with tempfile.TemporaryDirectory() as directory:
            defaults = Path(directory) / "mysql.cnf"
            defaults.write_text('[client]\npassword="secret"\n', encoding="utf-8")
            command = mysql_command("wx_editor", defaults=defaults)
        self.assertEqual(command[0], "mysql")
        self.assertTrue(command[1].startswith("--defaults-extra-file="))
        self.assertEqual(command[-1], "wx_editor")
        self.assertNotIn("secret", " ".join(command))

    def test_container_mysql_retains_existing_environment_support(self):
        """没有宿主凭据文件时，沿用原有 mysql8 容器方案。"""
        from server_support import mysql_command
        command = mysql_command("wx_editor", defaults=Path("does-not-exist.cnf"))
        self.assertEqual(command[:4], ["docker", "exec", "-i", "mysql8"])
        self.assertIn("MYSQL_ROOT_PASSWORD", " ".join(command))
        self.assertIn("--protocol=TCP", " ".join(command))

    def test_dump_uses_same_private_configuration(self):
        """备份与查询选择相同数据库环境，且关闭表空间权限要求。"""
        from server_support import mysql_command
        with tempfile.TemporaryDirectory() as directory:
            defaults = Path(directory) / "mysql.cnf"
            defaults.touch()
            command = mysql_command("wx_editor", dump=True, defaults=defaults)
        self.assertEqual(command[0], "mysqldump")
        self.assertIn("--single-transaction", command)
        self.assertIn("--no-tablespaces", command)

    def test_digest_streams_large_files_on_python39(self):
        """摘要实现兼容服务器 Python 3.9，并分块处理程序包。"""
        from server_support import file_sha256
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "sample.jar"
            payload = b"wx-cloud" * 200000
            file.write_bytes(payload)
            self.assertEqual(file_sha256(file), hashlib.sha256(payload).hexdigest())

    def test_initial_environment_has_database_password_and_isolated_ports(self):
        """首次初始化即具备编排必需凭据；重跑不能轮换已有密码。"""
        from server_support import deployment_environment
        environment = deployment_environment({}, {"MINIO_ROOT_USER": "user", "MINIO_ROOT_PASSWORD": "key"})
        self.assertGreaterEqual(len(environment["WX_MYSQL_ROOT_PASSWORD"]), 32)
        self.assertEqual(environment["WX_SYSTEM_PORT"], "48181")
        self.assertEqual(environment["WX_DB_PORT"], "13306")
        self.assertEqual(deployment_environment(environment, {}), environment)

    def test_recovery_starts_old_container_even_if_gateway_rebuild_fails(self):
        """重建失败时先释放新网关占用的入口，再独立恢复旧服务。"""
        from server_support import recover_legacy
        def fail():
            raise RuntimeError("compose failed")
        with patch("server_support.subprocess.run") as run:
            with self.assertRaisesRegex(RuntimeError, "compose failed"):
                recover_legacy(fail)
        self.assertEqual(run.call_args_list[0].args[0], ["docker", "stop", "wx-editor-gateway"])
        self.assertEqual(run.call_args_list[1].args[0], ["docker", "start", "metabase-backend"])

    def test_data_owner_comes_from_runtime_image_instead_of_old_container(self):
        """旧容器可覆盖运行用户，数据目录归属必须使用新镜像的实际默认用户。"""
        from server_support import runtime_identity
        with patch("server_support.subprocess.check_output", return_value=b"100:101\n") as run:
            self.assertEqual(runtime_identity("wx-cloud-runtime:test148"), (100, 101))
        self.assertEqual(run.call_args.args[0][:3], ["docker", "run", "--rm"])
        self.assertIn("wx-cloud-runtime:test148", run.call_args.args[0])


class GeneratedConfigTest(unittest.TestCase):
    """验证生成器在端口隔离后保留资源上传、下载及转换配置。"""

    def test_resource_settings_survive_generation_and_ports_can_be_isolated(self):
        """重现资源根节点被覆盖的问题，并验证连接目标同步换端口。"""
        script = Path(__file__).with_name("prepare-config.py")
        with tempfile.TemporaryDirectory() as directory:
            environment = {**os.environ, "WX_CONFIG_OUTPUT": directory,
                           "WX_SYSTEM_PORT": "48181", "WX_INFRA_PORT": "48182",
                           "WX_DIMENSIONS_PORT": "48192", "WX_DB_PORT": "13306"}
            subprocess.run([sys.executable, str(script)], env=environment, check=True,
                           capture_output=True)
            config = yaml.safe_load((Path(directory) / "dimensions.yaml").read_text(encoding="utf-8"))
            gateway = yaml.safe_load((Path(directory) / "gateway.yaml").read_text(encoding="utf-8"))
        self.assertEqual(config["server"]["port"], 48192)
        self.assertIn("127.0.0.1:13306/", config["spring"]["datasource"]["dynamic"]["datasource"]["master"]["url"])
        self.assertEqual(config["dim3d"]["resource"]["platform-tenant-id"], 1)
        self.assertEqual(config["dim3d"]["resource"]["download-endpoint"], "https://192.168.10.148:9444")
        self.assertEqual(config["dim3d"]["resource"]["upload-endpoint"], "https://192.168.10.148:9445")
        self.assertIn("resource-convert-url", config["dim3d"])
        self.assertEqual(gateway["spring"]["cloud"]["gateway"]["routes"][0]["uri"], "http://127.0.0.1:48181")
        self.assertEqual(config["spring"]["cloud"]["discovery"]["client"]["simple"]["instances"]["infra-server"][0]["uri"], "http://127.0.0.1:48182")


if __name__ == "__main__":
    unittest.main()
