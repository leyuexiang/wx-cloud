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
        routes = gateway["spring"]["cloud"]["gateway"]["routes"]
        self.assertEqual(routes[0]["uri"], "http://127.0.0.1:48181")
        self.assertEqual(config["spring"]["cloud"]["discovery"]["client"]["simple"]["instances"]["infra-server"][0]["uri"], "http://127.0.0.1:48182")
        by_id = {route["id"]: route for route in routes}
        self.assertEqual(by_id["image-oss-upload-test148"]["predicates"], ["Path=/api/resource/oss/upload"])
        self.assertEqual(by_id["image-oss-upload-test148"]["uri"], "http://127.0.0.1:48182")
        self.assertIn("/admin-api/infra/file/resource/oss/upload", by_id["image-oss-upload-test148"]["filters"][-1])
        self.assertIn("/admin-api/infra/file/resource/oss/listByIds/", by_id["image-oss-list-by-id-test148"]["filters"][-1])
        self.assertIn("(?<ids>[^/]+)", by_id["image-oss-list-by-id-test148"]["filters"][-1])
        self.assertIn("/admin-api/infra/file/resource/oss/listByIds/", by_id["image-oss-list-by-id-test148"]["filters"][-1])
        self.assertEqual(by_id["image-oss-list-by-id-test148"]["predicates"],
                         ["Path=/api/resource/oss/listByIds/{ids}"])
        self.assertIn("/admin-api/infra/file/resource/oss/upload", by_id["infra-test148"]["predicates"][0])
        self.assertIn("/admin-api/dim3d/**", by_id["dimensions-test148"]["predicates"][0])
        self.assertEqual(by_id["operation-dim3d-test148"]["uri"], "http://127.0.0.1:48192")
        self.assertEqual(by_id["tenant-dim3d-test148"]["uri"], "http://127.0.0.1:48192")
        self.assertIn("/admin-api/dim3d/", by_id["operation-dim3d-test148"]["filters"][-1])
        self.assertIn("/admin-api/dim3d/", by_id["tenant-dim3d-test148"]["filters"][-1])


class ResourceUploadCorsTest(unittest.TestCase):
    """验证 MinIO 暂存上传只向明确允许的浏览器来源开放跨域读取。"""

    def test_default_origins_include_current_frontend_and_legacy_tool(self):
        """默认兼容当前 5180 页面与旧 8765 工具，并只回显精确来源。"""
        from resource_upload_config import parse_allowed_origins, render_nginx_origin_map

        origins = parse_allowed_origins(None)
        self.assertEqual(origins, ("http://127.0.0.1:8765", "http://localhost:5180"))
        config = render_nginx_origin_map(origins)
        self.assertIn('default "";', config)
        self.assertIn('"http://127.0.0.1:8765" $http_origin;', config)
        self.assertIn('"http://localhost:5180" $http_origin;', config)
        self.assertNotIn("*", config)

    def test_origin_override_is_normalized_and_deduplicated(self):
        """运维覆盖按源地址规范化，重复值不会扩大或重复写入白名单。"""
        from resource_upload_config import parse_allowed_origins

        self.assertEqual(
            parse_allowed_origins("HTTP://LOCALHOST:80,http://localhost"),
            ("http://localhost",),
        )

    def test_lan_frontend_override_keeps_exact_host_and_port(self):
        """当前局域网页面通过环境覆盖加入，来源路径和其他端口不会被一并放行。"""
        from resource_upload_config import parse_allowed_origins, render_nginx_origin_map

        origins = parse_allowed_origins("http://192.168.10.247:5180")
        self.assertEqual(origins, ("http://192.168.10.247:5180",))
        config = render_nginx_origin_map(origins)
        self.assertIn('"http://192.168.10.247:5180" $http_origin;', config)
        self.assertNotIn("localhost", config)
        self.assertNotIn("~", config)

    def test_origin_override_rejects_path_and_nginx_injection(self):
        """来源值不得夹带路径、凭据或 Nginx 指令。"""
        from resource_upload_config import parse_allowed_origins

        for invalid in (
            "http://localhost:5180/path",
            'http://localhost:5180"; return 200; #',
            "http://user@localhost:5180",
            "http://localhost:65536",
        ):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                parse_allowed_origins(invalid)

    def test_download_and_upload_maps_can_coexist_for_lan_frontend(self):
        """同一代理同时加载上传和下载配置，映射变量独立且精确放行当前页面。"""
        import importlib.util
        from resource_upload_config import parse_allowed_origins, render_nginx_origin_map

        script = Path(__file__).with_name("setup-resource-download.py")
        spec = importlib.util.spec_from_file_location("download_setup", script)
        download = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(download)
        origins = parse_allowed_origins("http://192.168.10.247:5180")
        upload = render_nginx_origin_map(origins)
        config = download.render_download_config("/trusted/cert", origins)
        self.assertIn("map $http_origin $wx_resource_download_cors_origin", config)
        self.assertNotIn("$wx_resource_upload_cors_origin", config)
        self.assertIn("$wx_resource_upload_cors_origin", upload)
        self.assertIn('"http://192.168.10.247:5180" $http_origin;', config)
        self.assertIn('default "";', config)
        self.assertIn("ssl_certificate /trusted/cert/server.crt;", config)
        self.assertIn("limit_except GET { deny all; }", config)
        self.assertIn("location / { return 404; }", config)
        self.assertNotIn("*", config)

    def test_cors_variable_cannot_inject_nginx_directives(self):
        """配置变量名只允许受控标识符，不能夹带代理指令。"""
        from resource_upload_config import render_nginx_origin_map
        with self.assertRaises(ValueError):
            render_nginx_origin_map(("http://localhost:5180",), variable="x; include /tmp/x;")


if __name__ == "__main__":
    unittest.main()
