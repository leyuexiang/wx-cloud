"""图片专项部署助手。复用现场配置与容器参数，隔离验证后允许原子替换及回退。"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import socket
import subprocess
import time
import urllib.request


ROOT = Path('/opt/wx/editor-test148')
RELEASE = ROOT / 'releases/image01-20261112'
BACKUP = ROOT / 'backups/image01-20261112'
SERVICES = ('infra', 'dimensions', 'gateway')
PORTS = {'infra': 49082, 'dimensions': 49092, 'gateway': 12532}
ARTIFACTS = {
    'infra': ('wx-module-infra-biz.jar', '1f7b8150bd91921fc09d223906e75dd820b56df346a23f5f6c70a3442622f3e1'),
    'dimensions': ('wx-module-3d.jar', '2ed0c237b75c1cf34ff39e474812731773fa0dc0207f23eb99b6b90e9e24bad8'),
    'gateway': ('wx-gateway.jar', 'c1bc1f0acaa20807ae5ccaddcc6ad362140e9257ca21f12b95d87b65c83ae482'),
}
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def run(arguments):
    """命令使用参数数组，失败只报告受控输出，避免异常把环境凭据写入日志。"""
    result = subprocess.run(arguments, cwd=ROOT, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr[-1500:] or result.stdout[-1500:])
    return result.stdout.strip()


def healthy(port, timeout=180):
    """有界轮询实际健康接口，使用回环地址并关闭代理，不依赖旧容器的健康标记。"""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with OPENER.open('http://127.0.0.1:%s/actuator/health' % port, timeout=3) as response:
                if json.load(response).get('status') == 'UP':
                    return
        except (OSError, ValueError):
            pass
        time.sleep(2)
    raise RuntimeError('健康检查超时，端口 %s' % port)


def prepare():
    """先核对候选摘要，再为现场网关补充四个精确别名，其他配置保持现场版本。"""
    for service, (name, expected) in ARTIFACTS.items():
        source = RELEASE / name
        digest = hashlib.sha256()
        with source.open('rb') as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(block)
        if digest.hexdigest() != expected:
            raise RuntimeError('候选包摘要不匹配：' + service)
        os.chmod(source, 0o644)
    config = (ROOT / 'config/gateway.yaml').read_text(encoding='utf-8')
    if 'admin-image-oss-upload-test148' in config:
        raise RuntimeError('现场已经包含图片别名，请核对本批是否已部署')
    # 文件含 Swagger 文档路由和业务路由两个 routes，按已核实的缩进定位业务路由。
    marker = '      routes:\n'
    if config.count(marker) != 1:
        raise RuntimeError('现场网关路由结构发生变化')
    routes = []
    for prefix, label in (('/api', 'image'), ('/admin-api', 'admin-image')):
        for action, suffix in (('upload', 'upload'), ('list-by-id', 'listByIds/{ids}')):
            source = prefix + '/resource/oss/' + suffix
            rewrite_source = source.replace('{ids}', '(?<ids>[^/]+)')
            target = '/admin-api/infra/file/resource/oss/' + suffix.replace('{ids}', r'$\{ids}')
            routes.extend([
                '    # 图片上传及按编号读取复用基础设施服务，并移除外部伪造身份头。',
                '    - id: %s-oss-%s-test148' % (label, action),
                '      order: -20',
                '      uri: http://127.0.0.1:48082',
                '      predicates: [%s]' % json.dumps('Path=' + source),
                '      filters:',
                '      - RemoveRequestHeader=x-bot-user',
                "      - 'RewritePath=%s, %s'" % (rewrite_source, target),
            ])
    production = config.replace(marker, marker + '\n'.join('  ' + line for line in routes) + '\n', 1)
    (RELEASE / 'gateway.production.yaml').write_text(production, encoding='utf-8')
    isolated = production.replace('127.0.0.1:48082', '127.0.0.1:49082').replace('127.0.0.1:48092', '127.0.0.1:49092')
    (RELEASE / 'gateway.isolated.yaml').write_text(isolated, encoding='utf-8')
    extracted = RELEASE / 'extracted'
    # 候选目录只创建一次，避免重试期间覆盖正在被候选容器读取的文件。
    extracted.mkdir(mode=0o777)
    os.chmod(extracted, 0o777)
    run(['docker', 'run', '--rm', '--entrypoint', 'java', '-v',
         str(RELEASE / ARTIFACTS['dimensions'][0]) + ':/opt/wx/app.jar:ro', '-v',
         str(extracted) + ':/opt/wx/out', 'wx-cloud-runtime:test148',
         '-Djarmode=layertools', '-jar', '/opt/wx/app.jar', 'extract', '--destination', '/opt/wx/out'])
    print('候选摘要、网关配置及三维解包准备通过', flush=True)


def stage():
    """按实际容器快照复制运行参数，候选服务仅监听回环地址，复用现有鉴权和数据库。"""
    snapshots = json.loads((BACKUP / 'containers.json').read_text())
    originals = {item['Name'].removeprefix('/wx-editor-'): item for item in snapshots}
    for service in SERVICES:
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', PORTS[service]))
        original = originals[service]
        name = 'wx-editor-image01-' + service
        env_file = RELEASE / ('.env-' + service)
        env_file.write_text('\n'.join(original['Config']['Env']) + '\n', encoding='utf-8')
        os.chmod(env_file, 0o600)
        args = ['docker', 'run', '-d', '--name', name, '--network', 'host', '--restart', 'no',
                '--env-file', str(env_file), '--memory', str(original['HostConfig']['Memory']),
                '--log-opt', 'max-size=20m', '--log-opt', 'max-file=2']
        if original['Config'].get('User'):
            args += ['--user', original['Config']['User']]
        # 数据卷仍挂载现场位置。程序、网关配置及三维解包目录分别替换为本批候选文件。
        for mount in original['Mounts']:
            source = mount['Source']
            destination = mount['Destination']
            if destination == '/opt/wx/app.jar':
                source = str(RELEASE / ARTIFACTS[service][0])
            if service == 'gateway' and destination == '/opt/wx/application.yaml':
                source = str(RELEASE / 'gateway.isolated.yaml')
            if service == 'dimensions' and destination == '/opt/wx/extracted':
                source = str(RELEASE / 'extracted')
            args += ['-v', source + ':' + destination + (':ro' if not mount['RW'] else '')]
        entrypoint = original['Config']['Entrypoint']
        args += ['--entrypoint', entrypoint[0], original['Config']['Image']]
        args += entrypoint[1:] + (original['Config']['Cmd'] or [])
        args += ['--server.port=' + str(PORTS[service]), '--server.address=127.0.0.1']
        try:
            run(args)
        finally:
            env_file.unlink(missing_ok=True)
        healthy(PORTS[service])
        print('隔离服务健康：%s，端口 %s' % (service, PORTS[service]), flush=True)
    (RELEASE / 'isolated-healthy.json').write_text(json.dumps(PORTS), encoding='utf-8')


def switch():
    """隔离服务通过后替换正式文件。强制重建刷新文件挂载，失败立即恢复本批备份。"""
    if not (RELEASE / 'isolated-healthy.json').exists():
        raise RuntimeError('隔离健康验证尚未通过')
    for service in SERVICES:
        healthy(PORTS[service], 10)
    try:
        for service, (name, _) in ARTIFACTS.items():
            temporary = ROOT / 'jars' / (service + '.image01-new.jar')
            shutil.copy2(RELEASE / name, temporary)
            os.replace(temporary, ROOT / 'jars' / (service + '.jar'))
        shutil.copy2(RELEASE / 'gateway.production.yaml', ROOT / 'config/gateway.image01-new.yaml')
        os.replace(ROOT / 'config/gateway.image01-new.yaml', ROOT / 'config/gateway.yaml')
        # 旧解包目录已备份。先改名保留旧挂载，再将完整候选目录原子移入正式路径。
        (ROOT / 'extracted/dimensions').rename(RELEASE / 'previous-extracted')
        (RELEASE / 'extracted').rename(ROOT / 'extracted/dimensions')
        run(['docker', 'compose', '-f', 'compose.yaml', '-f', 'compose.exploded.yaml',
             'up', '-d', '--no-deps', '--force-recreate', 'infra', 'dimensions', 'gateway'])
        for port in (48082, 48092, 12522):
            healthy(port)
        print('正式入口与两个业务服务均健康', flush=True)
    except Exception:
        rollback()
        raise


def rollback():
    """只恢复本次三个程序、网关配置与解包目录，使用本批备份，数据库不执行回退。"""
    for service in SERVICES:
        shutil.copy2(BACKUP / (service + '.jar'), ROOT / 'jars' / (service + '.jar'))
    shutil.copy2(BACKUP / 'config/gateway.yaml', ROOT / 'config/gateway.yaml')
    target = ROOT / 'extracted/dimensions'
    failed = RELEASE / ('failed-extracted-' + str(int(time.time())))
    if target.exists():
        target.rename(failed)
    shutil.copytree(BACKUP / 'extracted-dimensions', target)
    run(['docker', 'compose', '-f', 'compose.yaml', '-f', 'compose.exploded.yaml',
         'up', '-d', '--no-deps', '--force-recreate', 'infra', 'dimensions', 'gateway'])
    for port in (48082, 48092, 12522):
        healthy(port)
    print('本批程序回退并通过健康检查', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'stage', 'switch', 'rollback'))
    action = parser.parse_args().action
    {'prepare': prepare, 'stage': stage, 'switch': switch, 'rollback': rollback}[action]()
