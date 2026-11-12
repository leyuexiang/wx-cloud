"""从工作站编排两节点；二进制流直传，私有引导数据仅驻留内存。"""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE = '/home/wx-editor-build-archives/iot-dist-20261111'
TARGET = '/home/jtyf-editor246/releases/iot-20261111'


def ssh(host, command, data=None):
    args = ['ssh', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', host, command]
    result = subprocess.run(args, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode: raise RuntimeError('远端步骤失败，节点=' + host)
    return result.stdout


def copy(local, host, remote):
    subprocess.run(['scp', '-q', str(local), host + ':' + remote], check=True)


def stream(source_command, target_command):
    """不在148系统盘或工作站落地整份镜像，避免磁盘和内存复制开销。"""
    source = subprocess.Popen(['ssh', '-o', 'BatchMode=yes', '192.168.10.148', source_command], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    target = subprocess.run(['ssh', '-o', 'BatchMode=yes', 'root@192.168.10.246', target_command], stdin=source.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    source.stdout.close()
    if source.wait() or target.returncode: raise RuntimeError('制品加密直传失败')


def main():
    ssh('192.168.10.148', 'mkdir -m 700 -p ' + SOURCE)
    ssh('root@192.168.10.246', 'mkdir -m 755 -p ' + TARGET)
    for name in ['app246.py', 'node148_setup.py']:
        copy(ROOT / 'deploy/cluster246' / name, '192.168.10.148', SOURCE + '/' + name)
    copy(ROOT / 'deploy/test148/server_support.py', '192.168.10.148', SOURCE + '/server_support.py')
    copy(ROOT / 'deploy/cluster246/app246.py', 'root@192.168.10.246', TARGET + '/app246.py')
    public = json.loads(ssh('root@192.168.10.246', 'python3 ' + TARGET + '/app246.py init'))['publicKey']
    private = json.loads(ssh('192.168.10.148', '/usr/bin/python3 ' + SOURCE + '/node148_setup.py', json.dumps({'nodePublicKey': public}).encode()))
    print('节点身份与三表最小权限账号已准备，未回显私有引导数据', flush=True)
    stream('docker save wx-cloud-runtime:test148', 'docker load')
    # 新旧Docker镜像存储格式会改变导入后的ID；比较内容层，再固定目标节点实际ID。
    source_layers = json.loads(ssh('192.168.10.148', "docker image inspect wx-cloud-runtime:test148 --format '{{json .RootFS.Layers}}'"))
    target_layers = json.loads(ssh('root@192.168.10.246', "docker image inspect wx-cloud-runtime:test148 --format '{{json .RootFS.Layers}}'"))
    if source_layers != target_layers: raise RuntimeError('导入镜像内容层不一致')
    private['javaImage'] = ssh('root@192.168.10.246', "docker image inspect wx-cloud-runtime:test148 --format '{{.Id}}'").decode().strip()
    stream('cat /opt/wx/editor-test148/releases/iot-management-20261111/iot.jar', 'cat > ' + TARGET + '/iot.jar')
    stream('cat /home/wx-editor-web148/releases/iot-web-20261111/site.tar.gz', 'cat > ' + TARGET + '/site.tar.gz')
    result = ssh('root@192.168.10.246', 'python3 ' + TARGET + '/app246.py prepare', json.dumps(private).encode())
    private.clear()
    print(result.decode())


if __name__ == '__main__':
    try: main()
    except Exception as error:
        print('双机准备未完成：' + str(error) if isinstance(error, RuntimeError) else type(error).__name__)
        raise SystemExit(1)
