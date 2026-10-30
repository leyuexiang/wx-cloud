"""在独立测试库执行 ART-01 迁移，保留备份与校验记录，不重建服务。"""

from pathlib import Path
import argparse
import datetime
import hashlib
import json
import os
import re
import subprocess


ROOT = Path("/opt/wx/editor-test148")
TABLES = ("wx_dim3d_resource", "wx_dim3d_model", "wx_dim3d_icon", "wx_dim3d_template")
SQL_FILES = ("001-schema.up.sql", "002-links.up.sql", "003-schema.down.sql")
NEW_COLUMNS = ("resource_scope", "content_state", "storage_prefix", "manifest_path",
               "manifest_sha256", "content_hash", "content_format", "platform",
               "app_compatibility", "current_revision", "current_operation_id")


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def mysql(database, sql):
    """凭据只在 MySQL 容器内读取，不进入参数、证据或日志。"""
    command = ["docker", "exec", "-i", "mysql8", "sh", "-c",
               'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot "$@"',
               "--", "-N", "-B", "-r", database]
    result = subprocess.run(command, input=sql.encode(), capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors="replace").strip())
    return result.stdout.decode()


def scalar(database, sql):
    return mysql(database, sql).strip()


def validate_database(database):
    if database != "wx_editor_test148" and not re.fullmatch(r"wx_editor_art01_rehearsal_[a-z0-9_]+", database):
        raise RuntimeError("只能操作独立测试库或本迁移的演练库")
    version = scalar(database, "SELECT VERSION();").split("-")[0]
    if tuple(map(int, version.split(".")[:3])) < (8, 0, 16):
        raise RuntimeError("必须使用支持 CHECK 约束的 MySQL 8.0.16 以上版本")
    foreign = scalar(database, "SELECT COUNT(*) FROM information_schema.tables "
                     "WHERE table_schema=DATABASE() AND LEFT(table_name,3)<>'wx_';")
    if foreign != "0":
        raise RuntimeError("发现非 wx_ 表，拒绝在范围不明的库上迁移")


