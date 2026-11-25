"""部署 148 虚拟设备试运行代理，订阅范围和发布身份彼此分开。"""

import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess


ROOT = Path('/home/wx-iot-mqtt-lab148')
NAME = 'wx-iot-mqtt-lab148'
IMAGE = 'sha256:5fe9ea977b7abafc6b66d2c0c76f1a60c168aa0be5a874baa90d2844d432bc20'
PORT = 18884
REFERENCE = 'IOT_MQTT_PILOT'
WRITERS = {'lab-switch': 'lab/iot/tenant-2/switch', 'lab-temperature': 'lab/iot/tenant-2/temperature'}


def run(*args):
    result = subprocess.run(args, capture_output=True, text=True, timeout=45)
    if result.returncode:
        # 标准输出可能含有原服务环境或密钥，只输出失败阶段。
        raise RuntimeError(f'{args[0]} 退出码 {result.returncode}')
    return result.stdout


def write(path, text, mode=0o600, group=0):
    with os.fdopen(os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, mode), 'w', encoding='utf-8') as out:
        out.write(text)
        os.fchown(out.fileno(), 0, group)
        os.fchmod(out.fileno(), mode)


def fingerprint(path):
    return run('openssl', 'x509', '-in', str(path), '-noout', '-fingerprint', '-sha256').strip().split('=', 1)[1]


def provision():
    os.umask(0o077)
    if ROOT.exists():
        raise RuntimeError('试运行目录已存在，请先核对现有代理状态')
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', PORT))
    run('docker', 'image', 'inspect', IMAGE)
    current = json.loads(run('docker', 'inspect', 'wx-editor-iot-data08'))[0]
    env = dict(value.split('=', 1) for value in current['Config']['Env'])
    subscriber = json.loads(env[REFERENCE])
    name, password = subscriber['username'], subscriber['password']
    if not re.fullmatch(r'[A-Za-z0-9_.-]{1,64}', name) or name in WRITERS or not password or '\n' in password or '\r' in password:
        raise ValueError('接入账号不符合专用代理的凭据格式')

    ROOT.mkdir(mode=0o700)
    config = ROOT / 'config'
    config.mkdir(mode=0o750)
    os.chown(config, 0, 1883)
    config.chmod(0o750)
    data = ROOT / 'data'
    data.mkdir(mode=0o700)
    os.chown(data, 1883, 1883)
    ca_key = ROOT / 'ca.key'
    ca = ROOT / 'ca.crt'
    key = config / 'server.key'
    csr = ROOT / 'server.csr'
    extensions = ROOT / 'server.ext'
    write(extensions, 'extendedKeyUsage=serverAuth\nsubjectAltName=DNS:localhost,IP:127.0.0.1\n')
    run('openssl', 'req', '-x509', '-newkey', 'rsa:3072', '-nodes', '-sha256', '-days', '365',
        '-subj', '/CN=WX MQTT Lab148 CA', '-addext', 'basicConstraints=critical,CA:TRUE',
        '-addext', 'keyUsage=critical,keyCertSign,cRLSign', '-keyout', str(ca_key), '-out', str(ca))
    run('openssl', 'req', '-new', '-newkey', 'rsa:2048', '-nodes', '-subj', '/CN=localhost',
        '-keyout', str(key), '-out', str(csr))
    run('openssl', 'x509', '-req', '-in', str(csr), '-CA', str(ca), '-CAkey', str(ca_key),
        '-CAcreateserial', '-days', '180', '-sha256', '-extfile', str(extensions), '-out', str(config / 'server.crt'))
    os.chown(key, 0, 1883)
    key.chmod(0o640)
    (config / 'server.crt').chmod(0o644)

    writers = {user: secrets.token_urlsafe(24) for user in WRITERS}
    write(ROOT / 'writers.private.json', json.dumps(writers, indent=2) + '\n')
    # 哈希工具在隔离容器中转换临时明文文件，不通过命令行参数传递密码。
    passfile = config / 'password_file'
    write(passfile, ''.join(user + ':' + secret + '\n' for user, secret in {**writers, name: password}.items()))
    try:
        run('docker', 'run', '--rm', '--network', 'none', '--user', '0:0', '-v', f'{config}:/seed',
            '--entrypoint', 'mosquitto_passwd', IMAGE, '-U', '/seed/password_file')
    except Exception:
        passfile.unlink()
        raise
    os.chown(passfile, 1883, 1883)
    passfile.chmod(0o640)
    acl = ''.join(f'user {user}\ntopic write {topic}\n' for user, topic in WRITERS.items())
    acl += f'user {name}\n' + ''.join(f'topic read {topic}\n' for topic in WRITERS.values())
    write(config / 'acl', acl, 0o640, 1883)
    os.chown(config / 'acl', 1883, 1883)
    write(config / 'mosquitto.conf', '''listener 18884 127.0.0.1
allow_anonymous false
password_file /mosquitto/config/password_file
acl_file /mosquitto/config/acl
certfile /mosquitto/config/server.crt
keyfile /mosquitto/config/server.key
tls_version tlsv1.2
persistence true
persistence_location /mosquitto/data/
autosave_interval 30
persistent_client_expiration 1d
max_connections 12
max_packet_size 65536
max_queued_messages 256
max_queued_bytes 1048576
max_qos 1
retain_available false
log_dest stdout
''', 0o640, 1883)
    run('docker', 'run', '-d', '--name', NAME, '--network', 'host', '--restart', 'unless-stopped',
        '--user', '1883:1883', '--read-only', '--memory', '96m', '--pids-limit', '64', '--cap-drop', 'ALL',
        '--security-opt', 'no-new-privileges:true', '--log-opt', 'max-size=5m', '--log-opt', 'max-file=2',
        '-v', f'{config}:/mosquitto/config:ro', '-v', f'{data}:/mosquitto/data', IMAGE)
    print(json.dumps({'endpoint': f'127.0.0.1:{PORT}', 'caSha256': fingerprint(ca), 'topics': WRITERS,
                      'credentialReference': REFERENCE, 'image': IMAGE}))


if __name__ == '__main__':
    provision()
