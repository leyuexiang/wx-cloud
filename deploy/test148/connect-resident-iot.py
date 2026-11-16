"""逐个接入常驻应用，保存原容器用于回退，不修改业务数据库。"""

import argparse
import copy
import hashlib
import http.client
import json
import os
from pathlib import Path
import socket
import time
import urllib.parse
import urllib.request


SERVICES = {
    'dimensions148': ('wx-editor-dimensions-data08', 48093, '/opt/wx/editor-test148/releases/iot-resident-kafka-20261116', 'dimensions'),
    'ingest148': ('wx-editor-iot-data08', 48096, '/opt/wx/editor-test148/releases/iot-resident-kafka-20261116', 'ingest'),
    'management246': ('jtyf-editor246-iot-1', 48096, '/home/jtyf-editor246/releases/iot-resident-kafka-20261116', 'management246'),
}


class DockerConnection(http.client.HTTPConnection):
    """仅访问宿主已有的本地 Docker 套接字，不向网络开放管理接口。"""
    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect('/var/run/docker.sock')


def docker(method, path, body=None):
    connection = DockerConnection('localhost', timeout=45)
    try:
        data = json.dumps(body).encode('utf-8') if body is not None else None
        connection.request(method, '/v1.41' + path, data, {'Content-Type': 'application/json'})
        response = connection.getresponse()
        raw = response.read()
        if not 200 <= response.status < 300:
            # 请求可能包含环境秘密，错误响应不直接回显。
            raise RuntimeError('容器操作失败，状态码 ' + str(response.status))
        return json.loads(raw) if raw else None
    finally:
        connection.close()


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def save(path, value):
    # 环境快照及容器创建参数只在目标节点的受限文件中保存。
    with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w', encoding='utf-8') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)


def validate_transport(credentials, identity):
    """健康接口无法发现分发编码缺失，换包前同时校验双向认证和消息编解码。"""
    properties = credentials / 'application.properties'
    values = dict(line.split('=', 1) for line in properties.read_text(encoding='utf-8').splitlines()
                  if '=' in line and not line.lstrip().startswith(('#', '!')))
    required = {
        'spring.kafka.bootstrap-servers': '127.0.0.1:19092',
        'spring.kafka.properties.security.protocol': 'SSL',
        'spring.kafka.properties.ssl.endpoint.identification.algorithm': 'https',
        'spring.kafka.listener.auto-startup': 'false',
    }
    if identity == 'dimensions':
        package = 'cn.iocoder.wx.framework.websocket.core.sender.kafka.'
        required.update({
            'spring.kafka.producer.value-serializer': package + 'KafkaWebSocketMessageSerializer',
            'spring.kafka.consumer.value-deserializer': package + 'KafkaWebSocketMessageDeserializer',
            'wx.iot-state.listener-auto-startup': 'true',
            'wx.websocket.sender-kafka.auto-startup': 'true',
        })
    if any(values.get(key) != value for key, value in required.items()):
        raise ValueError('Kafka 认证、监听或 WebSocket 编解码配置不完整')


def request_for(original, artifact, credentials, selinux=False):
    """复用原镜像、身份、环境、健康检查、资源上限及安全设置，仅替换本批挂载。"""
    if original['HostConfig']['NetworkMode'] != 'host':
        raise ValueError('常驻接入仅支持已核验的主机网络')
    mounts = original['Mounts']
    jar, = [m for m in mounts if m['Destination'] == '/opt/wx/app.jar' and m['Type'] == 'bind' and not m['RW']]
    if any(m['Destination'] == '/opt/wx/kafka' for m in mounts):
        raise ValueError('当前应用已挂载 Kafka 配置，请核对版本')
    config = copy.deepcopy(original['Config'])
    config['Image'] = original['Image']
    config['Hostname'] = ''
    config['Cmd'] = (config.get('Cmd') or []) + ['--spring.config.additional-location=file:/opt/wx/kafka/application.properties']
    # 原 Compose 仍能识别本服务；移除旧内容摘要，后续编排会按新清单核对。
    labels = config.get('Labels') or {}
    labels.pop('com.docker.compose.config-hash', None)
    config['Labels'] = labels
    host = copy.deepcopy(original['HostConfig'])
    binds = host.get('Binds') or []
    previous = jar['Source'] + ':/opt/wx/app.jar:'
    replacement = str(artifact) + ':/opt/wx/app.jar:'
    if sum(b.startswith(previous) for b in binds) != 1:
        raise ValueError('原应用包挂载不明确')
    host['Binds'] = [replacement + b[len(previous):] if b.startswith(previous) else b for b in binds]
    host['Binds'].append(str(credentials) + ':/opt/wx/kafka:ro' + (',Z' if selinux else ''))
    host['RestartPolicy'] = {'Name': 'unless-stopped', 'MaximumRetryCount': 0}
    config['HostConfig'] = host
    return config


