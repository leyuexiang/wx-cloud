"""在专用演练库验证迁移、约束和回退；保留库与报告，不操作正式资源。"""

from pathlib import Path
import importlib.util
import json
import os
import uuid


HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("art01_migration", HERE / "migrate-resource-art01.py")
M = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)


def verify():
    os.umask(0o077)
    if Path.cwd().resolve() != M.ROOT:
        raise RuntimeError("必须从测试部署目录运行")
    database = "wx_editor_art01_rehearsal_" + uuid.uuid4().hex[:12]
    # 只创建唯一命名的新库，不复用、不删除任何已有数据库。
    M.mysql("mysql", f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;")
    M.mysql(database, (M.ROOT / "schema-core.sql").read_text())
    M.mysql(database, """
        INSERT INTO wx_dim3d_resource(id,resource_name,resource_type,asset_name,resource_relative_path,tenant_id,del_flag) VALUES
        (101,'演练模型','MODEL','car','old/car','2','0'),
        (102,'旧默认','MODEL','old','old/default','000000','0'),
        (103,'其他租户','MODEL','other','old/other','3','0'),
        (104,'已删除','MODEL','deleted','old/deleted','2','1');
        INSERT INTO wx_dim3d_model(id,model_name,category,model_file_id,tenant_id,del_flag) VALUES
        (201,'有效对应','TEST',101,'2','0'),(202,'跨租户编号','TEST',101,'3','0'),
        (203,'悬空编号','TEST',999,'2','0'),(204,'删除资源','TEST',104,'2','0'),(205,'删除目录','TEST',101,'2','1');
        INSERT INTO wx_dim3d_icon(id,icon_name,category,icon_file_id,tenant_id) VALUES(301,'对象存储编号','TEST',101,'2');
        INSERT INTO wx_dim3d_template(id,template_name,category,template_file_id,tenant_id) VALUES(401,'旧快照编号','TEST',101,'2');
    """)
    results = []

    def record(name, passed):
        if not passed: raise AssertionError(name)
        results.append({"name": name, "passed": True})

    migration_dir = HERE / "migrations/art01"
    applied = M.execute("apply", database, migration_dir)
    record("迁移保留原字段与原记录", applied["originalRowsUnchanged"])
    record("只回填一条同租户模型真实关联", M.read_links(database) == [[201, 101]])
    record("图标及模板不按同号猜测关联", all(M.scalar(database, f"SELECT COUNT(*) FROM {t} WHERE resource_id IS NOT NULL;") == "0" for t in M.TABLES[2:]))
    record("旧默认值不提升为全局资源", M.scalar(database, "SELECT COUNT(*) FROM wx_dim3d_resource WHERE resource_scope IS NULL AND content_state='LEGACY';") == "4")
    record("重复执行幂等", M.execute("apply", database, migration_dir)["action"] == "already-applied")

    def sql_case(name, sql, error=None):
        try:
            M.mysql(database, "START TRANSACTION;\n" + sql + ";\nROLLBACK;")
            record(name, error is None)
        except RuntimeError as exc:
            record(name, error is not None and ("ERROR " + str(error)) in str(exc))

    def resource(scope, tenant, extra_columns="", extra_values="", state="PENDING", row_id=901):
        tenant_sql = "NULL" if tenant is None else "'" + tenant + "'"
        return ("INSERT INTO wx_dim3d_resource(id,resource_name,resource_type,asset_name,resource_relative_path,resource_scope,tenant_id,content_state"
                + extra_columns + ") VALUES(" + str(row_id) + ",'演练','MODEL','car','old/car','" + scope + "'," + tenant_sql + ",'" + state + "'" + extra_values + ")")

    sql_case("允许全局0归属", resource("GLOBAL_SYSTEM", "0"))
    sql_case("允许规范租户共享归属", resource("TENANT_SHARED", "2"))
    for scope, tenant in [("GLOBAL_SYSTEM", "000000"), ("GLOBAL_SYSTEM", "2"), ("GLOBAL_SYSTEM", None),
                          ("TENANT_SHARED", "0"), ("TENANT_SHARED", "02"), ("TENANT_SHARED", "-2"),
                          ("TENANT_SHARED", "9223372036854775808"), ("global_system", "0"), ("UNKNOWN", "0")]:
        sql_case("拒绝错误范围归属:" + scope + "/" + str(tenant), resource(scope, tenant), 3819)
    sql_case("READY拒绝不完整当前指针", resource("GLOBAL_SYSTEM", "0", state="READY"), 3819)
    sql_case("拒绝错误摘要", resource("GLOBAL_SYSTEM", "0", ",content_hash", ",'" + "A" * 64 + "'"), 3819)
    sql_case("旧转换写入仍可保留LEGACY状态", "INSERT INTO wx_dim3d_resource(id,resource_name,resource_type,asset_name,resource_relative_path) VALUES(903,'兼容','MODEL','old','old/source')")

    operation_id = "6063365a-210b-486b-8c2d-928b10e66a39"
    def operation(row_id=101, status="UPLOADING", extra_columns="", extra_values=""):
        return ("INSERT INTO wx_dim3d_resource_operation(id,resource_id,operation_type,resource_scope,tenant_id,operator_id,request_hash,expected_revision,status,expires_at"
            + extra_columns + ") VALUES('" + operation_id + "'," + str(row_id) + ",'REPLACE','TENANT_SHARED','2',1,'" + "a" * 64 + "',0,'" + status + "','2030-01-01 00:00:00'" + extra_values + ")")
    sql_case("允许受约束的操作记录", operation())
    sql_case("拒绝悬空资源操作", operation(999999), 1452)
    sql_case("已提交必须有快照和提交修订", operation(status="COMMITTED"), 3819)
    sql_case("已提交修订必须递增一", operation(status="COMMITTED", extra_columns=",after_snapshot,committed_revision", extra_values=",JSON_OBJECT('resourceId','101'),2"), 3819)
    sql_case("允许完整已提交回执", operation(status="COMMITTED", extra_columns=",after_snapshot,committed_revision", extra_values=",JSON_OBJECT('resourceId','101'),1"))
    ready = ("UPDATE wx_dim3d_resource SET resource_scope='TENANT_SHARED',tenant_id='2',content_state='READY',storage_prefix='resources/101/a',"
             "manifest_path='manifest.json',manifest_sha256='" + "a" * 64 + "',content_hash='" + "b" * 64 + "',content_format='unity.assetbundle.v1',"
             "platform='WebGL',app_compatibility='test-art1',current_revision=1,current_operation_id='" + operation_id + "' WHERE id=101")
    sql_case("完整当前指针引用同资源操作", operation() + ";" + ready)
    sql_case("拒绝引用其它资源的同号操作", operation(103) + ";" + ready, 1452)

    # 回退保护先检查再拒绝，验证失败不会执行任何删除字段语句。
    M.mysql(database, operation() + ";")
    try:
        M.execute("rollback", database, migration_dir)
        raise AssertionError("存在操作记录时应拒绝回退")
    except RuntimeError as exc:
        record("存在操作记录拒绝回退", "已有资源操作记录" in str(exc))
    M.mysql(database, "DELETE FROM wx_dim3d_resource_operation WHERE id='" + operation_id + "';")
    M.mysql(database, resource("GLOBAL_SYSTEM", "0") + ";")
    try:
        M.execute("rollback", database, migration_dir)
        raise AssertionError("存在新资源时应拒绝回退")
    except RuntimeError as exc:
        record("存在新资源拒绝回退", "已有新资源数据" in str(exc))
    M.mysql(database, "DELETE FROM wx_dim3d_resource WHERE id=901;")
    rollback = M.execute("rollback", database, migration_dir)
    record("回退结构及原记录完全恢复", rollback["restoredExactly"])
    final = M.execute("apply", database, migration_dir)
    record("回退后可以重新迁移", final["originalRowsUnchanged"] and final["backfilledModelLinks"] == 1)
    result = {"scope": "独立演练库结构、数据与回退验证，不是业务替换事务或权限接口验收", "database": database,
              "passed": len(results), "failed": 0, "checks": results, "applied": applied,
              "rollback": rollback, "finalApply": final, "retainedForAudit": True}
    report = HERE / "evidence-art01-rehearsal.json"
    M.save(report, result)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    verify()
