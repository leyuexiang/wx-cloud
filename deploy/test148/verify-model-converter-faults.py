"""仅在 MODEL-03 独立候选验证真实繁忙、连接中断和请求超时，结束后恢复候选。"""

import argparse
from concurrent.futures import ThreadPoolExecutor
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request
import uuid

ROOT = Path('/opt/wx/editor-test148')
BACKEND = 'wx-editor-model03-main'
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def command(arguments):
    """外部失败只报告命令名称，环境凭据不能进入故障记录。"""
    result = subprocess.run(arguments, capture_output=True, text=True, timeout=90)
    if result.returncode:
        raise RuntimeError('候选命令失败：' + arguments[0])
    return result.stdout.strip()


def healthy(port):
    """启动等待有明确上限，仅访问本机候选的已有健康入口。"""
    path = '/actuator/health' if port == 49102 else '/health'
    for _ in range(45):
        try:
            with OPENER.open('http://127.0.0.1:' + str(port) + path, timeout=2) as response:
                if response.status == 200:
                    return
        except Exception:
            pass
        time.sleep(1)
    raise RuntimeError('候选健康检查超时')


def start_backend(original, directory, timeout_ms=None):
    """同一副本串行启动；环境短暂落在受限文件，启动后立即删除。"""
    env = directory / 'backend.env'
    env.write_text('\n'.join(original['Config']['Env']) + '\n', encoding='utf-8')
    args = ['docker', 'run', '-d', '--name', BACKEND, '--restart', 'no', '--network', 'host',
            '--env-file', str(env), '--entrypoint', 'java', '--user', original['Config']['User'],
            '-w', original['Config']['WorkingDir']]
    for mount in original['Mounts']:
        args += ['-v', mount['Source'] + ':' + mount['Destination'] + ('' if mount['RW'] else ':ro')]
    settings = [item for item in original['Config']['Cmd']
                if not item.startswith('--dim3d.model-import.converter-timeout-ms=')]
    if timeout_ms is not None:
        settings.append('--dim3d.model-import.converter-timeout-ms=' + str(timeout_ms))
    try:
        command(args + [original['Config']['Image']] + settings)
    finally:
        env.unlink(missing_ok=True)
    healthy(49102)


def verify(case, directory):
    """完整经过现有真实身份、签名上传、源冻结和后台失败，不伪造转换响应。"""
    log = directory / (case + '.log')
    with log.open('w', encoding='utf-8') as stream:
        result = subprocess.run(['python3', str(Path(__file__).with_name('verify-model-import-source.py')),
                                 '--target', 'main', '--conversion', '--failure-case', case],
                                cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, timeout=180)
    report = json.loads(log.read_text(encoding='utf-8').strip().splitlines()[-1])
    if result.returncode or not report['passed']:
        raise RuntimeError('真实故障验证失败：' + case)
    return {'case': case, 'passed': True, 'checks': len(report['checks']),
            'errorCode': report['conversionState']['errorCode'],
            'retryable': report['conversionState']['retryable'],
            'cancelled': report['cancelled'], 'temporaryObjectRemoved': report['temporaryObjectRemoved'],
            'conversionObjectsRemoved': report['conversionObjectsRemoved']}