def healthy(port, name):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    for _ in range(75):
        if not docker('GET', '/containers/' + name + '/json')['State']['Running']:
            raise RuntimeError('候选应用已退出')
        try:
            with opener.open('http://127.0.0.1:' + str(port) + '/actuator/health', timeout=2) as response:
                if json.load(response).get('status') == 'UP':
                    return
        except (OSError, ValueError):
            pass
        time.sleep(2)
    raise RuntimeError('常驻应用未在限定时间恢复健康')


def prepare(service, artifact, expected):
    name, port, directory, identity = SERVICES[service]
    root = Path(directory)
    root.mkdir(parents=True, exist_ok=True, mode=0o700)
    credentials = root / 'kafka' / identity
    if sha(artifact) != expected or not (credentials / 'application.properties').is_file():
        raise ValueError('候选包摘要或服务凭据不符')
    validate_transport(credentials, identity)
    original = docker('GET', '/containers/' + name + '/json')
    request = request_for(original, artifact, credentials, service == 'management246')
    config_mounts = [m for m in original['Mounts'] if m['Destination'] in ('/opt/wx/app.jar', '/opt/wx/application.yaml')]
    plan = {'service': service, 'name': name, 'port': port, 'originalId': original['Id'], 'artifact': str(artifact),
            'sha256': expected, 'files': {m['Source']: sha(m['Source']) for m in config_mounts},
            'credentialsSha256': sha(credentials / 'application.properties'),
            'backup': name + '-before-kafka-1116', 'request': request}
    save(root / (service + '-original.private.json'), original)
    save(root / (service + '-plan.private.json'), plan)
    print(json.dumps({'service': service, 'status': 'PREPARED', 'sha256': expected}))


def rollback(service):
    name, port, directory, _ = SERVICES[service]
    root = Path(directory)
    plan = json.loads((root / (service + '-plan.private.json')).read_text(encoding='utf-8'))
    backup = docker('GET', '/containers/' + plan['backup'] + '/json')
    if backup['Id'] != plan['originalId']:
        raise RuntimeError('原容器编号不匹配，停止回退')
    report = json.loads((root / (service + '-report.json')).read_text(encoding='utf-8'))
    if docker('GET', '/containers/' + name + '/json')['Id'] != report['containerId']:
        raise RuntimeError('当前容器已变化，停止回退')
    docker('DELETE', '/containers/' + name + '?force=true')
    docker('POST', '/containers/' + plan['backup'] + '/rename?name=' + name)
    docker('POST', '/containers/' + name + '/start')
    healthy(port, name)
    print(json.dumps({'service': service, 'status': 'ROLLED_BACK'}))


def apply(service):
    name, port, directory, identity = SERVICES[service]
    root = Path(directory)
    plan = json.loads((root / (service + '-plan.private.json')).read_text(encoding='utf-8'))
    if docker('GET', '/containers/' + name + '/json')['Id'] != plan['originalId'] \
            or sha(plan['artifact']) != plan['sha256'] \
            or any(sha(path) != expected for path, expected in plan['files'].items()) \
            or sha(root / 'kafka' / identity / 'application.properties') != plan['credentialsSha256']:
        raise RuntimeError('预检后容器、包或配置已变化，停止切换')
    # 切换前确认旧容器能保留；重名直接失败，不覆盖已有回退材料。
    names = {n.lstrip('/') for c in docker('GET', '/containers/json?all=true') for n in c['Names']}
    if plan['backup'] in names:
        raise RuntimeError('同名回退容器已存在')
    docker('POST', '/containers/' + name + '/stop?t=25')
    try:
        docker('POST', '/containers/' + name + '/rename?name=' + plan['backup'])
    except Exception:
        # 完成旧容器改名前失败，仍按原名称恢复服务。
        docker('POST', '/containers/' + name + '/start')
        healthy(port, name)
        raise
    created = False
    try:
        docker('POST', '/containers/create?name=' + urllib.parse.quote(name), plan['request'])
        created = True
        docker('POST', '/containers/' + name + '/start')
        healthy(port, name)
    except Exception:
        if created:
            docker('DELETE', '/containers/' + name + '?force=true')
        docker('POST', '/containers/' + plan['backup'] + '/rename?name=' + name)
        docker('POST', '/containers/' + name + '/start')
        healthy(port, name)
        raise RuntimeError('新应用未就绪，已恢复原容器') from None
    report = {'service': service, 'status': 'CONNECTED', 'sha256': plan['sha256'], 'health': 'UP',
              'backupContainer': plan['backup'], 'restartPolicy': 'unless-stopped',
              'containerId': docker('GET', '/containers/' + name + '/json')['Id']}
    save(root / (service + '-report.json'), report)
    print(json.dumps(report))


if __name__ == '__main__':
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'apply', 'rollback'))
    parser.add_argument('service', choices=tuple(SERVICES))
    parser.add_argument('--artifact', type=Path)
    parser.add_argument('--sha256')
    args = parser.parse_args()
    if args.action == 'prepare':
        if args.artifact is None or args.sha256 is None:
            parser.error('prepare 需要候选包路径及摘要')
        prepare(args.service, args.artifact, args.sha256)
    else:
        {'apply': apply, 'rollback': rollback}[args.action](args.service)
