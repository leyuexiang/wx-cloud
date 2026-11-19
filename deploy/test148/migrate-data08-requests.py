"""历史请求保留回填；默认只读，写入限定隔离库、备份校验及停写窗口。"""
from __future__ import annotations

import argparse
from decimal import Decimal, InvalidOperation
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess


SPEC = importlib.util.spec_from_file_location('request_backfill_db', Path(__file__).with_name('verify-data08-db.py'))
VERIFY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFY)
SOURCE_TABLE = 'wx_3d_scene_revision'
TARGET_TABLE = 'wx_3d_scene_request'
SOURCE_COLUMNS = 'id,tenant_id,scene_id,revision_no,HEX(request_id),request_hash,UNIX_TIMESTAMP(create_time)'
TARGET_COLUMNS = ('id,tenant_id,scene_id,revision_no,HEX(request_id),request_hash,'
                  'UNIX_TIMESTAMP(created_time),UNIX_TIMESTAMP(expires_time),UNIX_TIMESTAMP(updated_time)')


def read_metadata(args):
    # 同一只读事务固定来源和目标视图；不下载场景四包，只读取必要身份元数据。
    rows = VERIFY.mysql(args, "SET time_zone='+00:00'; START TRANSACTION READ ONLY; SELECT " + SOURCE_COLUMNS
                        + ' FROM ' + SOURCE_TABLE + ' ORDER BY id; SELECT ' + TARGET_COLUMNS
                        + ' FROM ' + TARGET_TABLE + ' ORDER BY id; COMMIT;')
    if any(len(row) not in (7, 9) for row in rows):
        raise RuntimeError('回填元数据列不完整')
    return [row for row in rows if len(row) == 7], [row for row in rows if len(row) == 9]


def positive(value):
    number = int(value)
    if number < 1 or str(number) != str(value):
        raise ValueError('身份和修订号必须为规范正整数')
    return str(number)


def timestamp(value):
    try:
        number = Decimal(value)
    except InvalidOperation as error:
        raise ValueError('时间不是有效纪元秒') from error
    if not number.is_finite() or number <= 0 or number > 2147483647 or number.as_tuple().exponent < -6:
        raise ValueError('时间超出TIMESTAMP范围或精度')
    return format(number, 'f')


def normalize(rows, target=False):
    result = []
    for row in rows:
        if len(row) != (9 if target else 7): raise RuntimeError('元数据列数无效')
        head = [positive(value) for value in row[:4]]
        request = bytes.fromhex(row[4]).decode('utf-8')
        if not request.strip() or len(request) > 128 or not re.fullmatch('[0-9a-f]{64}', row[5]):
            raise ValueError('请求号或摘要不完整，禁止猜测回填')
        result.append(head + [request.encode('utf-8').hex().upper(), row[5]] + [timestamp(value) for value in row[6:]])
    return sorted(result, key=lambda item: int(item[0]))


def key(row):
    return tuple(row[index] for index in (1, 2, 4))


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=True, separators=(',', ':')).encode()).hexdigest()


def expected_table(name, rows, target=False):
    # 十六进制二进制常量保留请求号原字节，避免引号、换行和不同排序规则改变快照判断。
    extra = ',expires_epoch DECIMAL(20,6),updated_epoch DECIMAL(20,6)' if target else ''
    statements = [f'CREATE TEMPORARY TABLE {name}(id BIGINT PRIMARY KEY,tenant_id BIGINT,scene_id BIGINT,revision_no INT,'
                  f'request_id VARBINARY(512),request_hash VARBINARY(64),created_epoch DECIMAL(20,6){extra}) ENGINE=InnoDB;']
    if rows:
        values = [','.join(row[:4] + ["UNHEX('" + row[4] + "')", "UNHEX('" + row[5].encode().hex() + "')"] + row[6:])
                  for row in rows]
        statements.append(f'INSERT INTO {name} VALUES(' + '),('.join(values) + ');')
    return statements


def snapshot_guard(table, expected, count, target=False):
    created = 'created_time' if target else 'create_time'
    extra = (' AND UNIX_TIMESTAMP(r.expires_time)=e.expires_epoch AND UNIX_TIMESTAMP(r.updated_time)=e.updated_epoch'
             if target else '')
    return ("INSERT INTO _data08_request_guard VALUES((SELECT COUNT(*) FROM " + table + f')={count} AND '
            '(SELECT COUNT(*) FROM ' + table + ' r JOIN ' + expected + ' e ON r.id=e.id '
            'AND r.tenant_id=e.tenant_id AND r.scene_id=e.scene_id AND r.revision_no=e.revision_no '
            'AND BINARY r.request_id=e.request_id AND BINARY r.request_hash=e.request_hash '
            f'AND UNIX_TIMESTAMP(r.{created})=e.created_epoch' + extra + f')={count});')


