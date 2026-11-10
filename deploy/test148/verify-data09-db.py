"""只读核对 DATA-09 发布记录和票据表，不执行迁移或业务写入。"""

import argparse
import json
import subprocess


EXPECTED_DATABASE = "wx_editor_data08"
EXPECTED_TABLES = {"wx_3d_scene_publication", "wx_3d_scene_publication_ticket", "wx_3d_scene_publication_audit"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mysql", default="mysql")
    parser.add_argument("--login-path")
    parser.add_argument("--host", default="192.168.10.148")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user")
    parser.add_argument("--database", default=EXPECTED_DATABASE)
    args = parser.parse_args()
    if args.database == "wx_editor":
        raise SystemExit("拒绝对共享业务库执行 DATA-09 预检")
    command = [args.mysql, "--no-defaults", "--protocol=TCP", f"--host={args.host}",
               f"--port={args.port}", f"--database={args.database}",
               "--batch", "--skip-column-names"]
    if args.login_path:
        command.append(f"--login-path={args.login_path}")
    if args.user:
        command.append(f"--user={args.user}")

    def query(sql):
        result = subprocess.run(command, input=sql.encode("utf-8"), capture_output=True, check=True)
        return result.stdout.decode("utf-8").splitlines()

    if query("SELECT DATABASE();") != [args.database]:
        raise SystemExit("数据库上下文不匹配")
    tables = set(query("SHOW TABLES;"))
    missing = sorted(EXPECTED_TABLES - tables)
    if missing:
        raise SystemExit("缺少 DATA-09 表：" + ", ".join(missing))
    columns = query("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS "
                    "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN "
                    "('wx_3d_scene_publication','wx_3d_scene_publication_ticket','wx_3d_scene_publication_audit');")
    report = {
        "database": args.database,
        "host": args.host,
        "readOnly": True,
        "tables": sorted(EXPECTED_TABLES),
        "columnCount": len(columns),
        "publicationCount": int(query("SELECT COUNT(*) FROM wx_3d_scene_publication;")[0]),
        "ticketCount": int(query("SELECT COUNT(*) FROM wx_3d_scene_publication_ticket;")[0]),
        "auditCount": int(query("SELECT COUNT(*) FROM wx_3d_scene_publication_audit;")[0]),
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
