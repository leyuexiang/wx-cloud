"""148仅增加用途受限节点公钥和三表账号；私有引导结果只通过受信SSH传递。"""
import ipaddress
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys

import yaml
from app246 import authorized_key
from server_support import mysql_command

ROOT = Path('/opt/wx/editor-test148')
OUT = Path('/home/wx-editor-build-archives/iot-dist-20261111')


def run(*args, **kwargs):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs)
    if result.returncode: raise RuntimeError('节点148准备步骤失败')
    return result.stdout


def main():
    os.umask(0o077)
    node = json.load(sys.stdin)['nodePublicKey']
    line = authorized_key(node)
    state_path = OUT / 'bootstrap.private.json'
    if state_path.exists():
        state = json.loads(state_path.read_text())
        if state['nodePublicKey'] != node: raise RuntimeError('既有节点身份不匹配')
        print(json.dumps(state))
        return
    networks = json.loads(run('docker', 'inspect', 'mysql8', '--format', '{{json .NetworkSettings.Networks}}').decode())
    hosts = sorted({str(ipaddress.ip_address(n['Gateway'])) for n in networks.values() if n.get('Gateway')})
    assert hosts and all(h.startswith('172.') for h in hosts)
    before = run(*mysql_command('mysql'), input=b"SELECT COUNT(*) FROM user WHERE user='iot246_app';").decode().strip().splitlines()[-1]
    if before != '0': raise RuntimeError('专用账号已存在，不能覆盖')
    password = secrets.token_urlsafe(48)
    sql = []
    for host in hosts:
        sql.append("CREATE USER 'iot246_app'@'%s' IDENTIFIED BY '%s';" % (host, password))
        for table in ['wx_iot_connector', 'wx_iot_device_binding', 'wx_iot_message_inbox']:
            sql.append("GRANT SELECT,INSERT,UPDATE,DELETE ON wx_editor_data08.%s TO 'iot246_app'@'%s';" % (table, host))
    run(*mysql_command(), input='\n'.join(sql).encode())
    env = {}
    for item in (ROOT / '.env').read_text().splitlines():
        if item and not item.lstrip().startswith('#') and '=' in item:
            k, v = item.split('=', 1); env[k] = v.strip().strip('\"\'')
    auth = Path('/root/.ssh/authorized_keys')
    previous = auth.read_bytes() if auth.exists() else b''
    (OUT / 'authorized_keys.before').write_bytes(previous)
    with auth.open('ab') as stream:
        stream.write((b'\n' if previous and not previous.endswith(b'\n') else b'') + line.encode() + b'\n')
    auth.chmod(0o600)
    state = {'nodePublicKey': node, 'dbPassword': password, 'redisPassword': env['WX_REDIS_PASSWORD'],
             'hostPublicKey': Path('/etc/ssh/ssh_host_ed25519_key.pub').read_text().strip(),
             'iotConfig': yaml.safe_load((ROOT / 'releases/iot-management-20261111/iot.candidate.yaml').read_text()),
             'javaImage': 'sha256:fb388f77f8ccee590e977e538884e0539623e111ca02c3d63684662f55b2db30',
             'webImage': 'nginx@sha256:583b7f2fce1cdb8da5ad5c39ebd3c33b98b87d493190e548bdee1fa8c619c311'}
    state_path.write_text(json.dumps(state, ensure_ascii=False), encoding='utf-8')
    (OUT / 'setup-report.json').write_text(json.dumps({'prepared': True, 'databaseCreated': False,
        'account': 'iot246_app', 'accountHosts': hosts, 'tables': 3, 'privileges': ['SELECT','INSERT','UPDATE','DELETE'],
        'nodeExecutionAllowed': False, 'nodeInteractiveLoginAllowed': False,
        'newPublicRpcOrRedisPorts': False}, indent=2) + '\n')
    # 标准输出仅供本机受信编排进程内存消费；不得直接回显或写入仓库。
    print(json.dumps(state))


if __name__ == '__main__':
    try: main()
    except Exception as error:
        print('节点148准备失败：' + type(error).__name__, file=sys.stderr)
        raise SystemExit(1)
