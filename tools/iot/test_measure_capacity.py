"""校验容量报告的分位数与订阅分批，防止空样本或丢项被当成通过。"""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import Mock, MagicMock, patch
import tempfile

spec = importlib.util.spec_from_file_location('capacity', Path(__file__).with_name('measure-capacity.py'))
capacity = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capacity)


class CapacityMetricsTest(unittest.TestCase):
    def test_isolated_publisher_uses_explicit_credential_and_returns_ack(self):
        # 模拟传输仅验证独立发送函数的依赖和确认回执；真实进程/网络另行验收。
        protocol = capacity.runpy.run_path(str(Path(__file__).with_name('probe-mqtt.py')))
        protocol['wait_ack'] = Mock(side_effect=[b'\x00\x00', b'\x00\x01'])
        protocol['connect_packet'] = Mock(wraps=protocol['connect_packet'])
        transport, tls, output = MagicMock(), MagicMock(), Mock()
        sent = capacity.SentClock(1)
        signal = Mock()
        signal.is_set.return_value = False
        with patch.object(capacity.runpy, 'run_path', return_value=protocol), \
             patch.object(capacity.ssl, 'create_default_context', return_value=tls), \
             patch.object(capacity.socket, 'create_connection', return_value=transport):
            capacity.publish_messages({'mqttCa': 'test-ca', 'mqttHost': '127.0.0.1', 'mqttPort': 1883,
                'mqttUsername': 'test', 'topic': 'test'}, 'only-test', {'tag': 'test'},
                [{'deviceId': 'device'}], 1, sent, signal, output)
        result = output.send.call_args.args[0]
        self.assertNotIn('failureType', result)
        self.assertEqual(result['messagesAcknowledged'], 1)
        self.assertEqual(len(sent), 1)
        self.assertEqual(protocol['connect_packet'].call_args.args[-1], 'only-test')

    def test_cleanup_resumes_after_publication_already_revoked(self):
        # 撤销成功后若进程中断，重试应继续清理，不重复调用非幂等撤销接口。
        api = Mock()
        api.call.side_effect = [{'status': 'REVOKED'}, {'list': [], 'total': 0}, True]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'state.json'
            capacity.remember(path, {'tag': 'capacity-test', 'sceneId': 'scene', 'publicationId': 'pub'})
            report = capacity.cleanup(api, {'connectorId': 'test'}, path)
        self.assertTrue(report['sceneRemoved'])
        self.assertFalse(any('/revoke' in call.args[0] for call in api.call.call_args_list))

    def test_publication_scope_never_contains_draft_scene(self):
        state = {'sceneId': 'scene', 'publicationId': 'publication'}
        self.assertEqual(capacity.subscription_scope(state), {'sceneId': 'scene'})
        self.assertEqual(capacity.subscription_scope(state, {'token': 'test-only'}),
            {'publicationId': 'publication', 'publicationTicket': 'test-only'})

    def test_packet_id_wraps_without_colliding_with_inflight_window(self):
        identifiers = [capacity.packet_id(i) for i in range(65525, 65565)]
        self.assertEqual(len(set(identifiers)), 40)
        self.assertEqual(capacity.packet_id(65535), b'\xff\xff')
        self.assertEqual(capacity.packet_id(65536), b'\x00\x01')

    def test_bounded_metrics_preserve_duplicates_and_conservative_percentiles(self):
        metric = capacity.DeliveryMetrics(120000)
        self.assertTrue(metric.record(120000, .125))
        self.assertFalse(metric.record(120000, 900))
        self.assertEqual(metric.received, 1)
        self.assertEqual(metric.duplicates, 1)
        self.assertEqual(metric.percentiles(), {'p50': 1, 'p95': 1, 'p99': 1})
        self.assertLess(metric.storage_bytes(), 370000)
        with self.assertRaises(ValueError):
            metric.record(120001, 1)

    def test_histogram_tail_does_not_hide_overflow(self):
        metric = capacity.DeliveryMetrics(20)
        self.assertEqual(metric.percentiles()['p95'], None)
        for number in range(1, 21):
            metric.record(number, 2.01 if number < 19 else 70000.25)
        self.assertEqual(metric.percentiles(), {'p50': 3, 'p95': 70001, 'p99': 70001})

    def test_sent_clock_is_bounded_and_missing_is_not_zero(self):
        clock = capacity.SentClock(120000)
        self.assertIsNone(clock.get(1))
        clock[1] = 42
        self.assertEqual(clock.get(1), 42)
        # 统一事件数值可能以 1.0 返回，整数数值同义；布尔值和小数不充当序号。
        self.assertEqual(clock.get(1.0), 42)
        self.assertIsNone(clock.get(True))
        self.assertIsNone(clock.get(1.5))
        self.assertEqual(len(clock), 1)
        self.assertLess(clock.storage_bytes(), 1000000)

    def test_empty_samples_do_not_claim_zero_latency(self):
        self.assertEqual(capacity.percentiles([]), {'p50': None, 'p95': None, 'p99': None})

    def test_nearest_rank_uses_tail_and_keeps_input_order(self):
        values = list(range(100, 0, -1))
        self.assertEqual(capacity.percentiles(values), {'p50': 50, 'p95': 95, 'p99': 99})
        self.assertEqual(values[0], 100)

    def test_thousand_bindings_cover_each_device_once(self):
        rows = list(range(1000))
        groups = capacity.batches(rows)
        self.assertEqual(len(groups), 50)
        self.assertTrue(all(len(group) <= 20 for group in groups))
        self.assertEqual([row for group in groups for row in group], rows)


if __name__ == '__main__':
    unittest.main()
