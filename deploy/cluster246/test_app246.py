"""双机部署配置回归：只验证本批项目、回环通信及用途受限的节点身份。"""
import unittest
from app246 import authorized_key, iot_config, compose_config


class App246Test(unittest.TestCase):
    def test_tunnel_key_cannot_run_commands_and_only_allows_declared_ports(self):
        line = authorized_key('ssh-ed25519 AAAATEST node246')
        self.assertIn('from="192.168.10.246"', line)
        self.assertIn('command="/bin/false"', line)
        self.assertIn('restrict,port-forwarding', line)
        self.assertIn('permitlisten="127.0.0.1:48097"', line)
        self.assertIn('permitopen="127.0.0.1:3306"', line)
        with self.assertRaises(ValueError): authorized_key('ssh-ed25519 KEY\nextra-key')

    def test_iot_only_changes_local_dependencies_and_keeps_security(self):
        original = {'spring': {'datasource': {'dynamic': {'datasource': {'master': {'url': 'jdbc:mysql://127.0.0.1:3306/wx_editor_data08?x=1'}}}}, 'cloud': {}},
                    'wx': {'tenant': {'enable': True}, 'security': {'mock-enable': False}, 'iot': {'mqtt': {'enabled': False}}}}
        result = iot_config(original)
        master = result['spring']['datasource']['dynamic']['datasource']['master']
        self.assertIn(':13316/wx_editor_data08?', master['url'])
        self.assertEqual(master['username'], 'iot246_app')
        self.assertEqual(master['password'], '${IOT_DB_PASSWORD}')
        self.assertEqual(result['server']['address'], '127.0.0.1')
        self.assertFalse(result['wx']['iot']['mqtt']['enabled'])
        self.assertTrue(result['wx']['tenant']['enable'])
        self.assertFalse(result['wx']['security']['mock-enable'])
        self.assertEqual(result['spring']['data']['redis']['port'], 14880)
        self.assertIn(':3306/', original['spring']['datasource']['dynamic']['datasource']['master']['url'])

    def test_compose_cannot_touch_existing_vegetation_project(self):
        cfg = compose_config('sha256:java', 'nginx@sha256:web')
        self.assertEqual(set(cfg['services']), {'iot', 'web'})
        for service in cfg['services'].values():
            self.assertEqual(service['network_mode'], 'host')
            self.assertNotIn('ports', service)
            self.assertTrue(service['read_only'])
            self.assertNotIn('privileged', service)
        self.assertEqual(cfg['services']['web']['depends_on']['iot']['condition'], 'service_healthy')
        self.assertNotIn('/var/run/docker.sock', str(cfg))


if __name__ == '__main__': unittest.main()
