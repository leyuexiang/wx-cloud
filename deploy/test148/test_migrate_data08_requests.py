"""历史请求回填的身份、期限、幂等与失败门禁验证。"""
import importlib.util
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from argparse import Namespace
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location('migrate_requests', Path(__file__).with_name('migrate-data08-requests.py'))
M = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)


class RequestBackfillTest(unittest.TestCase):
    def args(self, **values):
        result = Namespace(database='wx_editor_data08', retention_days=30, apply=False,
                           confirm='', backup_marker=None, expected_plan=None)
        for key, value in values.items(): setattr(result, key, value)
        return result

    def source(self, rid='10', request='request', tenant='2'):
        return [rid, tenant, '100', '1', request.encode().hex().upper(), 'a' * 64, '1800000000']

    def target(self, rid='90', request='request', tenant='2'):
        return [rid, tenant, '100', '1', request.encode().hex().upper(), 'a' * 64,
                '1800000000', '1800000100', '1800000000']

    def make_plan(self, source, target=(), args=None):
        with patch.object(M, 'read_metadata', return_value=(source, list(target))):
            return M.plan(args or self.args())

    def test_missing_row_uses_source_creation_time_and_never_updates_history(self):
        report, sql = self.make_plan([self.source()])
        self.assertEqual(1, report['requestReferencesToWrite'])
        self.assertTrue(report['readOnly'])
        self.assertIn('2592000', sql)
        self.assertIn('FROM_UNIXTIME(e.created_epoch+', sql)
        self.assertIn('FOR UPDATE', sql)
        self.assertIn('ROW_COUNT()=1', sql)
        self.assertNotIn('UPDATE wx_', sql)
        self.assertNotIn('DELETE FROM', sql)

    def test_existing_expired_request_is_preserved_and_repeat_plan_is_zero(self):
        target = self.target()
        target[7] = '1'
        report, sql = self.make_plan([self.source()], [target])
        self.assertEqual(0, report['requestReferencesToWrite'])
        self.assertNotIn('INSERT INTO wx_3d_scene_request', sql)

    def test_cross_tenant_request_does_not_satisfy_a_missing_reference(self):
        other = self.source(rid='11', tenant='3')
        report, _ = self.make_plan([self.source(), other], [self.target(tenant='3')])
        self.assertEqual(1, report['requestReferencesToWrite'])

    def test_hash_or_revision_mismatch_and_orphan_stop_the_plan(self):
        for index, value in [(5, 'b' * 64), (3, '2'), (4, 'missing'.encode().hex())]:
            with self.subTest(index=index):
                row = self.target(); row[index] = value
                with self.assertRaises(RuntimeError): self.make_plan([self.source()], [row])

    def test_primary_key_collision_cannot_overwrite_an_existing_request(self):
        source = [self.source(), self.source(rid='11', request='other')]
        with self.assertRaises(RuntimeError): self.make_plan(source, [self.target(rid='10', request='other')])

    def test_invalid_source_and_policy_stop_before_writing(self):
        for index, value in [(0, '-1'), (4, 'ff'), (5, 'invalid'), (6, 'NaN')]:
            row = self.source(); row[index] = value
            with self.subTest(index=index), self.assertRaises((RuntimeError, ValueError)):
                self.make_plan([row])
        for values in ({'database': 'wx_editor'}, {'retention_days': 0}, {'retention_days': 366}):
            with self.assertRaises(RuntimeError): self.make_plan([self.source()], args=self.args(**values))

    def test_metadata_drift_changes_reviewed_digest(self):
        before, _ = self.make_plan([self.source()])
        after, _ = self.make_plan([self.source(), self.source(rid='11', request='other')])
        self.assertNotEqual(before['planSha256'], after['planSha256'])

    def test_apply_requires_confirmation_matching_plan_backup_and_stopped_writer(self):
        report, sql = self.make_plan([self.source()])
        with patch.object(M.VERIFY, 'mysql') as mysql:
            with self.assertRaises(RuntimeError): M.apply(self.args(), report, sql)
            mysql.assert_not_called()
        with tempfile.TemporaryDirectory() as folder:
            marker = Path(folder) / 'marker.json'
            marker.write_text(json.dumps({'database': 'wx_editor_data08', 'backup': str(Path(folder)/'absent'),
                                          'sha256': 'a'*64}), encoding='utf-8')
            args = self.args(confirm='DATA08-REQUEST-BACKFILL', expected_plan=report['planSha256'], backup_marker=str(marker))
            with patch.object(M.VERIFY, 'mysql') as mysql, self.assertRaises(RuntimeError): M.apply(args, report, sql)
            mysql.assert_not_called()

    def test_valid_backup_still_cannot_write_while_writer_is_running(self):
        report, sql = self.make_plan([self.source()])
        with tempfile.TemporaryDirectory() as folder:
            backup = Path(folder) / 'backup.gz'; backup.write_bytes(b'unit-backup')
            marker = Path(folder) / 'marker.json'
            marker.write_text(json.dumps({'database': 'wx_editor_data08', 'backup': str(backup),
                               'sha256': hashlib.sha256(backup.read_bytes()).hexdigest()}), encoding='utf-8')
            args = self.args(confirm='DATA08-REQUEST-BACKFILL', expected_plan=report['planSha256'], backup_marker=str(marker))
            with patch.object(M, 'require_stopped', side_effect=RuntimeError('writer running')), patch.object(M.VERIFY, 'mysql') as mysql:
                with self.assertRaises(RuntimeError): M.apply(args, report, sql)
                mysql.assert_not_called()
            with patch.object(M, 'require_stopped'), patch.object(M.VERIFY, 'mysql') as mysql:
                applied = M.apply(args, report, sql)
                mysql.assert_called_once_with(args, sql)
                self.assertTrue(applied['databaseWrite'])
                self.assertFalse(applied['readOnly'])

    def test_apply_rejects_changed_digest_before_inspecting_backup(self):
        report, sql = self.make_plan([self.source()])
        args = self.args(confirm='DATA08-REQUEST-BACKFILL', expected_plan='0'*64, backup_marker='does-not-exist')
        with patch.object(M.VERIFY, 'mysql') as mysql, self.assertRaises(RuntimeError): M.apply(args, report, sql)
        mysql.assert_not_called()


if __name__ == '__main__': unittest.main()
