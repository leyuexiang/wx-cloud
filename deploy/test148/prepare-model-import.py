"""核对、演练及应用 MODEL-02 增量，业务库写入绑定已通过的演练记录。"""

import argparse
import contextlib
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import uuid

from server_support import ROOT, file_sha256, mysql_command


DATABASES = ("wx_editor", "wx_editor_data08")
TABLES = ("wx_3d_resource", "wx_3d_resource_operation", "wx_3d_model")
IMPORT_TABLE = "wx_3d_resource_import"
MIGRATIONS = ("001-import.up.sql", "002-queue-utc.up.sql", "003-publication.up.sql")
ART_COLUMNS = {
    "id", "resource_scope", "content_state", "current_revision", "current_operation_id",
    "storage_prefix", "manifest_path", "manifest_sha256", "content_hash", "content_format",
    "platform", "app_compatibility",
}


def mysql(database, sql):
    """复用私有配置或既有容器凭据，命令参数和报告均不含密码。"""
    result = subprocess.run(
        mysql_command(database) + ["-N", "-B", "-r"],
        input=sql.encode("utf-8"), capture_output=True, check=True,
    )
    return result.stdout.decode("utf-8")


def inventory(database):
    """一次读取相关列，再读取索引和约束，避免逐字段查询。"""
    tables = ",".join("'" + table + "'" for table in (*TABLES, IMPORT_TABLE))
    columns = {}
    for line in mysql(database, "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,"
                      "COALESCE(COLUMN_DEFAULT,''),COALESCE(COLLATION_NAME,'') "
                      "FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() "
                      f"AND TABLE_NAME IN ({tables}) ORDER BY TABLE_NAME,ORDINAL_POSITION;").splitlines():
        table, name, kind, nullable, default, collation = line.split("\t")
        columns.setdefault(table, {})[name] = {
            "type": kind, "nullable": nullable, "default": default, "collation": collation,
        }
    indexes = {}
    for line in mysql(database, "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,"
                      "GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) "
                      "FROM information_schema.statistics WHERE TABLE_SCHEMA=DATABASE() "
                      f"AND TABLE_NAME IN ({tables}) GROUP BY TABLE_NAME,INDEX_NAME,NON_UNIQUE;").splitlines():
        table, name, non_unique, names = line.split("\t")
        indexes.setdefault(table, {})[name] = {"unique": non_unique == "0", "columns": names.split(",")}
    constraints = mysql(database, "SELECT TABLE_NAME,CONSTRAINT_NAME,CONSTRAINT_TYPE "
                        "FROM information_schema.table_constraints WHERE TABLE_SCHEMA=DATABASE() "
                        f"AND TABLE_NAME IN ({tables}) ORDER BY TABLE_NAME,CONSTRAINT_NAME;")
    return {"columns": columns, "indexes": indexes, "constraints": constraints}


def check_preconditions(database, schema):
    """按本批已核实的起点应用完整历史增量，部分迁移状态需先明确差异。"""
    if database not in DATABASES:
        raise ValueError("目标库必须为本批确认的两个业务库")
    columns = schema["columns"]
    if not all(table in columns for table in TABLES):
        raise ValueError("缺少资源、操作或模型目录基础表")
    if not ART_COLUMNS.issubset(columns[TABLES[0]]):
        raise ValueError("ART 资源前置列不完整")
    if IMPORT_TABLE in columns or "upload_source" in columns[TABLES[0]]:
        raise ValueError("模型导入增量已有全部或部分结构，须按实际差异处理")
    operation_id = columns[TABLES[1]].get("id", {})
    if operation_id.get("type") != "char(36)" or operation_id.get("collation") != "ascii_bin":
        raise ValueError("原操作编号类型或排序规则与导入外键不匹配")
    expected_index = {"unique": True, "columns": ["resource_id", "id"]}
    if schema["indexes"].get(TABLES[1], {}).get("uk_dim3d_operation_resource_id") != expected_index:
        raise ValueError("缺少导入复合外键依赖的原操作唯一索引")
    model = columns[TABLES[2]]
    if "resource_id" not in model or model.get("model_file_id", {}).get("nullable") != "NO":
        raise ValueError("模型目录起始结构与 003 增量不匹配")
    if "ck_dim3d_model_resource_link" in schema["constraints"]:
        raise ValueError("模型目录关联约束已存在，须按实际差异处理")


