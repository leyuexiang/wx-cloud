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

    def test_ticket_migration_stores_only_hash_and_expiry(self):
        sql = (ROOT / "002-publication-ticket.up.sql").read_text(encoding="utf-8")
        self.assertIn("wx_3d_scene_publication_ticket", sql)
        self.assertIn("token_hash", sql)
        self.assertIn("expires_time", sql)
        self.assertNotIn("token VARCHAR", sql)
        self.assertIn("status VARCHAR(16)", sql)

    def test_ticket_migration_is_after_publication_record(self):
        publication = (ROOT / "001-scene-publication.up.sql").read_text(encoding="utf-8")
        ticket = (ROOT / "002-publication-ticket.up.sql").read_text(encoding="utf-8")
        self.assertIn("wx_3d_scene_publication", publication)
        self.assertIn("publication_id", ticket)
        self.assertLess((ROOT / "001-scene-publication.up.sql").name, (ROOT / "002-publication-ticket.up.sql").name)

    def test_audit_migration_is_append_only_and_after_ticket(self):
        sql = (ROOT / "003-publication-audit.up.sql").read_text(encoding="utf-8")
        self.assertIn("wx_3d_scene_publication_audit", sql)
        self.assertIn("action VARCHAR(32)", sql)
        self.assertIn("ticket_id VARCHAR(64)", sql)
        self.assertNotIn("UPDATE wx_3d_scene_publication", sql)
        self.assertLess((ROOT / "002-publication-ticket.up.sql").name, (ROOT / "003-publication-audit.up.sql").name)


if __name__ == "__main__":
    unittest.main()
