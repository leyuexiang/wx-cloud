"""通过 148 隔离网关验证发布、票据管理和审计，保留撤销后的验收历史。"""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import urllib.parse
import uuid


ROOT = Path('/opt/wx/editor-test148')
BASE = 'http://127.0.0.1:12525/admin-api'
DATABASE = 'wx_editor_data08'


def main():
    os.umask(0o077)
    # 固定隔离库和网关后端，避免将专用样本写入业务库。
    assert '/wx_editor_data08?' in (ROOT / 'config/dimensions-data08.yaml').read_text()
    assert '127.0.0.1:48093' in (ROOT / 'config/gateway-data08.yaml').read_text()
    spec = importlib.util.spec_from_file_location('data08_api', Path(__file__).with_name('verify-data08-isolation-service.py'))
    api = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(api)
    identities = json.loads((ROOT / 'test-identities.json').read_text())
    tokens = {}
    checks = []
    publications = []
    tickets = []
    scene_id = None
    marker = 'DATA09-' + uuid.uuid4().hex

    def db(sql):
        command = ['docker', 'exec', '-i', 'mysql8', 'sh', '-c',
                   'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names --default-character-set=utf8mb4 ' + DATABASE]
        result = subprocess.run(command, input=sql, text=True, capture_output=True, check=True)
        return result.stdout.strip().splitlines()

    def check(name, condition):
        checks.append({'name': name, 'passed': bool(condition)})
        if not condition:
            raise RuntimeError(name)

    def call(path, role='tenant_admin', method='GET', body=None):
        identity, token = tokens[role]
        return api.call(BASE, path, token, identity['tenantId'], method, body)

    def accepted(path, method='GET', body=None):
        response = call(path, method=method, body=body)
        if response.get('code') != 200:
            raise RuntimeError(path.split('?')[0] + ' code=' + str(response.get('code')))
        return response.get('data')

    def publish(revision, request):
        query = urllib.parse.urlencode({'sceneId': scene_id, 'revisionNo': revision, 'requestId': request})
        return accepted('/dim3d/scene/publication/publish?' + query, 'POST')

    report = {'passed': False, 'database': DATABASE, 'gatewayPort': 12525, 'marker': marker, 'checks': checks}
    # 基线仅覆盖既有场景主记录字段；核对时按已记录场景编号排除本轮样本。
    before = db("SELECT id,tenant_id,current_revision,deleted FROM wx_3d_scene ORDER BY id;")
    try:
        for role in ('tenant_admin', 'viewer', 'super_admin'):
            identity = next(item for item in identities if item['role'] == role)
            login = api.call(BASE, '/system/auth/login', None, identity['tenantId'], 'POST',
                             {'username': identity['username'], 'password': identity['password']})
            check(role + '真实登录', login.get('code') == 200)
            tokens[role] = (identity, login['data']['accessToken'])
        created = accepted('/dim3d/scene/add', 'POST', {'name': marker, 'sceneType': 'local3d'})
        scene_id = created['sceneId']
        revision = accepted('/dim3d/scene/info/' + scene_id)['currentRevision']
        unauthenticated = api.call(BASE, '/dim3d/scene/publication/current/' + scene_id, None, 2)
        check('未登录查询拒绝', unauthenticated.get('code') == 401)
        query = urllib.parse.urlencode({'sceneId': scene_id, 'revisionNo': 1, 'requestId': marker})
        check('只读角色发布拒绝', call('/dim3d/scene/publication/publish?' + query, 'viewer', 'POST').get('code') != 200)
        check('不存在修订发布拒绝', call('/dim3d/scene/publication/publish?' + urllib.parse.urlencode(
            {'sceneId': scene_id, 'revisionNo': 999, 'requestId': marker + '-missing'}), method='POST').get('code') == 404)
        first = publish(1, marker)
        publication = first['publicationId']
        publications.append(publication)
        check('发布重试返回同一编号', publish(1, marker)['publicationId'] == publication)
        check('当前发布指向固定修订', accepted('/dim3d/scene/publication/current/' + scene_id)['revisionNo'] == 1)
        check('跨租户发布查询拒绝', call('/dim3d/scene/publication/' + publication, 'super_admin').get('code') == 404)
        snapshot = accepted('/dim3d/scene/publication/' + publication + '/snapshot')
        check('依赖快照与固定修订一致', snapshot['dependencySnapshot'] == revision['dependenciesJson'])
        check('只读角色签发票据拒绝', call('/dim3d/scene/publication/' + publication + '/ticket', 'viewer', 'POST').get('code') != 200)
        ticket = accepted('/dim3d/scene/publication/' + publication + '/ticket?expiresInSeconds=300', 'POST')
        tickets.append(ticket['ticketId'])
        # 明文票据只在进程内验证摘要，报告仅记录票据编号。
        import hashlib
        digest = hashlib.sha256(ticket['token'].encode()).hexdigest()
        stored = db("SELECT token_hash,scope,status,(expires_time>CURRENT_TIMESTAMP) FROM wx_3d_scene_publication_ticket WHERE ticket_id='" + ticket['ticketId'] + "';")
        check('票据摘要与有效期落库', stored == [digest + '\tREAD\tACTIVE\t1'])
        accepted('/dim3d/scene/publication/ticket/' + ticket['ticketId'], 'DELETE')
        check('票据撤销状态落库', db("SELECT status FROM wx_3d_scene_publication_ticket WHERE ticket_id='" + ticket['ticketId'] + "';") == ['REVOKED'])
        accepted('/dim3d/scene/publication/' + publication + '/revoke?reason=' + marker, 'POST')
        check('发布撤销后当前查询拒绝', call('/dim3d/scene/publication/current/' + scene_id).get('code') == 404)
        check('发布撤销后票据签发拒绝', call('/dim3d/scene/publication/' + publication + '/ticket', method='POST').get('code') == 404)
        rolled = accepted('/dim3d/scene/publication/' + publication + '/rollback?requestId=' + marker + '-rollback', 'POST')
        publications.append(rolled['publicationId'])
        check('回退生成新发布编号', rolled['publicationId'] != publication)
        actions = db("SELECT DISTINCT action FROM wx_3d_scene_publication_audit WHERE publication_id IN ('" + "','".join(publications) + "') ORDER BY action;")
        check('发布票据审计动作齐全', set(actions) == {'PUBLISH', 'REVOKE', 'ROLLBACK', 'TICKET_ISSUE', 'TICKET_REVOKE'})
        report['passed'] = True
    except Exception as error:
        report['failure'] = str(error)
    finally:
        # 通过业务接口撤销和软删除，保留不可变发布及审计历史供追溯。
        cleanup = []
        for publication in publications:
            response = call('/dim3d/scene/publication/' + publication + '/revoke?reason=' + marker, method='POST')
            cleanup.append(response.get('code') in (200, 409))
        if scene_id:
            cleanup.append(call('/dim3d/scene/remove/' + scene_id, method='DELETE').get('code') == 200)
            after = db('SELECT id,tenant_id,current_revision,deleted FROM wx_3d_scene WHERE id<>' + str(int(scene_id)) + ' ORDER BY id;')
            check('既有场景主记录保全', before == after)
        report.update(sceneId=scene_id, publicationIds=publications, ticketIds=tickets,
                      cleanupPassed=all(cleanup), retainedHistory=True, anonymousConsumptionVerified=False)
        for identity, token in tokens.values():
            api.call(BASE, '/system/auth/logout', token, identity['tenantId'], 'POST')
        report['passed'] = report['passed'] and all(cleanup)
        output = ROOT / 'backups' / (marker + '-result.json')
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
