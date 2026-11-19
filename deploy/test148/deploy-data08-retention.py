"""将已验收候选切换到12525的隔离三维服务，保持原配置和消息参数并保留原容器回退。"""
import argparse
import copy
import gzip
import hashlib
import http.client
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time
import urllib.request


ROOT = Path('/home/wx-data08-retention-20261119')
NAME = 'wx-editor-dimensions-data08'
BACKUP = NAME + '-before-retention-20261119'


class DockerConnection(http.client.HTTPConnection):
    """秘密只在宿主进程和本机Docker套接字之间传递，不进入命令行。"""
    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.connect('/var/run/docker.sock')


def docker(method, path, body=None):
    conn = DockerConnection('localhost', timeout=40)
    try:
        conn.request(method, '/v1.41' + path, body=json.dumps(body) if body else None,
                     headers={'Content-Type': 'application/json'})
        response = conn.getresponse()
        data = response.read()
        if response.status >= 400:
            raise RuntimeError('Docker操作失败，状态码' + str(response.status))
        return json.loads(data) if data else None
    finally:
        conn.close()


def inspect(name):
    return docker('GET', '/containers/' + name + '/json')


def health():
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen('http://127.0.0.1:48093/actuator/health', timeout=3) as response:
                if json.load(response).get('status') == 'UP': return
        except (OSError, ValueError):
            pass
        time.sleep(2)
    raise RuntimeError('隔离三维服务健康检查超时')


def save(name, value):
    path = ROOT / name
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')
    path.chmod(0o600)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('apply', 'rollback'))
    args = parser.parse_args()
    os.umask(0o077)
    if args.action == 'rollback':
        report = json.loads((ROOT / 'deployment.json').read_text())
        if inspect(NAME)['Id'] != report['containerId'] or inspect(BACKUP)['Id'] != report['backupContainerId']:
            raise RuntimeError('服务身份变化，拒绝自动回退')
        docker('POST', '/containers/' + NAME + '/stop?t=30')
        docker('POST', '/containers/' + NAME + '/rename?name=' + NAME + '-retention-rolled-back-20261119')
        docker('POST', '/containers/' + BACKUP + '/rename?name=' + NAME)
        docker('POST', '/containers/' + NAME + '/start')
        health()
        print(json.dumps({'rolledBack': True, 'health': 'UP'}))
        return
    if (ROOT / 'deployment.json').exists():
        raise RuntimeError('已有部署回执，禁止覆盖')
    state = json.loads((ROOT / 'candidate-state.json').read_text())
    accepted = json.loads((ROOT / 'verification.json').read_text())
    old = inspect(NAME)
    jar = ROOT / 'wx-module-3d.jar'
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    if not accepted['passed'] or state['sha256'] != digest or old['Id'] != state['originalId']:
        raise RuntimeError('候选验证、包摘要或原服务身份门禁未通过')
    config_path = Path(next(m['Source'] for m in old['Mounts'] if m['Destination'] == '/opt/wx/application.yaml'))
    if hashlib.sha256(config_path.read_bytes()).hexdigest() != state['configSha256']:
        raise RuntimeError('原服务配置已改变')
    if any(item.get('Names', []) == ['/' + BACKUP] for item in docker('GET', '/containers/json?all=1')):
        raise RuntimeError('备份容器名已存在')
    mysql = ['docker', 'exec', '-i', 'mysql8', 'sh', '-c',
             'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot -N -B wx_editor_data08']
    locks = subprocess.run(mysql, input='SELECT COUNT(*) FROM wx_3d_scene WHERE deleted=0 AND lock_expire>CURRENT_TIMESTAMP;',
                           text=True, capture_output=True, check=True).stdout.strip()
    if locks != '0': raise RuntimeError('存在活动编辑租约，停止切换')
    # 停写前做一致性快照；本次仅增量写引用，不执行结构或正文修改，回退保留新审计。
    command = ['docker', 'exec', 'mysql8', 'sh', '-c',
               'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --single-transaction --no-tablespaces wx_editor_data08 '
               'wx_3d_scene wx_3d_scene_revision wx_3d_scene_content wx_3d_scene_publication wx_3d_scene_request wx_3d_scene_bookmark']
    backup_path = ROOT / 'pre-deploy-scene-tables.sql.gz'
    with backup_path.open('wb') as stream:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        with gzip.GzipFile(fileobj=stream, mode='wb') as compressed:
            shutil.copyfileobj(process.stdout, compressed)
        process.stdout.close()
        _, stderr = process.communicate()
        if process.returncode:
            raise RuntimeError('备份失败，未停止服务')
    baseline = {name: {'id': inspect(name)['Id'], 'startedAt': inspect(name)['State']['StartedAt']}
                for name in ('wx-editor-dimensions', 'wx-editor-gateway', 'wx-editor-gateway-data08')}
    save('original-container.private.json', old)
    config = copy.deepcopy(old['Config'])
    config['Image'] = old['Image']
    config.pop('Hostname', None)
    config.pop('Domainname', None)
    host = copy.deepcopy(old['HostConfig'])
    original_jar = next(m for m in old['Mounts'] if m['Destination'] == '/opt/wx/app.jar')
    prefix = original_jar['Source'] + ':/opt/wx/app.jar:'
    if sum(value.startswith(prefix) for value in host['Binds']) != 1:
        raise RuntimeError('原程序挂载门禁失败')
    host['Binds'] = [str(jar) + ':/opt/wx/app.jar:ro' if value.startswith(prefix) else value for value in host['Binds']]
    config['Cmd'] = [value for value in config['Cmd'] if not value.startswith('--dim3d.scene-retention.')]
    config['Cmd'] += ['--dim3d.scene-retention.enabled=true', '--dim3d.scene-retention.request-retention-days=30']
    config['HostConfig'] = host
    created = False
    renamed = False
    try:
        docker('POST', '/containers/' + NAME + '/stop?t=30')
        docker('POST', '/containers/' + NAME + '/rename?name=' + BACKUP)
        renamed = True
        docker('POST', '/containers/create?name=' + NAME, config)
        created = True
        docker('POST', '/containers/' + NAME + '/start')
        health()
    except Exception:
        if created:
            docker('POST', '/containers/' + NAME + '/stop?t=10')
            docker('POST', '/containers/' + NAME + '/rename?name=' + NAME + '-retention-failed-20261119')
        if renamed:
            docker('POST', '/containers/' + BACKUP + '/rename?name=' + NAME)
        docker('POST', '/containers/' + NAME + '/start')
        health()
        raise RuntimeError('切换失败，已恢复原服务') from None
    current = inspect(NAME)
    report = {'health': 'UP', 'containerId': current['Id'], 'startedAt': current['State']['StartedAt'],
              'backupContainer': BACKUP, 'backupContainerId': old['Id'], 'sha256': digest,
              'backup': str(backup_path), 'backupSha256': hashlib.sha256(backup_path.read_bytes()).hexdigest(),
              'retentionEnabled': True, 'requestRetentionDays': 30, 'database': 'wx_editor_data08',
              'databaseMigration': False, 'otherServices': baseline, 'configUnchanged':
              hashlib.sha256(config_path.read_bytes()).hexdigest() == state['configSha256']}
    save('deployment.json', report)
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
