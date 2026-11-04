"""真实148场景接口验收；凭证只保留内存，报告不记录令牌和锁。"""
from pathlib import Path
import copy, datetime, json, urllib.request, urllib.error, uuid

ROOT = Path('/opt/wx/editor-test148')
BASE = 'http://127.0.0.1:12522/admin-api'
checks = []
actors = {}
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(path, actor=None, method='GET', body=None, tenant=None):
    headers = {'Content-Type': 'application/json', 'tenant-id': str(tenant or (actor or {}).get('tenantId', 2))}
    if actor and actor.get('token'): headers['Authorization'] = 'Bearer ' + actor['token']
    req = urllib.request.Request(BASE + path, method=method, headers=headers, data=json.dumps(body).encode() if body is not None else None)
    try: response = opener.open(req, timeout=25)
    except urllib.error.HTTPError as error: response = error
    return json.loads(response.read())


def check(name, condition):
    checks.append({'name': name, 'passed': bool(condition)})
    if not condition: raise RuntimeError(name)


for identity in json.loads((ROOT / 'test-identities.json').read_text()):
    response = call('/system/auth/login', identity, 'POST', {'username': identity['username'], 'password': identity['password']})
    check(identity['role'] + '登录', response['code'] == 200)
    actors[identity['role']] = {**identity, 'token': response['data']['accessToken']}
admin, tenant, viewer = [actors[key] for key in ['super_admin', 'tenant_admin', 'viewer']]
scene = None
client = 'scene01-acceptance-' + uuid.uuid4().hex
try:
    response = call('/dim3d/scene/add', tenant, 'POST', {'name': '148场景接口验收-' + datetime.datetime.now().strftime('%m%d%H%M'), 'sceneType': 'local3d', 'sceneCategory': 'TEST'})
    check('真实创建场景', response['code'] == 200)
    scene = response['data']['sceneId']
    check('大整数编号保持字符串', isinstance(scene, str) and int(scene) > 2**53)
    info = call('/dim3d/scene/info/' + scene, tenant)
    revision = info['data']['currentRevision']
    keys = ['sceneJson', 'threeDJson', 'twoDJson', 'dependenciesJson']
    check('初始四包完整', info['code'] == 200 and all(isinstance(json.loads(revision[k]), dict) for k in keys))
    check('跨租户读取被拒绝', call('/dim3d/scene/info/' + scene, admin)['code'] == 404)
    check('伪造租户头被拒绝', call('/dim3d/scene/info/' + scene, tenant, tenant=1)['code'] == 403)
    check('查看者不能取得编辑锁', call('/dim3d/scene/lock/' + scene + '?clientKey=viewer', viewer, 'POST')['code'] == 403)
    lock = call('/dim3d/scene/lock/' + scene + '?clientKey=' + client, tenant, 'POST')
    check('取得编辑锁', lock['code'] == 200)
    check('其他页签不能抢锁', call('/dim3d/scene/lock/' + scene + '?clientKey=other', tenant, 'POST')['code'] == 409)
    check('锁心跳续约', call('/dim3d/scene/heartbeat/' + scene, tenant, 'POST', {'lockToken': lock['data']['lockToken']})['code'] == 200)
    packages = {key: revision[key] for key in keys}
    content = json.loads(packages['sceneJson']); content['metadata']['scene01Acceptance'] = 'save-reopen'; packages['sceneJson'] = json.dumps(content, ensure_ascii=False)
    body = {**packages, 'sceneId': scene, 'lockToken': lock['data']['lockToken'], 'requestId': uuid.uuid4().hex, 'baseRevisionNo': 1, 'schemaVersion': 1}
    saved = call('/dim3d/scene/saveAll', tenant, 'PUT', body)
    check('真实保存第二版', saved['code'] == 200 and saved['data']['revisionNo'] == 2)
    replay = call('/dim3d/scene/saveAll', tenant, 'PUT', body)
    check('重试不重复产生修订', replay['data']['revisionId'] == saved['data']['revisionId'])
    stale = {**body, 'requestId': uuid.uuid4().hex}
    conflict = call('/dim3d/scene/saveAll', tenant, 'PUT', stale)
    check('旧基线保存明确冲突', conflict['code'] == 200 and conflict['data']['conflict'] and conflict['data']['conflictRevisionNo'] == 2)
    check('查看者不能保存', call('/dim3d/scene/saveAll', viewer, 'PUT', body)['code'] == 403)
    check('退出释放编辑锁', call('/dim3d/scene/unlock/' + scene + '?clientKey=' + client, tenant, 'DELETE')['code'] == 200)
    reopened = call('/dim3d/scene/info/' + scene, tenant)
    check('重新打开四包字节一致', all(reopened['data']['currentRevision'][key] == packages[key] for key in keys))
    check('新页签可重新编辑', call('/dim3d/scene/lock/' + scene + '?clientKey=' + client, tenant, 'POST')['code'] == 200)
finally:
    if scene: call('/dim3d/scene/unlock/' + scene + '?clientKey=' + client, tenant, 'DELETE')
report = {'passed': all(item['passed'] for item in checks), 'sceneId': scene, 'checks': checks, 'time': datetime.datetime.now().isoformat()}
(ROOT / 'verification-scene01.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False))
