"""持久化消息服务恢复时必须保留原数据和集群身份。"""

import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location('iot_kafka', Path(__file__).with_name('provision-iot-kafka.py'))
kafka = importlib.util.module_from_spec(spec)
spec.loader.exec_module(kafka)


class StorageRecoveryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.root_patch = patch.object(kafka, 'ROOT', self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)

    def test_missing_volume_cannot_be_silently_recreated(self):
        """Docker 自动创建同名空卷会掩盖数据丢失，恢复必须提前拒绝。"""
        with patch.object(kafka, 'run', return_value=subprocess.CompletedProcess([], 1, '', '')) as run:
            with self.assertRaisesRegex(RuntimeError, '持久卷不存在'):
                kafka.verify_storage('image-id', (), 'original-cluster')
            self.assertEqual(1, run.call_count)

    def test_provisioned_cluster_requires_existing_metadata(self):
        """已交付集群不能在空卷上重新格式化。"""
        (self.root / 'provisioned').touch()
        with patch.object(kafka, 'run', return_value=subprocess.CompletedProcess([], 0, '', '')):
            with self.assertRaisesRegex(RuntimeError, '缺少数据元信息'):
                kafka.verify_storage('image-id', (), 'original-cluster')

    def test_foreign_cluster_metadata_is_rejected(self):
        """恢复不能将另一个集群的数据误认为本批卷。"""
        with patch.object(kafka, 'run', return_value=subprocess.CompletedProcess([], 0, 'cluster.id=other\n', '')):
            with self.assertRaisesRegex(RuntimeError, '集群编号不匹配'):
                kafka.verify_storage('image-id', (), 'original-cluster')

    def test_original_metadata_and_unfinished_initialization_are_supported(self):
        """保留同一集群的恢复，以及首次格式化前失败后的接续能力。"""
        for metadata in ('cluster.id=original-cluster\nnode.id=1\n', ''):
            with self.subTest(metadata=metadata), patch.object(
                    kafka, 'run', return_value=subprocess.CompletedProcess([], 0, metadata, '')):
                kafka.verify_storage('image-id', (), 'original-cluster')


if __name__ == '__main__':
    unittest.main()