def snapshot(database, original_columns=None):
    """仅记录结构与行摘要；完整备份另存服务器受限目录。"""
    result = {"columns": {}, "rows": {}, "indexes": {}, "constraints": {}}
    for table in TABLES:
        columns = mysql(database, "SELECT JSON_ARRAY(COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,"
                        "EXTRA,COLLATION_NAME,COLUMN_COMMENT) FROM information_schema.columns "
                        f"WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{table}' ORDER BY ORDINAL_POSITION;")
        result["columns"][table] = [json.loads(line) for line in columns.splitlines()]
        if not result["columns"][table]:
            raise RuntimeError("缺少基础表：" + table)
        names = original_columns[table] if original_columns else [x[0] for x in result["columns"][table]]
        expressions = ",".join("`" + name + "`" for name in names)
        rows = mysql(database, f"SELECT id,SHA2(CAST(JSON_ARRAY({expressions}) AS CHAR),256) FROM `{table}` ORDER BY id;")
        result["rows"][table] = [line.split("\t") for line in rows.splitlines()]
        result["indexes"][table] = mysql(database, "SELECT INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME "
            f"FROM information_schema.statistics WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{table}' ORDER BY INDEX_NAME,SEQ_IN_INDEX;")
        result["constraints"][table] = mysql(database, "SELECT t.CONSTRAINT_NAME,t.CONSTRAINT_TYPE,c.CHECK_CLAUSE FROM information_schema.table_constraints t "
            "LEFT JOIN information_schema.check_constraints c ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME "
            f"WHERE t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME='{table}' ORDER BY t.CONSTRAINT_NAME;")
    result["operationColumns"] = mysql(database, "SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,COLLATION_NAME,COLUMN_COMMENT "
        "FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wx_dim3d_resource_operation' ORDER BY ORDINAL_POSITION;")
    result["operationIndexes"] = mysql(database, "SELECT INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME FROM information_schema.statistics "
        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wx_dim3d_resource_operation' ORDER BY INDEX_NAME,SEQ_IN_INDEX;")
    result["operationConstraints"] = mysql(database, "SELECT t.CONSTRAINT_NAME,t.CONSTRAINT_TYPE,c.CHECK_CLAUSE FROM information_schema.table_constraints t "
        "LEFT JOIN information_schema.check_constraints c ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME "
        "WHERE t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME='wx_dim3d_resource_operation' ORDER BY t.CONSTRAINT_NAME;")
    return result


def schema_fingerprint(value):
    return hashlib.sha256(json.dumps({k: v for k, v in value.items() if k != "rows"}, sort_keys=True).encode()).hexdigest()


def save(path, value):
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.chmod(0o600)
    temporary.replace(path)


def backup(database):
    directory = ROOT / "backups" / ("art01-" + database + "-" + datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
    directory.mkdir(mode=0o700, parents=True)
    target = directory / "database.sql"
    command = ["docker", "exec", "mysql8", "sh", "-c",
               'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --single-transaction --no-tablespaces --routines --triggers "$@"',
               "--", database]
    with os.fdopen(os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as output:
        subprocess.run(command, check=True, stdout=output, stderr=subprocess.PIPE)
    # 备份可能随测试日志增长，流式计算摘要，避免把整库副本读入内存。
    with target.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    return {"path": str(target), "sha256": digest, "mode": oct(target.stat().st_mode & 0o777)}


def read_links(database):
    return [list(map(int, line.split("\t"))) for line in mysql(database,
        "SELECT id,resource_id FROM wx_dim3d_model WHERE resource_id IS NOT NULL ORDER BY id;").splitlines()]


def execute(action, database, migration_dir):
    validate_database(database)
    files = {name: (migration_dir / name).read_bytes() for name in SQL_FILES}
    checksums = {name: hashlib.sha256(value).hexdigest() for name, value in files.items()}
    state_path = ROOT / ".migrations" / ("art01-" + database + ".json")
    state = json.loads(state_path.read_text()) if state_path.exists() else None
    if state and state["status"] == "APPLIED":
        if state["checksums"] != checksums:
            raise RuntimeError("已经执行的迁移文件发生变化，必须新增迁移而不是修改旧迁移")
        if schema_fingerprint(snapshot(database)) != state["afterSchema"]:
            raise RuntimeError("已迁移结构发生漂移，先审查，不自动重复改表")
        if action == "apply":
            return {"action": "already-applied", "database": database, "checksums": checksums}
    if action == "apply":
        before = snapshot(database)
        names = {row[0] for row in before["columns"]["wx_dim3d_resource"]}
        if names.intersection(NEW_COLUMNS) or before["operationColumns"] or any(
                any(row[0] == "resource_id" for row in before["columns"][table]) for table in TABLES[1:]):
            raise RuntimeError("迁移列/表已存在但无匹配成功记录，拒绝覆盖或猜测恢复")
        state = {"status": "APPLYING", "database": database, "startedAt": now(), "checksums": checksums,
                 "before": before, "backup": backup(database)}
        save(state_path, state)
        try:
            mysql(database, files["001-schema.up.sql"].decode())
            mysql(database, files["002-links.up.sql"].decode())
            original = {t: [row[0] for row in before["columns"][t]] for t in TABLES}
            after = snapshot(database, original)
            if before["rows"] != after["rows"]:
                raise RuntimeError("迁移期间原有字段/记录发生变化，保留备份并停止后续操作")
            state.update(status="APPLIED", completedAt=now(), afterSchema=schema_fingerprint(after), backfilledLinks=read_links(database))
            save(state_path, state)
            return {"action": "applied", "database": database, "checksums": checksums,
                    "backup": state["backup"], "originalRowsUnchanged": True,
                    "backfilledModelLinks": len(state["backfilledLinks"]),
                    "unclassifiedResources": int(scalar(database, "SELECT COUNT(*) FROM wx_dim3d_resource WHERE resource_scope IS NULL;"))}
        except Exception:
            state.update(status="FAILED", failedAt=now())
            save(state_path, state)
            raise
    if not state or state["status"] != "APPLIED":
        raise RuntimeError("没有可回退的成功迁移记录")
    # 回退只允许在新写入尚未启用时进行，不能删除已经产生的资源历史。
    if scalar(database, "SELECT COUNT(*) FROM wx_dim3d_resource_operation;") != "0":
        raise RuntimeError("已有资源操作记录，拒绝破坏性回退；请使用前向兼容迁移")
    if scalar(database, "SELECT COUNT(*) FROM wx_dim3d_resource WHERE resource_scope IS NOT NULL OR content_state<>'LEGACY' "
            "OR current_revision<>0 OR current_operation_id IS NOT NULL OR storage_prefix IS NOT NULL OR manifest_path IS NOT NULL "
            "OR manifest_sha256 IS NOT NULL OR content_hash IS NOT NULL OR content_format IS NOT NULL OR platform IS NOT NULL OR app_compatibility IS NOT NULL;") != "0":
        raise RuntimeError("已有新资源数据，拒绝回退删除字段")
    if read_links(database) != state["backfilledLinks"] or any(scalar(database,
            f"SELECT COUNT(*) FROM `{table}` WHERE resource_id IS NOT NULL;") != "0" for table in TABLES[2:]):
        raise RuntimeError("目录关联已被后续操作修改，拒绝回退")
    rollback_backup = backup(database)
    mysql(database, "UPDATE wx_dim3d_model SET resource_id=NULL,update_time=update_time WHERE resource_id IS NOT NULL;")
    mysql(database, files["003-schema.down.sql"].decode())
    after = snapshot(database)
    preserved = state["before"] == after
    state.update(status="ROLLED_BACK", rolledBackAt=now(), rollbackBackup=rollback_backup, restoredExactly=preserved)
    save(state_path, state)
    if not preserved:
        raise RuntimeError("回退完成但结构/原记录与迁移前不同，请检查并发写入和备份")
    return {"action": "rolled-back", "database": database, "restoredExactly": True, "backup": rollback_backup}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("apply", "rollback"))
    parser.add_argument("--database", default="wx_editor_test148")
    parser.add_argument("--migration-dir", type=Path, default=Path(__file__).resolve().parent / "migrations" / "art01")
    args = parser.parse_args()
    os.umask(0o077)
    if Path.cwd().resolve() != ROOT:
        raise SystemExit("必须从 /opt/wx/editor-test148 运行")
    # 防止两次迁移或回退交叉执行；等待中的服务请求不会持有此文件锁。
    import fcntl
    ROOT.joinpath(".migrations").mkdir(mode=0o700, exist_ok=True)
    with ROOT.joinpath(".migrations/art01.lock").open("a") as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        print(json.dumps(execute(args.action, args.database, args.migration_dir), ensure_ascii=False))


if __name__ == "__main__":
    main()
