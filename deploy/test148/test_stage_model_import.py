"""候选配置边界：独立库必须属于同一演练批次，拒绝碰到业务库及歧义地址。"""

import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("model_stage", Path(__file__).with_name("stage-model-import.py"))
stage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stage)


class CandidateConfigurationTest(unittest.TestCase):
    def test_conversion_candidate_rejects_external_or_ambiguous_converter(self):
        """隔离转换只能调用本机已验证的接口，拒绝外部地址、凭据及查询参数。"""
        for endpoint in ("http://example.com/convert", "http://127.0.0.1:32794/convert?x=1",
                         "http://user:pass@127.0.0.1:32794/convert", "http://127.0.0.1:32794/other"):
            with self.subTest(endpoint=endpoint), self.assertRaises(ValueError):
                stage.candidate_runtime("main", endpoint)

    def test_conversion_candidate_enables_worker_without_enabling_publication(self):
        """转换候选和接收候选端口隔离，兼容标识为空时不能发布供运行端消费。"""
        source = stage.candidate_runtime("main", None)
        conversion = stage.candidate_runtime("main", "http://127.0.0.1:32794/convert")
        self.assertNotEqual(source["port"], conversion["port"])
        self.assertNotEqual(source["phase"], conversion["phase"])
        self.assertIn("--dim3d.model-import.worker-enabled=false", source["arguments"])
        self.assertIn("--dim3d.model-import.worker-enabled=true", conversion["arguments"])
        self.assertIn("--dim3d.model-import.app-compatibility=", conversion["arguments"])

    def test_changes_only_database_and_preserves_other_runtime_settings(self):
        text = "url: jdbc:mysql://127.0.0.1:3306/wx_editor?useSSL=false\npassword: ${WX_DB_PASSWORD}\nserver:\n  port: 48092\n"
        result = stage.candidate_config(text, "wx_editor", "wx_model02_rehearsal_main_1234abcd")
        self.assertEqual(result, text.replace("/wx_editor?", "/wx_model02_rehearsal_main_1234abcd?"))

    def test_refuses_business_database_and_cross_target_clone(self):
        text = "url: jdbc:mysql://localhost:3306/wx_editor?useSSL=false"
        for clone in ("wx_editor", "wx_model02_rehearsal_data08_1234abcd", "wx_model02_rehearsal_main_1234abcd;DROP"):
            with self.subTest(clone=clone), self.assertRaises(ValueError):
                stage.candidate_config(text, "wx_editor", clone)

    def test_refuses_missing_or_multiple_datasource_matches(self):
        text = "url: jdbc:mysql://localhost:3306/wx_editor?useSSL=false\n"
        for value in ("url: jdbc:mysql://localhost:3306/other?x=1", text * 2):
            with self.subTest(text=value), self.assertRaises(ValueError):
                stage.candidate_config(value, "wx_editor", "wx_model02_rehearsal_main_1234abcd")


if __name__ == "__main__":
    unittest.main()
