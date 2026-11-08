"""通过隔离系统服务验证共享正文的模板另存、实例化及独立编辑。"""
from argparse import Namespace
import importlib.util
import json
from pathlib import Path
import uuid


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


API = load('data08_api', 'verify-data08-isolation-service.py')
VERIFY = load('data08_db', 'verify-data08-db.py')
ROUNDTRIP = load('data08_roundtrip', 'verify-data08-roundtrip.py')
SYSTEM = 'http://127.0.0.1:48181/admin-api'
SCENE = 'http://127.0.0.1:48093/admin-api'
ARGS = Namespace(host='127.0.0.1', port=3306, user='root', database='wx_editor_data08',
                 password_env=None, mysql_container='mysql8')
KEYS = ('sceneJson', 'threeDJson', 'twoDJson', 'dependenciesJson')


def main():
    # 两个服务必须同时指向已授权隔离库，防止分类初始化落到共享系统服务。
    root = Path('/opt/wx/editor-test148')
    for filename in ('system-data08.yaml', 'dimensions-data08.yaml'):
        if '/wx_editor_data08?' not in (root / 'config' / filename).read_text():
            raise RuntimeError('系统服务和三维服务必须连接 DATA-08 隔离库')
    identities = json.loads((root / 'test-identities.json').read_text())
    identity = next(item for item in identities if item['username'] == 'wxadmin')
    login = API.call(SYSTEM, '/system/auth/login', None, 1, 'POST',
                     {'username': identity['username'], 'password': identity['password']})
    if login.get('code') != 200:
        raise RuntimeError('隔离运营账号登录失败')
    token = login['data']['accessToken']
    client = 'data08-template-' + uuid.uuid4().hex
    scene_ids = []
    template_id = None
    dict_id = None
    type_id = None
    report = {'passed': False, 'checks': []}

    def call(base, path, method='GET', body=None):
        response = API.call(base, path, token, 1, method, body)
        if response.get('code') != 200:
            raise RuntimeError(path.split('?')[0]+' 返回业务码 '+str(response.get('code')))
        return response.get('data')

    def check(name, passed):
        if not passed:
            raise RuntimeError(name)
        report['checks'].append(name)

    try:
        types = call(SYSTEM, '/system/dict-type/simple-list')
        if not any(item.get('type') == 'dim3d_template_industry' for item in types):
            type_id = call(SYSTEM, '/system/dict-type/create', 'POST', {
                'name': 'DATA08模板隔离验收', 'type': 'dim3d_template_industry', 'status': 0,
            })
        category = 'data08_' + uuid.uuid4().hex[:8]
        dict_id = call(SYSTEM, '/system/dict-data/create', 'POST', {
            'dictType': 'dim3d_template_industry', 'label': 'DATA08隔离分类',
            'value': category, 'sort': 0, 'status': 0,
        })
        created = call(SCENE, '/dim3d/scene/add', 'POST', {
            'name': 'DATA08模板正文复用', 'sceneType': 'local3d',
        })
        source = created['sceneId']
        scene_ids.append(source)
        revision = call(SCENE, '/dim3d/scene/info/' + source)['currentRevision']
        packages = {key: revision[key] for key in KEYS}
        tree = json.loads(packages['threeDJson'])
        tree['components'] = [{'instanceId': 'root', 'objectType': 'group', 'sortNo': 0}]
        tree['components'].extend({
            'instanceId': 'child' + str(index), 'objectType': 'primitive',
            'parentInstanceId': 'root', 'sortNo': index,
            'transform': {'kind': 'local', 'position': [index, 0, 0],
                          'rotation': [0, 0, 0], 'scale': [1, 1, 1]},
        } for index in range(256))
        packages['threeDJson'] = json.dumps(tree, separators=(',', ':'))
        lock = call(SCENE, '/dim3d/scene/lock/' + source + '?clientKey=' + client, 'POST')
        call(SCENE, '/dim3d/scene/saveAll', 'PUT', {
            **packages, 'sceneId': source, 'schemaVersion': 1, 'baseRevisionNo': 1,
            'requestId': uuid.uuid4().hex, 'lockToken': lock['lockToken'],
        })
        template = call(SCENE, '/dim3d/scene/saveAsTemplateFromSnapshot', 'POST', {
            **packages, 'sourceSceneId': source, 'sourceRevisionNo': 2,
            'requestId': uuid.uuid4().hex, 'name': 'DATA08模板正文复用',
            'templateType': 'INDUSTRY', 'category': category, 'saveScope': 'all', 'schemaVersion': 1,
        })
        template_id = template['templateId']
        template_scene = template['templateSceneId']
        template_revision = call(SCENE, '/dim3d/scene/template/info/' + template_id)['currentRevision']
        check('模板完整四包回读', all(template_revision[key] == packages[key] for key in KEYS))
        references = VERIFY.mysql(ARGS,
            'SELECT content_id FROM wx_3d_scene_revision WHERE tenant_id=1 AND '
            f'((scene_id={int(source)} AND revision_no=2) OR (scene_id={int(template_scene)} AND revision_no=1))')
        check('模板与来源相同正文复用', len(references) == 2 and len({row[0] for row in references}) == 1)
        instance = call(SCENE, '/dim3d/scene/fromTemplate', 'POST', {
            'templateSceneId': template_scene, 'name': 'DATA08模板独立实例', 'requestId': uuid.uuid4().hex,
        })
        instance_id = instance['sceneId']
        scene_ids.append(instance_id)
        instance_revision = call(SCENE, '/dim3d/scene/info/' + instance_id)['currentRevision']
        copied_tree = json.loads(instance_revision['threeDJson'])
        copied = copied_tree['components']
        ids = {item['instanceId'] for item in copied}
        check('257节点编号与父子引用重建', len(ids) == 257 and 'root' not in ids and all(
            item['parentInstanceId'] in ids for item in copied if item.get('parentInstanceId')))
        copied_tree['components'][1]['transform']['position'][0] = 99
        edited = {key: instance_revision[key] for key in KEYS}
        edited['threeDJson'] = json.dumps(copied_tree, separators=(',', ':'))
        instance_lock = call(SCENE, '/dim3d/scene/lock/' + instance_id + '?clientKey=' + client, 'POST')
        call(SCENE, '/dim3d/scene/saveAll', 'PUT', {
            **edited, 'sceneId': instance_id, 'schemaVersion': 1, 'baseRevisionNo': 1,
            'requestId': uuid.uuid4().hex, 'lockToken': instance_lock['lockToken'],
        })
        reopened = call(SCENE, '/dim3d/scene/info/' + instance_id)['currentRevision']
        check('实例第二版独立保存', reopened['threeDJson'] == edited['threeDJson'])
        after = call(SCENE, '/dim3d/scene/template/info/' + template_id)['currentRevision']
        check('实例编辑后模板正文保持一致', all(after[key] == template_revision[key] for key in KEYS))
        report.update({
            'sourceSceneId': source, 'templateId': template_id, 'templateSceneId': template_scene,
            'instanceSceneId': instance_id, 'sharedContentId': references[0][0],
            'roundtrip': ROUNDTRIP.check(ARGS),
        })
        check('模板实例联验后全量回读', report['roundtrip']['passed'])
        report['passed'] = True
    finally:
        cleanup = []
        for scene_id in reversed(scene_ids):
            call(SCENE, '/dim3d/scene/unlock/' + scene_id + '?clientKey=' + client, 'DELETE')
            call(SCENE, '/dim3d/scene/remove/' + scene_id, 'DELETE')
            cleanup.append(scene_id)
        if template_id:
            call(SCENE, '/dim3d/scene/template/remove/' + template_id, 'DELETE')
        if dict_id:
            call(SYSTEM, '/system/dict-data/delete?id=' + str(dict_id), 'DELETE')
        if type_id:
            call(SYSTEM, '/system/dict-type/delete?id=' + str(type_id), 'DELETE')
        call(SYSTEM, '/system/auth/logout', 'POST')
        report['cleanup'] = {'softDeletedScenes': cleanup, 'templateSoftDeleted': template_id is not None,
                             'categoryRemoved': dict_id is not None}
        output = root / 'backups/data08-template-result.json'
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        output.chmod(0o600)
        print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
