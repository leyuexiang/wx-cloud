"""仅部署148隔离三维服务的场景增量。旧包备份后原子替换，健康或回归失败即回退。"""
from pathlib import Path
import argparse, datetime, hashlib, json, os, shutil, subprocess, time, urllib.request

ROOT = Path('/opt/wx/editor-test148')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def mysql(sql):
    command = ['docker', 'exec', '-i', 'mysql8', 'sh', '-c',
        'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot -N -B -r wx_editor_test148']
    result = subprocess.run(command, input=sql, text=True, capture_output=True)
    if result.returncode: raise RuntimeError('场景迁移SQL未成功，原始输出仅留在内存')
    return result.stdout


def restart():
    subprocess.run(['docker', 'compose', '-f', 'compose.yaml', 'up', '-d', '--no-deps', '--force-recreate', 'dimensions'], check=True, capture_output=True)
    for _ in range(60):
        try:
            body = json.loads(urllib.request.urlopen('http://127.0.0.1:48092/actuator/health', timeout=2).read())
            if body.get('status') == 'UP': return
        except Exception: pass
        time.sleep(2)
    raise RuntimeError('三维服务未在限定时间内恢复健康')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sha256', required=True)
    args = parser.parse_args()
    if Path.cwd().resolve() != ROOT: raise RuntimeError('部署目录不符')
    os.umask(0o077)
    candidate = ROOT / 'jars/dimensions-scene01.candidate.jar'
    if not candidate.is_file() or sha(candidate) != args.sha256: raise RuntimeError('候选服务包摘要不符')
    target = ROOT / 'jars/dimensions.jar'
    if sha(target) != '8bfac7925e7cb19f619ef0a48dbfcee863547167cdd08eadbb6b303ca75292d7':
        raise RuntimeError('线上包已不是已核验ART05版本，禁止覆盖其他工作')
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup = ROOT / 'backups' / ('scene01-' + stamp); backup.mkdir()
    shutil.copy2(target, backup / 'dimensions.jar')
    # 只备份结构，本增量不修改任何旧表数据；新场景及修订只写入新增表。
    dump = subprocess.run(['docker', 'exec', 'mysql8', 'sh', '-c',
        'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --no-data --skip-comments --set-gtid-purged=OFF wx_editor_test148'], capture_output=True)
    if dump.returncode: raise RuntimeError('数据库结构备份失败')
    (backup / 'schema-before.sql').write_bytes(dump.stdout)
    resource_before = mysql('SELECT id,current_revision,content_hash FROM wx_dim3d_resource WHERE id=2117612791529533441;')
    report = {'backup': str(backup), 'candidateSha256': args.sha256, 'previousSha256': sha(target), 'existingResourceBefore': resource_before.strip()}
    try:
        if mysql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('wx_dim3d_scene','wx_dim3d_scene_revision');").strip() != '0':
            raise RuntimeError('场景增量表已存在，需检查上一轮部署记录')
        mysql((ROOT / 'migrations/scene01/001-scene.up.sql').read_text(encoding='utf-8'))
        pending = target.with_suffix('.scene01-ready')
        shutil.copy2(candidate, pending); pending.replace(target)
        restart()
        # 既有登录、资源权限与旧场景入口继续执行原回归，报告另存，恢复历史文件字节。
        old = ROOT / 'verification-12522.json'
        previous = old.read_bytes() if old.exists() else None
        try:
            run = subprocess.run(['python3', 'verify-api.py'], env={**os.environ, 'WX_VERIFY_PORT':'12522'}, capture_output=True)
            (backup / 'api-regression.json').write_bytes(old.read_bytes())
            if run.returncode: raise RuntimeError('既有API回归未通过')
        finally:
            if previous is not None: old.write_bytes(previous)
        resource_after = mysql('SELECT id,current_revision,content_hash FROM wx_dim3d_resource WHERE id=2117612791529533441;')
        if resource_after != resource_before: raise RuntimeError('既有资源当前指针发生变化')
        report.update(status='DEPLOYED', existingResourceUnchanged=True, newTables=['wx_dim3d_scene','wx_dim3d_scene_revision'])
    except Exception as error:
        pending = target.with_suffix('.scene01-rollback')
        shutil.copy2(backup / 'dimensions.jar', pending); pending.replace(target); restart()
        report.update(status='ROLLED_BACK', failure=type(error).__name__, message=str(error))
        (ROOT / 'deployment-scene01.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        raise
    (ROOT / 'deployment-scene01.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__': main()
