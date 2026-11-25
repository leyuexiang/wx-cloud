"""部署 148 回环持久化 Kafka，凭据留在目标主机的受限目录。"""

import argparse
import json
import os
import secrets
import socket
import subprocess
import time
from pathlib import Path


ROOT = Path('/opt/wx/editor-test148/iot-kafka148')
CONTAINER = 'wx-iot-kafka148'
VOLUME = 'wx-iot-kafka148-data'
IMAGE = 'apache/kafka:3.9.2'
ENDPOINT = '127.0.0.1:19092'
CONTROLLER = '127.0.0.1:19093'
MOUNT = '/etc/wx-iot-kafka'
RECORD_TOPIC = 'twin.event.v1'
DEAD_TOPIC = 'twin.event.v1.dimensions.dlt'
WS_TOPIC = 'dimensions-iot-websocket.v1'
CHECK_TOPIC = 'wx.iot.kafka.preflight'
PROJECTION_GROUP = 'dimensions-iot-state-v1'
WS_GROUP_PREFIX = 'dimensions-iot-websocket-'


def run(*args, payload=None, timeout=40, check=True):
    result = subprocess.run(args, input=payload, text=True, capture_output=True, timeout=timeout)
    if check and result.returncode:
        # 具体配置由目标主机维护，命令出错时只报告程序与退出码。
        raise RuntimeError(f'{args[0]} 执行失败，退出码 {result.returncode}')
    return result


def write(file, value, group=None):
    path = ROOT / file
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    with os.fdopen(os.open(path, flags, 0o600), 'w', encoding='utf-8') as stream:
        stream.write(value)
        if group is not None:
            os.fchown(stream.fileno(), 0, group)
            os.fchmod(stream.fileno(), 0o640)
    return path


def broker_cli(command, identity, *args, payload=None, timeout=40, check=True):
    flag = {
        'kafka-console-producer.sh': '--producer.config',
        'kafka-console-consumer.sh': '--consumer.config',
    }.get(command, '--command-config')
    return run('docker', 'exec', '-i', CONTAINER, f'/opt/kafka/bin/{command}',
               '--bootstrap-server', ENDPOINT, flag, f'{MOUNT}/{identity}.properties', *args,
               payload=payload, timeout=timeout, check=check)


def wait_for_broker():
    for _ in range(15):
        try:
            broker_cli('kafka-topics.sh', 'admin', '--list', timeout=8)
            return
        except (RuntimeError, subprocess.TimeoutExpired):
            time.sleep(1)
    raise RuntimeError('Kafka 未通过带证书的健康检查')


def create_pkcs12(identity, server=False):
    key = ROOT / f'{identity}.key'
    csr = ROOT / f'{identity}.csr'
    cert = ROOT / f'{identity}.crt'
    extensions = 'extendedKeyUsage=clientAuth\n'
    if server:
        extensions = 'extendedKeyUsage=serverAuth,clientAuth\nsubjectAltName=DNS:localhost,IP:127.0.0.1\n'
    extra = write(f'{identity}.ext', extensions)
    run('openssl', 'req', '-new', '-newkey', 'rsa:3072', '-nodes',
        '-subj', f'/CN=wx-kafka-{identity}', '-keyout', str(key), '-out', str(csr))
    run('openssl', 'x509', '-req', '-in', str(csr), '-CA', str(ROOT / 'ca.crt'), '-CAkey', str(ROOT / 'ca.key'),
        '-CAcreateserial', '-days', '365', '-sha256', '-extfile', str(extra), '-out', str(cert))
    password = secrets.token_hex(24)
    keystore = ROOT / f'{identity}.p12'
    run('openssl', 'pkcs12', '-export', '-inkey', str(key), '-in', str(cert),
        '-certfile', str(ROOT / 'ca.crt'), '-out', str(keystore), '-passout', 'stdin', payload=password + '\n')
    for file in (key, csr, cert, extra):
        file.unlink()
    os.chown(keystore, 0, 1000)
    keystore.chmod(0o640)
    return password


