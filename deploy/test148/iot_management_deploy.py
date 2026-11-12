"""148物联网管理候选部署：复用身份服务，备份后仅增建缺失IoT表和精确管理路由。"""
import argparse
import copy
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request

import yaml

from management_menus import save_json, read_json
from server_support import file_sha256, mysql_command

ROOT = Path('/opt/wx/editor-test148')
RELEASE = ROOT / 'releases/iot-management-20261111'
IMAGE = 'wx-cloud-runtime:test148'
IOT = 'wx-editor-iot-data08'
ISOLATED_GATEWAY = 'wx-editor-iot-gateway-check'


def build_iot_config(system, datasource):
    """保留实际系统认证/租户/Redis/RPC配置，只替换本服务名称、库、端口及日志。"""
    url = datasource.get('dynamic', {}).get('datasource', {}).get('master', {}).get('url', '')
    if '/wx_editor_data08?' not in url:
        raise ValueError('IoT候选只允许当前隔离库')
    result = copy.deepcopy(system)
    spring = result.setdefault('spring', {})
    spring['application'] = {'name': 'iot-server'}
    spring.pop('config', None)
    spring.pop('profiles', None)
    spring['datasource'] = copy.deepcopy(datasource)
    spring['kafka'] = {'bootstrap-servers': '127.0.0.1:9092', 'listener': {'auto-startup': False},
                       'producer': {'acks': 'all', 'retries': 3,
                                    'key-serializer': 'org.apache.kafka.common.serialization.StringSerializer',
                                    'value-serializer': 'org.springframework.kafka.support.serializer.JsonSerializer',
                                    'properties': {'enable.idempotence': True}}}
    wx = result.setdefault('wx', {})
    wx.setdefault('info', {})['base-package'] = 'cn.iocoder.wx.module.iot'
    wx.setdefault('tenant', {})['enable'] = True
    wx.setdefault('security', {})['mock-enable'] = False
    wx['iot'] = {'mqtt': {'enabled': False}}
    result['server'] = {'port': 48096, 'address': '127.0.0.1'}
    result['logging'] = {'file': {'name': '/app/logs/wx-iot.log'}, 'level': {'root': 'INFO'}}
    result['management'] = {'endpoints': {'web': {'exposure': {'include': 'health,info'}}},
                            'endpoint': {'health': {'show-details': 'never'}}}
    return result


def add_iot_route(gateway):
    """仅开放连接器/绑定/接收记录；不顺带开放Webhook、模拟上报或其他IoT入口。"""
    result = copy.deepcopy(gateway)
    routes = result.setdefault('spring', {}).setdefault('cloud', {}).setdefault('gateway', {}).setdefault('routes', [])
    desired = {'id': 'iot-management-data08', 'uri': 'http://127.0.0.1:48096',
               'predicates': ['Path=/admin-api/iot/connector/**,/admin-api/iot/device-binding/**,/admin-api/iot/message-inbox/**'],
               'filters': ['RemoveRequestHeader=x-bot-user']}
    current = [r for r in routes if r.get('id') == desired['id']]
    if current:
        if current != [desired]:
            raise ValueError('已有IoT路由与候选冲突')
    else:
        routes.insert(0, desired)
    return result


def command(*args):
    """配置/环境等标准输出不直接回显，失败只保留固定阶段错误。"""
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError('部署命令失败：' + args[0])
    return result.stdout.decode().strip()


def health(port, container):
    """有界等待，健康接口不输出依赖细节；错误日志留在服务器受控目录。"""
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    for _ in range(90):
        if command('docker', 'inspect', '--format', '{{.State.Running}}', container) != 'true':
            raise RuntimeError('候选容器已退出')
        try:
            with opener.open('http://127.0.0.1:' + str(port) + '/actuator/health', timeout=2) as response:
                if json.load(response).get('status') == 'UP':
                    return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError('候选健康检查超时')


