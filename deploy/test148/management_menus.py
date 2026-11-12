"""统一管理菜单增量工具：读取标准接口，预览后按现场快照执行，不自动扩大角色或套餐。"""

import argparse
import copy
import datetime
import hashlib
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request


FIELDS = ("name", "permission", "type", "sort", "parentId", "path", "icon", "component",
          "componentName", "status", "visible", "keepAlive", "alwaysShow")
SOURCES = ("db/system/editor-management-menus.json", "wx-module-iot/db/iot-management-menus.json")


class ConfigurationError(RuntimeError):
    """仅向调用方提供安全摘要，避免HTTP响应或凭据进入验收记录。"""


def read_json(path):
    """中文清单和交付记录统一使用UTF-8。"""
    return json.loads(Path(path).read_text(encoding="utf-8"))


def save_json(path, value):
    """先写同目录临时文件再替换，逐项留证，拒绝覆盖首次执行记录由调用者保证。"""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    pending = path.with_suffix(path.suffix + ".pending")
    with pending.open("w", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    pending.replace(path)


def digest(value):
    """对规范化JSON做摘要，忽略列表接口中不参与配置的创建时间等字段。"""
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False,
                                     separators=(",", ":")).encode("utf-8")).hexdigest()


def menu_request(row):
    """仅透传系统MenuSaveVO字段，编号保持十进制字符串，避免经过浮点数。"""
    value = {key: row.get(key) for key in FIELDS}
    value["parentId"] = str(value["parentId"])
    return value


def load_manifests(root):
    """两专项使用同一groups/pages/buttons结构，并按父先子后展平。"""
    nodes = []
    for source in SOURCES:
        data = read_json(Path(root) / source)
        if data.get("schemaVersion") != 1:
            raise ConfigurationError("菜单清单版本不支持")
        for group in data["groups"]:
            nodes.append({"key": group["key"], "request": group["request"]})
            for page in group["pages"]:
                nodes.append({"key": page["key"], "parentKey": group["key"], "request": page["request"]})
                for button in page.get("buttons", []):
                    nodes.append({"key": page["key"] + ":" + button["request"]["permission"],
                                  "parentKey": page["key"], "request": button["request"]})
    seen = set()
    for node in nodes:
        if node["key"] in seen or (node.get("parentKey") and node["parentKey"] not in seen):
            raise ConfigurationError("菜单键重复或父级顺序错误")
        if node["request"]["type"] == 3 and not node["request"].get("permission"):
            raise ConfigurationError("按钮必须显式声明权限")
        seen.add(node["key"])
    return nodes


def build_plan(nodes, rows):
    """按稳定组件/权限匹配，歧义、跨父级和停用节点拒绝自动处理。"""
    snapshot = [dict(menu_request(row), id=str(row["id"])) for row in rows]
    snapshot.sort(key=lambda r: r["id"])
    indexes = {"component": {}, "permission": {}, "directory": {}, "name": {}}
    for row in snapshot:
        if row["type"] == 2:
            indexes["component"].setdefault(row.get("component"), []).append(row)
        if row["type"] == 3:
            indexes["permission"].setdefault(row.get("permission"), []).append(row)
        if row["type"] == 1:
            indexes["directory"].setdefault(row.get("path"), []).append(row)
        indexes["name"].setdefault((row["parentId"], row["name"]), []).append(row)
    planned, ids = [], {}
    for node in nodes:
        request = menu_request(node["request"])
        request["parentId"] = ids[node["parentKey"]] if node.get("parentKey") else "0"
        kind = request["type"]
        field = "directory" if kind == 1 else "component" if kind == 2 else "permission"
        identity = request["path"] if kind == 1 else request[field]
        matches = indexes[field].get(identity, [])
        if len(matches) > 1:
            raise ConfigurationError("存在重复菜单标识：" + node["key"])
        current = matches[0] if matches else None
        name_matches = indexes["name"].get((request["parentId"], request["name"]), [])
        if any(current is None or r["id"] != current["id"] for r in name_matches):
            raise ConfigurationError("同名菜单对应不同功能：" + node["key"])
        item = {"key": node["key"], "request": request}
        if current:
            if kind != 3 and current["parentId"] != request["parentId"]:
                raise ConfigurationError("既有菜单父级不一致：" + node["key"])
            if (current.get("permission") or "") != (request.get("permission") or ""):
                raise ConfigurationError("既有页面权限边界不一致：" + node["key"])
            if current["status"] != 0 or (kind != 3 and current["visible"] is not True):
                raise ConfigurationError("既有菜单被停用或隐藏，请先核对授权意图：" + node["key"])
            # 旧按钮权限可能位于已有业务目录；按权限复用，不移动或改名影响原后台。
            if kind == 3:
                item.update(id=current["id"], before=current, action="reuse", request=menu_request(current))
            else:
                item.update(id=current["id"], before=current,
                            action="reuse" if menu_request(current) == request else "update")
            ids[node["key"]] = current["id"]
        else:
            item["action"] = "create"
            ids[node["key"]] = "@" + node["key"]
        planned.append(item)
    return {"schemaVersion": 1, "manifestSha256": digest(nodes), "snapshotSha256": digest(snapshot),
            "nodes": planned, "grantsChanged": False}


