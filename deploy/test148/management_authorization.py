"""148授权发布：受控标准输入登录，先备份、预览，再为租户2绑定独立套餐。"""

import argparse
import datetime
import json
import os
from pathlib import Path
import subprocess
import sys

from management_menus import SystemApi, apply_plan, build_plan, digest, load_manifests, read_json, role_additions, save_json
from server_support import mysql_command, file_sha256


class AuthorizationError(RuntimeError):
    """只记录固定阶段说明，不向日志输出接口正文或敏感租户资料。"""


ROOT = Path("/opt/wx/editor-test148")
TENANT_FIELDS = ("id", "name", "contactName", "contactMobile", "status", "website",
                 "packageId", "expireTime", "accountCount")


def next_package_id(reserved_ids):
    """空套餐表仍可能存在悬空引用，显式避开全部引用编号，不能依赖自增首值。"""
    ident = max([0] + [int(value) for value in reserved_ids]) + 1
    if ident > 9223372036854775807:
        raise AuthorizationError("套餐编号超过数据库范围")
    return str(ident)


def package_plan(tenant_id, previous_package, role_menus, ids, additions, reserved_ids=()):
    """新建专用套餐，原角色权限取并集，避免套餐切换清理原有合法授权。"""
    if str(tenant_id) != "2" or not role_menus:
        raise AuthorizationError("必须核对目标租户及其全部角色快照")
    preserved = {str(menu) for grants in role_menus.values() for menu in grants}
    requested = {str(menu) for menu in additions}
    template = ids.get("templateManage")
    if template and str(template) in requested and str(template) not in preserved:
        raise AuthorizationError("普通租户套餐不能新增运营端模板能力")
    return {"previousPackageId": str(previous_package), "affectedTenantIds": ["2"],
            "preservedMenuIds": sorted(preserved),
            "request": {"id": next_package_id([previous_package, *reserved_ids]),
                        "name": "编辑器管理联验-租户2-20261111-独立", "status": 0,
                        "remark": "独立管理联验套餐；保留原角色权限，其他租户原套餐不变",
                        "menuIds": sorted(preserved | requested)}}


def tenant_update(before, package_id):
    """原样保留租户业务限制，不提交创建账号/密码或变更联系人等无关字段。"""
    if str(before.get("id")) != "2":
        raise AuthorizationError("目标租户不匹配")
    update = {key: before[key] for key in TENANT_FIELDS if key in before}
    update["packageId"] = str(package_id)
    return update


def db_rows(query):
    """数据库只用于现场核对及备份；授权写入全部走系统接口维护缓存和租户上下文。"""
    result = subprocess.run(mysql_command("wx_editor"), input=query, text=True, capture_output=True)
    if result.returncode:
        raise AuthorizationError("只读数据库核对失败")
    return [line.split("\t") for line in result.stdout.strip().splitlines()[1:]]


def snapshot(api):
    """完整核对角色及旧套餐关联租户，防止修复一个租户时扩大其他租户授权。"""
    tenant = api.call("GET", "/system/tenant/get?id=2")
    rows = db_rows("SELECT r.id,r.tenant_id,r.code,rm.menu_id FROM wx_system_role r "
                   "LEFT JOIN wx_system_role_menu rm ON r.id=rm.role_id AND rm.deleted=0 "
                   "WHERE r.deleted=0 ORDER BY r.id,rm.menu_id;")
    roles = {}
    for ident, tenant_id, code, menu_id in rows:
        role = roles.setdefault(ident, {"tenantId": tenant_id, "code": code, "menuIds": []})
        if menu_id != "NULL":
            role["menuIds"].append(menu_id)
    if not any(r["tenantId"] == "2" and r["code"] == "tenant_admin" for r in roles.values()):
        raise AuthorizationError("目标租户缺少预期管理员角色")
    return {"menus": api.call("GET", "/system/menu/list"), "tenant": tenant, "roles": roles,
            "packageUsers": db_rows("SELECT id,package_id FROM wx_system_tenant WHERE deleted=0 ORDER BY id;"),
            "packages": api.call("GET", "/system/tenant-package/page?pageNo=1&pageSize=100")}


