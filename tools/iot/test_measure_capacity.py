"""校验容量报告的分位数与订阅分批，防止空样本或丢项被当成通过。"""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import Mock, MagicMock, patch
import tempfile
import multiprocessing
import os
import time
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor


def write_shared_sample(buffers):
    """真实子进程修改样本，父进程必须读回同一固定缓冲区。"""
    metric = capacity.DeliveryMetrics(20, buffers)
    metric.record(1, 2.01)
    metric.record(1, 99)
    metric.renewal_acks = 3


def crash_receiver(*args):
    """模拟准备前异常退出，避免依赖真实业务服务。"""
    os._exit(7)


def exit_after_ready(*args):
    args[-1].send({'kind': 'ready'})
    args[-3].wait(10)
    os._exit(7)


def omit_final_result(*args):
    args[-1].send({'kind': 'ready'})
    args[-4].wait(10)
    args[-1].close()


def ignore_stop(*args):
    args[-1].send({'kind': 'ready'})
    time.sleep(10)


def exercise_setup_gate(*args):
    config, assignments, gate, output = args[1], args[4], args[-2], args[-1]
    def create(assignment):
        with gate:
            with config['lock']:
                config['active'].value += 1
                config['maximum'].value = max(config['maximum'].value, config['active'].value)
            time.sleep(.03)
            with config['lock']:config['active'].value -= 1
    with ThreadPoolExecutor(max_workers=5) as pool:list(pool.map(create, assignments))
    output.send({'kind': 'ready'})
    args[-4].wait(10)
    output.send({'kind': 'final', 'ready': True, 'cpuSeconds': 0})
    output.close()