def apply_plan(api, nodes, plan, receipt):
    """先比对现场；每步先记意图再提交，超时保留未知结果，不盲目重试创建。"""
    if Path(receipt).exists():
        raise ConfigurationError("执行记录已存在，请先核对前次结果")
    if plan != build_plan(nodes, api.call("GET", "/system/menu/list")):
        raise ConfigurationError("现场或清单已变化，请重新生成计划")
    report = {"status": "APPLYING", "planSha256": digest(plan), "applied": [], "pending": None,
              "target": {"base": getattr(api, "base", None), "tenantId": getattr(api, "tenant", None)},
              "grantsChanged": False, "startedAt": datetime.datetime.now(datetime.timezone.utc).isoformat()}
    save_json(receipt, report)
    ids = {}
    try:
        for item in plan["nodes"]:
            if item["action"] == "reuse":
                ids[item["key"]] = item["id"]
                continue
            body = copy.deepcopy(item["request"])
            if body["parentId"].startswith("@"):
                body["parentId"] = ids[body["parentId"][1:]]
            report["pending"] = dict(item, request=body)
            save_json(receipt, report)
            if item["action"] == "create":
                ident = api.call("POST", "/system/menu/create", body)
                if isinstance(ident, bool) or not str(ident).isdigit():
                    raise ConfigurationError("创建菜单返回编号无效，停止并保留待核对记录")
                ident = str(ident)
            else:
                ident = item["id"]
                if api.call("PUT", "/system/menu/update", dict(body, id=ident)) is not True:
                    raise ConfigurationError("菜单更新未确认成功")
            ids[item["key"]] = ident
            report["applied"].append(dict(item, id=ident, request=body))
            report["pending"] = None
            save_json(receipt, report)
        verified = build_plan(nodes, api.call("GET", "/system/menu/list"))
        if any(item["action"] != "reuse" or item["id"] != ids[item["key"]] for item in verified["nodes"]):
            raise ConfigurationError("菜单写入后的读取核验不一致")
        report.update(status="APPLIED", ids={item["key"]: ids[item["key"]] for item in plan["nodes"]})
        save_json(receipt, report)
        return report
    except Exception:
        report["status"] = "FAILED"
        save_json(receipt, report)
        raise


def rollback_menus(api, receipt, output):
    """回退前整体检查；新建节点只停用保留，修改节点恢复原快照，不物理删除菜单。"""
    if Path(output).exists() or receipt.get("status") != "APPLIED" or receipt.get("pending"):
        raise ConfigurationError("执行未完整确认或回退记录已存在，须先核对现场")
    current = {str(row["id"]): menu_request(row) for row in api.call("GET", "/system/menu/list")}
    actions = []
    for item in reversed(receipt["applied"]):
        if current.get(item["id"]) != item["request"]:
            raise ConfigurationError("菜单在执行后有变更，不能自动回退：" + item["key"])
        restore = (dict(item["request"], status=1, visible=False) if item["action"] == "create"
                   else menu_request(item["before"]))
        actions.append({"key": item["key"], "id": item["id"], "request": restore})
    report = {"status": "ROLLING_BACK", "target": receipt.get("target"), "applied": [], "pending": None,
              "sourceReceiptSha256": digest(receipt), "grantsChanged": False, "menusDeleted": False}
    save_json(output, report)
    try:
        for action in actions:
            report["pending"] = action
            save_json(output, report)
            if api.call("PUT", "/system/menu/update", dict(action["request"], id=action["id"])) is not True:
                raise ConfigurationError("菜单回退未确认成功")
            report["applied"].append(action)
            report["pending"] = None
            save_json(output, report)
        checked = {str(row["id"]): menu_request(row) for row in api.call("GET", "/system/menu/list")}
        if any(checked.get(action["id"]) != action["request"] for action in actions):
            raise ConfigurationError("回退后的读取核验不一致")
        report["status"] = "ROLLED_BACK"
        save_json(output, report)
        return report
    except Exception:
        report["status"] = "ROLLBACK_FAILED"
        save_json(output, report)
        raise