def plan(args):
    if args.database != 'wx_editor_data08' or not 1 <= args.retention_days <= 365:
        raise RuntimeError('仅允许隔离库及1至365天的保留策略')
    raw_source, raw_target = read_metadata(args)
    source, target = normalize(raw_source), normalize(raw_target, True)
    by_key = {key(row): row for row in source}
    if len(by_key) != len(source) or len({row[0] for row in source}) != len(source):
        raise RuntimeError('来源修订身份或请求号重复')
    target_keys = {key(row): row for row in target}
    if len(target_keys) != len(target) or len({row[0] for row in target}) != len(target):
        raise RuntimeError('目标请求身份重复')
    for row in target:
        original = by_key.get(key(row))
        if original is None or row[3] != original[3] or row[5] != original[5]:
            raise RuntimeError('已有请求引用缺少来源或摘要/修订号错配，停止回填')
    missing = [row for row in source if key(row) not in target_keys]
    occupied_ids = {row[0] for row in target}
    if any(row[0] in occupied_ids for row in missing):
        raise RuntimeError('拟回填主键被其他请求占用')
    seconds = args.retention_days * 86400
    for row in missing: timestamp(str(Decimal(row[6]) + seconds))
    sql = ["SET time_zone='+00:00'; START TRANSACTION;",
           'SELECT id FROM wx_3d_scene_revision ORDER BY id FOR UPDATE;',
           'SELECT id FROM wx_3d_scene_request ORDER BY id FOR UPDATE;']
    sql += expected_table('_data08_expected_source', source)
    sql += expected_table('_data08_expected_request', target, True)
    # CHECK失败时客户端立即退出并断开连接，未提交的新请求行随事务回滚。
    sql += ['CREATE TEMPORARY TABLE _data08_request_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;',
            snapshot_guard(SOURCE_TABLE, '_data08_expected_source', len(source)),
            snapshot_guard(TARGET_TABLE, '_data08_expected_request', len(target), True)]
    if missing:
        ids = ','.join(row[0] for row in missing)
        sql += ["INSERT INTO wx_3d_scene_request(id,tenant_id,scene_id,revision_no,request_id,request_hash,created_time,expires_time,updated_time) "
                "SELECT e.id,e.tenant_id,e.scene_id,e.revision_no,CONVERT(e.request_id USING utf8mb4),CONVERT(e.request_hash USING utf8mb4),"
                f'FROM_UNIXTIME(e.created_epoch),FROM_UNIXTIME(e.created_epoch+{seconds}),CURRENT_TIMESTAMP '
                f'FROM _data08_expected_source e WHERE e.id IN ({ids});',
                f'INSERT INTO _data08_request_guard VALUES(ROW_COUNT()={len(missing)});']
    sql += ['COMMIT;']
    report = {'database': args.database, 'readOnly': True, 'databaseWrite': False, 'revisionCount': len(source),
              'existingRequestCount': len(target), 'requestReferencesToWrite': len(missing),
              'retentionDays': args.retention_days, 'expiryBasis': 'revision.create_time',
              'sourceMetadataSha256': digest(source), 'existingRequestsSha256': digest(target),
              'planSha256': digest([args.retention_days, source, target])}
    return report, '\n'.join(sql)


def require_stopped():
    result = subprocess.run(['docker', 'inspect', 'wx-editor-dimensions-data08'], capture_output=True, text=True, check=True)
    state = json.loads(result.stdout)[0]['State']
    if state['Running'] or state.get('Status') != 'exited':
        raise RuntimeError('必须先停止隔离三维服务以建立写入窗口')


def apply(args, report, sql):
    if args.database != 'wx_editor_data08' or report.get('database') != args.database:
        raise RuntimeError('写入仅允许隔离库')
    if args.confirm != 'DATA08-REQUEST-BACKFILL' or args.expected_plan != report['planSha256']:
        raise RuntimeError('缺少显式确认或已审阅计划发生漂移')
    if not args.backup_marker: raise RuntimeError('必须提供已验证备份标记')
    marker = json.loads(Path(args.backup_marker).read_text(encoding='utf-8'))
    backup = Path(marker.get('backup', ''))
    if marker.get('database') != args.database or not backup.is_file():
        raise RuntimeError('备份数据库或文件不匹配')
    with backup.open('rb') as stream:
        checksum = hashlib.sha256()
        for block in iter(lambda: stream.read(1024 * 1024), b''): checksum.update(block)
    if checksum.hexdigest() != marker.get('sha256'): raise RuntimeError('备份文件摘要不匹配')
    require_stopped()
    VERIFY.mysql(args, sql)
    return {**report, 'readOnly': False, 'databaseWrite': True, 'backupSha256': checksum.hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--user', default='root')
    parser.add_argument('--database', default='wx_editor_data08')
    parser.add_argument('--password-env', default='WX_MYSQL_ROOT_PASSWORD')
    parser.add_argument('--mysql-container')
    parser.add_argument('--retention-days', type=int, default=30)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--confirm', default='')
    parser.add_argument('--expected-plan')
    parser.add_argument('--backup-marker')
    parser.add_argument('--json-output', type=Path)
    args = parser.parse_args()
    try:
        report, sql = plan(args)
        if args.apply: report = apply(args, report, sql)
    except Exception as error:
        report = {'passed': False, 'error': str(error)}
    if args.json_output: args.json_output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 1 if report.get('passed') is False else 0


if __name__ == '__main__': raise SystemExit(main())
