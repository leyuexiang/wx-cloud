"""备份后更新既有 data08 隔离服务，启动失败自动恢复原包和配置。"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request
from datetime import datetime, timezone


ROOT = Path('/opt/wx/editor-test148')
CONTAINER = 'wx-editor-dimensions-data08'
PREVIEW = '/admin-api/dim3d/scene/publication/preview'


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def docker(*args):
    return subprocess.run(['docker', *args], check=True, text=True, capture_output=True).stdout.strip()


def ready():
    deadline = time.monotonic() + 100
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen('http://127.0.0.1:48093/actuator/health', timeout=3) as response:
                up = json.loads(response.read()).get('status') == 'UP'
            if up and docker('inspect', '--format', '{{.State.Health.Status}}', CONTAINER) == 'healthy':
                return True
        except Exception:
            pass
        time.sleep(2)
    return False


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sha256', required=True)
    args = parser.parse_args()
    candidate = ROOT / 'jars/dimensions-data09.candidate.jar'
    target = ROOT / 'jars/dimensions-data08.jar'
    business = ROOT / 'jars/dimensions.jar'
    config = ROOT / 'config/dimensions-data08.yaml'
    assert digest(candidate) == args.sha256.lower(), 'candidate digest mismatch'
    assert not os.path.samefile(target, business), 'isolation package aliases business package'
    text = config.read_text(encoding='utf-8')
    assert '/wx_editor_data08?' in text, 'unexpected database'
    # 配置文件覆盖包内配置，必须同步加入精确匿名路径，不能放开管理路由通配符。
    lines = text.splitlines(keepends=True)
    if not any(line.strip() == '- ' + PREVIEW for line in lines):
        indexes = [i for i, line in enumerate(lines) if line.strip() == 'ignore-urls:']
        assert len(indexes) == 1, 'ambiguous ignore-urls section'
        index = indexes[0]
        indent = lines[index][:len(lines[index]) - len(lines[index].lstrip())]
        lines.insert(index + 1, indent + '- ' + PREVIEW + '\n')
    text = ''.join(lines)
    old_hash = digest(target)
    business_hash = digest(business)
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    backup = ROOT / 'backups' / ('data09-preview-' + stamp)
    backup.mkdir(parents=True)
    shutil.copy2(target, backup / target.name)
    shutil.copy2(config, backup / config.name)
    report = {'status': 'PREPARED', 'database': 'wx_editor_data08', 'container': CONTAINER,
              'sha256': args.sha256.lower(), 'previousSha256': old_hash,
              'businessSha256': business_hash, 'backup': str(backup), 'gatewayPort': 12525}
    try:
        docker('stop', CONTAINER)
        # 保留挂载文件的 inode，使现有容器重启后读取更新内容。
        shutil.copyfile(candidate, target)
        config.write_text(text, encoding='utf-8')
        docker('start', CONTAINER)
        assert ready(), 'isolated service did not become healthy'
        assert digest(business) == business_hash, 'business package changed'
        report['status'] = 'DEPLOYED'
    except Exception as error:
        docker('stop', CONTAINER)
        shutil.copyfile(backup / target.name, target)
        shutil.copyfile(backup / config.name, config)
        docker('start', CONTAINER)
        report.update(status='ROLLED_BACK' if ready() else 'ROLLBACK_UNHEALTHY', failure=type(error).__name__)
    (backup / 'deployment.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    (ROOT / 'deployment-data09-preview.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report, indent=2))
    return 0 if report['status'] == 'DEPLOYED' else 1


if __name__ == '__main__':
    raise SystemExit(main())
