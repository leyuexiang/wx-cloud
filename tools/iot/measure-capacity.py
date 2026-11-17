"""通过真实管理接口、MQTT 和认证长连接测量有界容量，不修改业务实现。

配置及资源清单不含秘密；登录账号和 MQTT 密码只通过标准输入提供。
每个客户端订阅全部选定逻辑设备，按 20 条分批兼容默认 8 KiB 传输帧上限。
"""
from __future__ import annotations

import argparse
from array import array
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
import json
import math
import multiprocessing
import ctypes
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


def packet_id(sequence):
    """确认窗口只有 20 项；回绕 16 位非零编号时，旧同号报文早已确认。"""
    return struct.pack('!H', (sequence - 1) % 65535 + 1)


def subscription_scope(state, ticket=None):
    """草稿与固定发布是互斥授权入口，不同时提交两种范围。"""
    return {'publicationId': state['publicationId'], 'publicationTicket': ticket['token']} if ticket else {'sceneId': state['sceneId']}


def numeric_sequence(value):
    """统一数值允许整数浮点编码，拒绝布尔、非有限数和真正的小数。"""
    if type(value) is int:
        return value
    if type(value) is float and math.isfinite(value) and value.is_integer():
        return int(value)
    return None


class SentClock:
    """固定容量单调时钟，零表示尚未发送，不能冒充有效延迟起点。"""
    def __init__(self, total):
        context = multiprocessing.get_context('spawn')
        self.values = context.RawArray('q', total + 1)
        self.counter = context.RawValue('i', 0)

    @property
    def count(self):
        return self.counter.value

    def __setitem__(self, sequence, instant):
        if sequence != self.count + 1:
            raise ValueError('发送序号必须连续')
        self.values[sequence] = instant
        # 单写者先公布时钟再公布序号，父进程只读；不在高频帧上竞争互斥锁。
        self.counter.value = sequence

    def __len__(self):
        return self.count

    def get(self, sequence):
        sequence = numeric_sequence(sequence)
        if sequence is None or not 1 <= sequence <= self.count:
            return None
        return self.values[sequence] or None

    def storage_bytes(self):
        return ctypes.sizeof(self.values) + ctypes.sizeof(self.counter)


class DeliveryMetrics:
    """位图精确核对完整性；固定直方图保守向上取整，不保存逐帧对象。"""
    def __init__(self, total):
        self.seen = bytearray(total + 1)
        self.histogram = array('I', [0]) * 60002
        self.received = self.duplicates = self.maximum = 0

    def record(self, sequence, latency_ms):
        sequence = numeric_sequence(sequence)
        if sequence is None or not 1 <= sequence < len(self.seen):
            raise ValueError('接收序号越界')
        if self.seen[sequence]:
            self.duplicates += 1
            return False
        self.seen[sequence] = 1
        bucket = max(0, math.ceil(latency_ms))
        self.histogram[min(bucket, 60001)] += 1
        self.maximum = max(self.maximum, bucket)
        self.received += 1
        return True

    def storage_bytes(self):
        return len(self.seen) + len(self.histogram) * self.histogram.itemsize

    def percentiles(self):
        return histogram_percentiles(self.histogram, self.received, self.maximum)


def histogram_percentiles(histogram, count, maximum):
    """超出直方图范围的尾部使用实际最大值，避免截断后虚报低延迟。"""
    result = dict.fromkeys(('p50', 'p95', 'p99'))
    if not count:
        return result
    ranks = {key: math.ceil(count * value) for key, value in [('p50', .5), ('p95', .95), ('p99', .99)]}
    cumulative = 0
    for bucket, amount in enumerate(histogram):
        cumulative += amount
        for key, rank in ranks.items():
            if result[key] is None and cumulative >= rank:
                result[key] = bucket if bucket < 60001 else maximum
        if all(value is not None for value in result.values()):
            break
    return result


class Api:
    def __init__(self, config, secrets):
        self.config = config
        self.tls = ssl.create_default_context(cafile=config['siteCa'])
        self.token = None
        login = self.call('/system/auth/login', 'POST', {
            'username': secrets['username'], 'password': secrets['password']})
        self.token = login['accessToken']
        self.user_id = str(login['userId'])

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


def viewer_credentials(secrets, clients):
    """查看身份可选，兼容原单账号；显式多账号必须足量且不能重复选取。"""
    if 'viewers' not in secrets:
        return []
    accounts = secrets['viewers']
    if not isinstance(accounts, list) or not clients <= len(accounts) <= 50:
        raise ValueError('查看账号必须足量且不超过五十个')
    selected = accounts[:clients]
    names = [account.get('username') for account in selected]
    if any(not isinstance(name, str) or not name for name in names) or len(set(names)) != clients:
        raise ValueError('查看账号不能缺失或重复')
    return selected