class ProcessReceiverTest(unittest.TestCase):
    def test_shared_metrics_cross_process_preserve_duplicate_and_rounding(self):
        from capacity_receivers import SharedMetricBuffers
        context = multiprocessing.get_context('spawn')
        buffers = SharedMetricBuffers(context, 20)
        process = context.Process(target=write_shared_sample, args=(buffers,))
        process.start(); process.join(10)
        if process.is_alive():
            process.terminate(); process.join(3)
            self.fail('测试子进程未按期退出')
        self.assertEqual(process.exitcode, 0)
        metric = capacity.DeliveryMetrics(20, buffers)
        self.assertEqual((metric.received, metric.duplicates, metric.renewal_acks), (1, 1, 3))
        self.assertEqual(metric.percentiles(), {'p50': 3, 'p95': 3, 'p99': 3})

    def test_process_partition_is_bounded_and_covers_each_client(self):
        from capacity_receivers import partition_clients
        self.assertEqual(partition_clients(50, 5), [list(range(i, 50, 5)) for i in range(5)])
        for processes in (0, 6, 51):
            with self.assertRaises(ValueError):partition_clients(50, processes)
        with self.assertRaises(ValueError):partition_clients(2, 3)

    def test_shared_errors_are_bounded_and_hide_unknown_exception_text(self):
        from capacity_receivers import SharedErrors
        errors = SharedErrors(multiprocessing.get_context('spawn'))
        for _ in range(10000):errors.append('private-token-in-exception')
        errors.append('unexpected-scope')
        self.assertEqual(errors.values(), ['unexpected-scope', 'transport-error'])
        self.assertLess(errors.storage_bytes(), 16)

    def test_setup_process_crash_reaps_children_and_never_reports_ready(self):
        from capacity_receivers import ProcessReceiverGroup
        context = multiprocessing.get_context('spawn')
        group = ProcessReceiverGroup(context, 1, 1, 20, capacity.DeliveryMetrics,
            setup_timeout=3, shutdown_timeout=3, worker_target=crash_receiver)
        clock = capacity.SentClock(20)
        with self.assertRaises(RuntimeError):
            group.start({}, {}, [], [{'number': 0, 'userId': '1', 'token': 'test-only'}], clock)
        self.assertTrue(group.errors())
        self.assertTrue(all(not process.is_alive() for process in group.processes))

    def test_measure_setup_failure_still_revokes_all_tickets_and_viewer_logins(self):
        api, first, second = Mock(), Mock(), Mock()
        first.user_id, second.user_id = '1', '2'
        api.call.side_effect = [
            {'ticketId': 'TKT-1', 'token': 'test-one'},
            {'ticketId': 'TKT-2', 'token': 'test-two'}, True, True]
        group = Mock()
        group.start.side_effect = RuntimeError('准备阶段失败')
        group.errors.return_value = ['receiver-setup-failed']
        with patch.object(capacity, 'Api', side_effect=[first, second]), \
             patch('capacity_receivers.ProcessReceiverGroup', return_value=group):
            result = capacity.measure(api, {}, {'viewers': [{'username': 'one'}, {'username': 'two'}]},
                {'devices': [{'deviceId': 'one'}], 'sceneId': 'scene', 'publicationId': 'pub'},
                1, 2, 1, 'publication', 2)
        self.assertFalse(result['passed'])
        self.assertEqual(result['messagesAttempted'], 0)
        self.assertEqual(result['ticketsRevoked'], 2)
        self.assertEqual(result['viewerSessionsLoggedOut'], 2)
        group.stop.assert_called_once(); first.logout.assert_called_once(); second.logout.assert_called_once()
        self.assertEqual([call.args[0] for call in api.call.call_args_list][-2:],
            ['/dim3d/scene/publication/ticket/TKT-1', '/dim3d/scene/publication/ticket/TKT-2'])

    def test_publisher_start_boundaries_close_pipes_and_revoke_tickets(self):
        # 接收计时确认或发送进程启动失败时，尚未进入发送循环也必须关闭两端管道。
        for stage in ('measurement', 'publisher-start'):
            with self.subTest(stage=stage):
                real_context = multiprocessing.get_context('spawn')
                context = Mock(wraps=real_context)
                incoming, outgoing, publisher = Mock(), Mock(), Mock()
                context.Pipe.return_value = (incoming, outgoing)
                context.Process.return_value = publisher
                api = Mock(user_id='2', token='test-only')
                api.call.side_effect = [{'ticketId': 'TKT-1', 'token': 'test-one'}, True]
                group = Mock()
                group.errors.return_value = []
                group.start.return_value = [Mock(metrics=Mock(received=0))]
                if stage == 'measurement':group.begin_measurement.side_effect = RuntimeError('计时准备失败')
                else:publisher.start.side_effect = RuntimeError('发送启动失败')
                with patch.object(capacity.multiprocessing, 'get_context', return_value=context), \
                     patch('capacity_receivers.ProcessReceiverGroup', return_value=group):
                    result = capacity.measure(api, {}, {'mqttPassword': 'test-only'},
                        {'devices': [{'deviceId': 'one'}], 'sceneId': 'scene', 'publicationId': 'pub'},
                        1, 1, 1, 'publication', 1)
                self.assertFalse(result['passed'])
                self.assertEqual(result['ticketsRevoked'], 1)
                incoming.close.assert_called_once(); outgoing.close.assert_called_once()
                publisher.join.assert_not_called(); group.stop.assert_called_once()

    def test_process_exit_after_ready_is_detected_during_load(self):
        from capacity_receivers import ProcessReceiverGroup
        context = multiprocessing.get_context('spawn')
        group = ProcessReceiverGroup(context, 1, 1, 1, capacity.DeliveryMetrics,
            setup_timeout=5, shutdown_timeout=1, worker_target=exit_after_ready)
        group.start({}, {}, [], [{'number': 0, 'userId': '1', 'token': 'test-only'}], capacity.SentClock(1))
        group.measuring.set(); group.processes[0].join(5)
        try:
            self.assertFalse(group.check_health())
            self.assertIn('receiver-process-exited', group.errors())
        finally:group.stop()

    def test_successful_exit_without_final_result_cannot_pass(self):
        from capacity_receivers import ProcessReceiverGroup
        context = multiprocessing.get_context('spawn')
        group = ProcessReceiverGroup(context, 1, 1, 1, capacity.DeliveryMetrics,
            setup_timeout=5, shutdown_timeout=2, worker_target=omit_final_result)
        group.start({}, {}, [], [{'number': 0, 'userId': '1', 'token': 'test-only'}], capacity.SentClock(1))
        group.stop()
        self.assertIn('receiver-result-missing', group.errors())
        self.assertEqual(group.stop(), 0)

    def test_shutdown_timeout_terminates_only_owned_child_and_is_failure(self):
        from capacity_receivers import ProcessReceiverGroup
        context = multiprocessing.get_context('spawn')
        group = ProcessReceiverGroup(context, 1, 1, 1, capacity.DeliveryMetrics,
            setup_timeout=5, shutdown_timeout=.1, worker_target=ignore_stop)
        group.start({}, {}, [], [{'number': 0, 'userId': '1', 'token': 'test-only'}], capacity.SentClock(1))
        begin = time.monotonic(); group.stop()
        self.assertLess(time.monotonic() - begin, 5)
        self.assertIn('receiver-shutdown-timeout', group.errors())
        self.assertFalse(group.processes[0].is_alive())

    def test_all_receiver_processes_share_five_setup_slots(self):
        from capacity_receivers import ProcessReceiverGroup
        context = multiprocessing.get_context('spawn')
        config = {'active': context.RawValue('I', 0), 'maximum': context.RawValue('I', 0), 'lock': context.Lock()}
        group = ProcessReceiverGroup(context, 25, 5, 1, capacity.DeliveryMetrics,
            setup_timeout=10, shutdown_timeout=3, worker_target=exercise_setup_gate)
        assignments = [{'number': i, 'userId': str(i), 'token': 'test-only'} for i in range(25)]
        group.start(config, {}, [], assignments, capacity.SentClock(1)); group.stop()
        self.assertEqual(group.errors(), [])
        self.assertGreater(config['maximum'].value, 1)
        self.assertLessEqual(config['maximum'].value, 5)

