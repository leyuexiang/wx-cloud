"""常驻换包必须保留运行边界，不把隔离候选的权限配置带入原服务。"""

import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest


spec = importlib.util.spec_from_file_location('resident', Path(__file__).with_name('connect-resident-iot.py'))
resident = importlib.util.module_from_spec(spec)
spec.loader.exec_module(resident)


class ResidentConfigurationTest(unittest.TestCase):
    def original(self):
        return {'Image': 'sha256:original', 'Config': {'Image': 'moving-tag', 'User': '100:101',
                'Entrypoint': ['java', '-jar', '/opt/wx/app.jar'], 'Cmd': ['--server.port=48096'],
                'Env': ['EXISTING_SECRET=private'], 'Healthcheck': {'Test': ['CMD', 'health']},
                'Labels': {'com.docker.compose.project': 'existing', 'com.docker.compose.config-hash': 'old'}},
                'HostConfig': {'NetworkMode': 'host', 'ReadonlyRootfs': True, 'CapDrop': ['ALL'],
                               'Memory': 805306368, 'SecurityOpt': ['no-new-privileges:true'],
                               'Binds': ['/old.jar:/opt/wx/app.jar:ro,Z', '/logs:/app/logs:rw,Z']},
                'Mounts': [{'Type': 'bind', 'Source': '/old.jar', 'Destination': '/opt/wx/app.jar', 'RW': False}]}

    def test_keeps_security_secrets_health_and_old_snapshot_intact(self):
        original = self.original()
        before = copy.deepcopy(original)
        updated = resident.request_for(original, '/new.jar', '/own-credentials', True)
        self.assertEqual(before, original)
        for key in ('User', 'Env', 'Entrypoint', 'Healthcheck'):
            self.assertEqual(before['Config'][key], updated[key])
        for key in ('ReadonlyRootfs', 'CapDrop', 'Memory', 'SecurityOpt'):
            self.assertEqual(before['HostConfig'][key], updated['HostConfig'][key])
        self.assertEqual('sha256:original', updated['Image'])
        self.assertIn('/logs:/app/logs:rw,Z', updated['HostConfig']['Binds'])
        self.assertIn('/new.jar:/opt/wx/app.jar:ro,Z', updated['HostConfig']['Binds'])
        self.assertIn('/own-credentials:/opt/wx/kafka:ro,Z', updated['HostConfig']['Binds'])
        self.assertEqual('--server.port=48096', updated['Cmd'][0])

    def test_entrypoint_only_container_can_append_configuration(self):
        original = self.original()
        original['Config']['Cmd'] = None
        updated = resident.request_for(original, '/new.jar', '/own-credentials')
        self.assertEqual(['--spring.config.additional-location=file:/opt/wx/kafka/application.properties'], updated['Cmd'])
        self.assertEqual('unless-stopped', updated['HostConfig']['RestartPolicy']['Name'])

    def test_rejects_unverified_network_and_existing_kafka_mount(self):
        original = self.original()
        original['HostConfig']['NetworkMode'] = 'bridge'
        with self.assertRaises(ValueError):
            resident.request_for(original, '/new.jar', '/own-credentials')
        original = self.original()
        original['Mounts'].append({'Destination': '/opt/wx/kafka'})
        with self.assertRaises(ValueError):
            resident.request_for(original, '/new.jar', '/own-credentials')

    def test_dimensions_rejects_default_string_codec(self):
        """三维服务即使处于健康状态，默认字符串编解码也无法送出订阅确认。"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            properties = root / 'application.properties'
            properties.write_text(
                'spring.kafka.bootstrap-servers=127.0.0.1:19092\n'
                'spring.kafka.properties.security.protocol=SSL\n'
                'spring.kafka.properties.ssl.endpoint.identification.algorithm=https\n'
                'spring.kafka.listener.auto-startup=false\n'
                'wx.iot-state.listener-auto-startup=true\n'
                'wx.websocket.sender-kafka.auto-startup=true\n', encoding='utf-8')
            resident.validate_transport(root, 'ingest')
            with self.assertRaisesRegex(ValueError, '编解码'):
                resident.validate_transport(root, 'dimensions')


if __name__ == '__main__':
    unittest.main()
