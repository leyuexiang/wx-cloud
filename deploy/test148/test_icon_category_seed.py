"""图片分类增量的数据行为检查；使用内存数据库，不连接或修改真实业务库。"""

from pathlib import Path
import sqlite3
import unittest


ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "deploy/test148/migrations/dictionary01/001-icon-category.up.sql"


class IconCategorySeedTest(unittest.TestCase):
    """执行实际迁移语句验证分类数据；正式 MySQL 方言验收须在隔离库另行执行。"""

    def setUp(self):
        # 只建本次数据增量需要的字典字段，类型及非空约束取自现有数据库基线。
        self.database = sqlite3.connect(":memory:")
        self.addCleanup(self.database.close)
        self.database.executescript("""
            CREATE TABLE wx_system_dict_type (
                id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL DEFAULT '',
                type TEXT NOT NULL DEFAULT '', status INTEGER NOT NULL DEFAULT 0,
                remark TEXT, creator TEXT DEFAULT '', updater TEXT DEFAULT '', deleted INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE wx_system_dict_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT, sort INTEGER NOT NULL DEFAULT 0,
                label TEXT NOT NULL DEFAULT '', value TEXT NOT NULL DEFAULT '', dict_type TEXT NOT NULL DEFAULT '',
                status INTEGER NOT NULL DEFAULT 0, remark TEXT, creator TEXT DEFAULT '', updater TEXT DEFAULT '',
                deleted INTEGER NOT NULL DEFAULT 0
            );
        """)

    def apply_migration(self):
        """直接执行待交付 SQL，避免把实现复制到测试中造成虚假的幂等证明。"""
        self.database.executescript(MIGRATION.read_text(encoding="utf-8"))

    def test_fresh_database_has_four_enabled_categories_in_screenshot_order(self):
        self.apply_migration()
        rows = self.database.execute(
            "SELECT label,value FROM wx_system_dict_data "
            "WHERE dict_type='dim3d_icon_category' AND status=0 AND deleted=0 ORDER BY sort"
        ).fetchall()
        self.assertEqual(rows, [("设备", "DEVICE_ICON"), ("建筑", "BUILDING_ICON"),
                                ("药材", "HERB_ICON"), ("仓储", "WAREHOUSE_ICON")])
        self.assertEqual(self.database.execute(
            "SELECT name,status FROM wx_system_dict_type WHERE type='dim3d_icon_category' AND deleted=0"
        ).fetchall(), [("图片分类", 0)])

    def test_repeat_execution_does_not_insert_or_update_unchanged_rows(self):
        self.apply_migration()
        before = self.database.execute("SELECT * FROM wx_system_dict_data ORDER BY id").fetchall()
        changes = self.database.total_changes
        self.apply_migration()
        self.assertEqual(self.database.execute("SELECT * FROM wx_system_dict_data ORDER BY id").fetchall(), before)
        self.assertEqual(self.database.total_changes, changes)

    def test_existing_label_keeps_its_business_code_and_metadata(self):
        # 历史编码可能已被图片引用，启用和重排分类不能替换该编码或重建其主键。
        self.database.execute("INSERT INTO wx_system_dict_type(name,type,status) VALUES (?,?,?)",
                              ("原图片分类名称", "dim3d_icon_category", 1))
        self.database.execute(
            "INSERT INTO wx_system_dict_data(sort,label,value,dict_type,status,remark,creator) VALUES (?,?,?,?,?,?,?)",
            (99, "设备", "legacy-device", "dim3d_icon_category", 1, "原备注", "原创建者"),
        )
        self.apply_migration()
        self.assertEqual(self.database.execute(
            "SELECT id,value,sort,status,remark,creator FROM wx_system_dict_data WHERE label='设备'"
        ).fetchall(), [(1, "legacy-device", 10, 0, "原备注", "原创建者")])
        self.assertEqual(self.database.execute("SELECT name,status FROM wx_system_dict_type").fetchall(),
                         [("原图片分类名称", 0)])

    def test_other_dictionary_and_additional_icon_categories_are_unchanged(self):
        self.database.execute(
            "INSERT INTO wx_system_dict_data(label,value,dict_type,status) VALUES (?,?,?,?)",
            ("模型分类", "DEVICE_ICON", "dim3d_model_category", 1),
        )
        self.database.execute(
            "INSERT INTO wx_system_dict_data(label,value,dict_type,sort) VALUES (?,?,?,?)",
            ("原有其他类", "custom", "dim3d_icon_category", 70),
        )
        before = self.database.execute("SELECT * FROM wx_system_dict_data ORDER BY id").fetchall()
        self.apply_migration()
        self.assertEqual(self.database.execute("SELECT * FROM wx_system_dict_data WHERE id IN (1,2) ORDER BY id")
                         .fetchall(), before)

    def test_soft_deleted_history_is_preserved_and_not_reactivated(self):
        self.database.execute(
            "INSERT INTO wx_system_dict_type(name,type,status,deleted) VALUES ('旧分类','dim3d_icon_category',1,1)"
        )
        self.database.execute(
            "INSERT INTO wx_system_dict_data(label,value,dict_type,deleted) "
            "VALUES ('设备','DEVICE_ICON','dim3d_icon_category',1)"
        )
        self.apply_migration()
        self.assertEqual(self.database.execute("SELECT deleted FROM wx_system_dict_data WHERE id=1").fetchone(), (1,))
        self.assertEqual(self.database.execute("SELECT deleted,status FROM wx_system_dict_type WHERE id=1")
                         .fetchone(), (1, 1))
        self.assertEqual(self.database.execute("SELECT COUNT(*) FROM wx_system_dict_data WHERE deleted=0")
                         .fetchone(), (4,))

    def test_failure_can_rollback_type_insert_without_leaving_partial_data(self):
        # 注入数据表不可用故障，按执行约束回滚，确保前一条类型插入不遗留部分结果。
        self.database.execute("DROP TABLE wx_system_dict_data")
        self.database.commit()
        with self.assertRaises(sqlite3.OperationalError):
            self.apply_migration()
        self.database.rollback()
        self.assertEqual(self.database.execute("SELECT COUNT(*) FROM wx_system_dict_type").fetchone(), (0,))


if __name__ == "__main__":
    unittest.main()