def verify_migrated(schema):
    """确认接收、队列及发布三个增量均已生效，避免仅以 SQL 返回成功判定完成。"""
    resource = schema["columns"][TABLES[0]]
    task = schema["columns"].get(IMPORT_TABLE, {})
    model = schema["columns"][TABLES[2]]
    if resource.get("upload_source", {}).get("nullable") != "YES":
        raise ValueError("历史资源来源字段必须保持可空")
    if not {"available_at", "artifact_descriptor", "publish_descriptor", "attempt_id", "lease_until"}.issubset(task):
        raise ValueError("导入任务列不完整")
    available = task["available_at"]
    if available["nullable"] != "NO" or available["default"].lower() != "utc_timestamp(3)":
        raise ValueError("队列时间默认值未统一为数据库 UTC")
    if model["model_file_id"]["nullable"] != "YES":
        raise ValueError("受管模型目录仍强制依赖旧文件编号")
    required = ("ck_dim3d_resource_upload_source", "fk_dim3d_import_operation",
                "ck_dim3d_import_publish_descriptor", "ck_dim3d_model_resource_link")
    if not all(name in schema["constraints"] for name in required):
        raise ValueError("来源、导入绑定或目录约束缺失")


def row_fingerprint(database, table, names):
    """按原有列逐行计算摘要并流式归并，内存占用与表行数无关。"""
    if table not in TABLES or not all(re.fullmatch(r"[A-Za-z0-9_]+", name) for name in names):
        raise ValueError("表或列标识不合法")
    fields = ",".join("`" + name + "`" for name in names)
    query = f"SELECT SHA2(CAST(JSON_ARRAY({fields}) AS CHAR),256) FROM `{table}` ORDER BY id;"
    digest = hashlib.sha256()
    rows = 0
    # 查询只输出固定长度摘要，避免原始业务数据进入报告或终端。
    with subprocess.Popen(mysql_command(database) + ["-N", "-B", "-e", query],
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE) as process:
        for line in process.stdout:
            digest.update(line)
            rows += 1
        error = process.stderr.read()
        if process.wait() != 0:
            raise RuntimeError("数据摘要读取失败：" + error.decode("utf-8", errors="replace")[:500])
    return {"rows": rows, "sha256": digest.hexdigest()}


def save_report(path, report):
    """报告只保留结构、数量和摘要，完整备份保存在同一受限目录。"""
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    path.chmod(0o600)


def schema_fingerprint(schema):
    """固定结构身份，应用时复核演练后发生的列、索引和约束漂移。"""
    return hashlib.sha256(json.dumps(schema, sort_keys=True).encode("utf-8")).hexdigest()


def backup_tables(database, directory):
    """只备份本批相关表，完整数据留在服务器受限目录中。"""
    target = directory / "tables.sql"
    # 通用助手带有全库存储过程选项，本批备份范围限定为三张资源相关表。
    command = [argument for argument in mysql_command(database, dump=True) if argument != "--routines"]
    with target.open("xb") as output:
        subprocess.run(command + ["--hex-blob", "--set-gtid-purged=OFF", *TABLES],
                       stdout=output, stderr=subprocess.PIPE, check=True)
    target.chmod(0o600)
    return target


def check_rehearsal_report(database, schema, checksums, report):
    """正式增量必须对应同一目标、同一结构和同一组已验证 SQL。"""
    if (report.get("status") != "REHEARSED" or report.get("sourceDatabase") != database
            or report.get("originalRowsUnchanged") is not True
            or report.get("sourceSchemaUnchanged") is not True
            or report.get("sourceSchemaSha256") != schema_fingerprint(schema)
            or report.get("migrationSha256") != checksums):
        raise ValueError("演练记录与当前目标、结构或迁移内容不一致")


def apply_migrations(database, before, migration_dir, rehearsal_path):
    """切换阶段应用已演练增量，逐步留证，失败时保留实际结构和备份。"""
    migrations = [(name, (migration_dir / name).read_bytes()) for name in MIGRATIONS]
    checksums = {name: hashlib.sha256(data).hexdigest() for name, data in migrations}
    rehearsal_report = json.loads(rehearsal_path.read_text(encoding="utf-8"))
    check_rehearsal_report(database, before, checksums, rehearsal_report)
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S")
    directory = ROOT / "backups" / f"model02-apply-{database}-{stamp}-{uuid.uuid4().hex[:8]}"
    directory.mkdir(mode=0o700, parents=True)
    report_path = directory / "report.json"
    report = {"sourceDatabase": database, "status": "APPLYING", "migrationSha256": checksums,
              "rehearsalReport": str(rehearsal_path), "backupDirectory": str(directory)}
    save_report(report_path, report)
    try:
        backup = backup_tables(database, directory)
        report.update(backupSha256=file_sha256(backup), backupBytes=backup.stat().st_size)
        names = {table: list(before["columns"][table]) for table in TABLES}
        baseline = {table: row_fingerprint(database, table, names[table]) for table in TABLES}
        # 本批切换期间先停止相关三维写入实例，使原有数据摘要能精确回查。
        for name, data in migrations:
            mysql(database, data.decode("utf-8"))
            report["lastAppliedMigration"] = name
            save_report(report_path, report)
        after_schema = inventory(database)
        verify_migrated(after_schema)
        after = {table: row_fingerprint(database, table, names[table]) for table in TABLES}
        if baseline != after:
            raise RuntimeError("原有数据摘要变化，需核对并发写入和备份")
        report.update(status="APPLIED", originalRowsUnchanged=True, rows=after,
                      afterSchemaSha256=schema_fingerprint(after_schema))
    except Exception as error:
        # MySQL 的结构语句分步提交。保留审计和新增结构，后续按实际成功步骤恢复。
        report.update(status="FAILED", errorType=type(error).__name__)
        save_report(report_path, report)
        raise
    save_report(report_path, report)
    return report