def busy(converter, directory):
    """只暂停独立转换器中的实际子工具，保留HTTP服务及真实并发门限；最终必定恢复。"""
    marker = '/tmp/model03-held-' + uuid.uuid4().hex + '.json'
    watcher = r"""
const fs=require('fs');
const marker=process.argv[1], deadline=Date.now()+30000;
fs.writeFileSync(marker+'.watcher',String(process.pid));
const timer=setInterval(()=>{
  if(Date.now()>deadline){clearInterval(timer);return;}
  for(const pid of fs.readdirSync('/proc').filter(x=>/^\d+$/.test(x))){
    try{
      const status=fs.readFileSync('/proc/'+pid+'/status','utf8');
      const cmd=fs.readFileSync('/proc/'+pid+'/cmdline','utf8').split('\0');
      // 只匹配服务器直接启动的已知工具，不暂停HTTP服务器、监控器或未知进程。
      if(!/^PPid:\s+1$/m.test(status)||!cmd.some(x=>/(?:^|\/)(?:obj2gltf(?:\.js)?|fbx2gltf|FBX2glTF|assimp)$/.test(x)))continue;
      fs.writeFileSync(marker,JSON.stringify({pid:Number(pid)}));
      process.kill(Number(pid),'SIGSTOP');clearInterval(timer);return;
    }catch(e){}
  }
},10);
"""
    command(['docker', 'exec', '-d', converter, 'node', '-e', watcher, marker])
    spec = importlib.util.spec_from_file_location('model_source', Path(__file__).with_name('verify-model-import-source.py'))
    source = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(source)
    payload, _ = source.source_payload('fbx')
    boundary = 'model03' + uuid.uuid4().hex
    body = ('--' + boundary + '\r\nContent-Disposition: form-data; name="format"\r\n\r\nglb\r\n--'
            + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="probe.zip"\r\n'
            + 'Content-Type: application/zip\r\n\r\n').encode() + payload + ('\r\n--' + boundary + '--\r\n').encode()

    def occupy():
        request = urllib.request.Request('http://127.0.0.1:32804/convert', data=body,
                                         headers={'Content-Type': 'multipart/form-data; boundary=' + boundary})
        with OPENER.open(request, timeout=120) as response:
            # 回读完整结果促使原服务正常结束并清理任务目录，不保存大产物。
            while response.read(65536):
                pass
            return response.status

    with ThreadPoolExecutor(max_workers=1) as pool:
        future = pool.submit(occupy)
        try:
            for _ in range(100):
                probe = subprocess.run(['docker', 'exec', converter, 'test', '-f', marker], capture_output=True)
                if probe.returncode == 0:
                    break
                if future.done():
                    raise RuntimeError('占位转换已结束，未暂停实际工具')
                time.sleep(0.1)
            else:
                raise RuntimeError('未观察到实际转换工具')
            result = verify('converter-busy', directory)
        finally:
            # 由容器内记录的唯一进程恢复，不向其他容器或共享转换服务发送信号。
            resume = "const fs=require('fs'),p=process.argv[1];if(fs.existsSync(p+'.watcher')){try{process.kill(Number(fs.readFileSync(p+'.watcher','utf8')),'SIGTERM');}catch(e){if(e.code!=='ESRCH')throw e;}fs.unlinkSync(p+'.watcher');}if(fs.existsSync(p)){const s=JSON.parse(fs.readFileSync(p));try{process.kill(s.pid,'SIGCONT');}catch(e){if(e.code!=='ESRCH')throw e;}finally{fs.unlinkSync(p);}}"
            command(['docker', 'exec', converter, 'node', '-e', resume, marker])
        if future.result(timeout=90) != 200:
            raise RuntimeError('占位转换未恢复成功')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--converter', required=True)
    parser.add_argument('--release', type=Path, required=True)
    args = parser.parse_args()
    if (Path.cwd().resolve() != ROOT or not re.fullmatch(r'wx-model03-converter-[0-9a-f]{8}', args.converter)
            or args.release.resolve().parent != ROOT / 'releases' or not args.release.is_dir()):
        parser.error('仅允许已创建的本批独立转换候选和受限发布目录')
    os.umask(0o077)
    original = json.loads(command(['docker', 'inspect', BACKEND]))[0]
    settings = original['Config']['Cmd']
    required = ('--server.address=127.0.0.1', '--server.port=49102',
                '--dim3d.model-import.app-compatibility=',
                '--dim3d.model-import.converter-url=http://127.0.0.1:32804/convert')
    if not all(item in settings for item in required):
        raise ValueError('后台不是本批禁止发布的回环候选')
    config = next(mount['Source'] for mount in original['Mounts'] if mount['Destination'] == '/opt/wx/application.yaml')
    if not re.search(r'/wx_model02_rehearsal_main_[0-9a-f]{8}\?', Path(config).read_text(encoding='utf-8')):
        raise ValueError('后台不是目标演练副本')
    converter = json.loads(command(['docker', 'inspect', args.converter]))[0]
    if converter['HostConfig']['PortBindings'] != {'8099/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '32804'}]}:
        raise ValueError('转换器端口不是本批回环入口')
    if 'CONVERTER_MAX_CONCURRENT=1' not in converter['Config']['Env']:
        raise ValueError('繁忙验收要求独立转换器并发门限为一')
    backup = BACKEND + '-before-fault-' + uuid.uuid4().hex[:8]
    reports = []
    restored = False
    try:
        reports.append(busy(args.converter, args.release))
        command(['docker', 'stop', args.converter])
        try:
            reports.append(verify('converter-unavailable', args.release))
        finally:
            command(['docker', 'start', args.converter])
            healthy(32804)
        command(['docker', 'stop', BACKEND])
        command(['docker', 'rename', BACKEND, backup])
        try:
            start_backend(original, args.release, timeout_ms=1)
            reports.append(verify('converter-timeout', args.release))
        finally:
            # 启动若提前失败，临时候选可能未创建；仍需继续恢复已保留的原实例。
            subprocess.run(['docker', 'rm', '-f', BACKEND], capture_output=True, timeout=30)
            command(['docker', 'rename', backup, BACKEND])
            command(['docker', 'start', BACKEND])
            healthy(49102)
        restored = True
    finally:
        report = {'checks': reports, 'candidateRestored': restored, 'publicationEnabled': False}
        (args.release / 'fault-result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