def tls_properties(identity, password):
    return (f'ssl.keystore.type=PKCS12\nssl.keystore.location={MOUNT}/{identity}.p12\n'
            f'ssl.keystore.password={password}\nssl.key.password={password}\n'
            f'ssl.truststore.type=PEM\nssl.truststore.location={MOUNT}/ca.crt\n'
            'ssl.endpoint.identification.algorithm=https\nssl.enabled.protocols=TLSv1.3,TLSv1.2\n')


def allow(identity, resource, operations, prefix=False):
    options = ('--resource-pattern-type', 'prefixed') if prefix else ()
    broker_cli('kafka-acls.sh', 'admin', '--add', '--allow-principal', f'User:CN=wx-kafka-{identity}',
               *resource, *options, *(item for operation in operations for item in ('--operation', operation)))


def provision():
    # 先核对名称及端口，已有集群一律保留并走验证入口。
    if ROOT.exists() or run('docker', 'inspect', CONTAINER, check=False).returncode == 0 \
            or run('docker', 'volume', 'inspect', VOLUME, check=False).returncode == 0:
        raise RuntimeError('专用目录、容器或数据卷已存在，使用 verify 核对现场')
    for endpoint in (ENDPOINT, CONTROLLER):
        address, port = endpoint.split(':')
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
            probe.bind((address, int(port)))
    image_id = run('docker', 'image', 'inspect', IMAGE, '-f', '{{.Id}}').stdout.strip()
    os.umask(0o077)
    ROOT.mkdir(mode=0o710)
    os.chown(ROOT, 0, 1000)
    # 显式恢复目录穿越权限，避免安全 umask 将请求的 0710 收紧为 0700。
    ROOT.chmod(0o710)
    run('openssl', 'req', '-x509', '-newkey', 'rsa:3072', '-nodes', '-sha256', '-days', '730',
        '-subj', '/CN=WX-IoT-Kafka148-CA', '-addext', 'basicConstraints=critical,CA:TRUE',
        '-addext', 'keyUsage=critical,keyCertSign,cRLSign', '-keyout', str(ROOT / 'ca.key'), '-out', str(ROOT / 'ca.crt'))
    (ROOT / 'ca.crt').chmod(0o644)
    server_password = create_pkcs12('broker', server=True)
    for identity in ('admin', 'ingest', 'dimensions'):
        properties = 'security.protocol=SSL\n' + tls_properties(identity, create_pkcs12(identity))
        write(f'{identity}.properties', properties + 'request.timeout.ms=5000\ndefault.api.timeout.ms=10000\n', group=1000)
    server = f'''node.id=1
process.roles=broker,controller
listeners=CLIENT://{ENDPOINT},CONTROLLER://{CONTROLLER}
advertised.listeners=CLIENT://{ENDPOINT}
listener.security.protocol.map=CLIENT:SSL,CONTROLLER:SSL
controller.listener.names=CONTROLLER
controller.quorum.voters=1@{CONTROLLER}
inter.broker.listener.name=CLIENT
ssl.client.auth=required
authorizer.class.name=org.apache.kafka.metadata.authorizer.StandardAuthorizer
allow.everyone.if.no.acl.found=false
super.users=User:CN=wx-kafka-broker;User:CN=wx-kafka-admin
log.dirs=/var/lib/kafka/data
auto.create.topics.enable=false
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
default.replication.factor=1
log.retention.hours=168
log.retention.bytes=536870912
log.segment.bytes=67108864
''' + tls_properties('broker', server_password)
    write('server.properties', server, group=1000)
    write('image-id', image_id + '\n')
    run('docker', 'volume', 'create', '--label', 'wx.service=iot-kafka148', VOLUME)
    resume()


