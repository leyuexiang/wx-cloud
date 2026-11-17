"""MODEL-09 双库发布。受限备份、空数据结构副本、独立候选与原容器回退分别留存回执。"""
import argparse
import copy
import http.client
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import time
import urllib.request

from server_support import file_sha256, mysql_command, runtime_identity

ROOT = Path('/opt/wx/editor-test148')
RELEASE = Path('/home/wx-model09-release-20261117')
SOURCE = Path('/home/wx-model09-source-20261117')
TARGETS = {'main': ('wx_editor', 'wx-editor-dimensions', 48092, 49212),
           'data08': ('wx_editor_data08', 'wx-editor-dimensions-data08', 48093, 49213)}
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def run(args, **kwargs):
    """外部诊断可能包含私有配置，失败仅输出退出码，完整备份限制在发布目录。"""
    result = subprocess.run(args, capture_output=True, **kwargs)
    if result.returncode:
        raise RuntimeError('外部命令失败，退出码 ' + str(result.returncode))
    return result.stdout


def sql(statement, database=None):
    return run(mysql_command(database) + ['-N', '-B'], input=statement.encode()).decode().strip()


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')


def inspect(name):
    return json.loads(run(['docker', 'inspect', name]))[0]


def healthy(port, timeout=180):
    """请求真实回环健康端点，有界等待启动与依赖恢复。"""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with OPENER.open('http://127.0.0.1:%s/actuator/health' % port, timeout=3) as response:
                if json.load(response).get('status') == 'UP':
                    return
        except (OSError, ValueError):
            pass
        time.sleep(2)
    raise RuntimeError('服务健康检查超时 ' + str(port))


class DockerConnection(http.client.HTTPConnection):
    """通过本机容器引擎套接字复制原生启动配置，环境变量始终留在服务器进程内。"""
    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.connect('/var/run/docker.sock')


def clone_container(original, name, mounts, candidate=False, port=None):
    """保留镜像、资源限制、日志、用户、健康及环境，只有本批明确的挂载和候选参数改变。"""
    config = copy.deepcopy(original['Config'])
    config['Image'] = original['Image']
    config.pop('Hostname', None)
    config.pop('Domainname', None)
    config.pop('MacAddress', None)
    host = copy.deepcopy(original['HostConfig'])
    host['Binds'] = [mounts.get(item['Destination'], item['Source']) + ':' + item['Destination']
                     + (':ro' if not item['RW'] else ':rw') for item in original['Mounts']]
    if candidate:
        host['RestartPolicy'] = {'Name': 'no', 'MaximumRetryCount': 0}
        # 空结构副本没有历史队列；候选使用独立日志与本地消息发送器，避免消费现场物联网消息。
        config['Entrypoint'] = ['java']
        config['Cmd'] = ['-Xms128m', '-Xmx768m', '-jar', '/opt/wx/app.jar',
                         '--spring.config.location=file:/opt/wx/application.yaml',
                         '--server.address=127.0.0.1', '--server.port=' + str(port),
                         '--wx.websocket.sender-type=local', '--wx.iot-state.enabled=false']
        config.pop('Healthcheck', None)
        config['Labels'] = {'wx.release': 'model09-20261117'}
    config['HostConfig'] = host
    connection = DockerConnection('localhost', timeout=30)
    connection.request('POST', '/v1.41/containers/create?name=' + name,
                       body=json.dumps(config), headers={'Content-Type': 'application/json'})
    response = connection.getresponse()
    body = response.read()
    connection.close()
    if response.status != 201:
        raise RuntimeError('容器创建失败 ' + str(response.status))
    run(['docker', 'start', name])


def reference_exists(database):
    return sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='" + database
               + "' AND table_name='wx_3d_scene_publication_resource';") == '1'


def apply_reference(database):
    """模型受管删除复用发布引用保护，缺表时仅补既有 004、005 增量，不重建任何现有表。"""
    if reference_exists(database):
        return []
    files = [SOURCE / 'deploy/test148/migrations/data09' / name for name in
             ('004-publication-resource.up.sql', '005-resource-reference-collation.up.sql')]
    applied = []
    for path in files:
        sql(path.read_text(encoding='utf-8'), database)
        applied.append({'file': path.name, 'sha256': file_sha256(path)})
    return applied


