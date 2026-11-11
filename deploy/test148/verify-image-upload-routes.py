#!/usr/bin/env python3
"""离线候选配置门禁：校验 IMG-03 精确文件别名和下游路径回归。"""
from pathlib import Path
import tempfile
import os
import subprocess
import sys
import yaml

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = Path(__file__).with_name("prepare-config.py")


def routes_of(config):
    return {item["id"]: item for item in config["spring"]["cloud"]["gateway"]["routes"]}


def main():
    with tempfile.TemporaryDirectory() as tmp:
        env = {**os.environ, "WX_CONFIG_OUTPUT": tmp, "WX_SYSTEM_PORT": "48181",
               "WX_INFRA_PORT": "48182", "WX_DIMENSIONS_PORT": "48192", "WX_DB_PORT": "13306"}
        subprocess.run([sys.executable, str(SCRIPT)], env=env, check=True, capture_output=True)
        gateway = yaml.safe_load((Path(tmp) / "gateway.yaml").read_text(encoding="utf-8"))
    routes = routes_of(gateway)
    expected = {
        "image-oss-upload-test148": ("http://127.0.0.1:48182", "Path=/api/resource/oss/upload"),
        "image-oss-list-by-id-test148": ("http://127.0.0.1:48182", "Path=/api/resource/oss/listByIds/{ids}"),
        "admin-image-oss-upload-test148": ("http://127.0.0.1:48182", "Path=/admin-api/resource/oss/upload"),
        "admin-image-oss-list-by-id-test148": ("http://127.0.0.1:48182", "Path=/admin-api/resource/oss/listByIds/{ids}"),
        "operation-dim3d-test148": ("http://127.0.0.1:48192", "Path=/api/operation/dim3d/**"),
        "tenant-dim3d-test148": ("http://127.0.0.1:48192", "Path=/api/tenant/web/dim3d/**"),
    }
    for route_id, (uri, predicate) in expected.items():
        route = routes[route_id]
        assert route["uri"] == uri, (route_id, route["uri"])
        assert predicate in route["predicates"], (route_id, route["predicates"])
        assert "RemoveRequestHeader=x-bot-user" in route["filters"]
    assert "/admin-api/infra/file/resource/oss/upload" in routes["image-oss-upload-test148"]["filters"][-1]
    assert "/admin-api/infra/file/resource/oss/listByIds/" in routes["image-oss-list-by-id-test148"]["filters"][-1]
    assert "(?<ids>[^/]+)" in routes["image-oss-list-by-id-test148"]["filters"][-1]
    assert "/admin-api/dim3d/" in routes["operation-dim3d-test148"]["filters"][-1]
    assert "/admin-api/dim3d/" in routes["tenant-dim3d-test148"]["filters"][-1]
    print("IMG-03 路由配置检查通过，六个别名保留鉴权并移除不可信内部身份头")


if __name__ == "__main__":
    main()
