"""统一菜单配置回归：只使用内存接口，覆盖重复执行、误匹配和故障留证。"""

import copy
from pathlib import Path
import tempfile
import unittest

import management_menus as menus


ROOT = Path(__file__).resolve().parents[2]


class MemoryApi:
    """模拟系统菜单传输边界，不替代真实角色及租户验收。"""

    def __init__(self, rows=(), fail_after=None):
        self.rows = copy.deepcopy(list(rows))
        self.writes = 0
        self.fail_after = fail_after

    def call(self, method, path, body=None):
        if method == "GET":
            return copy.deepcopy(self.rows)
        if self.fail_after is not None and self.writes >= self.fail_after:
            raise menus.ConfigurationError("测试写入失败")
        self.writes += 1
        if path.endswith("/create"):
            ident = str(9007199254740993 + self.writes)
            self.rows.append(dict(body, id=ident))
            return ident
        row = next(r for r in self.rows if str(r["id"]) == str(body["id"]))
        row.update(body)
        return True


class ManagementMenusTest(unittest.TestCase):
    """计划必须来自统一清单，写入前检查现场并逐项保存实际结果。"""

    def setUp(self):
        self.nodes = menus.load_manifests(ROOT)

    def test_manifest_has_three_groups_and_seven_pages(self):
        self.assertEqual([n["request"]["name"] for n in self.nodes if n["request"]["type"] == 1],
                         ["资源管理", "场景管理", "物联网管理"])
        self.assertEqual(sum(n["request"]["type"] == 2 for n in self.nodes), 7)
        self.assertEqual(len(self.nodes), 22)

    def test_created_children_use_real_returned_parent_ids(self):
        api = MemoryApi()
        plan = menus.build_plan(self.nodes, [])
        with tempfile.TemporaryDirectory() as d:
            report = menus.apply_plan(api, self.nodes, plan, Path(d) / "receipt.json")
        model = next(r for r in api.rows if r.get("component") == "resourceManage/modelManage/index")
        group = next(r for r in api.rows if r["name"] == "资源管理")
        self.assertEqual(str(model["parentId"]), group["id"])
        self.assertEqual(report["status"], "APPLIED")
        self.assertTrue(all("@" not in str(r["parentId"]) for r in api.rows))

    def test_second_application_does_not_write(self):
        api = MemoryApi()
        with tempfile.TemporaryDirectory() as d:
            menus.apply_plan(api, self.nodes, menus.build_plan(self.nodes, []), Path(d) / "first.json")
            writes = api.writes
            plan = menus.build_plan(self.nodes, api.rows)
            self.assertTrue(all(n["action"] == "reuse" for n in plan["nodes"]))
            menus.apply_plan(api, self.nodes, plan, Path(d) / "second.json")
            self.assertEqual(api.writes, writes)

    def test_blank_optional_fields_match_server_button_normalization(self):
        # MenuServiceImpl会将按钮的组件、图标和路径置为空串，不能因此每次都重复更新。
        api = MemoryApi()
        with tempfile.TemporaryDirectory() as d:
            menus.apply_plan(api, self.nodes, menus.build_plan(self.nodes, []), Path(d) / "first.json")
        for row in api.rows:
            if row["type"] == 3:
                row.update(component="", componentName="", icon="", path="")
        self.assertTrue(all(n["action"] == "reuse" for n in menus.build_plan(self.nodes, api.rows)["nodes"]))

    def test_duplicate_component_is_rejected(self):
        row = dict(self.nodes[1]["request"], id="1", parentId="0")
        with self.assertRaises(menus.ConfigurationError):
            menus.build_plan(self.nodes, [row, dict(row, id="2")])

    def test_same_named_unrelated_page_is_not_overwritten(self):
        row = dict(self.nodes[1]["request"], id="1", parentId="0", component="unknown/index")
        group = dict(self.nodes[0]["request"], id="2")
        row["parentId"] = "2"
        with self.assertRaises(menus.ConfigurationError):
            menus.build_plan(self.nodes, [group, row])

    def test_disabled_menu_is_not_automatically_enabled(self):
        row = dict(self.nodes[0]["request"], id="1", status=1)
        with self.assertRaises(menus.ConfigurationError):
            menus.build_plan(self.nodes, [row])

    def test_existing_page_permission_is_not_cleared(self):
        group = dict(self.nodes[0]["request"], id="1")
        row = dict(self.nodes[1]["request"], id="2", parentId="1", permission="existing:query")
        with self.assertRaises(menus.ConfigurationError):
            menus.build_plan(self.nodes, [group, row])

    def test_existing_page_under_another_parent_is_rejected(self):
        row = dict(self.nodes[1]["request"], id="2", parentId="999")
        group = dict(self.nodes[0]["request"], id="1")
        with self.assertRaises(menus.ConfigurationError):
            menus.build_plan(self.nodes, [group, row])

    def test_existing_permission_button_is_reused_without_moving_or_renaming(self):
        button = next(n for n in self.nodes if n["key"] == "modelManage:dim3d:model:query")
        row = dict(button["request"], id="44", parentId="0", name="已有模型查询", sort=99)
        plan = menus.build_plan(self.nodes, [row])
        reused = next(n for n in plan["nodes"] if n["key"] == button["key"])
        self.assertEqual(reused["action"], "reuse")
        self.assertEqual(reused["request"]["parentId"], "0")
        self.assertEqual(reused["request"]["name"], "已有模型查询")

    def test_changed_snapshot_fails_before_any_write(self):
        plan = menus.build_plan(self.nodes, [])
        api = MemoryApi([dict(self.nodes[0]["request"], id="1")])
        with tempfile.TemporaryDirectory() as d, self.assertRaises(menus.ConfigurationError):
            menus.apply_plan(api, self.nodes, plan, Path(d) / "receipt.json")
        self.assertEqual(api.writes, 0)

    def test_failure_receipt_retains_created_ids(self):
        api = MemoryApi(fail_after=2)
        with tempfile.TemporaryDirectory() as d:
            receipt = Path(d) / "receipt.json"
            with self.assertRaises(menus.ConfigurationError):
                menus.apply_plan(api, self.nodes, menus.build_plan(self.nodes, []), receipt)
            report = menus.read_json(receipt)
        self.assertEqual(report["status"], "FAILED")
        self.assertEqual(len(report["applied"]), 2)
        self.assertTrue(all(n["id"] for n in report["applied"]))

    def test_existing_presentation_change_has_before_snapshot(self):
        row = dict(self.nodes[0]["request"], id="1", sort=99)
        plan = menus.build_plan(self.nodes, [row])
        self.assertEqual(plan["nodes"][0]["action"], "update")
        self.assertEqual(plan["nodes"][0]["before"]["sort"], 99)

    def test_role_plan_preserves_existing_and_omits_write_permissions(self):
        ids = {n["key"]: str(i + 100) for i, n in enumerate(self.nodes)}
        plan = menus.role_additions(self.nodes, ids, ["88"], mode="read")
        self.assertIn("88", plan["menuIds"])
        selected = {n["request"].get("permission") for n in self.nodes if ids[n["key"]] in plan["menuIds"]}
        self.assertIn("iot:connector:query", selected)
        self.assertNotIn("iot:connector:delete", selected)
        self.assertNotIn(ids["templateManage"], plan["menuIds"])

    def test_no_wildcard_or_package_mutation(self):
        ids = {n["key"]: str(i + 100) for i, n in enumerate(self.nodes)}
        with self.assertRaises(menus.ConfigurationError):
            menus.role_additions(self.nodes, ids, [], mode="all")
        # 模板仍由运营端专属能力决定，物联网管理模式不添加资源写入权。
        plan = menus.role_additions(self.nodes, ids, [], mode="iot-manage")
        self.assertIn(ids["connectors:iot:connector:delete"], plan["menuIds"])
        self.assertNotIn(ids["templateManage"], plan["menuIds"])

    def test_rollback_disables_created_menus_and_preserves_old_config(self):
        api = MemoryApi([dict(self.nodes[0]["request"], id="10", sort=99)])
        with tempfile.TemporaryDirectory() as d:
            report = menus.apply_plan(api, self.nodes, menus.build_plan(self.nodes, api.rows), Path(d) / "apply.json")
            result = menus.rollback_menus(api, report, Path(d) / "rollback.json")
        self.assertEqual(result["status"], "ROLLED_BACK")
        self.assertEqual(next(r for r in api.rows if r["id"] == "10")["sort"], 99)
        self.assertTrue(all(r["status"] == 1 for r in api.rows if r["id"] != "10"))
        self.assertEqual(len(api.rows), 22)

    def test_rollback_rejects_changes_made_after_application(self):
        api = MemoryApi()
        with tempfile.TemporaryDirectory() as d:
            report = menus.apply_plan(api, self.nodes, menus.build_plan(self.nodes, []), Path(d) / "apply.json")
            api.rows[-1]["name"] = "现场另有修改"
            writes = api.writes
            with self.assertRaises(menus.ConfigurationError):
                menus.rollback_menus(api, report, Path(d) / "rollback.json")
            self.assertEqual(api.writes, writes)

    def test_rollback_rejects_unconfirmed_request(self):
        with tempfile.TemporaryDirectory() as d, self.assertRaises(menus.ConfigurationError):
            menus.rollback_menus(MemoryApi(), {"status": "FAILED", "pending": {"key": "unknown"}}, Path(d) / "rollback.json")


if __name__ == "__main__":
    unittest.main()
