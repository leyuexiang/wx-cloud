import importlib.util
import pathlib
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("verify-data08-db.py")
SPEC = importlib.util.spec_from_file_location("verify_data08_db", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
content_bytes = MODULE.content_bytes
content_hash = MODULE.content_hash


class Data08DigestContractTest(unittest.TestCase):
    def test_java_framing_is_stable_and_utf8_aware(self):
        packages = ["园区".encode(), b"{}", b"[]", b"\xe2\x9c\x93"]
        self.assertEqual(13, content_bytes(packages))
        self.assertEqual(content_hash(1, packages), content_hash(1, packages))
        self.assertNotEqual(content_hash(1, [b"ab", b"c", b"d", b"e"]), content_hash(1, [b"a", b"bc", b"d", b"e"]))
        self.assertNotEqual(content_hash(1, packages), content_hash(2, packages))


if __name__ == "__main__":
    unittest.main()
