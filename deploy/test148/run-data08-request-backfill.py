"""备份并在有界停写窗口补齐历史请求，恢复原隔离服务并校验来源未变。"""
import argparse
from argparse import Namespace
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request


ROOT = Path('/home/wx-data08-request-backfill-20261119')
SERVICE = 'wx-editor-dimensions-data08'


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


M = load('backfill_requests', 'migrate-data08-requests.py')
ROUNDTRIP = load('request_roundtrip', 'verify-data08-roundtrip.py')
AUDIT = load('request_audit', 'audit-data08-retention.py')


def inspect(name):
    return json.loads(subprocess.check_output(['docker', 'inspect', name]))[0]


def healthy():
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen('http://127.0.0.1:48093/actuator/health', timeout=3) as response:
                if json.load(response).get('status') == 'UP': return
        except (OSError, ValueError): pass
        time.sleep(2)
    raise RuntimeError('原隔离服务未恢复健康')


def file_hash(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024*1024), b''): result.update(block)
    return result.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--confirm', default='')
    args = parser.parse_args()
    if not args.execute or args.confirm != 'DATA08-REQUEST-WINDOW': raise RuntimeError('需要确认隔离回填窗口')
    if (ROOT/'result.json').exists(): raise RuntimeError('已有执行记录，禁止覆盖')
    reviewed = json.loads((ROOT/'plan.json').read_text(encoding='utf-8'))
    if reviewed['requestReferencesToWrite'] != 182: raise RuntimeError('本轮计划数量不符')
    db = Namespace(host='127.0.0.1', port=3306, user='root', database='wx_editor_data08', password_env=None,
                   mysql_container='mysql8', retention_days=30, apply=True, confirm='DATA08-REQUEST-BACKFILL',
                   expected_plan=reviewed['planSha256'], backup_marker=str(ROOT/'backup-marker.json'))
    before = inspect(SERVICE)
    if not before['State']['Running']: raise RuntimeError('来源服务必须原本运行')
    if '--dim3d.scene-retention.enabled=true' not in before['Config']['Cmd']:
        raise RuntimeError('保留策略尚未启用，停止回填')
    baseline = {name: {'id': inspect(name)['Id'], 'startedAt': inspect(name)['State']['StartedAt']}
                for name in ('wx-editor-dimensions', 'wx-editor-gateway', 'wx-editor-gateway-data08')}
    config = Path(next(m['Source'] for m in before['Mounts'] if m['Destination'] == '/opt/wx/application.yaml'))
    config_hash = file_hash(config)
    if config.read_text(encoding='utf-8').count('/wx_editor_data08?') != 1: raise RuntimeError('数据源门禁失败')
    if not ROUNDTRIP.check(db)['passed']: raise RuntimeError('前置全量回读失败')

    def sql(statement): return M.VERIFY.mysql(db, statement)

    def no_lease():
        if sql('SELECT COUNT(*) FROM wx_3d_scene WHERE deleted=0 AND lock_expire>CURRENT_TIMESTAMP')[0][0] != '0':
            raise RuntimeError('存在活动编辑租约，停止写入')

    no_lease()
    report = {'passed': False, 'database': db.database, 'reviewedPlan': reviewed,
              'serviceId': before['Id'], 'otherServices': baseline}
    stopped = False
    try:
        # 停写后再重新规划并核对已审阅摘要，不能以历史计划覆盖现场新增请求。
        subprocess.run(['docker', 'stop', SERVICE], check=True, capture_output=True, timeout=45)
        stopped = True
        no_lease()
        planned, transaction = M.plan(db)
        if planned['planSha256'] != reviewed['planSha256']: raise RuntimeError('停写后计划漂移，请重新只读审计')
        checksum_sql = ('CHECKSUM TABLE wx_3d_scene,wx_3d_scene_revision,wx_3d_scene_content,'
                        'wx_3d_scene_bookmark,wx_3d_scene_publication EXTENDED')
        business_before = sql(checksum_sql)
        if any(row[1] == 'NULL' for row in business_before): raise RuntimeError('业务表校验和不可用')
        source_before, requests_before = M.read_metadata(db)
        backup_path = ROOT/'scene-retention-tables.sql.gz'
        command = ['docker', 'exec', 'mysql8', 'sh', '-c',
                   'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --single-transaction --no-tablespaces wx_editor_data08 '
                   'wx_3d_scene wx_3d_scene_revision wx_3d_scene_content wx_3d_scene_request wx_3d_scene_bookmark wx_3d_scene_publication']
        with backup_path.open('wb') as output:
            process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            with gzip.GzipFile(fileobj=output, mode='wb') as compressed: shutil.copyfileobj(process.stdout, compressed)
            process.stdout.close()
            process.communicate()
            if process.returncode: raise RuntimeError('备份失败，停止回填')
        backup_path.chmod(0o600)
        marker = {'database': db.database, 'backup': str(backup_path), 'sha256': file_hash(backup_path)}
        (ROOT/'backup-marker.json').write_text(json.dumps(marker, indent=2), encoding='utf-8')
        report['backup'] = marker
        report['applied'] = M.apply(db, planned, transaction)
        source_after, requests_after = M.read_metadata(db)
        before_ids = {row[0] for row in requests_before}
        preserved = [row for row in requests_after if row[0] in before_ids]
        report['sourceMetadataUnchanged'] = M.normalize(source_before) == M.normalize(source_after)
        report['existingRequestsUnchanged'] = M.normalize(requests_before, True) == M.normalize(preserved, True)
        report['businessTablesUnchanged'] = business_before == sql(checksum_sql)
        report['repeatPlan'], _ = M.plan(db)
        report['roundtrip'] = ROUNDTRIP.check(db)
        audit = AUDIT.audit(db)
        report['retentionSummary'] = {key: audit[key] for key in ('revisionCount', 'reasonCounts', 'reclaimCandidateCount')}
        report['requestCountAfter'] = len(requests_after)
        report['passed'] = (report['sourceMetadataUnchanged'] and report['existingRequestsUnchanged']
                            and report['businessTablesUnchanged'] and report['roundtrip']['passed']
                            and report['repeatPlan']['requestReferencesToWrite'] == 0
                            and len(requests_after) == len(source_before))
    except Exception as error:
        report['error'] = str(error)
    finally:
        # 不删除已提交引用；无论迁移是否成功都恢复原容器，错误场景保留备份和执行记录。
        if stopped:
            try:
                if inspect(SERVICE)['Id'] != before['Id']: raise RuntimeError('服务身份发生外部变化')
                subprocess.run(['docker', 'start', SERVICE], check=True, capture_output=True, timeout=40)
                healthy()
                report['serviceRestored'] = True
            except Exception as error:
                report['serviceRestored'] = False
                report['restoreError'] = str(error)
        else:
            report['serviceRestored'] = inspect(SERVICE)['State']['Running']
        report['configUnchanged'] = file_hash(config) == config_hash
        report['otherServicesUnchanged'] = all(inspect(name)['Id'] == item['id']
            and inspect(name)['State']['StartedAt'] == item['startedAt'] for name, item in baseline.items())
        report['passed'] = report['passed'] and report['serviceRestored'] and report['configUnchanged'] and report['otherServicesUnchanged']
        (ROOT/'result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report['passed'] else 1


if __name__ == '__main__': raise SystemExit(main())
