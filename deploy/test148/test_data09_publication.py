from pathlib import Path
import unittest


ROOT = Path(__file__).parent / "migrations" / "data09"


class Data09PublicationMigrationTest(unittest.TestCase):
    def test_up_defines_immutable_publication_fields_and_indexes(self):
        sql = (ROOT / "001-scene-publication.up.sql").read_text(encoding="utf-8")
        for token in (
            "wx_3d_scene_publication",
            "publication_id",
            "revision_no",
            "source_hash",
            "dependency_snapshot",
            "request_id",
            "current_flag",
            "uk_scene_publication_request",
            "idx_scene_publication_current",
        ):
            self.assertIn(token, sql)

    def test_down_only_removes_new_table(self):
        sql = (ROOT / "001-scene-publication.down.sql").read_text(encoding="utf-8")
        self.assertEqual("DROP TABLE IF EXISTS wx_3d_scene_publication;", sql.strip())


if __name__ == "__main__":
    unittest.main()