def rehearse(database, before, migration_dir):
    """备份业务表后只向新建副本应用增量，保留副本供后续隔离实例核验。"""
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S")
    suffix = uuid.uuid4().hex[:8]
    directory = ROOT / "backups" / f"model02-{database}-{stamp}-{suffix}"
    directory.mkdir(mode=0o700, parents=True)
    clone = "wx_model02_rehearsal_" + ("main" if database == "wx_editor" else "data08") + "_" + suffix
    report = {"sourceDatabase": database, "rehearsalDatabase": clone, "status": "PREPARING",
              "backupDirectory": str(directory), "migrationSha256": {},
              "sourceSchemaSha256": schema_fingerprint(before)}
    report_path = directory / "report.json"
    save_report(report_path, report)
    try:
        migrations = [(name, (migration_dir / name).read_bytes()) for name in MIGRATIONS]
        report["migrationSha256"] = {name: hashlib.sha256(data).hexdigest() for name, data in migrations}
        backup = backup_tables(database, directory)
        report["backupSha256"] = file_sha256(backup)
        report["backupBytes"] = backup.stat().st_size
        mysql(None, f"CREATE DATABASE `{clone}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;")
        with backup.open("rb") as source:
            subprocess.run(mysql_command(clone), stdin=source, stdout=subprocess.PIPE,
                           stderr=subprocess.PIPE, check=True)
        original_names = {table: list(before["columns"][table]) for table in TABLES}
        baseline = {table: row_fingerprint(clone, table, original_names[table]) for table in TABLES}
        for name, data in migrations:
            mysql(clone, data.decode("utf-8"))
            report["lastAppliedMigration"] = name
            save_report(report_path, report)
        verify_migrated(inventory(clone))
        after = {table: row_fingerprint(clone, table, original_names[table]) for table in TABLES}
        if baseline != after:
            raise RuntimeError("演练迁移改变了原有字段数据")
        if inventory(database) != before:
            raise RuntimeError("源库相关结构在演练期间发生变化，需重新核对")
        report.update(status="REHEARSED", originalRowsUnchanged=True, rows=after,
                      sourceSchemaUnchanged=True)
    except Exception as error:
        report.update(status="FAILED", errorType=type(error).__name__)
        save_report(report_path, report)
        raise
    save_report(report_path, report)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", choices=DATABASES, required=True)
    parser.add_argument("--migration-dir", type=Path, default=Path(__file__).parent / "migrations" / "market01")
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument("--rehearse", action="store_true", help="备份相关表并在独立副本执行增量")
    actions.add_argument("--apply", action="store_true", help="依据同批演练记录执行业务库增量")
    parser.add_argument("--rehearsal-report", type=Path, help="对应目标库已通过的演练报告")
    args = parser.parse_args()
    os.umask(0o077)
    if Path.cwd().resolve() != ROOT:
        raise SystemExit("必须从既有部署根目录运行")
    if args.apply and args.rehearsal_report is None:
        parser.error("应用增量需要提供对应目标库的 --rehearsal-report")
    with contextlib.ExitStack() as resources:
        if args.apply or args.rehearse:
            import fcntl
            # 两个库分别加进程锁；同一目标的备份和结构变更保持完整串行。
            lock_directory = ROOT / ".migrations"
            lock_directory.mkdir(mode=0o700, exist_ok=True)
            lock = resources.enter_context((lock_directory / f"model02-{args.database}.lock").open("a"))
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        before = inventory(args.database)
        check_preconditions(args.database, before)
        if args.apply:
            report = apply_migrations(args.database, before, args.migration_dir, args.rehearsal_report)
        elif args.rehearse:
            report = rehearse(args.database, before, args.migration_dir)
        else:
            report = {"sourceDatabase": args.database, "status": "READY", "migrationOrder": list(MIGRATIONS)}
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
