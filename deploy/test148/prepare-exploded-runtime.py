"""把 Spring Boot 可执行包解包为文件系统运行目录，规避嵌套 JAR 关闭期类加载告警。"""
from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import subprocess
from pathlib import Path


ROOT = Path("/opt/wx/editor-test148")
IMAGE = "wx-cloud-runtime:test148"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def prepare(service: str) -> dict:
    if service != "dimensions":
        raise ValueError("当前只允许为 dimensions 准备展开目录")
    jar = ROOT / "jars" / f"{service}.jar"
    target = ROOT / "extracted" / service
    staging = ROOT / "extracted" / f".{service}.staging"
    if not jar.is_file():
        raise FileNotFoundError(jar)
    if staging.exists():
        shutil.rmtree(staging)
    staging.mkdir(parents=True)
    # layertools 由镜像内普通用户执行，目录需要允许容器写入临时输出。
    os.chmod(staging, 0o777)
    subprocess.run(
        [
            "docker", "run", "--rm", "--entrypoint", "java",
            "-v", f"{jar}:/opt/wx/app.jar:ro",
            "-v", f"{staging}:/opt/wx/out",
            IMAGE, "-Djarmode=layertools", "-jar", "/opt/wx/app.jar",
            "extract", "--destination", "/opt/wx/out",
        ],
        check=True,
    )
    if target.exists():
        shutil.rmtree(target)
    staging.replace(target)
    return {"service": service, "jar": str(jar), "jarSha256": sha256(jar), "directory": str(target)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--service", default="dimensions")
    args = parser.parse_args()
    print(prepare(args.service))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