def resume():
    """使用已生成的证书恢复初始化，保留原数据卷和集群编号。"""
    if not (ROOT / 'server.properties').is_file() or not (ROOT / 'image-id').is_file():
        raise RuntimeError('恢复需要已有证书配置和镜像编号')
    ROOT.chmod(0o710)
    image_id = (ROOT / 'image-id').read_text(encoding='utf-8').strip()
    mounts = ('-v', f'{ROOT}:{MOUNT}:ro', '-v', f'{VOLUME}:/var/lib/kafka/data')
    # Docker 的挂载参数会自动补建空卷，恢复前必须单独核对原卷仍存在。
    if run('docker', 'volume', 'inspect', VOLUME, check=False).returncode:
        raise RuntimeError('持久卷不存在，禁止自动创建同名空卷')
    cluster_file = ROOT / 'cluster-id'
    if not cluster_file.exists():
        empty = run('docker', 'run', '--rm', '--network', 'none', *mounts, '--entrypoint', '/bin/sh',
                    image_id, '-c', 'test -z "$(ls -A /var/lib/kafka/data)"', check=False)
        if empty.returncode:
            raise RuntimeError('数据卷已有内容但缺少集群编号，需核对原集群')
        cluster = run('docker', 'run', '--rm', '--network', 'none', '--entrypoint',
                      '/opt/kafka/bin/kafka-storage.sh', image_id, 'random-uuid').stdout.strip()
        write('cluster-id', cluster + '\n')
    cluster = cluster_file.read_text(encoding='utf-8').strip()
    verify_storage(image_id, mounts, cluster)
    container = run('docker', 'inspect', CONTAINER, check=False)
    if container.returncode:
        run('docker', 'run', '--rm', '--network', 'none', *mounts, '--entrypoint', '/opt/kafka/bin/kafka-storage.sh',
            image_id, 'format', '--ignore-formatted', '-t', cluster, '-c', f'{MOUNT}/server.properties', timeout=90)
        run('docker', 'run', '-d', '--name', CONTAINER, '--network', 'host', '--restart', 'unless-stopped',
            '--memory', '1200m', '--log-driver', 'json-file', '--log-opt', 'max-size=10m', '--log-opt', 'max-file=3',
            '--env', 'KAFKA_HEAP_OPTS=-Xms256m -Xmx512m', *mounts, '--entrypoint', '/opt/kafka/bin/kafka-server-start.sh',
            image_id, f'{MOUNT}/server.properties')
    else:
        state = json.loads(container.stdout)[0]
        if state['Image'] != image_id or state['HostConfig']['NetworkMode'] != 'host' \
                or not any(m.get('Name') == VOLUME and m['Destination'] == '/var/lib/kafka/data'
                           for m in state['Mounts']) \
                or not any(m.get('Source') == str(ROOT) and m['Destination'] == MOUNT and not m['RW']
                           for m in state['Mounts']):
            raise RuntimeError('已有容器的镜像、网络或挂载不匹配')
        if not state['State']['Running']:
            run('docker', 'start', CONTAINER)
    wait_for_broker()
    for topic, hours in ((RECORD_TOPIC, 168), (DEAD_TOPIC, 168), (WS_TOPIC, 2), (CHECK_TOPIC, 1)):
        broker_cli('kafka-topics.sh', 'admin', '--create', '--if-not-exists', '--topic', topic, '--partitions', '1', '--replication-factor', '1',
                   '--config', f'retention.ms={hours * 3600 * 1000}',
                   '--config', 'segment.ms=' + str(min(hours, 1) * 3600 * 1000))
    allow('ingest', ('--topic', RECORD_TOPIC), ('Write', 'Describe'))
    allow('dimensions', ('--topic', RECORD_TOPIC), ('Read', 'Describe'))
    allow('dimensions', ('--topic', DEAD_TOPIC), ('Write', 'Describe'))
    allow('dimensions', ('--topic', WS_TOPIC), ('Read', 'Write', 'Describe'))
    allow('dimensions', ('--group', PROJECTION_GROUP), ('Read',))
    allow('dimensions', ('--group', WS_GROUP_PREFIX), ('Read',), prefix=True)
    for identity in ('ingest', 'dimensions'):
        allow(identity, ('--cluster',), ('IdempotentWrite',))
    if not (ROOT / 'provisioned').exists():
        write('provisioned', cluster + '\n')
    print(json.dumps({'state': 'PROVISIONED', 'endpoint': ENDPOINT, 'volume': VOLUME}))