def open_viewer_apis(config, secrets, clients, opened):
    """逐个登录并按服务端用户编号去重；先登记会话，失败时调用者也能全部注销。"""
    for account in viewer_credentials(secrets, clients):
        opened.append(Api(config, account))
    if len({api.user_id for api in opened}) != len(opened):
        raise ValueError('登录结果未对应独立身份')
    return opened


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
    if state.get('publicationId'):
        # 先关闭固定快照授权，再清理草稿及接入绑定，避免留下仍可访问的临时发布。
        publication_path = '/dim3d/scene/publication/' + state['publicationId']
        if api.call(publication_path)['status'] != 'REVOKED':
            api.call(publication_path + '/revoke', 'POST')
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
    def __init__(self, api, config, state, rows, number, sent, stop, ticket=None):
        self.number, self.sent, self.stop = number, sent, stop
        self.user_id = api.user_id
        self.rows, self.scene_id, self.tenant_id = rows, state['sceneId'], config['tenantId']
        self.metrics, self.errors = DeliveryMetrics(len(sent.values) - 1), []
        self.renewal_acks = 0
        self.last_renew = time.monotonic()
        self.frames = [{'requestId': f'capacity-{number}-{i}', **subscription_scope(state, ticket), 'bindings': [
            {'subscriptionId': row['deviceId'], 'objectId': row['objectId'], 'deviceId': row['deviceId'],
             'domain': 'twoD', 'pointCode': 'temperature'} for row in batch]} for i, batch in enumerate(batches(rows))]
        self.frame_sizes = {frame['requestId']: len(frame['bindings']) for frame in self.frames}
        self.socket = websocket.create_connection(config['websocket'] + '?' + urllib.parse.urlencode({
            'token': api.token, 'tenant-id': str(config['tenantId'])}), timeout=20,
            sslopt={'ca_certs': config['siteCa']}, http_proxy_host=None, skip_utf8_validation=True)
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
                    else:
                        self.metrics.record(sequence, (time.perf_counter_ns() - origin) / 1e6)
                elif kind == 'iot-state-subscribe-error' or (kind == 'iot-state-subscribe-ack' and value.get('rejected')):
                    self.errors.append('subscription-rejected')
                elif kind == 'iot-state-subscribe-ack':
                    if len(value.get('accepted', [])) != self.frame_sizes.get(value.get('requestId')):
                        self.errors.append('incomplete-renewal')
                    self.renewal_acks += 1
            except websocket.WebSocketTimeoutException:
                continue
            except Exception as error:
                if not self.stop.is_set():
                    self.errors.append(type(error).__name__)
                break

    def close(self):
        self.socket.close()
        self.thread.join(timeout=3)


def publish_messages(config, mqtt_password, state, rows, cycles, sent, publisher_stop, output):
    """独立进程发送；共享数组只含非秘密时间，票据和浏览器状态留在父进程。"""
    count = len(rows)
    report = {}
    protocol = runpy.run_path(str(Path(__file__).with_name('probe-mqtt.py')))
    acknowledged = 0
    try:
        context = ssl.create_default_context(cafile=config['mqttCa'])
        topic = protocol['utf8'](config['topic'])
        with socket.create_connection((config['mqttHost'], config['mqttPort']), timeout=10) as raw:
            with context.wrap_socket(raw, server_hostname=config['mqttHost']) as conn:
                conn.sendall(protocol['connect_packet']('capacity' + uuid4().hex[:15], config['mqttUsername'], mqtt_password))
                if protocol['wait_ack'](conn, 0x20, time.monotonic() + 10) != b'\0\0':
                    raise RuntimeError('MQTT 认证失败')
                start, cpu_start = time.monotonic(), time.process_time()
                abort = None
                awaiting = []
                acknowledged = 0
                maximum_ack_seconds = 0
                for sequence in range(count * cycles):
                    deadline = start + sequence * 5 / count
                    remaining = deadline - time.monotonic()
                    if remaining > 0:
                        time.sleep(remaining)
                    if time.monotonic() - deadline > 2:
                        report['scheduleLagSeconds'] = round(time.monotonic() - deadline, 3)
                        abort = 'publisher-schedule-lag-over-2s'; break
                    if publisher_stop.is_set():
                        abort = 'parent-stop'; break
                    row = rows[sequence % count]
                    value = sequence + 1
                    identifier = packet_id(value)
                    payload = {'externalDeviceId': row['deviceId'], 'pointCode': 'rawTemperature', 'value': value,
                        'valueType': 'NUMBER', 'quality': 'GOOD', 'eventTime': datetime.now(timezone.utc).isoformat(),
                        'traceId': state['tag'] + '-' + str(uuid4())}
                    body = topic + identifier + json.dumps(payload).encode('utf-8')
                    sent[value] = time.perf_counter_ns()
                    conn.sendall(b'\x32' + protocol['encode_remaining_length'](len(body)) + body)
                    # 发送时钟与接收端 CPU 压力分离，最多 20 条在途；每个确认仍逐条核对。
                    awaiting.append(identifier)
                    if len(awaiting) == 20 or value == count * cycles:
                        ack_started = time.monotonic()
                        for expected_id in awaiting:
                            if protocol['wait_ack'](conn, 0x40, time.monotonic() + 5) != expected_id:
                                raise RuntimeError('MQTT 发布确认编号不匹配')
                            acknowledged += 1
                        awaiting.clear()
                        maximum_ack_seconds = max(maximum_ack_seconds, time.monotonic() - ack_started)
                for expected_id in awaiting:
                    if protocol['wait_ack'](conn, 0x40, time.monotonic() + 5) != expected_id:
                        raise RuntimeError('MQTT 发布确认编号不匹配')
                    acknowledged += 1
                report['publishSeconds'] = round(time.monotonic() - start, 3)
                report['maximumAckBatchSeconds'] = round(maximum_ack_seconds, 3)
                report['publisherCpuSeconds'] = round(time.process_time() - cpu_start, 3)
                report['abortReason'] = abort
                conn.sendall(b'\xe0\x00')
        report['messagesAcknowledged'] = acknowledged
    except Exception as error:
        report.update(failureType=type(error).__name__, messagesAcknowledged=acknowledged,
            abortReason='publisher-exception')
    finally:
        output.send(report)
        output.close()


