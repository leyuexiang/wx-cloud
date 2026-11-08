"""TPL-07-02 模板权限和租户边界的源码门禁。"""
from pathlib import Path
import unittest


ROOT = Path(__file__).parents[2]
CONTROLLER = ROOT / "wx-module-3d/src/main/java/cn/iocoder/wx/module/dimensions/controller/admin/Dim3dSceneController.java"
SERVICE = ROOT / "wx-module-3d/src/main/java/cn/iocoder/wx/module/dimensions/service/Dim3dSceneService.java"


class Tpl07GovernanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.controller = CONTROLLER.read_text(encoding="utf-8")
        cls.service = SERVICE.read_text(encoding="utf-8")

    def test_template_creation_is_platform_admin_only(self):
        self.assertIn("@PreAuthorize(\"@ss.hasAnyRoles('super_admin')\")", self.controller)
        self.assertIn("saveAsTemplateFromSnapshot", self.controller)

    def test_template_governance_excludes_viewer_role(self):
        for endpoint in ("templatePage", "templateInfo", "templateStatus", "removeTemplate"):
            position = self.controller.index(endpoint)
            prefix = self.controller[max(0, position - 180):position]
            self.assertIn("super_admin','tenant_admin", prefix, endpoint)
            self.assertNotIn("viewer", prefix, endpoint)

    def test_template_queries_keep_tenant_boundary(self):
        self.assertGreaterEqual(self.service.count("tenant_id=? AND deleted=0"), 3)
        self.assertIn("WHERE id=? AND tenant_id=? AND deleted=0", self.service)
        self.assertIn("operator_id", self.service)


if __name__ == "__main__":
    unittest.main()