def verify_storage(image_id, mounts, cluster):
    """格式化前核对数据来源，卷丢失或换成其他集群时停止恢复。"""
    if run('docker', 'volume', 'inspect', VOLUME, check=False).returncode:
        raise RuntimeError('持久卷不存在，禁止自动创建同名空卷')
    metadata = run('docker', 'run', '--rm', '--network', 'none', *mounts, '--entrypoint', '/bin/sh',
                   image_id, '-c', 'if [ -f /var/lib/kafka/data/meta.properties ]; then cat /var/lib/kafka/data/meta.properties; fi').stdout
    if not metadata.strip():
        if (ROOT / 'provisioned').exists():
            raise RuntimeError('已交付集群缺少数据元信息，禁止重新格式化')
        return
    values = dict(line.split('=', 1) for line in metadata.splitlines() if '=' in line and not line.startswith('#'))
    if values.get('cluster.id') != cluster:
        raise RuntimeError('持久卷集群编号不匹配，禁止恢复')


def verify():
    if not (ROOT / 'provisioned').is_file():
        raise RuntimeError('缺少完成标记，请核对初始化阶段')
    wait_for_broker()
    for identity, expected in (
        ('ingest', {RECORD_TOPIC}),
        ('dimensions', {RECORD_TOPIC, DEAD_TOPIC, WS_TOPIC}),
    ):
        visible = set(broker_cli('kafka-topics.sh', identity, '--list').stdout.split())
        if visible != expected:
            raise RuntimeError(f'{identity} 主题可见范围错误')
        forbidden = broker_cli('kafka-topics.sh', identity, '--describe', '--topic', CHECK_TOPIC, check=False)
        diagnostic = forbidden.stdout + forbidden.stderr
        if forbidden.returncode == 0 or not any(text in diagnostic for text in (
                'TopicAuthorizationException', 'does not exist as expected')):
            raise RuntimeError(f'{identity} 对未授权主题的拒绝检查失败')
    print(json.dumps({'state': 'VERIFIED', 'endpoint': ENDPOINT, 'leastPrivilegeChecked': True}))


def verify_restart():
    verify()
    before = broker_cli('kafka-get-offsets.sh', 'admin', '--topic', CHECK_TOPIC).stdout.strip()
    offset = int(before.split(':')[-1])
    marker = 'persistence-' + secrets.token_hex(12)
    broker_cli('kafka-console-producer.sh', 'admin', '--topic', CHECK_TOPIC,
               '--producer-property', 'acks=all', payload=marker + '\n')
    for identity, forbidden_topic in (('ingest', WS_TOPIC), ('dimensions', RECORD_TOPIC)):
        result = broker_cli('kafka-console-producer.sh', identity, '--topic', forbidden_topic,
                            '--producer-property', 'max.block.ms=5000', payload='denied-probe\n', check=False)
        if 'TopicAuthorizationException' not in result.stdout + result.stderr:
            raise RuntimeError(f'{identity} 的越权写入未被拒绝')
    run('docker', 'restart', '--time', '20', CONTAINER, timeout=40)
    wait_for_broker()
    received = broker_cli('kafka-console-consumer.sh', 'admin', '--topic', CHECK_TOPIC,
                          '--partition', '0', '--offset', str(offset), '--max-messages', '1', '--timeout-ms', '12000')
    if received.stdout.strip() != marker:
        raise RuntimeError('Kafka 重启后未读取到原测试记录')
    verify()
    state = json.loads(run('docker', 'inspect', CONTAINER).stdout)[0]
    if state['HostConfig']['RestartPolicy']['Name'] != 'unless-stopped':
        raise RuntimeError('Kafka 缺少进程恢复策略')
    report = {'state': 'VERIFIED', 'endpoint': ENDPOINT, 'volume': VOLUME,
              'restartRetention': True, 'writeRestrictions': True, 'clientAuthentication': 'mutual TLS',
              'automaticRestart': 'unless-stopped', 'singleNode': True}
    path = ROOT / 'verification.json'
    path.write_text(json.dumps(report, indent=2), encoding='utf-8')
    path.chmod(0o600)
    print(json.dumps(report))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('provision', 'resume', 'verify', 'verify-restart'))
    {'provision': provision, 'resume': resume, 'verify': verify, 'verify-restart': verify_restart}[parser.parse_args().action]()
