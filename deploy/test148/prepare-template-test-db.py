"""核对或准备 TPL-04 专用库。客户端凭据通过已有 MySQL 登录配置提供。"""

import argparse
import subprocess
from pathlib import Path


DATABASE = "wx_editor_tpl04_test"
MIGRATION = Path(__file__).resolve().parent / "migrations/tpl01/001-scene-template.up.sql"
BASE_TABLES = {"wx_3d_scene", "wx_3d_scene_revision", "wx_3d_template",
               "wx_system_dict_type", "wx_system_dict_data"}
TEMPLATE_TABLE = "wx_3d_scene_template"
TYPES = "('dim3d_template_industry', 'dim3d_template_exhibition')"
TEST_VALUE = "TPL04_SMOKE"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="执行迁移及测试分类初始化，默认只读")
    parser.add_argument("--mysql", default="mysql", help="MySQL 客户端路径")
    parser.add_argument("--docker-container", help="在指定 MySQL 容器内执行客户端")
    parser.add_argument("--login-path", help="预先配置的 MySQL 登录名称")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user")
    args = parser.parse_args()
    if args.docker_container:
        command = ["docker", "exec", "-i", args.docker_container, "sh", "-c",
                   'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --protocol=TCP -h127.0.0.1 "$@"', "--"]
    else:
        command = [args.mysql, "--no-defaults"]
    if args.login_path:
        command.append(f"--login-path={args.login_path}")
    command.extend(["--protocol=TCP", f"--host={args.host}", f"--port={args.port}",
                    f"--database={DATABASE}", "--default-character-set=utf8mb4",
                    "--batch", "--skip-column-names"])
    if args.user:
        command.append(f"--user={args.user}")

    def query(sql):
        try:
            result = subprocess.run(command, input=sql.encode("utf-8"), capture_output=True, check=True)
        except subprocess.CalledProcessError as exc:
            raise SystemExit(exc.stderr.decode("utf-8", errors="replace").strip()) from None
        return result.stdout.decode("utf-8").splitlines()

    # 固定目标库并核对活动连接和表前缀，防止将测试迁移指向共享业务库或旧结构。
    if query("SELECT DATABASE();") != [DATABASE]:
        raise SystemExit("当前数据库不匹配，停止。")
    tables = set(query("SHOW TABLES;"))
    if missing := BASE_TABLES - tables:
        raise SystemExit("缺少基线表，停止：" + ", ".join(sorted(missing)))
    category_check = f"""
        SELECT t.type FROM wx_system_dict_type t
        JOIN wx_system_dict_data d ON d.dict_type = t.type
        WHERE t.type IN {TYPES} AND t.deleted = 0 AND d.deleted = 0
          AND t.status = 0 AND d.status = 0 AND d.value = '{TEST_VALUE}'
        GROUP BY t.type HAVING COUNT(*) = 1;
    """
    if args.apply:
        # 保留原有分类。遇到停用或重复的测试编码，停在任何写入前。
        conflicts = query(f"""
            SELECT type FROM wx_system_dict_type WHERE type IN {TYPES} AND deleted = 0
            GROUP BY type HAVING COUNT(*) <> 1 OR MAX(status) <> 0
            UNION
            SELECT dict_type FROM wx_system_dict_data
            WHERE dict_type IN {TYPES} AND value = '{TEST_VALUE}' AND deleted = 0
            GROUP BY dict_type HAVING COUNT(*) <> 1 OR MAX(status) <> 0;
        """)
        if conflicts:
            raise SystemExit("发现停用或重复的类型或测试分类，未执行写入：" + ", ".join(conflicts))
        query(MIGRATION.read_text(encoding="utf-8"))
        query(f"""
            START TRANSACTION;
            INSERT INTO wx_system_dict_type (name,type,status,creator,updater)
            SELECT s.label,s.type,0,'TPL-04','TPL-04'
            FROM (SELECT '模板测试行业' AS label,'dim3d_template_industry' AS type
                  UNION ALL SELECT '模板测试会展','dim3d_template_exhibition') s
            WHERE NOT EXISTS (SELECT 1 FROM wx_system_dict_type t WHERE t.type=s.type AND t.deleted=0);
            INSERT INTO wx_system_dict_data (sort,label,value,dict_type,status,creator,updater)
            SELECT 10,'TPL-04 联验分类','{TEST_VALUE}',t.type,0,'TPL-04','TPL-04'
            FROM wx_system_dict_type t WHERE t.type IN {TYPES} AND t.deleted=0
              AND NOT EXISTS (SELECT 1 FROM wx_system_dict_data d
                              WHERE d.dict_type=t.type AND d.value='{TEST_VALUE}' AND d.deleted=0);
            COMMIT;
        """)
        tables = set(query("SHOW TABLES;"))
    enabled = query(category_check)
    print(f"专用库 {DATABASE} 基线通过。模板表：{'已就绪' if TEMPLATE_TABLE in tables else '未迁移'}。")
    print(f"启用的 {TEST_VALUE} 分类：{len(enabled)}/2。")
    if args.apply and (TEMPLATE_TABLE not in tables or len(enabled) != 2):
        raise SystemExit("迁移后核对失败，停止启动候选服务。")


if __name__ == "__main__":
    main()
