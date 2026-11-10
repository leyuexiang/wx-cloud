from pathlib import Path
import unittest


SCRIPT = Path(__file__).with_name("verify-data09-db.py")


class Data09ReadOnlyVerifierTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SCRIPT.read_text(encoding="utf-8")

    def test_verifier_is_read_only_and_rejects_shared_database(self):
        self.assertIn('EXPECTED_DATABASE = "wx_editor_data08"', self.source)
        self.assertIn('if args.database == "wx_editor":', self.source)
        for keyword in ("INSERT INTO", "UPDATE ", "DELETE FROM", "CREATE TABLE", "DROP TABLE"):
            self.assertNotIn(keyword, self.source)

    def test_verifier_checks_publication_and_ticket_tables(self):
        self.assertIn("wx_3d_scene_publication", self.source)
        self.assertIn("wx_3d_scene_publication_ticket", self.source)
        self.assertIn("information_schema.COLUMNS", self.source)


if __name__ == "__main__":
    unittest.main()