spec = importlib.util.spec_from_file_location('capacity', Path(__file__).with_name('measure-capacity.py'))
capacity = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capacity)


class CapacityMetricsTest(unittest.TestCase):
    def test_management_api_dynamic_import_from_other_directory_stays_compatible(self):
        # 历史夹具从外部目录用 spec 加载 API，不能要求新增接收模块已在调用方搜索路径中。
        source = "import importlib.util; s=importlib.util.spec_from_file_location('c'," + repr(str(Path(capacity.__file__).resolve())) + ");m=importlib.util.module_from_spec(s);s.loader.exec_module(m);assert hasattr(m,'Api')"
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run([sys.executable, '-c', source], cwd=directory, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr.decode(errors='replace'))

    def test_distinct_viewer_selection_rejects_duplicate_names_and_short_lists(self):
        with self.assertRaises(ValueError):
            capacity.viewer_credentials({'viewers': [{'username': 'same'}, {'username': 'same'}]}, 2)
        with self.assertRaises(ValueError):
            capacity.viewer_credentials({'viewers': [{'username': 'only'}]}, 2)
        self.assertEqual(capacity.viewer_credentials({}, 2), [])

    def test_viewer_login_rejects_same_server_identity_and_tracks_partial_sessions(self):
        opened = []
        first, second = Mock(), Mock()
        first.user_id = second.user_id = 'same-id'
        with patch.object(capacity, 'Api', side_effect=[first, second]):
            with self.assertRaises(ValueError):
                capacity.open_viewer_apis({}, {'viewers': [{'username': 'first'}, {'username': 'second'}]}, 2, opened)
        # 调用者 finally 持有全部已建立会话，第二个重复身份也不能遗漏注销。
        self.assertEqual(opened, [first, second])

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
