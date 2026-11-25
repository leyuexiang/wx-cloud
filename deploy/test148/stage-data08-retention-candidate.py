"""只在49143回环端口启动保留策略候选，复用原受控配置，不切换常驻服务。"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess


ROOT = Path('/home/wx-data08-retention-20261119')
NAME = 'wx-data08-retention-candidate-20261119'
SOURCE = 'wx-editor-dimensions-data08'


def run(command):
    result = subprocess.run(command, capture_output=True, text=True, timeout=60)
    if result.returncode:
        # Docker诊断可能携带运行参数，只保存在服务器受控目录。
        path = ROOT / 'command-error.private.txt'
        path.write_text(result.stderr, encoding='utf-8')
        path.chmod(0o600)
        raise RuntimeError('候选命令失败，详见服务器受限诊断文件')
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('start', 'stop'))
    parser.add_argument('--sha256')
    args = parser.parse_args()
    os.umask(0o077)
    state_path = ROOT / 'candidate-state.json'
    if args.action == 'stop':
        state = json.loads(state_path.read_text(encoding='utf-8'))
        current = json.loads(run(['docker', 'inspect', NAME]))[0]
        if current['Id'] != state['candidateId']:
            raise RuntimeError('候选身份已变更，停止操作')
        run(['docker', 'stop', NAME])
        state['stopped'] = True
        state_path.write_text(json.dumps(state, indent=2), encoding='utf-8')
        print(json.dumps({'stopped': True, 'container': NAME}))
        return
    if state_path.exists():
        raise RuntimeError('已有候选状态，禁止覆盖')
    jar = ROOT / 'wx-module-3d.jar'
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    if not args.sha256 or digest != args.sha256:
        raise RuntimeError('候选包摘要不匹配')
    original = json.loads(run(['docker', 'inspect', SOURCE]))[0]
    config = Path(next(m['Source'] for m in original['Mounts'] if m['Destination'] == '/opt/wx/application.yaml'))
    text = config.read_text(encoding='utf-8')
    if text.count('/wx_editor_data08?') != 1 or original['State']['Status'] != 'running':
        raise RuntimeError('来源数据库或容器状态不匹配')
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 49143))
    env_file = ROOT / 'candidate.env.private'
    env_file.write_text('\n'.join(original['Config']['Env']) + '\n', encoding='utf-8')
    env_file.chmod(0o600)
    jar.chmod(0o644)
    logs = ROOT / 'logs'
    logs.mkdir(mode=0o750)
    uid = int(run(['docker', 'exec', SOURCE, 'id', '-u']))
    gid = int(run(['docker', 'exec', SOURCE, 'id', '-g']))
    os.chown(logs, uid, gid)
    command = ['docker', 'run', '-d', '--name', NAME, '--network', 'host', '--restart', 'no',
               '--memory', '1536m', '--log-opt', 'max-size=10m', '--log-opt', 'max-file=2',
               '--env-file', str(env_file), '--tmpfs', '/tmp:rw,size=128m', '--entrypoint', 'java']
    if original['Config'].get('WorkingDir'):
        command += ['--workdir', original['Config']['WorkingDir']]
    for mount in original['Mounts']:
        if mount['Destination'] == '/opt/wx/app.jar':
            command += ['-v', str(jar) + ':/opt/wx/app.jar:ro']
        else:
            command += ['-v', mount['Source'] + ':' + mount['Destination'] + ':ro']
    command += ['-v', str(logs) + ':/opt/wx/retention-logs', original['Image'], '-Xms128m', '-Xmx1024m',
                '-jar', '/opt/wx/app.jar', '--spring.config.location=file:/opt/wx/application.yaml',
                '--spring.config.additional-location=file:/opt/wx/kafka/application.properties',
                '--server.port=49143', '--server.address=127.0.0.1', '--dim3d.scene-retention.enabled=true',
                '--dim3d.scene-retention.request-retention-days=30', '--dim3d.scene-content.dual-write-enabled=true',
                '--dim3d.scene-content.dual-write-tenants=1,2', '--dim3d.model-import.worker-enabled=false',
                '--wx.iot-state.enabled=false', '--spring.kafka.listener.auto-startup=false',
                '--wx.websocket.sender-kafka.auto-startup=false', '--wx.websocket.sender-type=local',
                '--logging.file.name=/opt/wx/retention-logs/candidate.log']
    try:
        candidate = run(command)
    finally:
        env_file.unlink(missing_ok=True)
    state = {'candidateId': candidate, 'originalId': original['Id'], 'originalStartedAt': original['State']['StartedAt'],
             'configSha256': hashlib.sha256(config.read_bytes()).hexdigest(), 'sha256': digest,
             'database': 'wx_editor_data08', 'port': 49143, 'image': original['Image'], 'stopped': False}
    state_path.write_text(json.dumps(state, indent=2), encoding='utf-8')
    print(json.dumps(state, indent=2))


if __name__ == '__main__':
    main()
