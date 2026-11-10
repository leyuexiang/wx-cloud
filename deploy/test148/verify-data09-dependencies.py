"""复用 data08 的已提交全局模型验证发布依赖冻结，不改写资源和已有场景。"""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import urllib.parse
import urllib.request
import uuid


ROOT = Path('/opt/wx/editor-test148')
BASE = 'http://127.0.0.1:12525/admin-api'


def main():
    os.umask(0o077)
    assert '/wx_editor_data08?' in (ROOT / 'config/dimensions-data08.yaml').read_text()
    assert '127.0.0.1:48093' in (ROOT / 'config/gateway-data08.yaml').read_text()
    spec = importlib.util.spec_from_file_location('api', Path(__file__).with_name('verify-data08-isolation-service.py'))
    api = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(api)
    identity = next(item for item in json.loads((ROOT / 'test-identities.json').read_text()) if item['role'] == 'tenant_admin')
    token = None
    scene_id = None
    client = uuid.uuid4().hex
    marker = 'DATA09-DEPS-' + uuid.uuid4().hex
    checks, publications, tickets = [], [], []
    report = {'passed': False, 'database': 'wx_editor_data08', 'gatewayPort': 12525, 'marker': marker, 'checks': checks}

    def db(sql):
        result = subprocess.run(['docker', 'exec', '-i', 'mysql8', 'sh', '-c',
            'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names --default-character-set=utf8mb4 wx_editor_data08'],
            input=sql, text=True, capture_output=True, check=True)
        return [line.split('\t') for line in result.stdout.splitlines()]

    def baseline():
        # 只比较已有行，资源和操作以整行 JSON 快照/指针摘要核对，不输出私有存储路径。
        queries = [
            "SELECT CONCAT('scene:',id),SHA2(CONCAT_WS('|',tenant_id,name,current_revision,deleted),256) FROM wx_3d_scene;",
            "SELECT CONCAT('revision:',id),SHA2(CONCAT_WS('|',tenant_id,scene_id,revision_no,schema_version,scene_json,three_d_json,two_d_json,dependencies_json),256) FROM wx_3d_scene_revision;",
            "SELECT CONCAT('content:',id),SHA2(CONCAT_WS('|',tenant_id,schema_version,content_hash,scene_json,three_d_json,two_d_json,dependencies_json,deleted),256) FROM wx_3d_scene_content;",
            "SELECT CONCAT('resource:',id),SHA2(CONCAT_WS('|',resource_scope,tenant_id,current_revision,content_hash,manifest_sha256,storage_prefix,manifest_path,del_flag),256) FROM wx_3d_resource;",
            "SELECT CONCAT('operation:',id),SHA2(CONCAT_WS('|',resource_id,committed_revision,status,after_snapshot),256) FROM wx_3d_resource_operation;"
        ]
        return dict(db('\n'.join(queries)))

    def check(name, condition):
        checks.append({'name': name, 'passed': bool(condition)})
        if not condition:
            raise RuntimeError(name)

    def call(path, method='GET', body=None):
        return api.call(BASE, path, token, identity['tenantId'], method, body)

    def accepted(path, method='GET', body=None):
        response = call(path, method, body)
        if response.get('code') != 200:
            raise RuntimeError(path.split('?')[0] + ' code=' + str(response.get('code')))
        return response['data']

    def publish(revision, request):
        path = '/dim3d/scene/publication/publish?' + urllib.parse.urlencode(
            {'sceneId': scene_id, 'revisionNo': revision, 'requestId': request})
        return call(path, 'POST')

    before = baseline()
    try:
        models = db("SELECT id,current_revision FROM wx_3d_resource WHERE resource_scope='GLOBAL_SYSTEM' AND tenant_id='0' AND resource_type='MODEL' AND content_state='READY' AND del_flag='0' AND current_revision>1 ORDER BY id;")
        # 明确要求当前版和更早的可用已提交版，缺少样本时失败，不将空数据当作通过。
        fixture = None
        for resource, current in models:
            snapshots = db('SELECT committed_revision,HEX(after_snapshot) FROM wx_3d_resource_operation WHERE resource_id=' + str(int(resource)) + " AND status='COMMITTED' ORDER BY committed_revision;")
            usable = {}
            for revision, saved in snapshots:
                snapshot = json.loads(bytes.fromhex(saved).decode())['resource']
                if snapshot.get('content_state') == 'READY' and snapshot.get('del_flag') == '0':
                    usable[int(revision)] = snapshot
            old = [number for number in usable if number < int(current)]
            if old and int(current) in usable:
                fixture = (resource, int(current), min(old), usable)
                break
        check('存在真实当前版与历史版模型样本', fixture is not None)
        resource, current, old, snapshots = fixture
        report.update(resourceId=resource, currentResourceRevision=current, historicalResourceRevision=old)
        login = api.call(BASE, '/system/auth/login', None, identity['tenantId'], 'POST',
                         {'username': identity['username'], 'password': identity['password']})
        check('真实管理员登录', login.get('code') == 200)
        token = login['data']['accessToken']
        scene_id = accepted('/dim3d/scene/add', 'POST', {'name': marker, 'sceneType': 'local3d'})['sceneId']
        original = accepted('/dim3d/scene/info/' + scene_id)['currentRevision']
        lock = accepted('/dim3d/scene/lock/' + scene_id + '?clientKey=' + client, 'POST')
        revision_no = 1

        def save(dependency):
            nonlocal revision_no
            packages = {key: original[key] for key in ('sceneJson', 'threeDJson', 'twoDJson', 'dependenciesJson')}
            dependencies = json.loads(packages['dependenciesJson'])
            dependencies['resources'] = dependency
            packages['dependenciesJson'] = json.dumps(dependencies, ensure_ascii=False, separators=(',', ':'))
            value = accepted('/dim3d/scene/saveAll', 'PUT', {**packages, 'sceneId': scene_id, 'schemaVersion': original['schemaVersion'],
                'lockToken': lock['lockToken'], 'baseRevisionNo': revision_no, 'requestId': uuid.uuid4().hex})
            revision_no = value['revisionNo']
            return revision_no

        dependency = {'resourceKey': 'model', 'assetId': resource, 'kind': 'unity-model', 'owners': []}
        first_revision = save([dependency])
        response = publish(first_revision, marker + '-current')
        check('缺省版本依赖发布成功', response.get('code') == 200)
        first = response['data']['publicationId']
        publications.append(first)
        references = db("SELECT resource_revision,content_hash,manifest_sha256 FROM wx_3d_scene_publication_resource WHERE publication_id='" + first + "';")
        check('引用固定当前已提交版本及双摘要', references == [[str(current), snapshots[current]['content_hash'], snapshots[current]['manifest_sha256']]])
        replay = publish(first_revision, marker + '-current')
        check('发布重放复用编号且不重复引用', replay.get('data', {}).get('publicationId') == first
              and db("SELECT COUNT(*) FROM wx_3d_scene_publication_resource WHERE publication_id='" + first + "';") == [['1']])
        check('新发布使用索引版本二', accepted('/dim3d/scene/publication/' + first + '/snapshot')['indexVersion'] == 2)
        ticket = accepted('/dim3d/scene/publication/' + first + '/ticket', 'POST')
        tickets.append(ticket['ticketId'])
        request = urllib.request.Request(BASE + '/dim3d/scene/publication/preview', method='POST', headers={'X-Scene-Ticket': ticket['token']})
        with urllib.request.urlopen(request, timeout=20) as reply:
            preview = json.loads(reply.read())
            check('索引二仍可凭票据读取原文四包', preview.get('code') == 200 and preview['data']['indexVersion'] == 2
                  and json.loads(preview['data']['dependenciesJson'])['resources'] == [dependency]
                  and reply.headers.get('Cache-Control') == 'no-store')
        historical = {**dependency, 'revision': str(old), 'contentHash': snapshots[old]['content_hash'], 'manifestSha256': snapshots[old]['manifest_sha256']}
        historical_revision = save([historical])
        response = publish(historical_revision, marker + '-old')
        check('显式历史版本发布成功', response.get('code') == 200)
        second = response['data']['publicationId']
        publications.append(second)
        check('历史版本不追随资源当前指针', db("SELECT resource_revision FROM wx_3d_scene_publication_resource WHERE publication_id='" + second + "';") == [[str(old)]] and old != current)
        invalid = [
            ('缺失资源拒绝', [{**dependency, 'assetId': '999999999999999999'}]),
            ('未提交版本拒绝', [{**dependency, 'revision': str(current + 100000)}]),
            ('错误资源摘要拒绝', [{**dependency, 'contentHash': 'f' * 64}]),
            ('重复资源键拒绝', [dependency, dependency]),
            ('未接入发布解析器的类型拒绝', [{**dependency, 'kind': 'image'}])
        ]
        for name, entries in invalid:
            bad_revision = save(entries)
            failed = publish(bad_revision, marker + '-invalid-' + str(bad_revision))
            current_publication = accepted('/dim3d/scene/publication/current/' + scene_id)
            check(name + '且原发布保持不变', failed.get('code') == 409 and current_publication['publicationId'] == second)
        accepted('/dim3d/scene/publication/' + second + '/revoke?reason=' + marker, 'POST')
        restored = accepted('/dim3d/scene/publication/' + second + '/rollback?requestId=' + marker + '-rollback', 'POST')
        publications.append(restored['publicationId'])
        check('回退新发布保留固定历史资源版本', restored['publicationId'] != second and db(
            "SELECT resource_revision FROM wx_3d_scene_publication_resource WHERE publication_id='" + restored['publicationId'] + "';") == [[str(old)]])
        report['passed'] = True
    except Exception as error:
        report['failure'] = str(error) if type(error) is RuntimeError else type(error).__name__
    finally:
        cleanup = []
        if token:
            for ticket_id in tickets:
                try:
                    cleanup.append(call('/dim3d/scene/publication/ticket/' + ticket_id, 'DELETE').get('code') in (200, 404))
                except Exception:
                    cleanup.append(False)
            for publication in publications:
                try:
                    cleanup.append(call('/dim3d/scene/publication/' + publication + '/revoke?reason=' + marker, 'POST').get('code') in (200, 409))
                except Exception:
                    cleanup.append(False)
            if scene_id:
                try:
                    call('/dim3d/scene/unlock/' + scene_id + '?clientKey=' + client, 'DELETE')
                    cleanup.append(call('/dim3d/scene/remove/' + scene_id, 'DELETE').get('code') == 200)
                except Exception:
                    cleanup.append(False)
            try:
                call('/system/auth/logout', 'POST')
            except Exception:
                cleanup.append(False)
        try:
            after = baseline()
            check('既有场景正文和资源提交快照保全', all(after.get(key) == value for key, value in before.items()))
        except Exception:
            report['passed'] = False
        report.update(sceneId=scene_id, publicationIds=publications, ticketIds=tickets, cleanupPassed=bool(scene_id) and all(cleanup),
                      physicalClosureVerified=False, ticketFileSigningVerified=False)
        report['passed'] = report['passed'] and report['cleanupPassed'] and all(item['passed'] for item in checks)
        output = ROOT / 'backups' / (marker + '-result.json')
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
