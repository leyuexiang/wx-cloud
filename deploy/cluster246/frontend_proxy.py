"""148新管理端精确代理：保持Unity所用网关、静态包及旧API不变，仅热加载Nginx。"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request

ROOT = Path('/home/wx-editor-build-archives/iot-dist-20261111')
TARGET = Path('/www/server/panel/vhost/nginx/jtyf-editor-web148.conf')
GATEWAY = Path('/opt/wx/editor-test148/config/gateway-data08.yaml')
NGINX = '/www/server/nginx/sbin/nginx'


def candidate_config(original):
    """正则路由按顺序匹配，只在既有新管理API规则前插入3类IoT管理接口。"""
    if 'IOT-DIST-20261111' in original:
        raise ValueError('已存在本批代理，请先核对发布状态')
    lines = original.splitlines(keepends=True)
    anchors = [i for i, line in enumerate(lines) if line.startswith('    location ~ "^/dts-api/admin-api/')]
    if len(anchors) != 1:
        raise ValueError('管理端API代理基线不唯一')
    block = '''    # IOT-DIST-20261111：仅新管理端IoT走246；Unity所用12525及其他API保持原样。
    location ~ "^/dts-api/admin-api/iot/(?:connector|device-binding|message-inbox)/[A-Za-z0-9_-]+$" {
        client_max_body_size 128k;
        rewrite ^/dts-api(/.*)$ $1 break;
        proxy_pass http://127.0.0.1:48097;
        proxy_set_header Authorization $http_authorization;
        proxy_set_header tenant-id $http_tenant_id;
        proxy_set_header x-bot-user "";
        proxy_set_header Cookie "";
        proxy_hide_header Access-Control-Allow-Origin;
        proxy_hide_header Cache-Control;
        proxy_connect_timeout 5s;
        proxy_read_timeout 30s;
    }
    # END-IOT-DIST-20261111
'''
    lines.insert(anchors[0], block)
    return ''.join(lines)


def sha(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''): value.update(block)
    return value.hexdigest()


def run(*args):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode: raise RuntimeError('代理发布步骤失败：' + Path(args[0]).name)
    return result.stdout.decode().strip()


def baseline():
    """固定Unity依赖的网关容器、网关配置和原网页构建，切换前后逐项对比。"""
    return {'gatewayConfigSha256': sha(GATEWAY),
            'gatewayStartedAt': run('docker', 'inspect', '--format', '{{.State.StartedAt}}', 'wx-editor-gateway-data08'),
            'gatewayContainerId': run('docker', 'inspect', '--format', '{{.Id}}', 'wx-editor-gateway-data08'),
            'oldIotStartedAt': run('docker', 'inspect', '--format', '{{.State.StartedAt}}', 'wx-editor-iot-data08'),
            'threeWebIndexSha256': sha('/opt/wx/editor-web148/site/threeWeb/index.html'),
            'dtsArchiveSha256': sha('/home/wx-editor-web148/releases/iot-web-20261111/site.tar.gz')}


def save(path, data):
    pending = path.with_suffix('.pending')
    pending.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    pending.replace(path)


def activate(content):
    """新配置先完整替换并测试，再热加载；失败恢复原配置，保留原连接及业务进程。"""
    before = TARGET.read_bytes()
    pending = TARGET.with_suffix('.dist-ready')
    pending.write_bytes(content); pending.chmod(0o644); pending.replace(TARGET)
    try:
        run(NGINX, '-t'); run(NGINX, '-s', 'reload')
    except Exception:
        TARGET.write_bytes(before)
        run(NGINX, '-t'); run(NGINX, '-s', 'reload')
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'apply', 'rollback'))
    args = parser.parse_args()
    if Path(__file__).resolve().parent != ROOT: raise RuntimeError('发布目录不匹配')
    os.umask(0o077)
    state_path = ROOT / 'frontend-proxy-deployment.json'
    if args.action == 'prepare':
        if state_path.exists(): raise RuntimeError('原发布记录已存在')
        before = TARGET.read_text(encoding='utf-8')
        candidate = candidate_config(before)
        with urllib.request.urlopen('http://127.0.0.1:48097/actuator/health', timeout=5) as response:
            if json.load(response).get('status') != 'UP': raise RuntimeError('246反向通道不健康')
        browser = json.loads((ROOT / 'node246-live-browser.json').read_text(encoding='utf-8'))
        if not browser.get('passed') or len(browser.get('checks', [])) != 11:
            raise RuntimeError('246真实网页未通过')
        shutil.copy2(TARGET, ROOT / 'frontend.before.conf')
        (ROOT / 'frontend.candidate.conf').write_text(candidate, encoding='utf-8')
        state = {'status': 'PREPARED', 'beforeSha256': sha(TARGET),
                 'candidateSha256': sha(ROOT / 'frontend.candidate.conf'), 'unityBaseline': baseline(),
                 'proxyUpstream': '127.0.0.1:48097', 'gateway12525Changed': False, 'oldIotStopped': False}
    else:
        state = json.loads(state_path.read_text(encoding='utf-8'))
        if baseline() != state['unityBaseline']: raise RuntimeError('Unity依赖现场已变化，暂停本批配置变更')
        if args.action == 'apply':
            if state['status'] != 'PREPARED' or sha(TARGET) != state['beforeSha256']:
                raise RuntimeError('代理基线已变化，不能覆盖')
            content = ROOT / 'frontend.candidate.conf'
            if sha(content) != state['candidateSha256']: raise RuntimeError('候选摘要不匹配')
            activate(content.read_bytes()); state['status'] = 'APPLIED'
        else:
            if state['status'] != 'APPLIED' or sha(TARGET) != state['candidateSha256']:
                raise RuntimeError('代理已有后续变更，不能回退')
            content = ROOT / 'frontend.before.conf'
            if sha(content) != state['beforeSha256']: raise RuntimeError('原配置摘要不匹配')
            activate(content.read_bytes()); state['status'] = 'ROLLED_BACK'
        if baseline() != state['unityBaseline']: raise RuntimeError('Unity依赖比对不一致')
        state['unityBaselinePreserved'] = True
    save(state_path, state)
    print(json.dumps(state, ensure_ascii=False))


if __name__ == '__main__':
    try: main()
    except Exception as error:
        print(str(error) if isinstance(error, (ValueError, RuntimeError)) else type(error).__name__)
        raise SystemExit(1)