def measure(api, config, secrets, state, count, clients, cycles, scope='draft'):
    rows = state['devices'][:count]
    if len(rows) != count:
        raise ValueError('设备清单不足')
    viewers, sent, tickets, viewer_apis = [], SentClock(count * cycles), [], []
    stop = threading.Event()
    begun = time.monotonic()
    report = {'logicalDevices': count, 'intervalSeconds': 5, 'clients': clients,
        'bindingsPerClient': count, 'mqttConnections': 1, 'distinctAccounts': 1, 'cycles': cycles,
        'expectedMessages': count * cycles, 'expectedDeliveries': count * cycles * clients,
        'targetMessagesPerSecond': count / 5, 'clockSource': '同一负载发生器的单调时钟',
        'renderingMeasured': False, 'subscriptionScope': scope, 'passed': False,
        'latencyResolutionMs': 1, 'latencyRounding': 'ceil', 'minuteSamples': []}
    try:
        open_viewer_apis(config, secrets, clients, viewer_apis)
        report['distinctAccounts'] = len(viewer_apis) if viewer_apis else 1
        if scope == 'publication':
            if not state.get('publicationId'):
                raise ValueError('固定发布测试需要资源清单中的 publicationId')
            # 每条连接独立票据，明文只保留内存；总时长限制十分钟，票据上限二十分钟。
            for _ in range(clients):
                tickets.append(api.call(query('/dim3d/scene/publication/' + state['publicationId'] + '/ticket',
                    expiresInSeconds=1200), 'POST'))
        report['distinctTickets'] = len(tickets)
        # 限制建连并发，避免把同时握手突发和稳态消息容量混在一起。
        with ThreadPoolExecutor(max_workers=5) as pool:
            futures = [pool.submit(Viewer, viewer_apis[i] if viewer_apis else api, config, state, rows, i, sent, stop,
                tickets[i] if tickets else None) for i in range(clients)]
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
        # 发送进程与五十个接收线程隔离；仍使用同一操作系统单调时钟和固定数组。
        context = multiprocessing.get_context('spawn')
        publisher_stop = context.Event()
        incoming, outgoing = context.Pipe(duplex=False)
        publisher = context.Process(target=publish_messages,
            args=(config, secrets['mqttPassword'], state, rows, cycles, sent, publisher_stop, outgoing))
        publisher.start()
        outgoing.close()
        parent_cpu_start = time.process_time()
        next_cycle, next_minute = count, count * 12
        monitor_abort = None
        try:
            while not incoming.poll(.1):
                attempted = len(sent)
                if any(viewer.errors for viewer in viewers):
                    monitor_abort = 'viewer-error'
                    publisher_stop.set()
                elif attempted > count * 2 and min(v.metrics.received for v in viewers) < attempted - count * 2:
                    monitor_abort = 'delivery-backlog-over-10s'
                    publisher_stop.set()
                if attempted >= next_cycle:
                    print(json.dumps({'stage': 'publishing', 'messages': attempted,
                        'deliveries': sum(v.metrics.received for v in viewers)}), flush=True)
                    next_cycle = (attempted // count + 1) * count
                if attempted >= next_minute:
                    report['minuteSamples'].append({'minute': next_minute // (count * 12),
                        'messages': attempted, 'deliveries': sum(v.metrics.received for v in viewers),
                        'minimumClientReceived': min(v.metrics.received for v in viewers),
                        'renewalAcks': sum(v.renewal_acks for v in viewers)})
                    next_minute += count * 12
                if not publisher.is_alive() and not incoming.poll():
                    raise RuntimeError('发送进程结束但未交付报告')
            result = incoming.recv()
            if result.get('failureType'):
                report.update(result)
                raise RuntimeError('发送进程失败')
            report.update(result)
            if monitor_abort:
                report['abortReason'] = monitor_abort
            acknowledged = result['messagesAcknowledged']
            # 最后一帧与子进程报告可能同时到达，仍须记录最后一分钟采样。
            if len(sent) >= next_minute:
                report['minuteSamples'].append({'minute': next_minute // (count * 12),
                    'messages': len(sent), 'deliveries': sum(v.metrics.received for v in viewers),
                    'minimumClientReceived': min(v.metrics.received for v in viewers),
                    'renewalAcks': sum(v.renewal_acks for v in viewers)})
            report['loadGeneratorCpuSeconds'] = round(time.process_time() - parent_cpu_start + result['publisherCpuSeconds'], 3)
            report['publisherIsolation'] = 'spawn-process-shared-clock'
        finally:
            publisher_stop.set()
            publisher.join(timeout=12)
            if publisher.is_alive():
                publisher.terminate()
                publisher.join(timeout=3)
            incoming.close()
        drain_start = time.monotonic()
        while time.monotonic() - drain_start < 20 and any(v.metrics.received < len(sent) for v in viewers) and not any(v.errors for v in viewers):
            time.sleep(.2)
        report.update(messagesAcknowledged=acknowledged, mqttMaxInFlight=20, uniqueDeliveries=sum(v.metrics.received for v in viewers),
            duplicateDeliveries=sum(v.metrics.duplicates for v in viewers), drainSeconds=round(time.monotonic() - drain_start, 3),
            clientErrors=[{'client': v.number, 'errors': v.errors} for v in viewers if v.errors])
        report['clientResults'] = [{'client': v.number, 'userId': v.user_id, 'received': v.metrics.received,
            'missing': count * cycles - v.metrics.received, 'renewalAcks': v.renewal_acks,
            'latencyMs': v.metrics.percentiles()} for v in sorted(viewers, key=lambda v: v.number)]
        combined = [sum(v.metrics.histogram[i] for v in viewers) for i in range(60002)]
        report['latencyMs'] = histogram_percentiles(combined, report['uniqueDeliveries'], max(v.metrics.maximum for v in viewers))
        report['metricStorageBytes'] = sent.storage_bytes() + sum(v.metrics.storage_bytes() for v in viewers)
        report['deliveryComplete'] = not report['abortReason'] and not report['clientErrors'] and len(sent) == count * cycles and all(v.metrics.received == len(sent) and not v.metrics.duplicates for v in viewers)
        # 以一个上报周期作为本轮工程观察门槛，正式延迟指标仍需业务单独签收。
        report['latencyGateMs'] = 5000
        report['passed'] = report['deliveryComplete'] and report['latencyMs']['p95'] is not None and report['latencyMs']['p95'] <= report['latencyGateMs']
    except Exception as error:
        # 失败也保留已建立连接和已发送数量，原始异常不包含在公开报告中。
        report.update(failureType=type(error).__name__, messagesAttempted=len(sent),
            uniqueDeliveries=sum(v.metrics.received for v in viewers))
    finally:
        stop.set()
        for viewer in viewers:
            viewer.close()
        revoked, revoke_failures = 0, 0
        for ticket in tickets:
            try:
                api.call('/dim3d/scene/publication/ticket/' + ticket['ticketId'], 'DELETE')
                revoked += 1
            except Exception:
                revoke_failures += 1
        report.update(ticketsRevoked=revoked, ticketRevokeFailures=revoke_failures)
        logout_failures = 0
        for viewer_api in viewer_apis:
            try:
                viewer_api.logout()
            except Exception:
                logout_failures += 1
        # 失败轮也按实际登录返回编号统计，不以配置名称或预期连接数冒充独立身份。
        report['distinctAccounts'] = len({viewer_api.user_id for viewer_api in viewer_apis}) if 'viewers' in secrets else 1
        report.update(viewerSessionsLoggedOut=len(viewer_apis) - logout_failures, viewerLogoutFailures=logout_failures)
        if logout_failures:
            report['passed'] = False
        if revoke_failures:
            report['passed'] = False
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
    parser.add_argument('--scope', choices=['draft', 'publication'], default='draft')
    args = parser.parse_args()
    if not 1 <= args.devices <= 1000 or not 1 <= args.clients <= 50 or not 1 <= args.cycles <= 120:
        parser.error('本工具限于 1000 逻辑设备、50 条连接及 120 轮，禁止无界压力')
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
                args.devices, args.clients, args.cycles, args.scope)
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
