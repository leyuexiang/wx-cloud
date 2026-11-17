"""通过真实管理接口、MQTT 和认证长连接测量有界容量，不修改业务实现。

配置及资源清单不含秘密；登录账号和 MQTT 密码只通过标准输入提供。
每个客户端订阅全部选定逻辑设备，按 20 条分批兼容默认 8 KiB 传输帧上限。
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import runpy
import socket
import ssl
import struct
import sys
import threading
import time
import urllib.parse
import urllib.request
from uuid import uuid4

import websocket


def batches(rows, size=20):
    return [rows[i:i + size] for i in range(0, len(rows), size)]


def percentiles(values):
    """采用最接近秩法；空样本必须返回空值，不能以零延迟冒充成功。"""
    values = sorted(values)
    return {name: round(values[max(0, math.ceil(len(values) * p) - 1)], 3) if values else None
            for name, p in [('p50', .5), ('p95', .95), ('p99', .99)]}


class Api:
    def __init__(self, config, secrets):
        self.config = config
        self.tls = ssl.create_default_context(cafile=config['siteCa'])
        self.token = None
        self.token = self.call('/system/auth/login', 'POST', {
            'username': secrets['username'], 'password': secrets['password']})['accessToken']

    def call(self, path, method='GET', body=None):
        headers = {'tenant-id': str(self.config['tenantId']), 'Content-Type': 'application/json'}
        if self.token:
            headers['Authorization'] = 'Bearer ' + self.token
        request = urllib.request.Request(self.config['api'] + path, method=method, headers=headers,
            data=None if body is None else json.dumps(body, ensure_ascii=False).encode('utf-8'))
        with urllib.request.urlopen(request, context=self.tls, timeout=20) as response:
            result = json.load(response)
        if result.get('code') not in (0, 200):
            # 服务端错误文字可能包含输入；只输出路径和稳定业务码。
            raise RuntimeError(f"接口 {path.split('?')[0]} 返回 {result.get('code')}")
        return result.get('data')

    def logout(self):
        if self.token:
            self.call('/system/auth/logout', 'POST')
            self.token = None


def query(path, **params):
    return path + '?' + urllib.parse.urlencode(params)


def remember(path, value):
    temporary = path.with_suffix('.pending')
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')
    temporary.replace(path)


def prepare(api, config, path, count):
    if path.exists():
        raise RuntimeError('资源清单已存在，请先核对或清理；不重复创建')
    tag = 'capacity-' + uuid4().hex[:12]
    state = {'tag': tag, 'devices': [], 'sceneId': None, 'revisionNo': None}
    remember(path, state)
    scene = api.call('/dim3d/scene/add', 'POST', {'name': tag, 'sceneType': 'local3d'})['sceneId']
    state['sceneId'] = scene
    remember(path, state)
    lock = api.call(query('/dim3d/scene/lock/' + scene, clientKey=tag), 'POST')['lockToken']
    try:
        revision = api.call('/dim3d/scene/info/' + scene)['currentRevision']
        packages = {key: revision[key] for key in ('sceneJson', 'threeDJson', 'twoDJson', 'dependenciesJson')}
        two = json.loads(packages['twoDJson'])
        dep = json.loads(packages['dependenciesJson'])
        rows = [{'deviceId': tag + '-' + str(i), 'objectId': tag + '-widget-' + str(i)} for i in range(count)]
        # 本工具测量服务端数据分发，不以这份最小组件正文宣称浏览器渲染容量。
        two['components'] = [{'instanceId': row['objectId'], 'componentType': 'VNumber',
            'resourceRefKeys': [], 'sortNo': i, 'visible': True, 'locked': False,
            'layoutConfig': {'l': 0, 't': 0, 'w': 100, 'h': 40, 'zIndex': i},
            'renderConfig': {'value': 0}, 'contentJson': {}} for i, row in enumerate(rows)]
        dep['dataBindings'] = [{'bindingKey': row['deviceId'], 'enabled': True,
            'owner': {'domain': 'twoD', 'ownerType': 'instance', 'instanceId': row['objectId']},
            'source': {'type': 'adapter', 'requestConfig': {'adapterId': 'iot-state',
                'config': {'deviceId': row['deviceId'], 'pointCode': 'temperature', 'delivery': 'websocket'}}},
            'mappingConfig': {'bindings': [{'kind': 'widget', 'sourceField': 'value', 'targetPath': 'value', 'valueType': 'number'}]}}
            for row in rows]
        packages['twoDJson'] = json.dumps(two)
        packages['dependenciesJson'] = json.dumps(dep)
        state['revisionNo'] = api.call('/dim3d/scene/saveAll', 'PUT', dict(packages, sceneId=scene,
            baseRevisionNo=revision['revisionNo'], lockToken=lock, requestId=tag, schemaVersion=1))['revisionNo']
        remember(path, state)
        def create(row):
            identifier = api.call('/iot/device-binding/create', 'POST', {
                'connectorId': config['connectorId'], 'externalDeviceId': row['deviceId'],
                'deviceId': row['deviceId'], 'sceneId': scene, 'objectId': row['objectId'],
                'mappingRevision': 'capacity-v1', 'enabled': True,
                'pointMappingJson': json.dumps({'points': {'rawTemperature': {'pointCode': 'temperature', 'valueType': 'NUMBER'}}})})
            return {**row, 'bindingId': str(identifier)}
        with ThreadPoolExecutor(max_workers=4) as pool:
            futures = [pool.submit(create, row) for row in rows]
            for future in as_completed(futures):
                state['devices'].append(future.result())
                remember(path, state)
                if len(state['devices']) % 100 == 0:
                    print(json.dumps({'prepared': len(state['devices'])}), flush=True)
        state['devices'].sort(key=lambda row: int(row['deviceId'].rsplit('-', 1)[-1]))
        remember(path, state)
    finally:
        api.call(query('/dim3d/scene/unlock/' + scene, clientKey=tag), 'DELETE')
    return {'prepared': len(state['devices']), 'sceneId': scene, 'revisionNo': state['revisionNo']}


def cleanup(api, config, path):
    state = json.loads(path.read_text(encoding='utf-8'))
    if state.get('cleaned'):
        return state['cleanupReport']
    # 以临时场景重新列举，覆盖创建已成功但本机尚未写入清单的短暂窗口。
    rows = []
    page = 1
    while True:
        result = api.call(query('/iot/device-binding/page', pageNo=page, pageSize=100,
            connectorId=config['connectorId'], sceneId=state['sceneId']))
        rows.extend(result['list'])
        if len(rows) >= result['total']:
            break
        page += 1
    if any(not row['externalDeviceId'].startswith(state['tag'] + '-') for row in rows):
        raise RuntimeError('临时场景中存在非本轮绑定，拒绝自动清理')
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(lambda row: api.call(query('/iot/device-binding/delete', id=row['id']), 'DELETE'), rows))
    api.call('/dim3d/scene/remove/' + state['sceneId'], 'DELETE')
    result = {'bindingsRemoved': len(rows), 'sceneRemoved': True, 'sceneId': state['sceneId']}
    state.update(cleaned=True, cleanupReport=result)
    remember(path, state)
    return result


class Viewer:
    def __init__(self, api, config, state, rows, number, sent, stop):
        self.number, self.sent, self.stop = number, sent, stop
        self.rows, self.scene_id, self.tenant_id = rows, state['sceneId'], config['tenantId']
        self.seen, self.latencies, self.errors, self.duplicates = set(), [], [], 0
        self.last_renew = time.monotonic()
        self.frames = [{'requestId': f'capacity-{number}-{i}', 'sceneId': state['sceneId'], 'bindings': [
            {'subscriptionId': row['deviceId'], 'objectId': row['objectId'], 'deviceId': row['deviceId'],
             'domain': 'twoD', 'pointCode': 'temperature'} for row in batch]} for i, batch in enumerate(batches(rows))]
        self.socket = websocket.create_connection(config['websocket'] + '?' + urllib.parse.urlencode({
            'token': api.token, 'tenant-id': str(config['tenantId'])}), timeout=20,
            sslopt={'ca_certs': config['siteCa']}, http_proxy_host=None)
        self.send_lock = threading.Lock()
        try:
            for frame in self.frames:
                self.send(frame)
                while True:
                    kind, value = self.receive()
                    if kind == 'iot-state-subscribe-ack':
                        if len(value.get('accepted', [])) != len(frame['bindings']) or value.get('rejected'):
                            raise RuntimeError('订阅未完整获准')
                        break
                    if kind == 'iot-state-subscribe-error':
                        raise RuntimeError('订阅返回错误')
            self.socket.settimeout(1)
            self.thread = threading.Thread(target=self.listen, daemon=True)
            self.thread.start()
        except BaseException:
            self.socket.close()
            raise

    def send(self, frame):
        with self.send_lock:
            self.socket.send(json.dumps({'type': 'iot-state-subscribe', 'content': json.dumps(frame)}))

    def receive(self):
        while True:
            raw = self.socket.recv()
            if not raw:
                raise ConnectionError('服务器关闭连接；请核对传输帧上限和订阅权限')
            if raw == 'ping':
                with self.send_lock:
                    self.socket.send('pong')
                continue
            frame = json.loads(raw)
            return frame.get('type'), json.loads(frame.get('content') or '{}')

    def listen(self):
        while not self.stop.is_set():
            try:
                if time.monotonic() - self.last_renew >= 25:
                    for frame in self.frames:
                        self.send(frame)
                    self.last_renew = time.monotonic()
                kind, value = self.receive()
                if kind == 'iot-state-update':
                    sequence = value.get('value')
                    origin = self.sent.get(sequence)
                    if origin is None:
                        self.errors.append('unexpected-sequence')
                    elif (value.get('sceneId') != self.scene_id or value.get('tenantId') != self.tenant_id
                          or value.get('deviceId') != self.rows[(int(sequence) - 1) % len(self.rows)]['deviceId']
                          or value.get('objectId') != self.rows[(int(sequence) - 1) % len(self.rows)]['objectId']):
                        self.errors.append('unexpected-scope')
                    elif sequence in self.seen:
                        self.duplicates += 1
                    else:
                        self.seen.add(sequence)
                        self.latencies.append((time.perf_counter_ns() - origin) / 1e6)
                elif kind == 'iot-state-subscribe-error' or (kind == 'iot-state-subscribe-ack' and value.get('rejected')):
                    self.errors.append('subscription-rejected')
            except websocket.WebSocketTimeoutException:
                continue
            except Exception as error:
                if not self.stop.is_set():
                    self.errors.append(type(error).__name__)
                break

    def close(self):
        self.socket.close()
        self.thread.join(timeout=3)


def measure(api, config, secrets, state, count, clients, cycles):
    rows = state['devices'][:count]
    if len(rows) != count:
        raise ValueError('设备清单不足')
    viewers, sent = [], {}
    stop = threading.Event()
    begun = time.monotonic()
    protocol = runpy.run_path(str(Path(__file__).with_name('probe-mqtt.py')))
    report = {'logicalDevices': count, 'intervalSeconds': 5, 'clients': clients,
        'bindingsPerClient': count, 'mqttConnections': 1, 'distinctAccounts': 1, 'cycles': cycles,
        'expectedMessages': count * cycles, 'expectedDeliveries': count * cycles * clients,
        'targetMessagesPerSecond': count / 5, 'clockSource': '同一负载发生器的单调时钟',
        'renderingMeasured': False, 'subscriptionScope': 'draft', 'passed': False}
    try:
        # 限制建连并发，避免把同时握手突发和稳态消息容量混在一起。
        with ThreadPoolExecutor(max_workers=5) as pool:
            futures = [pool.submit(Viewer, api, config, state, rows, i, sent, stop) for i in range(clients)]
            setup_errors = []
            for future in as_completed(futures):
                try:
                    viewers.append(future.result())
                except Exception as error:
                    setup_errors.append(type(error).__name__)
            if setup_errors:
                report['setupErrors'] = setup_errors
                raise RuntimeError('部分订阅连接未建立')
        report['setupSeconds'] = round(time.monotonic() - begun, 3)
        print(json.dumps({'stage': 'subscribed', **{k: report[k] for k in ('logicalDevices', 'clients', 'setupSeconds')}}), flush=True)
        context = ssl.create_default_context(cafile=config['mqttCa'])
        topic = protocol['utf8'](config['topic'])
        with socket.create_connection((config['mqttHost'], config['mqttPort']), timeout=10) as raw:
            with context.wrap_socket(raw, server_hostname=config['mqttHost']) as conn:
                conn.sendall(protocol['connect_packet']('capacity' + uuid4().hex[:15], config['mqttUsername'], secrets['mqttPassword']))
                if protocol['wait_ack'](conn, 0x20, time.monotonic() + 10) != b'\0\0':
                    raise RuntimeError('MQTT 认证失败')
                start, cpu_start = time.monotonic(), time.process_time()
                abort = None
                for sequence in range(count * cycles):
                    deadline = start + sequence * 5 / count
                    remaining = deadline - time.monotonic()
                    if remaining > 0:
                        time.sleep(remaining)
                    if time.monotonic() - deadline > 2:
                        abort = 'publisher-schedule-lag-over-2s'; break
                    if any(viewer.errors for viewer in viewers):
                        abort = 'viewer-error'; break
                    if sequence > count * 2 and min(len(v.seen) for v in viewers) < sequence - count * 2:
                        abort = 'delivery-backlog-over-10s'; break
                    row = rows[sequence % count]
                    value = sequence + 1
                    packet_id = struct.pack('!H', value)
                    payload = {'externalDeviceId': row['deviceId'], 'pointCode': 'rawTemperature', 'value': value,
                        'valueType': 'NUMBER', 'quality': 'GOOD', 'eventTime': datetime.now(timezone.utc).isoformat(),
                        'traceId': state['tag'] + '-' + str(uuid4())}
                    body = topic + packet_id + json.dumps(payload).encode('utf-8')
                    sent[value] = time.perf_counter_ns()
                    conn.sendall(b'\x32' + protocol['encode_remaining_length'](len(body)) + body)
                    if protocol['wait_ack'](conn, 0x40, time.monotonic() + 5) != packet_id:
                        raise RuntimeError('MQTT 发布确认编号不匹配')
                    if value % count == 0:
                        print(json.dumps({'stage': 'publishing', 'messages': value,
                            'deliveries': sum(len(v.seen) for v in viewers)}), flush=True)
                report['publishSeconds'] = round(time.monotonic() - start, 3)
                report['loadGeneratorCpuSeconds'] = round(time.process_time() - cpu_start, 3)
                report['abortReason'] = abort
                conn.sendall(b'\xe0\x00')
        drain_start = time.monotonic()
        while time.monotonic() - drain_start < 20 and any(len(v.seen) < len(sent) for v in viewers) and not any(v.errors for v in viewers):
            time.sleep(.2)
        report.update(messagesAcknowledged=len(sent), uniqueDeliveries=sum(len(v.seen) for v in viewers),
            duplicateDeliveries=sum(v.duplicates for v in viewers), drainSeconds=round(time.monotonic() - drain_start, 3),
            clientErrors=[{'client': v.number, 'errors': v.errors} for v in viewers if v.errors])
        report['clientResults'] = [{'client': v.number, 'received': len(v.seen), 'missing': len(sent) - len(v.seen),
            'latencyMs': percentiles(v.latencies)} for v in sorted(viewers, key=lambda v: v.number)]
        report['latencyMs'] = percentiles([latency for v in viewers for latency in v.latencies])
        report['deliveryComplete'] = not report['abortReason'] and not report['clientErrors'] and len(sent) == count * cycles and all(len(v.seen) == len(sent) and not v.duplicates for v in viewers)
        # 以一个上报周期作为本轮工程观察门槛，正式延迟指标仍需业务单独签收。
        report['latencyGateMs'] = 5000
        report['passed'] = report['deliveryComplete'] and report['latencyMs']['p95'] is not None and report['latencyMs']['p95'] <= report['latencyGateMs']
    except Exception as error:
        # 失败也保留已建立连接和已发送数量，原始异常不包含在公开报告中。
        report.update(failureType=type(error).__name__, messagesAttempted=len(sent),
            uniqueDeliveries=sum(len(v.seen) for v in viewers))
    finally:
        stop.set()
        for viewer in viewers:
            viewer.close()
    report['totalSeconds'] = round(time.monotonic() - begun, 3)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'run', 'cleanup'])
    parser.add_argument('--config', type=Path, required=True)
    parser.add_argument('--state', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--devices', type=int, default=1000)
    parser.add_argument('--clients', type=int, default=50)
    parser.add_argument('--cycles', type=int, default=12)
    args = parser.parse_args()
    if not 1 <= args.devices <= 1000 or not 1 <= args.clients <= 50 or not 1 <= args.cycles <= 12:
        parser.error('本工具限于 1000 逻辑设备、50 条连接及 12 轮，禁止无界压力')
    config = json.loads(args.config.read_text(encoding='utf-8'))
    secrets = json.load(sys.stdin)
    api = Api(config, secrets)
    try:
        if args.action == 'prepare':
            result = prepare(api, config, args.state, args.devices)
        elif args.action == 'cleanup':
            result = cleanup(api, config, args.state)
        else:
            result = measure(api, config, secrets, json.loads(args.state.read_text(encoding='utf-8')),
                args.devices, args.clients, args.cycles)
        remember(args.report, result)
        print(json.dumps({k: v for k, v in result.items() if k != 'clientResults'}, ensure_ascii=False), flush=True)
        if args.action == 'run' and not result.get('passed'):
            raise SystemExit(2)
    finally:
        api.logout()


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # 网络异常可能含带认证参数的 URL，终端仅记录异常类型。
        print(json.dumps({'failed': True, 'type': type(error).__name__}), file=sys.stderr)
        sys.exit(1)
