"""DATA-08 隔离库演练编排器，默认只输出命令计划。"""
from __future__ import annotations

import argparse
import json
import pathlib
import shlex
import subprocess
import sys


ROOT = pathlib.Path(__file__).parent


def commands(args: argparse.Namespace) -> list[list[str]]:
    common = ["--host", args.host, "--port", str(args.port), "--user", args.user,
              "--database", args.database, "--password-env", args.password_env]
    if args.mysql_container:
        common += ["--mysql-container", args.mysql_container]
    return [
        [sys.executable, str(ROOT / "apply-data08-schema.py"), *common],
        [sys.executable, str(ROOT / "migrate-data08-db.py"), *common],
        [sys.executable, str(ROOT / "verify-data08-roundtrip.py"), *common],
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description="DATA-08 隔离库演练编排")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--database", default="wx_editor")
    parser.add_argument("--password-env", default="WX_MYSQL_ROOT_PASSWORD")
    parser.add_argument("--mysql-container")
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--confirm", default="")
    args = parser.parse_args()
    plan = commands(args)
    if not args.execute:
        print(json.dumps({"readOnly": True, "commands": [shlex.join(command) for command in plan]}, ensure_ascii=False, indent=2))
        return 0
    if args.confirm != "DATA08-REHEARSAL":
        print(json.dumps({"passed": False, "error": "执行演练必须显式传入 --confirm DATA08-REHEARSAL"}, ensure_ascii=False))
        return 2
    reports = []
    for command in plan:
        result = subprocess.run(command, text=True, capture_output=True)
        reports.append({"command": shlex.join(command), "exitCode": result.returncode, "stdout": result.stdout, "stderr": result.stderr})
        if result.returncode:
            print(json.dumps({"passed": False, "reports": reports}, ensure_ascii=False, indent=2))
            return result.returncode
    print(json.dumps({"passed": True, "reports": reports}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
