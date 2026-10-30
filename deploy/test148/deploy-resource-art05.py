"""只替换三维测试服务：显式候选文件和摘要、独立回执、失败回退，不覆写历史验收证据。"""
from pathlib import Path
import argparse, datetime, hashlib, json, os, shutil, subprocess, time, urllib.request

ROOT = Path('/opt/wx/editor-test148')


def digest(file):
    with file.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def run(*args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, **kwargs)


def healthy():
    for _ in range(60):
        try:
            value = json.loads(urllib.request.urlopen('http://127.0.0.1:48092/actuator/health', timeout=2).read())
            if value.get('status') == 'UP':
                return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError('三维服务健康检查未通过')


def main():
    args = argparse.ArgumentParser()
    args.add_argument('--candidate', required=True)
    args.add_argument('--sha256', required=True)
    options = args.parse_args()
    if Path.cwd().resolve() != ROOT:
        raise RuntimeError('工作目录不匹配')
    os.umask(0o077)
    candidate = (ROOT / options.candidate).resolve()
    if candidate.parent != ROOT / 'jars' or candidate.name != 'dimensions-art05.candidate.jar' or digest(candidate) != options.sha256:
        raise RuntimeError('候选路径或摘要不匹配，未修改服务')
    target = ROOT / 'jars/dimensions.jar'
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup = ROOT / 'backups' / ('art05-service-' + stamp)
    backup.mkdir(mode=0o700)
    shutil.copy2(target, backup / 'dimensions.jar')
    evidence = ROOT / 'evidence-art05' / stamp
    evidence.mkdir(parents=True, mode=0o700)
    # 老回归仍可复用，但输出另存本次证据，结束后恢复老报告原字节。
    outputs = ['verification-art03.json', 'verification-art02.json', 'download-verification.json', 'verification-12522.json']
    previous = {name: (ROOT / name).read_bytes() if (ROOT / name).exists() else None for name in outputs}
    report = {'backup': str(backup), 'evidence': str(evidence), 'candidateSha256': options.sha256,
              'previousSha256': digest(target), 'databaseStructureChanged': False, 'gatewayUnchanged': True}
    try:
        pending = target.with_suffix('.art05-ready')
        shutil.copy2(candidate, pending)
        pending.replace(target)
        run('docker', 'compose', '-f', 'compose.yaml', 'up', '-d', '--no-deps', '--force-recreate', 'dimensions')
        healthy()
        environment = {**os.environ, 'WX_ART03_PORT': '48092', 'WX_ART03_SOURCE_DIR': str(ROOT / 'samples/art05-car-source'),
                       'WX_ART02_PORT': '48092', 'WX_ART02_DOWNLOAD_ENABLED': '1', 'WX_ART03_ENABLED': '1', 'WX_VERIFY_PORT': '12522'}
        for script in ['verify-resource-art05.py', 'verify-resource-art03.py', 'verify-resource-art02.py', 'verify-resource-download.py', 'verify-api.py']:
            report['phase'] = script
            run('python3', script, env=environment)
        for name in outputs + ['verification-art05.json']:
            if (ROOT / name).exists():
                shutil.copy2(ROOT / name, evidence / name)
        report['activeSha256'] = digest(target)
        if report['activeSha256'] != options.sha256:
            raise RuntimeError('激活文件摘要不匹配')
        report['status'] = 'DEPLOYED'
    except Exception as error:
        pending = target.with_suffix('.art05-rollback')
        shutil.copy2(backup / 'dimensions.jar', pending)
        pending.replace(target)
        run('docker', 'compose', '-f', 'compose.yaml', 'up', '-d', '--no-deps', '--force-recreate', 'dimensions')
        healthy()
        report.update(status='ROLLED_BACK', errorType=type(error).__name__, activeSha256=digest(target))
    finally:
        for name, content in previous.items():
            if (ROOT / name).exists():
                shutil.copy2(ROOT / name, evidence / name)
            if content is None:
                (ROOT / name).unlink(missing_ok=True)
            else:
                (ROOT / name).write_bytes(content)
        (evidence / 'deployment.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
        (ROOT / 'deployment-art05.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report))
    if report['status'] != 'DEPLOYED':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
