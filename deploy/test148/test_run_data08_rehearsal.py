import importlib.util
import pathlib
import unittest
from argparse import Namespace


MODULE_PATH = pathlib.Path(__file__).with_name("run-data08-rehearsal.py")
SPEC = importlib.util.spec_from_file_location("run_data08_rehearsal", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class Data08RehearsalPlanTest(unittest.TestCase):
    def test_plan_has_schema_backfill_and_roundtrip_order(self):
        args = Namespace(host="127.0.0.1", port=3306, user="root", database="wx_editor",
                         password_env="WX_MYSQL_ROOT_PASSWORD", mysql_container="mysql8")
        plan = MODULE.commands(args)
        self.assertEqual(3, len(plan))
        self.assertTrue(plan[0][1].endswith("apply-data08-schema.py"))
        self.assertTrue(plan[1][1].endswith("migrate-data08-db.py"))
        self.assertTrue(plan[2][1].endswith("verify-data08-roundtrip.py"))
        self.assertIn("--mysql-container", plan[0])


if __name__ == "__main__":
    unittest.main()
