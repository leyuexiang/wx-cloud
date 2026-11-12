"""真实配置前验证套餐隔离及原授权保留，使用纯数据计划，不连接服务。"""
import unittest
from management_authorization import package_plan, tenant_update, AuthorizationError, next_package_id


class AuthorizationPlanTest(unittest.TestCase):
    """旧套餐被其他租户引用时，只为指定租户生成独立套餐。"""

    def test_existing_grants_are_preserved_without_template_expansion(self):
        plan = package_plan("2", "1", {"2": ["90", "91"], "3": ["90"]},
                            {"resources": "1", "templateManage": "4", "connectors": "5"},
                            ["1", "5"])
        self.assertEqual(set(plan["request"]["menuIds"]), {"90", "91", "1", "5"})
        self.assertNotIn("4", plan["request"]["menuIds"])
        self.assertEqual(plan["affectedTenantIds"], ["2"])
        self.assertEqual(plan["previousPackageId"], "1")
        self.assertEqual(plan["request"]["id"], "2")

    def test_empty_role_snapshot_is_rejected(self):
        with self.assertRaises(AuthorizationError):
            package_plan("2", "1", {}, {}, [])

    def test_template_already_granted_is_preserved(self):
        plan = package_plan("2", "1", {"2": ["4"]}, {"templateManage": "4"}, ["5"])
        self.assertEqual(set(plan["request"]["menuIds"]), {"4", "5"})

    def test_tenant_update_only_changes_package_and_keeps_limits(self):
        before = {"id": "2", "name": "测试租户", "contactName": "管理员", "status": 0,
                  "packageId": "1", "expireTime": 4102444800000, "accountCount": 100,
                  "website": None, "createTime": 1, "username": "不应重置", "password": "不应提交"}
        update = tenant_update(before, "888")
        self.assertEqual(update["packageId"], "888")
        self.assertEqual(update["accountCount"], 100)
        self.assertEqual(update["expireTime"], before["expireTime"])
        self.assertNotIn("password", update)
        self.assertNotIn("username", update)
        self.assertNotIn("createTime", update)

    def test_wrong_tenant_is_rejected(self):
        with self.assertRaises(AuthorizationError):
            tenant_update({"id": "3"}, "888")

    def test_package_id_skips_orphan_references_and_existing_packages(self):
        self.assertEqual(next_package_id(["1", "88", "100"]), "101")
        plan = package_plan("2", "1", {"2": []}, {}, [], reserved_ids=["88"])
        self.assertEqual(plan["request"]["id"], "89")

    def test_package_id_must_fit_database_long(self):
        with self.assertRaises(AuthorizationError):
            next_package_id(["9223372036854775807"])


if __name__ == "__main__":
    unittest.main()