def backup():
    """全量单事务备份系统库，原密码哈希等仅进入服务器权限600备份，不回传工作区。"""
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    directory = ROOT / "backups" / ("iot-management-" + stamp)
    directory.mkdir(mode=0o700)
    target = directory / "wx_editor.sql"
    with target.open("wb") as stream:
        result = subprocess.run(mysql_command("wx_editor", dump=True), stdout=stream, stderr=subprocess.PIPE)
    if result.returncode or target.stat().st_size == 0:
        raise AuthorizationError("系统数据库备份失败，未执行配置写入")
    return {"path": str(target), "sha256": file_sha256(target), "size": target.stat().st_size}


def main():
    """prepare和apply分开，跨接口发布失败时保留执行记录，不盲目重跑。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "apply", "recover"))
    args = parser.parse_args()
    source = Path(__file__).resolve().parents[2]
    release = ROOT / "releases/iot-management-20261111"
    if source != release or not (ROOT / ".env").exists():
        raise AuthorizationError("发布目录不匹配")
    os.umask(0o077)
    credentials = json.load(sys.stdin)
    api = SystemApi("http://127.0.0.1:12525/admin-api", credentials)
    credentials.clear()
    info = api.call("GET", "/system/auth/get-permission-info")
    if "super_admin" not in info.get("roles", []):
        raise AuthorizationError("发布必须使用经系统核对的平台管理员身份")
    nodes = load_manifests(source)
    state_path = release / "authorization-state.private.json"
    report_path = release / "authorization-report.json"
    if args.action == "prepare":
        if state_path.exists():
            raise AuthorizationError("准备记录已存在，请先检查执行状态")
        before = snapshot(api)
        if str(before["tenant"]["packageId"]) != "1":
            raise AuthorizationError("现场套餐引用已变化，须重新评估")
        plan = build_plan(nodes, before["menus"])
        package_name = "编辑器管理联验-租户2-20261111-独立"
        if any(p["name"] == package_name for p in before["packages"].get("list", [])):
            raise AuthorizationError("同名专用套餐已存在，须核对前次发布")
        saved = backup()
        save_json(state_path, {"status": "PREPARED", "before": before, "plan": plan, "backup": saved})
        save_json(release / "api-menu-plan.json", plan)
        report = {"status": "PREPARED", "backup": saved, "tenantId": "2", "menuActions":
                  {action: sum(n["action"] == action for n in plan["nodes"]) for action in ("create", "reuse", "update")},
                  "otherTenantsUnchanged": True, "authorizationWritten": False}
        save_json(report_path, report)
    else:
        state = read_json(state_path)
        recovering = args.action == "recover"
        current = snapshot(api)
        if recovering:
            # 仅处理本批已确认的自增编号碰撞；无法确认的请求一律保留现场人工核对。
            if (state["status"] != "FAILED" or state.get("pending") is not None
                    or state.get("newPackageId") != "1" or current["tenant"]["packageId"] != state["before"]["tenant"]["packageId"]
                    or current["roles"] != state["before"]["roles"]):
                raise AuthorizationError("不满足编号碰撞恢复条件")
            for role in current["roles"].values():
                if role["tenantId"] not in ("1", "2") and role["menuIds"]:
                    raise AuthorizationError("其他租户已有授权，不能自动收敛旧套餐")
            if any(n["action"] != "reuse" for n in build_plan(nodes, current["menus"])["nodes"]):
                raise AuthorizationError("菜单已有新变更，不能自动恢复")
            save_json(release / "collision-before.private.json", state)
        elif state["status"] != "PREPARED" or digest(current) != digest(state["before"]):
            raise AuthorizationError("现场已变化或发布已执行，不能重跑")
        if file_sha256(state["backup"]["path"]) != state["backup"]["sha256"]:
            raise AuthorizationError("备份摘要不匹配")
        state["status"] = "APPLYING"
        save_json(state_path, state)
        report = read_json(report_path)
        try:
            receipt = (read_json(release / "api-menu-receipt.json") if recovering
                       else apply_plan(api, nodes, state["plan"], release / "api-menu-receipt.json"))
            report["menuStatus"] = receipt["status"]
            original_roles = {ident: r["menuIds"] for ident, r in state["before"]["roles"].items() if r["tenantId"] == "2"}
            additions = role_additions(nodes, receipt["ids"], [], "iot-manage")["menuIds"]
            reserved = [row[1] for row in current["packageUsers"]]
            reserved.extend(str(p["id"]) for p in current["packages"].get("list", []))
            package = package_plan("2", "1", original_roles, receipt["ids"], additions, reserved)
            save_json(release / "package-plan.json", package)
            state["pending"] = "CREATE_PACKAGE"
            save_json(state_path, state)
            package_id = api.call("POST", "/system/tenant-package/create", package["request"])
            if isinstance(package_id, bool) or str(package_id) != package["request"]["id"]:
                raise AuthorizationError("套餐创建编号未确认，须检查现场")
            state.update(newPackageId=str(package_id), pending="UPDATE_TENANT")
            save_json(state_path, state)
            if api.call("PUT", "/system/tenant/update", tenant_update(state["before"]["tenant"], package_id)) is not True:
                raise AuthorizationError("租户更新未确认")
            state["pending"] = None
            save_json(state_path, state)
            if recovering:
                # 租户2已迁出；其余旧引用租户原本均无菜单，空占位保持其原权限边界。
                state["pending"] = "RESTRICT_OLD_PLACEHOLDER"
                save_json(state_path, state)
                if api.call("PUT", "/system/tenant-package/update", {
                    "id": "1", "name": "历史缺失套餐隔离占位-20261111", "status": 0,
                    "remark": "历史悬空引用占位，无新增菜单；租户2使用独立套餐", "menuIds": []}) is not True:
                    raise AuthorizationError("历史套餐收敛未确认")
                state["pending"] = None
                save_json(state_path, state)
            after = snapshot(api)
            if recovering and any(p["menuIds"] for p in after["packages"].get("list", []) if str(p["id"]) == "1"):
                raise AuthorizationError("历史套餐仍包含菜单，不能确认恢复")
            expected = set(package["request"]["menuIds"])
            for ident, previous in state["before"]["roles"].items():
                current = after["roles"].get(ident)
                if previous["tenantId"] == "2" and previous["code"] == "tenant_admin":
                    valid = current and set(current["menuIds"]) == expected
                else:
                    valid = current == previous
                if not valid:
                    raise AuthorizationError("角色授权回读不一致")
            if str(after["tenant"]["packageId"]) != str(package_id):
                raise AuthorizationError("租户套餐回读不一致")
            if [r for r in after["packageUsers"] if r[0] != "2"] != [r for r in state["before"]["packageUsers"] if r[0] != "2"]:
                raise AuthorizationError("其他租户套餐引用变化")
            state.update(status="APPLIED", after=after)
            report.update(status="APPLIED", newPackageId=str(package_id), authorizationWritten=True,
                          preservedMenuIds=package["preservedMenuIds"], packageMenuCount=len(expected),
                          viewerUnchanged=True, otherTenantsUnchanged=True, databaseReinitialized=False,
                          collisionRecovered=recovering, oldPlaceholderHasNoMenus=recovering,
                          manualReviewRequired=False)
        except Exception:
            state["status"] = "FAILED"
            report.update(status="FAILED", manualReviewRequired=True)
            raise
        finally:
            save_json(state_path, state)
            save_json(report_path, report)
    print(json.dumps(read_json(report_path), ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # 原始网络响应和租户资料不进入终端；受控状态文件保留具体执行阶段。
        detail = str(error) if isinstance(error, AuthorizationError) else type(error).__name__
        print("授权发布未完成：" + detail, file=sys.stderr)
        raise SystemExit(1)
