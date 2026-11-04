"""独立探测器回归：仅用回环协议替身及模拟连接，不访问真实环境。"""

import contextlib
import importlib.util
import io
import os
from pathlib import Path
import socket
import ssl
import threading
import time
import unittest
from unittest.mock import patch, MagicMock

spec = importlib.util.spec_from_file_location("probe_mqtt", Path(__file__).with_name("probe-mqtt.py"))
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)

BASE = {
    "IOT_MQTT_PROBE_HOST": "127.0.0.1", "IOT_MQTT_PROBE_PORT": "1883",
    "IOT_MQTT_PROBE_USERNAME": "test-user", "IOT_MQTT_PROBE_PASSWORD": "test-secret",
    "IOT_MQTT_PROBE_TOPIC": "test/telemetry", "IOT_MQTT_PROBE_TIMEOUT": "0.3",
}


class ProbeTests(unittest.TestCase):
    def run_server(self, connack=b"\x00\x00", suback=b"\x00\x01\x01",
                   unsuback=b"\x00\x02", retained=False, stall=False):
        # 本地替身独立检查出站帧及生命周期，不能用它代替真实代理认证验收。
        packets, errors = [], []
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            server.settimeout(2)

            def worker():
                try:
                    with server.accept()[0] as client:
                        def read():
                            packet = probe.read_packet(client, time.monotonic() + 2)
                            packets.append(packet)
                            return packet

                        def send(header, body):
                            client.sendall(bytes((header,)) + probe.encode_remaining_length(len(body)) + body)

                        read()
                        send(0x20, connack)
                        if connack != b"\x00\x00":
                            return
                        read()
                        if stall:
                            # 不返回确认，客户端必须自行超时并发送断开。
                            read()
                            return
                        if retained:
                            send(0x31, b"\x00\x01tprivate-payload")
                        send(0x90, suback)
                        next_header, _ = read()
                        if next_header == 0xA2:
                            if retained:
                                send(0x32, b"\x00\x01t\x00\x08private-payload")
                            send(0xB0, unsuback)
                            read()
                except Exception as error:
                    errors.append(error)

            thread = threading.Thread(target=worker, daemon=True)
            thread.start()
            env = dict(BASE, IOT_MQTT_PROBE_PORT=str(server.getsockname()[1]))
            output = io.StringIO()
            with patch.dict(os.environ, env, clear=True), contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
                code = probe.main()
            thread.join(3)
            self.assertFalse(thread.is_alive())
            self.assertEqual(errors, [])
        self.assertNotIn("test-secret", output.getvalue())
        self.assertNotIn("private-payload", output.getvalue())
        return code, packets, output.getvalue()

    def test_success_wire_sequence(self):
        code, packets, _ = self.run_server()
        self.assertEqual(code, 0)
        self.assertEqual([p[0] for p in packets], [0x10, 0x82, 0xA2, 0xE0])
        self.assertEqual(packets[0][1][:10], b"\x00\x04MQTT\x04\xc2\x00\x1e")
        self.assertEqual(packets[1][1], b"\x00\x01\x00\x0etest/telemetry\x01")
        self.assertEqual(packets[2][1], b"\x00\x02\x00\x0etest/telemetry")
        self.assertEqual(packets[-1][1], b"")

    def test_retained_messages_do_not_replace_ack(self):
        code, packets, _ = self.run_server(retained=True)
        self.assertEqual(code, 0)
        self.assertEqual([p[0] for p in packets], [0x10, 0x82, 0xA2, 0xE0])

    def test_authentication_rejected(self):
        for rc in (2, 4, 5):
            with self.subTest(rc=rc):
                code, packets, output = self.run_server(connack=bytes((0, rc)))
                self.assertEqual(code, 1)
                self.assertEqual(len(packets), 1)
                self.assertIn(f"返回码={rc}", output)

    def test_invalid_connack(self):
        for body in (b"\x01\x00", b"\x00\x06", b"\x00\x00\x00"):
            with self.subTest(body=body):
                self.assertEqual(self.run_server(connack=body)[0], 1)

    def test_rejected_downgraded_or_mismatched_suback(self):
        for body in (b"\x00\x01\x80", b"\x00\x01\x00", b"\x00\x02\x01", b"\x00\x01\x01\x01"):
            with self.subTest(body=body):
                code, packets, _ = self.run_server(suback=body)
                self.assertEqual(code, 1)
                self.assertEqual(packets[-1][0], 0xE0)

    def test_invalid_unsuback(self):
        for body in (b"\x00\x01", b"\x00\x02\x00"):
            with self.subTest(body=body):
                self.assertEqual(self.run_server(unsuback=body)[0], 1)

    def test_missing_ack_times_out_and_disconnects(self):
        code, packets, output = self.run_server(stall=True)
        self.assertEqual(code, 1)
        self.assertIn("超时", output)
        self.assertEqual(packets[-1][0], 0xE0)

    def test_invalid_config_never_opens_socket(self):
        for key, value in (("TOPIC", "a/#"), ("TOPIC", "a/+"), ("TOPIC", ""),
                           ("USERNAME", ""), ("CLIENT_ID", ""), ("PORT", "0"),
                           ("PORT", "invalid"), ("TIMEOUT", "nan"), ("TIMEOUT", "inf"),
                           ("TIMEOUT", "0"), ("TIMEOUT", "61"), ("TLS", "yes"),
                           ("TOPIC", "a\x00b"), ("TOPIC", "a" * 65536)):
            with self.subTest(key=key, value=value[:30]):
                env = dict(BASE, **{"IOT_MQTT_PROBE_" + key: value})
                # 空字符不能进入操作系统环境，直接模拟配置读取来验证字段校验。
                with patch.object(probe.os, "getenv", side_effect=lambda k, d=None: env.get(k, d)), patch.object(probe.socket, "create_connection") as connect, contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(probe.main(), 1)
                    connect.assert_not_called()

    def test_missing_password_never_opens_socket(self):
        env = dict(BASE)
        del env["IOT_MQTT_PROBE_PASSWORD"]
        with patch.dict(os.environ, env, clear=True), patch.object(probe.socket, "create_connection") as connect, contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(probe.main(), 1)
            connect.assert_not_called()

    def test_tls_failure_closes_raw_socket(self):
        raw, context = MagicMock(), MagicMock()
        context.wrap_socket.side_effect = ssl.SSLError("test-secret")
        output = io.StringIO()
        with patch.dict(os.environ, dict(BASE, IOT_MQTT_PROBE_TLS="true"), clear=True), patch.object(probe.socket, "create_connection", return_value=raw), patch.object(probe.ssl, "create_default_context", return_value=context), contextlib.redirect_stderr(output):
            self.assertEqual(probe.main(), 1)
        raw.close.assert_called_once()
        self.assertNotIn("test-secret", output.getvalue())
        self.assertEqual(context.minimum_version, ssl.TLSVersion.TLSv1_2)

    def test_deadline_not_extended_by_packets(self):
        sock = MagicMock()
        with patch.object(probe, "read_packet", return_value=(0x30, b"discard")) as read, patch.object(probe.time, "monotonic", return_value=10):
            # 专门验证循环透传同一截止时间；真实套接字超时另有回环用例。
            read.side_effect = [(0x30, b"discard"), TimeoutError()]
            with self.assertRaises(TimeoutError):
                probe.wait_ack(sock, 0x90, 11)
            self.assertEqual([c.args[1] for c in read.call_args_list], [11, 11])
        with patch.object(probe.time, "monotonic", return_value=12):
            with self.assertRaises(TimeoutError):
                probe.receive(sock, 1, 11)
            sock.recv.assert_not_called()

    def test_packet_limits_and_partial_close(self):
        for data in (b"\x90\x80\x80\x80\x80", b"\x90" + probe.encode_remaining_length(1048577), b"\x90\x03\x00"):
            with self.subTest(data=data):
                sock = MagicMock()
                pending = bytearray(data)
                def recv(count):
                    result = bytes(pending[:count])
                    del pending[:count]
                    return result
                sock.recv.side_effect = recv
                with self.assertRaises(RuntimeError):
                    probe.read_packet(sock, time.monotonic() + 1)


if __name__ == "__main__":
    unittest.main()