def role_additions(nodes, ids, existing, mode):
    """仅生成显式权限增量，不执行授权；模板能力及资源写入不由IoT模式推导。"""
    if mode not in ("read", "iot-manage"):
        raise ConfigurationError("授权模式只能为read或iot-manage")
    chosen = {str(value) for value in existing}
    for node in nodes:
        request = node["request"]
        permission = request.get("permission", "")
        if node["key"] == "templateManage":
            continue
        if request["type"] != 3 or permission.endswith(":query") or (mode == "iot-manage" and permission.startswith("iot:")):
            chosen.add(str(ids[node["key"]]))
    return {"menuIds": sorted(chosen), "addedMenuIds": sorted(chosen - {str(v) for v in existing}),
            "mode": mode, "applied": False}


class SystemApi:
    """凭据从受控文件读取；不接受命令行密码，不输出服务端消息和令牌。"""

    def __init__(self, base, credentials):
        parsed = urllib.parse.urlparse(base)
        if parsed.scheme not in ("http", "https") or parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ConfigurationError("接口地址格式无效")
        # 明文HTTP仅允许SSH隧道或服务器本机回环，避免登录密码经过局域网明文传输。
        if parsed.scheme == "http" and parsed.hostname not in ("127.0.0.1", "localhost", "::1"):
            raise ConfigurationError("远端登录必须使用HTTPS或回环SSH隧道")
        self.base = base.rstrip("/")
        self.tenant = str(credentials["tenantId"])
        self.token = None
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        login = self.call("POST", "/system/auth/login", {"username": credentials["username"], "password": credentials["password"]})
        self.token = login["accessToken"]

    def call(self, method, path, body=None):
        headers = {"Content-Type": "application/json", "tenant-id": self.tenant}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        request = urllib.request.Request(self.base + path, method=method, headers=headers,
                                         data=json.dumps(body).encode("utf-8") if body is not None else None)
        try:
            with self.opener.open(request, timeout=20) as response:
                result = json.load(response)
        except urllib.error.HTTPError as error:
            raise ConfigurationError("系统接口HTTP状态=" + str(error.code)) from None
        except Exception:
            raise ConfigurationError("系统接口网络或响应格式异常，请核对现场") from None
        if result.get("code") != 200:
            raise ConfigurationError("系统接口业务码=" + str(result.get("code")))
        return result.get("data")


def main():
    """默认只查看清单；实际写入必须提供同环境的预览计划和独立执行记录路径。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("inventory", "plan", "apply", "rollback", "role-plan"))
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--base")
    parser.add_argument("--credentials", type=Path)
    parser.add_argument("--identity-role", default="super_admin")
    parser.add_argument("--plan", type=Path)
    parser.add_argument("--receipt", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--role-menus", type=Path)
    parser.add_argument("--mode", choices=("read", "iot-manage"), default="read")
    args = parser.parse_args()
    nodes = load_manifests(args.root)
    if args.output.exists():
        raise ConfigurationError("输出记录已存在，不能覆盖原始证据")
    if args.action == "inventory":
        save_json(args.output, {"scope": "离线清单，非现场计划或授权结果", "nodes": nodes,
                                "manifestSha256": digest(nodes), "applied": False})
    elif args.action == "role-plan":
        if not args.receipt or not args.role_menus:
            parser.error("role-plan需要--receipt与--role-menus")
        receipt = read_json(args.receipt)
        if receipt.get("status") != "APPLIED":
            raise ConfigurationError("菜单尚未执行成功，不能推导角色增量")
        save_json(args.output, role_additions(nodes, receipt["ids"], read_json(args.role_menus), args.mode))
    else:
        if not args.credentials or not args.base:
            parser.error("plan/apply需要--credentials与--base")
        credentials = read_json(args.credentials)
        if isinstance(credentials, list):
            credentials = next((r for r in credentials if r.get("role") == args.identity_role), None)
        if not credentials:
            raise ConfigurationError("受控文件中没有指定身份")
        api = SystemApi(args.base, credentials)
        if args.action == "plan":
            plan = build_plan(nodes, api.call("GET", "/system/menu/list"))
            plan["target"] = {"base": api.base, "tenantId": api.tenant}
            save_json(args.output, plan)
        elif args.action == "apply":
            if not args.plan:
                parser.error("apply需要--plan")
            plan = read_json(args.plan)
            if plan.pop("target", None) != {"base": api.base, "tenantId": api.tenant}:
                raise ConfigurationError("计划目标与当前接口或租户不一致")
            apply_plan(api, nodes, plan, args.output)
        else:
            if not args.receipt:
                parser.error("rollback需要--receipt")
            receipt = read_json(args.receipt)
            if receipt.get("target") != {"base": api.base, "tenantId": api.tenant}:
                raise ConfigurationError("执行记录目标与当前接口或租户不一致")
            rollback_menus(api, receipt, args.output)
    print("统一菜单操作已完成：" + args.action)


if __name__ == "__main__":
    try:
        main()
    except ConfigurationError as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
