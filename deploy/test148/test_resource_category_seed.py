"""模型、场景和两类模板分类增量检查，执行原始脚本并验证历史数据保护。"""

from pathlib import Path
import sqlite3
import unittest


MIGRATION = Path(__file__).resolve().parent / "migrations/dictionary01/002-resource-categories.up.sql"
EXPECTED = {
    "dim3d_model_category": [
        ("设备", "DEVICE_MODEL"), ("建筑", "BUILDING_MODEL"), ("药材", "HERB_MODEL"),
        ("仓储", "WAREHOUSE_MODEL"), ("车辆", "VEHICLE_MODEL"), ("植被", "VEGETATION_MODEL"),
    ],
    "dim3d_scene_category": [
        ("智慧园区", "PARK_SCENE"), ("智慧工厂", "FACTORY_SCENE"), ("智慧农业", "AGRICULTURE_SCENE"),
        ("智慧仓储", "WAREHOUSE_SCENE"), ("智慧城市", "CITY_SCENE"), ("数字展厅", "EXHIBITION_SCENE"),
    ],
    "dim3d_template_industry": [
        ("智慧园区", "PARK_INDUSTRY"), ("智慧工厂", "FACTORY_INDUSTRY"), ("智慧农业", "AGRICULTURE_INDUSTRY"),
        ("智慧仓储", "WAREHOUSE_INDUSTRY"), ("智慧城市", "CITY_INDUSTRY"), ("智慧水务", "WATER_INDUSTRY"),
    ],
    "dim3d_template_exhibition": [
        ("企业展厅", "CORPORATE_EXHIBITION"), ("产品展厅", "PRODUCT_EXHIBITION"), ("文化展馆", "CULTURE_EXHIBITION"),
        ("科普教育", "EDUCATION_EXHIBITION"), ("规划展示", "PLANNING_EXHIBITION"), ("综合展厅", "GENERAL_EXHIBITION"),
    ],
}


class ResourceCategorySeedTest(unittest.TestCase):
    def setUp(self):
        # 字段和默认值与图片分类检查一致，内存连接随每例关闭，实际数据库方言另行核验。
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
        self.database.executescript(MIGRATION.read_text(encoding="utf-8"))

    def test_four_types_and_twenty_four_items_are_enabled_in_order(self):
        self.apply_migration()
        self.assertEqual(self.database.execute("SELECT COUNT(*) FROM wx_system_dict_type WHERE status=0").fetchone(), (4,))
        for dictionary, expected in EXPECTED.items():
            rows = self.database.execute(
                "SELECT label,value,sort FROM wx_system_dict_data WHERE dict_type=? AND status=0 AND deleted=0 ORDER BY sort,id",
                (dictionary,),
            ).fetchall()
            self.assertEqual(rows, [(label, code, index * 10) for index, (label, code) in enumerate(expected, 1)])

    def test_repeat_execution_has_no_additional_writes(self):
        self.apply_migration()
        before = self.database.total_changes
        self.apply_migration()
        self.assertEqual(self.database.total_changes, before)

    def test_existing_label_keeps_code_id_and_metadata(self):
        self.database.execute("INSERT INTO wx_system_dict_type(name,type,status) VALUES ('原模型分类','dim3d_model_category',1)")
        self.database.execute("""INSERT INTO wx_system_dict_data(sort,label,value,dict_type,status,remark,creator)
            VALUES (99,'设备','legacy-device','dim3d_model_category',1,'原备注','原创建者')""")
        self.apply_migration()
        self.assertEqual(self.database.execute("SELECT id,value,sort,status,remark,creator FROM wx_system_dict_data WHERE id=1").fetchone(),
                         (1, "legacy-device", 10, 0, "原备注", "原创建者"))
        self.assertEqual(self.database.execute("SELECT name,status FROM wx_system_dict_type WHERE id=1").fetchone(), ("原模型分类", 0))

    def test_shared_labels_are_scoped_to_each_template_dictionary(self):
        # 两类业务可使用相同名称和不同编码，新增操作的去重边界必须包含字典类型。
        self.database.execute("INSERT INTO wx_system_dict_data(label,value,dict_type,status) VALUES ('智慧园区','legacy-park','dim3d_template_industry',1)")
        self.apply_migration()
        self.assertEqual(self.database.execute("SELECT value,status FROM wx_system_dict_data WHERE label='智慧园区' ORDER BY dict_type").fetchall(),
                         [("PARK_SCENE", 0), ("legacy-park", 0)])

    def test_other_dictionaries_and_custom_categories_keep_their_values(self):
        self.database.executemany("INSERT INTO wx_system_dict_data(label,value,dict_type,sort,status) VALUES (?,?,?,?,?)", [
            ("设备", "DEVICE_ICON", "dim3d_icon_category", 70, 1),
            ("自定义", "custom", "dim3d_scene_category", 99, 1),
            ("智慧园区", "model-custom", "dim3d_model_category", 77, 1),
        ])
        before = self.database.execute("SELECT * FROM wx_system_dict_data ORDER BY id").fetchall()
        self.apply_migration()
        self.assertEqual(self.database.execute("SELECT * FROM wx_system_dict_data WHERE id<=3 ORDER BY id").fetchall(), before)

    def test_deleted_history_stays_deleted(self):
        self.database.execute("INSERT INTO wx_system_dict_type(name,type,deleted) VALUES ('历史类型','dim3d_template_industry',1)")
        self.database.execute("INSERT INTO wx_system_dict_data(label,value,dict_type,deleted) VALUES ('智慧园区','PARK_INDUSTRY','dim3d_template_industry',1)")
        self.apply_migration()
        self.assertEqual(self.database.execute("SELECT deleted FROM wx_system_dict_type WHERE id=1").fetchone(), (1,))
        self.assertEqual(self.database.execute("SELECT deleted FROM wx_system_dict_data WHERE id=1").fetchone(), (1,))
        self.assertEqual(self.database.execute("SELECT COUNT(*) FROM wx_system_dict_data WHERE deleted=0").fetchone(), (24,))

    def test_existing_code_is_not_duplicated_or_renamed(self):
        # 编码已被其他名称占用应由执行前核查拦截，迁移本身也不能改写原行或插入重复编码。
        self.database.execute("INSERT INTO wx_system_dict_data(label,value,dict_type) VALUES ('既有设备名称','DEVICE_MODEL','dim3d_model_category')")
        self.apply_migration()
        self.assertEqual(self.database.execute("SELECT label,value FROM wx_system_dict_data WHERE dict_type='dim3d_model_category' AND value='DEVICE_MODEL'").fetchall(),
                         [("既有设备名称", "DEVICE_MODEL")])

    def test_failure_rolls_back_inserted_types(self):
        self.database.execute("DROP TABLE wx_system_dict_data")
        self.database.commit()
        with self.assertRaises(sqlite3.OperationalError):
            self.apply_migration()
        self.database.rollback()
        self.assertEqual(self.database.execute("SELECT COUNT(*) FROM wx_system_dict_type").fetchone(), (0,))


if __name__ == "__main__":
    unittest.main()
