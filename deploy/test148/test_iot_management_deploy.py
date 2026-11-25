"""IoT候选配置边界：只公开三类管理接口，保持租户和认证开启。"""
import copy
import unittest
from iot_management_deploy import build_iot_config, add_iot_route


class IotCandidateConfigTest(unittest.TestCase):
    def test_config_reuses_identity_but_isolates_database_and_network(self):
        base = {"spring": {"application": {"name": "system-server"}, "cloud": {"discovery": {"kept": True}}},
                "wx": {"tenant": {"enable": True}, "security": {"mock-enable": False}}}
        database = {"dynamic": {"datasource": {"master": {"url": "jdbc:mysql://localhost/wx_editor_data08?x=1"}}}}
        old = copy.deepcopy(base)
        result = build_iot_config(base, database)
        self.assertEqual(base, old)
        self.assertEqual(result["server"], {"port": 48096, "address": "127.0.0.1"})
        self.assertEqual(result["wx"]["iot"]["mqtt"]["enabled"], "${IOT_MQTT_ENABLED:false}")
        self.assertEqual(result["wx"]["iot"]["mqtt"]["tenant-id"], "${IOT_MQTT_TENANT_ID:}")
        self.assertEqual(result["wx"]["iot"]["mqtt"]["connector-id"], "${IOT_MQTT_CONNECTOR_ID:}")
        self.assertTrue(result["wx"]["tenant"]["enable"])
        self.assertFalse(result["wx"]["security"]["mock-enable"])
        self.assertEqual(result["spring"]["datasource"], database)

    def test_database_must_be_existing_data08_target(self):
        with self.assertRaises(ValueError):
            build_iot_config({}, {"dynamic": {"datasource": {"master": {"url": "jdbc:mysql://localhost/wx_editor?x=1"}}}})

    def test_route_does_not_open_webhook_or_mock_and_is_idempotent(self):
        original = {"spring": {"cloud": {"gateway": {"routes": [{"id": "existing", "uri": "http://kept"}]}}}}
        result = add_iot_route(original)
        self.assertEqual(len(original["spring"]["cloud"]["gateway"]["routes"]), 1)
        routes = result["spring"]["cloud"]["gateway"]["routes"]
        self.assertEqual(routes[0]["id"], "iot-management-data08")
        self.assertIn("RemoveRequestHeader=x-bot-user", routes[0]["filters"])
        self.assertNotIn("/admin-api/iot/**", routes[0]["predicates"][0])
        self.assertNotIn("telemetry", routes[0]["predicates"][0])
        self.assertEqual(add_iot_route(result), result)


if __name__ == "__main__":
    unittest.main()
