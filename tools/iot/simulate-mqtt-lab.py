"""通过已认证的 MQTT 试运行代理上报开关或温度，复用现有报文探测工具。"""

import argparse
from datetime import datetime, timedelta, timezone
import getpass
import json
import math
import os
from pathlib import Path
import runpy
import socket
import ssl
import struct
import sys
import time
from uuid import uuid4


PROTOCOL = runpy.run_path(str(Path(__file__).with_name('probe-mqtt.py')))
VALUES = {'switch': ('rawVisible', 'BOOLEAN'), 'temperature': ('rawTemperature', 'NUMBER')}


def payload(device, value, sent_at):
    point, kind = VALUES[device]
    message = {'externalDeviceId': 'virtual-' + device, 'pointCode': point, 'value': value,
               'valueType': kind, 'quality': 'GOOD', 'eventTime': sent_at.isoformat(), 'traceId': uuid4().hex}
    if device == 'temperature':
        message['unit'] = '°C'
    return message


def publish(context, device, password, messages):
    """一次会话发布一批，按报文编号核对服务质量等级 1（QoS 1）的确认。"""
    topic = PROTOCOL['utf8']('lab/iot/tenant-2/' + device)
    client = 'lab' + uuid4().hex[:20]
    with socket.create_connection(('127.0.0.1', 18884), timeout=8) as raw:
        with context.wrap_socket(raw, server_hostname='127.0.0.1') as conn:
            conn.sendall(PROTOCOL['connect_packet'](client, 'lab-' + device, password))
            if PROTOCOL['wait_ack'](conn, 0x20, time.monotonic() + 8) != b'\x00\x00':
                raise RuntimeError('MQTT 认证失败')
            for index, message in enumerate(messages, 1):
                packet_id = struct.pack('!H', index)
                body = topic + packet_id + json.dumps(message, ensure_ascii=False).encode('utf-8')
                conn.sendall(b'\x32' + PROTOCOL['encode_remaining_length'](len(body)) + body)
                if PROTOCOL['wait_ack'](conn, 0x40, time.monotonic() + 8) != packet_id:
                    raise RuntimeError('收到的发布确认编号不匹配')
            conn.sendall(b'\xe0\x00')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('device', choices=tuple(VALUES))
    parser.add_argument('value', help='开关使用 true 或 false，温度使用 JSON 数字')
    parser.add_argument('--ca', type=Path, required=True)
    parser.add_argument('--scenario', choices=('normal', 'duplicate', 'out-of-order'), default='normal')
    parser.add_argument('--count', type=int, default=1)
    parser.add_argument('--interval', type=float, default=5)
    parser.add_argument('--step', type=float, default=0.5)
    args = parser.parse_args()
    try:
        value = json.loads(args.value)
    except ValueError:
        parser.error('上报值必须是有效的 JSON')
    if (args.device == 'switch' and type(value) is not bool) or (args.device == 'temperature' and type(value) not in (int, float)):
        parser.error('上报值与设备类型不匹配')
    if not 1 <= args.count <= 120 or not 1 <= args.interval <= 60 or not math.isfinite(args.step) or not -1e12 <= value <= 1e12:
        parser.error('次数、间隔或数值超出试运行范围')
    password = os.environ.get('IOT_MQTT_LAB_PASSWORD') or getpass.getpass('MQTT 发布密码: ')
    if not password:
        parser.error('MQTT 发布密码不能为空')
    context = ssl.create_default_context(cafile=str(args.ca))
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    for index in range(args.count):
        now = datetime.now(timezone.utc)
        current = payload(args.device, value, now)
        batch = [current]
        if args.scenario == 'duplicate':
            batch.append(current)
        elif args.scenario == 'out-of-order':
            old_value = not value if args.device == 'switch' else value - 5
            batch.append(payload(args.device, old_value, now - timedelta(seconds=3)))
        publish(context, args.device, password, batch)
        print(json.dumps({'brokerAcknowledged': True, 'scenario': args.scenario, 'messages': batch}, ensure_ascii=False), flush=True)
        if index + 1 < args.count:
            time.sleep(args.interval)
            value = not value if args.device == 'switch' else value + args.step


if __name__ == '__main__':
    try:
        main()
    except (OSError, RuntimeError):
        print('MQTT 认证、连接或发布确认失败，请核对测试转发、证书、账号和主题。', file=sys.stderr)
        sys.exit(1)
