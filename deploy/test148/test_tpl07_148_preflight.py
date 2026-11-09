"""TPL-07-05-05 148 只读结构预检准备门禁。"""
from pathlib import Path
import unittest


ROOT = Path(__file__).parent


class Tpl07Test148PreflightTest(unittest.TestCase):
    def test_prepare_script_defaults_to_dedicated_database_and_read_only(self):
        source = (ROOT / "prepare-template-test-db.py").read_text(encoding="utf-8")
        self.assertIn('DATABASE = "wx_editor_tpl04_test"', source)
        self.assertIn('parser.add_argument("--apply", action="store_true"', source)
        self.assertIn('if args.apply:', source)
        self.assertIn('if query("SELECT DATABASE();") != [DATABASE]:', source)

    def test_template_migration_chain_is_explicit(self):
        readme = (ROOT / "migrations" / "tpl01" / "README.md").read_text(encoding="utf-8")
        self.assertIn("prepare-template-test-db.py", readme)
        self.assertIn("--apply", readme)
        self.assertIn("wx_editor_tpl04_test", readme)
        self.assertIn("现网常驻业务库为 `wx_editor`", readme)
        self.assertIn("004-template-share.up.sql", readme)

    def test_gateway_preflight_port_is_not_frontend_root(self):
        config = (ROOT / "config" / "dimensions.yaml").read_text(encoding="utf-8")
        self.assertIn("base-url: http://192.168.10.148:12522", config)


if __name__ == "__main__":
    unittest.main()