def run_container(name, jar, config, port, heap):
    """沿用已核验镜像及环境文件，不修改原系统/基础设施/三维服务。"""
    logdir = ROOT / 'logs' / name
    logdir.mkdir(parents=True, exist_ok=True)
    # 现场镜像默认非root账号100:101，日志目录仅赋予对应账号写入权。
    os.chown(logdir, 100, 101)
    command('docker', 'run', '-d', '--name', name, '--network', 'host', '--restart', 'unless-stopped',
            '--memory', '768m', '--cpus', '1', '--pids-limit', '512', '--env-file', str(ROOT / '.env'),
            '-v', str(jar) + ':/opt/wx/app.jar:ro', '-v', str(config) + ':/opt/wx/application.yaml:ro',
            '-v', str(logdir) + ':/app/logs', '--entrypoint', 'java', IMAGE, '-Xms64m', '-Xmx' + heap,
            '-jar', '/opt/wx/app.jar', '--spring.config.location=file:/opt/wx/application.yaml',
            '--server.port=' + str(port), '--server.address=127.0.0.1')
    health(port, name)


def prepare():
    """仅在三张表全部不存在时增建；已有表必须走单独审计的增量迁移，不重跑初始化。"""
    report_path = RELEASE / 'iot-deployment.json'
    if report_path.exists():
        raise RuntimeError('部署记录已存在，不能重跑准备')
    # 宿主带YAML库的解释器为Python3.6，使用兼容参数，不依赖新增运行环境。
    tables = subprocess.run(mysql_command('wx_editor_data08'), input="SHOW TABLES LIKE '%iot%';",
                            universal_newlines=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if tables.returncode or len(tables.stdout.strip().splitlines()) > 1:
        raise RuntimeError('IoT表已存在或元数据检查失败')
    backup = ROOT / 'backups/iot-data08-20261111'
    backup.mkdir(mode=0o700)
    dump = backup / 'wx_editor_data08.sql'
    with dump.open('wb') as stream:
        result = subprocess.run(mysql_command('wx_editor_data08', dump=True), stdout=stream, stderr=subprocess.PIPE)
    if result.returncode or dump.stat().st_size == 0:
        raise RuntimeError('隔离库备份未完成')
    target = ROOT / 'config/gateway-data08.yaml'
    shutil.copy2(target, backup / 'gateway-data08.yaml')
    system = yaml.safe_load((ROOT / 'config/system.yaml').read_text())
    dimensions = yaml.safe_load((ROOT / 'config/dimensions-data08.yaml').read_text())
    iot = build_iot_config(system, dimensions['spring']['datasource'])
    gateway = add_iot_route(yaml.safe_load(target.read_text()))
    for name, config in [('iot.candidate.yaml', iot), ('gateway-iot.candidate.yaml', gateway)]:
        path = RELEASE / name
        path.write_text(yaml.safe_dump(config, allow_unicode=True), encoding='utf-8')
        path.chmod(0o644)
    jar = RELEASE / 'iot.jar'
    migration = RELEASE / 'wx-module-iot/db/iot.sql'
    report = {'status': 'BACKED_UP', 'database': 'wx_editor_data08', 'backup': str(backup),
              'databaseBackupSha256': file_sha256(dump), 'previousGatewaySha256': file_sha256(target),
              'candidateGatewaySha256': file_sha256(RELEASE / 'gateway-iot.candidate.yaml'),
              'iotJarSha256': file_sha256(jar), 'migrationSha256': file_sha256(migration),
              'mqttEnabled': False, 'existingServicesRestarted': False, 'publicGatewayChanged': False}
    if report['iotJarSha256'] != 'bcfc854427d668dee2d63fb25ad50750c301caf190a4b06e9d997fcc352a2b9a':
        raise RuntimeError('候选包摘要与已验证版本不一致')
    save_json(report_path, report)
    result = subprocess.run(mysql_command('wx_editor_data08'), input=migration.read_bytes(), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    report['status'] = 'SCHEMA_READY' if not result.returncode else 'SCHEMA_FAILED'
    save_json(report_path, report)
    if result.returncode:
        raise RuntimeError('表增建未全部完成，保留现场核对，不自动重跑')
    run_container(IOT, jar, RELEASE / 'iot.candidate.yaml', 48096, '384m')
    report['status'] = 'SERVICE_HEALTHY'
    save_json(report_path, report)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'start', 'isolate', 'apply', 'rollback'))
    args = parser.parse_args()
    if Path(__file__).resolve().parents[2] != RELEASE:
        raise RuntimeError('发布目录不匹配')
    os.umask(0o077)
    if args.action == 'prepare':
        prepare()
    else:
        path = RELEASE / 'iot-deployment.json'
        report = read_json(path)
        if args.action == 'start':
            if report['status'] not in ('SCHEMA_READY', 'ISOLATED') or file_sha256(RELEASE / 'iot.jar') != report['iotJarSha256']:
                raise RuntimeError('结构或候选摘要不满足启动条件')
            previous_status = report['status']
            run_container(IOT, RELEASE / 'iot.jar', RELEASE / 'iot.candidate.yaml', 48096, '384m')
            report['status'] = 'ISOLATED' if previous_status == 'ISOLATED' else 'SERVICE_HEALTHY'
        elif args.action == 'isolate':
            if report['status'] != 'SERVICE_HEALTHY':
                raise RuntimeError('服务尚未健康')
            run_container(ISOLATED_GATEWAY, ROOT / 'jars/gateway.jar', RELEASE / 'gateway-iot.candidate.yaml', 12536, '384m')
            report.update(status='ISOLATED', isolatedGatewayPort=12536)
        elif args.action == 'apply':
            verification = read_json(RELEASE / 'iot-isolation-verification.json')
            target = ROOT / 'config/gateway-data08.yaml'
            if report['status'] != 'ISOLATED' or not verification.get('passed') or file_sha256(target) != report['previousGatewaySha256']:
                raise RuntimeError('隔离验收或现场摘要不满足发布条件')
            command('docker', 'stop', 'wx-editor-gateway-data08')
            try:
                # 保留原挂载inode，停容器后原位写入；失败恢复本批原始配置再启动。
                with target.open('wb') as stream:
                    stream.write((RELEASE / 'gateway-iot.candidate.yaml').read_bytes())
                    stream.flush(); os.fsync(stream.fileno())
                command('docker', 'start', 'wx-editor-gateway-data08')
                health(12525, 'wx-editor-gateway-data08')
            except Exception:
                command('docker', 'stop', 'wx-editor-gateway-data08')
                target.write_bytes((Path(report['backup']) / 'gateway-data08.yaml').read_bytes())
                command('docker', 'start', 'wx-editor-gateway-data08')
                health(12525, 'wx-editor-gateway-data08')
                raise
            report.update(status='DEPLOYED', publicGatewayChanged=True, changedGatewayPort=12525)
            command('docker', 'rm', '-f', ISOLATED_GATEWAY)
        else:
            target = ROOT / 'config/gateway-data08.yaml'
            if file_sha256(target) != report['candidateGatewaySha256']:
                raise RuntimeError('现场配置有后续变化，不能覆盖回退')
            if file_sha256(Path(report['backup']) / 'gateway-data08.yaml') != report['previousGatewaySha256']:
                raise RuntimeError('本批回退配置摘要不匹配')
            command('docker', 'stop', 'wx-editor-gateway-data08')
            target.write_bytes((Path(report['backup']) / 'gateway-data08.yaml').read_bytes())
            command('docker', 'start', 'wx-editor-gateway-data08')
            health(12525, 'wx-editor-gateway-data08')
            command('docker', 'stop', IOT)
            report.update(status='ROLLED_BACK', iotTablesPreserved=True)
        save_json(path, report)
    print(json.dumps(read_json(RELEASE / 'iot-deployment.json'), ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('IoT部署未完成：' + type(error).__name__)
        raise SystemExit(1)
