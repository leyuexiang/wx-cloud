"""文档配置回归：聚合已部署服务，且精确文档路由不能改写业务路径。"""

from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[2]
SERVICES = {"system": 49181, "infra": 49182, "dimensions": 49192}


class OpenApiRouteTest(unittest.TestCase):
    """提交配置与重新生成的配置遵循同一文档及权限边界。"""

    def assert_documentation(self, config, ports=None):
        """仅开启已部署三模块；路径已有业务前缀，不再添加上下文前缀。"""
        aggregation = config["knife4j"]["gateway"]
        self.assertTrue(aggregation["enabled"])
        self.assertEqual("manual", aggregation["strategy"])
        documents = {entry["service-name"]: entry for entry in aggregation["routes"]}
        self.assertEqual({name + "-server" for name in SERVICES}, set(documents))
        routes = config["spring"]["cloud"]["gateway"]["routes"]
        for name in SERVICES:
            with self.subTest(service=name):
                prefix = "/admin-api/" + name
                document = documents[name + "-server"]
                self.assertEqual(prefix + "/v3/api-docs", document["url"])
                self.assertEqual("/", document["context-path"])
                route = next(item for item in routes if item["id"] == name + "-openapi-test148")
                self.assertEqual(["Method=GET", "Path=" + prefix + "/v3/api-docs," + prefix + "/v3/api-docs/**"],
                                 route["predicates"])
                self.assertLess(route["order"], 0)
                self.assertEqual(["RemoveRequestHeader=x-bot-user",
                                  "RewritePath=^" + prefix + r"(?<docPath>/v3/api-docs(?:/.*)?)$, $\{docPath}"],
                                 route["filters"])
                if ports:
                    self.assertEqual("http://127.0.0.1:" + str(ports[name]), route["uri"])
                # 文档修复不能把普通业务路径去掉前缀，也不能解除内部身份头清理。
                business = next(item for item in routes if item["id"] == name + "-test148")
                self.assertEqual(["RemoveRequestHeader=x-bot-user"], business["filters"])

    def test_checked_in_gateway(self):
        """提交的 148 配置直接具备完整聚合及文档路由。"""
        config = yaml.safe_load((ROOT / "deploy/test148/config/gateway.yaml").read_text(encoding="utf-8"))
        self.assert_documentation(config)

    def test_generated_gateway(self):
        """临时目录生成配置，确保重部署不丢失修复或硬编码服务端口。"""
        with tempfile.TemporaryDirectory(prefix="wx-openapi-") as directory:
            environment = {**os.environ, "WX_CONFIG_OUTPUT": directory,
                           **{"WX_" + name.upper() + "_PORT": str(port) for name, port in SERVICES.items()}}
            subprocess.run([sys.executable, str(ROOT / "deploy/test148/prepare-config.py")],
                           env=environment, check=True, capture_output=True)
            config = yaml.safe_load((Path(directory) / "gateway.yaml").read_text(encoding="utf-8"))
            self.assert_documentation(config, SERVICES)


if __name__ == "__main__":
    unittest.main()
