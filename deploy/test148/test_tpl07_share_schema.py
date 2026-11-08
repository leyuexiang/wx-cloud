"""TPL-07-05-02 跨租户共享授权结构门禁。"""
from pathlib import Path
import unittest


SQL = Path(__file__).with_name("migrations") / "tpl01" / "004-template-share.up.sql"


class Tpl07ShareSchemaTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = SQL.read_text(encoding="utf-8").lower()

    def test_share_is_independent_and_version_bound(self):
        self.assertIn("wx_3d_scene_template_share", self.sql)
        for column in ("owner_tenant_id", "target_tenant_id", "template_id", "template_scene_id", "version_no"):
            self.assertIn(column, self.sql)

    def test_revocation_keeps_history(self):
        self.assertIn("revoked_time", self.sql)
        self.assertIn("revoked_by", self.sql)
        self.assertIn("status varchar(16)", self.sql)
        self.assertIn("idx_template_share_history", self.sql)

    def test_lookup_is_target_tenant_scoped(self):
        self.assertIn("idx_template_share_lookup (target_tenant_id,status,template_id,version_no)", self.sql)


if __name__ == "__main__":
    unittest.main()
