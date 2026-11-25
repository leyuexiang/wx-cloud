"""在唯一命名的合成库验证请求回填、漂移拒绝与事务回滚，不复制业务正文。"""
import argparse
from argparse import Namespace
import importlib.util
import json
from pathlib import Path
import re
import uuid
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location('request_migration', Path(__file__).with_name('migrate-data08-requests.py'))
M = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--confirm', default='')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not args.execute or args.confirm != 'DATA08-REQUEST-REHEARSAL':
        raise RuntimeError('仅在明确确认后创建合成演练库')
    name = 'wx_data08_request_rehearsal_' + uuid.uuid4().hex[:12]
    db = Namespace(host='127.0.0.1', port=3306, user='root', database='mysql',
                   password_env=None, mysql_container='mysql8')
    checks = []
    report = {'passed': False, 'database': name, 'syntheticOnly': True, 'checks': checks}
    created = False

    def sql(statement): return M.VERIFY.mysql(db, statement)

    def check(label, condition):
        checks.append({'name': label, 'passed': bool(condition)})
        if not condition: raise RuntimeError(label)

    def make_plan():
        # 只替换测试连接目标，规划仍按正式隔离库的身份校验规则生成原样事务。
        source, target = M.read_metadata(db)
        with patch.object(M, 'read_metadata', return_value=(source, target)):
            return M.plan(Namespace(database='wx_editor_data08', retention_days=30))

    try:
        sql('CREATE DATABASE `' + name + '` CHARACTER SET utf8mb4;')
        created = True
        db.database = name
        # 来源只含规划器需要的元数据，不携带任何共享业务表的正文或身份。
        sql('CREATE TABLE wx_3d_scene_revision(id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,scene_id BIGINT NOT NULL,'
            'revision_no INT NOT NULL,request_id VARCHAR(128) NOT NULL,request_hash CHAR(64) NOT NULL,create_time TIMESTAMP NOT NULL,'
            'UNIQUE(tenant_id,scene_id,revision_no),UNIQUE(tenant_id,scene_id,request_id)) ENGINE=InnoDB;')
        schema = (Path(__file__).parent/'migrations/data08/003-retention-references.up.sql').read_text(encoding='utf-8')
        sql(schema)
        special = "历史请求'换行\n".encode().hex()
        sql("SET time_zone='+00:00'; INSERT INTO wx_3d_scene_revision VALUES"
            f"(10,2,100,1,CONVERT(UNHEX('{special}') USING utf8mb4),REPEAT('a',64),FROM_UNIXTIME(1700000000)),"
            f"(11,3,100,1,CONVERT(UNHEX('{special}') USING utf8mb4),REPEAT('a',64),FROM_UNIXTIME(1700000000)),"
            "(12,2,100,2,'existing',REPEAT('b',64),FROM_UNIXTIME(1700000000));"
            "INSERT INTO wx_3d_scene_request VALUES(90,2,100,'existing',REPEAT('b',64),2,FROM_UNIXTIME(1700000010),"
            "FROM_UNIXTIME(1700000000),FROM_UNIXTIME(1700000000));")
        baseline = sql('SELECT ' + M.TARGET_COLUMNS + ' FROM wx_3d_scene_request ORDER BY id')
        report_plan, transaction = make_plan()
        check('只补两条跨租户缺失请求', report_plan['requestReferencesToWrite'] == 2)
        sql("UPDATE wx_3d_scene_revision SET request_hash=REPEAT('c',64) WHERE id=10")
        refused = False
        try: sql(transaction)
        except RuntimeError: refused = True
        check('事务内检测来源漂移并拒绝提交', refused and sql('SELECT COUNT(*) FROM wx_3d_scene_request')[0][0] == '1')
        sql("UPDATE wx_3d_scene_revision SET request_hash=REPEAT('a',64) WHERE id=10")
        _, transaction = make_plan()
        # 在插入完成后注入CHECK失败，验证连接退出后整笔事务确实回滚。
        refused = False
        try: sql(transaction.replace('COMMIT;', 'INSERT INTO _data08_request_guard VALUES(0); COMMIT;'))
        except RuntimeError: refused = True
        check('中途失败回滚已插入请求', refused and sql('SELECT COUNT(*) FROM wx_3d_scene_request')[0][0] == '1')
        sql(transaction)
        after = sql('SELECT ' + M.TARGET_COLUMNS + ' FROM wx_3d_scene_request ORDER BY id')
        check('历史请求成功落库', len(after) == 3)
        check('已有过期记录逐字段保持', next(row for row in after if row[0] == '90') == baseline[0])
        check('原创建时间计算到期且不重新起算', sql('SELECT COUNT(*) FROM wx_3d_scene_request WHERE id IN (10,11) '
              'AND UNIX_TIMESTAMP(created_time)=1700000000 AND UNIX_TIMESTAMP(expires_time)=1702592000')[0][0] == '2')
        check('特殊请求号与租户字节保持', sql(f"SELECT COUNT(*) FROM wx_3d_scene_request WHERE HEX(request_id)=UPPER('{special}')")[0][0] == '2')
        repeat, _ = make_plan()
        check('重复规划零新增', repeat['requestReferencesToWrite'] == 0)
        report['plan'] = report_plan
        report['passed'] = True
    except Exception as error:
        report['error'] = str(error)
    finally:
        if created:
            # 库名只来自本进程随机值；仅回收本轮成功创建且从未装入业务数据的合成库。
            if not re.fullmatch('wx_data08_request_rehearsal_[0-9a-f]{12}', name):
                raise RuntimeError('演练库名校验失败')
            db.database = 'mysql'
            sql('DROP DATABASE `' + name + '`;')
            report['syntheticDatabaseRemoved'] = True
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report['passed'] else 1


if __name__ == '__main__': raise SystemExit(main())