def prepare():
    """逐库完整备份与结构演练，正式数据只读；保留原容器配置及实际运行展开目录。"""
    artifact = SOURCE / 'wx-module-3d/target/wx-module-3d.jar'
    if (RELEASE / 'build.exit').read_text().strip() != '0':
        raise RuntimeError('固定源码构建尚未成功')
    package = RELEASE / 'dimensions.jar'
    shutil.copyfile(artifact, package)
    package.chmod(0o644)
    sha = file_sha256(package)
    report = {'sourceCommit': run(['git', '-C', str(SOURCE), 'rev-parse', 'HEAD']).decode().strip(),
              'artifactSha256': sha, 'artifactBytes': package.stat().st_size, 'targets': {}}
    for key, (database, name, port, candidate_port) in TARGETS.items():
        directory = RELEASE / key
        directory.mkdir(mode=0o700)
        original = inspect(name)
        save(directory / 'original-container.json', original)
        previous = {}
        for mount in original['Mounts']:
            source = Path(mount['Source'])
            if mount['Destination'] == '/opt/wx/app.jar':
                shutil.copy2(source, directory / 'previous.jar')
                previous['sha256'] = file_sha256(source)
            if mount['Destination'] == '/opt/wx/application.yaml':
                shutil.copy2(source, directory / 'previous.yaml')
            if mount['Destination'] == '/opt/wx/extracted':
                shutil.copytree(source, directory / 'previous-extracted')
        for label, options in (('database.sql', []), ('schema.sql', ['--no-data'])):
            with (directory / label).open('wb') as output:
                result = subprocess.run(mysql_command(database, dump=True) + options,
                                        stdout=output, stderr=subprocess.PIPE)
            if result.returncode:
                raise RuntimeError('数据库备份失败 ' + key)
        clone = 'wx_model09_' + key + '_20261117'
        if sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='" + clone + "';") != '0':
            raise RuntimeError('候选库已存在，请根据回执接续')
        sql('CREATE DATABASE `' + clone + '` CHARACTER SET utf8mb4;')
        run(mysql_command(clone), input=(directory / 'schema.sql').read_bytes())
        migrations = apply_reference(clone)
        sql("GRANT SELECT,INSERT,UPDATE,DELETE ON `" + clone + "`.* TO 'wx_editor_test148'@'%';")
        text = (directory / 'previous.yaml').read_text(encoding='utf-8')
        text, count = re.subn(r'(jdbc:mysql://[^\s/]+/)' + database + r'(?=[?\s])',
                              lambda match: match[1] + clone, text)
        if count != 1:
            raise RuntimeError('候选数据源替换数量不为一')
        config = directory / 'candidate.yaml'
        config.write_text(text, encoding='utf-8')
        uid, gid = runtime_identity(original['Config']['Image'])
        os.chown(config, uid, gid)
        config.chmod(0o400)
        # 目录仅允许根进入，容器进程仍需穿越路径读取只读配置和包。
        directory.chmod(0o755)
        for private in ('original-container.json', 'previous.yaml', 'database.sql', 'schema.sql'):
            (directory / private).chmod(0o600)
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', candidate_port))
        candidate = 'wx-editor-model09-' + key
        clone_container(original, candidate, {'/opt/wx/app.jar': str(package),
                        '/opt/wx/application.yaml': str(config)}, True, candidate_port)
        healthy(candidate_port)
        report['targets'][key] = {'database': database, 'clone': clone, 'container': name,
            'candidate': candidate, 'port': port, 'candidatePort': candidate_port,
            'previousSha256': previous['sha256'], 'candidateMigrations': migrations,
            'backupSha256': file_sha256(directory / 'database.sql')}
        save(RELEASE / 'prepared.json', report)
        print('候选健康 ' + key, flush=True)
    return report


def switch():
    """候选真实验收成功后逐服务切换，原容器改名保留，任何失败恢复已切换的原实例。"""
    report = json.loads((RELEASE / 'prepared.json').read_text())
    for key in TARGETS:
        verified = json.loads((RELEASE / ('verify-candidate-' + key + '.json')).read_text())
        if not verified.get('passed') or not verified.get('cleaned'):
            raise RuntimeError('候选真实验收或清理尚未通过 ' + key)
    switched = []
    try:
        # 正式程序仍采用原启动模式。主实例展开目录从同一摘要的包生成。
        extracted = RELEASE / 'extracted'
        extracted.mkdir(mode=0o777)
        extracted.chmod(0o777)
        run(['docker', 'run', '--rm', '--entrypoint', 'java', '-v',
             str(RELEASE / 'dimensions.jar') + ':/opt/wx/app.jar:ro', '-v', str(extracted) + ':/out',
             'wx-cloud-runtime:test148', '-Djarmode=layertools', '-jar', '/opt/wx/app.jar',
             'extract', '--destination', '/out'])
        for key, target in report['targets'].items():
            original = json.loads((RELEASE / key / 'original-container.json').read_text())
            current = inspect(target['container'])
            if current['Id'] != original['Id']:
                raise RuntimeError('发布期间原容器已被其他任务替换')
            target['appliedMigrations'] = apply_reference(target['database'])
            run(['docker', 'stop', target['container']])
            backup_name = target['container'] + '-before-model09'
            run(['docker', 'rename', target['container'], backup_name])
            switched.append((target, backup_name))
            run(['docker', 'update', '--restart=no', backup_name])
            clone_container(original, target['container'], {'/opt/wx/app.jar': str(RELEASE / 'dimensions.jar'),
                            '/opt/wx/extracted': str(extracted)})
            healthy(target['port'])
            mounted = run(['docker', 'exec', target['container'], 'sha256sum', '/opt/wx/app.jar']).decode().split()[0]
            if mounted != report['artifactSha256']:
                raise RuntimeError('运行容器程序摘要不一致')
            target['backupContainer'] = backup_name
            target['startedAt'] = inspect(target['container'])['State']['StartedAt']
            save(RELEASE / 'deployment.json', report)
            print('正式服务健康 ' + key, flush=True)
        report['status'] = 'DEPLOYED'
    except Exception:
        for target, backup_name in reversed(switched):
            subprocess.run(['docker', 'rm', '-f', target['container']], capture_output=True)
            run(['docker', 'rename', backup_name, target['container']])
            original = json.loads((RELEASE / ('main' if target['database'] == 'wx_editor' else 'data08')
                                   / 'original-container.json').read_text())
            policy = original['HostConfig']['RestartPolicy']['Name']
            run(['docker', 'update', '--restart=' + policy, target['container']])
            run(['docker', 'start', target['container']])
            healthy(target['port'])
        report['status'] = 'ROLLED_BACK'
        save(RELEASE / 'deployment.json', report)
        raise
    save(RELEASE / 'deployment.json', report)
    return report


def main():
    os.umask(0o077)
    # 发布目录可穿越，备份与快照逐文件为 600，避免容器非特权用户无法读包。
    RELEASE.mkdir(exist_ok=True)
    RELEASE.chmod(0o755)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'switch'))
    action = parser.parse_args().action
    result = prepare() if action == 'prepare' else switch()
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
