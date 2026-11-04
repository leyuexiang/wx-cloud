"""字典路由回归：仅校验精确公共读取路径，所有生成文件在临时目录自动清理。"""

from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[2]


class DictionaryRouteTest(unittest.TestCase):
    """同步验证默认网关、148 配置及生成器，防止重新生成后丢失前端字典入口。"""

    def assert_dictionary_route(self, config):
        """只允许字典按类型读取的路径改写，不扩大为全部系统接口的公共别名。"""
        routes = config["spring"]["cloud"]["gateway"]["routes"]
        matches = [route for route in routes if route["id"].startswith("system-dictionary")]
        self.assertEqual(len(matches), 1, "缺少唯一的前端字典精确路由")
        self.assertEqual(matches[0]["predicates"], ["Path=/api/system/dict/data/type/{dictType}"])
        self.assertIn(
            r"RewritePath=/api/system/dict/data/type/(?<dictType>[^/]+), /admin-api/system/dict/data/type/$\{dictType}",
            matches[0]["filters"],
        )
        self.assertIn("RemoveRequestHeader=x-bot-user", matches[0]["filters"])

    def test_default_and_148_routes(self):
        """源码配置与提交的测试配置都必须识别当前前端路径。"""
        for path in (ROOT / "wx-gateway/src/main/resources/application.yaml",
                     ROOT / "deploy/test148/config/gateway.yaml"):
            with self.subTest(path=path.relative_to(ROOT)):
                # 默认配置包含多个环境文档，路由定义位于首文档。
                documents = list(yaml.safe_load_all(path.read_text(encoding="utf-8")))
                self.assert_dictionary_route(documents[0])

    def test_generated_gateway_keeps_dictionary_route(self):
        """生成器使用部署配置中的系统端口，且不把测试产物写入仓库。"""
        with tempfile.TemporaryDirectory(prefix="wx-dict-config-") as directory:
            environment = {**os.environ, "WX_CONFIG_OUTPUT": directory, "WX_SYSTEM_PORT": "49181"}
            subprocess.run([sys.executable, str(ROOT / "deploy/test148/prepare-config.py")],
                           env=environment, check=True, capture_output=True)
            config = yaml.safe_load((Path(directory) / "gateway.yaml").read_text(encoding="utf-8"))
            self.assert_dictionary_route(config)
            route = next(item for item in config["spring"]["cloud"]["gateway"]["routes"]
                         if item["id"].startswith("system-dictionary"))
            self.assertEqual(route["uri"], "http://127.0.0.1:49181")


if __name__ == "__main__":
    unittest.main()
