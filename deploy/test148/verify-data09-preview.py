"""经 148 隔离网关验证匿名固定四包消费，凭据仅在内存使用。"""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


ROOT = Path('/opt/wx/editor-test148')
BASE = 'http://127.0.0.1:12525/admin-api'
PREVIEW = '/dim3d/scene/publication/preview'
KEYS = ('sceneJson', 'threeDJson', 'twoDJson', 'dependenciesJson')


def main():
    os.umask(0o077)
    assert '/wx_editor_data08?' in (ROOT / 'config/dimensions-data08.yaml').read_text()
    assert '127.0.0.1:48093' in (ROOT / 'config/gateway-data08.yaml').read_text()
    spec = importlib.util.spec_from_file_location('api', Path(__file__).with_name('verify-data08-isolation-service.py'))
    api = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(api)
    identities = json.loads((ROOT / 'test-identities.json').read_text())
    identity = next(item for item in identities if item['role'] == 'tenant_admin')
    marker = 'DATA09-PREVIEW-' + uuid.uuid4().hex
    token = None
    scene_id = None
    client = uuid.uuid4().hex
    publications = []
    tickets = []
    checks = []
    report = {'passed': False, 'database': 'wx_editor_data08', 'gatewayPort': 12525, 'marker': marker, 'checks': checks}

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

    def preview(ticket=None, headers=None, suffix='', method='POST', path=PREVIEW):
        request_headers = dict(headers or {})
        if ticket:
            request_headers['X-Scene-Ticket'] = ticket
        request = urllib.request.Request(BASE + path + suffix, headers=request_headers, method=method)
        try:
            response = urllib.request.urlopen(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return json.loads(response.read()), response.headers.get('Cache-Control', '')

    def issue(publication, seconds=300):
        value = accepted('/dim3d/scene/publication/' + publication + '/ticket?expiresInSeconds=' + str(seconds), 'POST')
        tickets.append(value['ticketId'])
        return value

    def publish(revision):
        value = accepted('/dim3d/scene/publication/publish?' + urllib.parse.urlencode(
            {'sceneId': scene_id, 'revisionNo': revision, 'requestId': marker + '-' + str(revision)}), 'POST')
        publications.append(value['publicationId'])
        return value['publicationId']

    def baseline():
        # 仅比较联验前已存在行；正文用数据库摘要比较，不将真实正文归档。
        sql = """
SELECT CONCAT('scene:',id),SHA2(CONCAT_WS('|',tenant_id,name,current_revision,deleted),256) FROM wx_3d_scene;
SELECT CONCAT('revision:',id),SHA2(CONCAT_WS('|',tenant_id,scene_id,revision_no,schema_version,scene_json,three_d_json,two_d_json,dependencies_json),256) FROM wx_3d_scene_revision;
SELECT CONCAT('content:',id),SHA2(CONCAT_WS('|',tenant_id,schema_version,content_hash,scene_json,three_d_json,two_d_json,dependencies_json,deleted),256) FROM wx_3d_scene_content;
"""
        result = subprocess.run(['docker', 'exec', '-i', 'mysql8', 'sh', '-c',
            'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names wx_editor_data08'],
            input=sql, text=True, capture_output=True, check=True)
        return dict(line.split('\t', 1) for line in result.stdout.splitlines())

    before = baseline()
    try:
        login = api.call(BASE, '/system/auth/login', None, identity['tenantId'], 'POST',
                         {'username': identity['username'], 'password': identity['password']})
        check('真实管理员登录', login.get('code') == 200)
        token = login['data']['accessToken']
        scene_id = accepted('/dim3d/scene/add', 'POST', {'name': marker, 'sceneType': 'local3d'})['sceneId']
        original = accepted('/dim3d/scene/info/' + scene_id)['currentRevision']
        publication = publish(1)
        expiry_started = time.monotonic()
        expiring = issue(publication, 60)
        ticket = issue(publication)
        other = issue(publication)
        missing, cache = preview()
        check('匿名无票据拒绝且禁止缓存', missing.get('code') == 401 and cache == 'no-store')
        check('未知票据拒绝', preview('invalid-ticket')[0].get('code') == 401)
        # 地址参数仅使用无效占位值，真实票据始终不进入 URL。
        query = urllib.parse.urlencode({'ticket': 'query-ticket-placeholder'})
        check('查询参数票据不授予权限', preview(suffix='?' + query)[0].get('code') == 401)
        consumed, cache = preview(ticket['token'])
        check('无登录无租户头读取固定四包', consumed.get('code') == 200 and cache == 'no-store'
              and all(consumed['data'].get(key) == original[key] for key in KEYS))
        check('响应不泄露管理及票据信息', set(consumed['data']) == set(KEYS) | {
            'publicationId', 'sceneId', 'revisionNo', 'sourceHash', 'indexVersion', 'schemaVersion'})
        # 匿名调用携带伪造租户和发布参数，也只能消费票据绑定的固定身份。
        overridden = preview(ticket['token'], {'tenant-id': '1'}, '?publicationId=PUB-other&sceneId=999')[0]
        check('伪造租户及发布编号不覆盖绑定', overridden.get('data') == consumed['data'])
        denied = api.call(BASE, '/dim3d/scene/publication/current/' + scene_id, None, identity['tenantId'])
        check('相邻管理接口仍拒绝匿名', denied.get('code') == 401)
        check('读取票据不授予后台查询权限', preview(ticket['token'], {'tenant-id': str(identity['tenantId'])},
              method='GET', path='/dim3d/scene/publication/current/' + scene_id)[0].get('code') == 401)
        lock = accepted('/dim3d/scene/lock/' + scene_id + '?clientKey=' + client, 'POST')
        packages = {key: original[key] for key in KEYS}
        scene = json.loads(packages['sceneJson'])
        scene.setdefault('metadata', {})['data09Preview'] = marker
        packages['sceneJson'] = json.dumps(scene, ensure_ascii=False, separators=(',', ':'))
        saved = accepted('/dim3d/scene/saveAll', 'PUT', {**packages, 'sceneId': scene_id,
            'lockToken': lock['lockToken'], 'requestId': uuid.uuid4().hex, 'baseRevisionNo': 1,
            'schemaVersion': original['schemaVersion']})
        check('真实草稿保存至第二修订', saved['revisionNo'] == 2)
        check('草稿改变后票据四包不变', preview(ticket['token'])[0].get('data') == consumed['data'])
        publish(2)
        check('当前发布切换后旧票据仍固定首版', preview(ticket['token'])[0].get('data') == consumed['data'])
        accepted('/dim3d/scene/publication/ticket/' + ticket['ticketId'], 'DELETE')
        check('撤销票据后消费拒绝', preview(ticket['token'])[0].get('code') == 401)
        check('撤销单票不影响其他有效票据', preview(other['token'])[0].get('code') == 200)
        # 采用真实有效期到期，不通过数据库改写过期时间模拟。
        remaining = max(0, 62 - (time.monotonic() - expiry_started))
        deadline = time.monotonic() + remaining
        while time.monotonic() < deadline:
            time.sleep(min(5, max(0, deadline - time.monotonic())))
        report['expiryWaitSeconds'] = round(remaining, 2)
        check('真实六十秒有效期届满拒绝', preview(expiring['token'])[0].get('code') == 401)
        accepted('/dim3d/scene/publication/' + publication + '/revoke?reason=' + marker, 'POST')
        check('历史发布撤销使活动票据失效', preview(other['token'])[0].get('code') == 401)
        report['passed'] = True
    except Exception as error:
        # 只记录固定检查名或异常类型，避免异常对象携带签名或凭据。
        report['failure'] = str(error) if type(error) is RuntimeError else type(error).__name__
    finally:
        cleanup = []
        if token:
            for ticket_id in tickets:
                try:
                    cleanup.append(call('/dim3d/scene/publication/ticket/' + ticket_id, 'DELETE').get('code') in (200, 404))
                except Exception:
                    cleanup.append(False)
            for publication_id in publications:
                try:
                    cleanup.append(call('/dim3d/scene/publication/' + publication_id + '/revoke?reason=' + marker, 'POST').get('code') in (200, 409))
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
            check('既有场景修订及共享正文保全', all(after.get(key) == value for key, value in before.items()))
        except Exception:
            report['passed'] = False
        report.update(sceneId=scene_id, publicationIds=publications, ticketIds=tickets,
            cleanupPassed=bool(scene_id) and all(cleanup), dependencyClosureVerified=False)
        report['passed'] = report['passed'] and report['cleanupPassed'] and all(item['passed'] for item in checks)
        output = ROOT / 'backups' / (marker + '-result.json')
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
