"""复用真实包创建专用受管样本验证删除保护；不尝试删除任何既有资源。"""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import ssl
import subprocess
import urllib.parse
import urllib.request
import uuid

ROOT = Path('/opt/wx/editor-test148')
BASE = 'http://127.0.0.1:12525/admin-api'
BUCKET = 'wx-art-test148'
SOURCE = '2115735432249536513'


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


def main():
    os.umask(0o077)
    assert '/wx_editor_data08?' in (ROOT / 'config/dimensions-data08.yaml').read_text()
    assert 'bucketName: ' + BUCKET in (ROOT / 'config/dimensions-data08.yaml').read_text()
    api = module('api', ROOT / 'verify-data08-isolation-service.py')
    s3 = module('s3', ROOT / 'art-s3-test-support.py')
    identities = json.loads((ROOT / 'test-identities.json').read_text())
    tokens, checks, operations, publications = {}, [], [], []
    resource = scene_id = None
    client = uuid.uuid4().hex
    marker = 'DATA09-RETENTION-' + uuid.uuid4().hex
    report = {'passed': False, 'database': 'wx_editor_data08', 'gatewayPort': 12525, 'marker': marker, 'checks': checks}

    def db(sql):
        result = subprocess.run(['docker', 'exec', '-i', 'mysql8', 'sh', '-c',
            'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names --default-character-set=utf8mb4 wx_editor_data08'],
            input=sql, text=True, capture_output=True, check=True)
        return [line.split('\t') for line in result.stdout.splitlines()]

    def baseline():
        # 比较既有资源指针和已提交快照；新样本行不覆盖已有编号。
        return dict(db("SELECT CONCAT('resource:',id),SHA2(CONCAT_WS('|',resource_scope,tenant_id,current_revision,content_hash,manifest_sha256,storage_prefix,manifest_path,del_flag),256) FROM wx_3d_resource; SELECT CONCAT('operation:',id),SHA2(CONCAT_WS('|',resource_id,committed_revision,status,after_snapshot),256) FROM wx_3d_resource_operation;"))

    def check(name, condition):
        checks.append({'name': name, 'passed': bool(condition)})
        if not condition:
            raise RuntimeError(name)

    def call(path, method='GET', body=None, role='tenant_admin'):
        identity, token = tokens[role]
        return api.call(BASE, path, token, identity['tenantId'], method, body)

    def accepted(path, method='GET', body=None):
        result = call(path, method, body)
        if result.get('code') != 200:
            raise RuntimeError(path.split('?')[0] + ' code=' + str(result.get('code')))
        return result['data']

    def operation(kind, revision=None, restore=None):
        body = {'operationId': str(uuid.uuid4()), 'operationType': kind}
        if kind == 'CREATE':
            body.update(resourceScope='TENANT_SHARED', metadata={'resourceName': marker, 'resourceType': 'MODEL', 'assetName': manifest['entry']['assetName']})
        else:
            body.update(resourceId=resource, expectedRevision=str(revision))
        if restore:
            body['snapshotOperationId'] = restore
        value = accepted('/dim3d/resource-operations', 'POST', body)
        operations.append(value['operationId'])
        return value

    before = baseline()
    try:
        for role in ('tenant_admin',):
            identity = next(item for item in identities if item['role'] == role)
            login = api.call(BASE, '/system/auth/login', None, identity['tenantId'], 'POST',
                            {'username': identity['username'], 'password': identity['password']})
            check(role + '真实登录', login.get('code') == 200)
            tokens[role] = identity, login['data']['accessToken']
        saved = db("SELECT HEX(after_snapshot) FROM wx_3d_resource_operation WHERE resource_id=" + SOURCE + " AND committed_revision=1 AND status='COMMITTED';")
        snapshot = json.loads(bytes.fromhex(saved[0][0]).decode())['resource']
        prefix = snapshot['storage_prefix']
        status, raw = s3.request('GET', '/' + BUCKET + '/' + prefix + snapshot['manifest_path'])
        check('既有源清单可读取且摘要一致', status == 200 and hashlib.sha256(raw).hexdigest() == snapshot['manifest_sha256'])
        manifest = json.loads(raw)
        check('真实源包处于样本预算内', len(manifest['files']) <= 32 and sum(int(item['size']) for item in manifest['files']) <= 32 * 1024 * 1024)
        created = operation('CREATE')
        resource = created['resourceId']
        manifest['resourceId'] = resource
        payload = {key: value for key, value in manifest.items() if key != 'contentHash'}
        payload['files'] = sorted(payload['files'], key=lambda item: item['path'])
        manifest['contentHash'] = hashlib.sha256(json.dumps(payload, sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()
        manifest_raw = json.dumps(manifest, sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()
        files = {snapshot['manifest_path']: manifest_raw}
        for item in manifest['files']:
            status, content = s3.request('GET', '/' + BUCKET + '/' + prefix + item['path'])
            check('真实源文件字节:' + item['path'], status == 200 and len(content) == int(item['size']) and hashlib.sha256(content).hexdigest() == item['sha256'])
            files[item['path']] = content
        plan = {'files': [{'path': path, 'size': len(content), 'sha256': hashlib.sha256(content).hexdigest()} for path, content in files.items()]}
        targets = accepted('/dim3d/resource-operations/' + created['operationId'] + '/upload-targets', 'POST', plan)
        context = ssl.create_default_context(cafile='/www/server/panel/vhost/cert/jtyf-web-art-20261028-133dd9a1e33e42c9/server.crt')
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, headers, newurl):
                return None
        opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=context), NoRedirect())
        for target in targets['targets']:
            uri = urllib.parse.urlsplit(target['url'])
            if uri.scheme != 'https' or uri.netloc != '192.168.10.148:9445' or not uri.path.startswith('/' + BUCKET + '/resources/staging/TENANT_SHARED/2/' + resource + '/'):
                raise RuntimeError('样本受控上传入口越界')
            with opener.open(urllib.request.Request(target['url'], data=files[target['path']], method='PUT', headers=target['requiredHeaders']), timeout=45) as reply:
                check('受控上传:' + target['path'], reply.status == 200)
        commit = {'manifestPath': snapshot['manifest_path'], 'manifestSha256': hashlib.sha256(manifest_raw).hexdigest(),
                  'contentHash': manifest['contentHash'], 'contentFormat': manifest['contentFormat'], 'platform': manifest['platform'], 'appCompatibility': manifest['appCompatibility']}
        check('专用真实包受控提交成功', accepted('/dim3d/resource-operations/' + created['operationId'] + '/commit', 'POST', commit)['status'] == 'COMMITTED')
        deletion = operation('DELETE', 1)
        check('无发布引用时逻辑删除正常', accepted('/dim3d/resource-operations/' + deletion['operationId'] + '/commit', 'POST', {})['status'] == 'COMMITTED')
        restoration = operation('RESTORE', 2, created['operationId'])
        check('原资源快照可恢复为第三修订', accepted('/dim3d/resource-operations/' + restoration['operationId'] + '/commit', 'POST', {})['committedRevision'] == '3')
        scene_id = accepted('/dim3d/scene/add', 'POST', {'name': marker, 'sceneType': 'local3d'})['sceneId']
        original = accepted('/dim3d/scene/info/' + scene_id)['currentRevision']
        lock = accepted('/dim3d/scene/lock/' + scene_id + '?clientKey=' + client, 'POST')
        packages = {key: original[key] for key in ('sceneJson', 'threeDJson', 'twoDJson', 'dependenciesJson')}
        dependencies = json.loads(packages['dependenciesJson'])
        dependencies['resources'] = [{'resourceKey': 'retained-model', 'assetId': resource, 'kind': 'unity-model', 'owners': []}]
        packages['dependenciesJson'] = json.dumps(dependencies, separators=(',', ':'))
        accepted('/dim3d/scene/saveAll', 'PUT', {**packages, 'sceneId': scene_id, 'schemaVersion': original['schemaVersion'], 'lockToken': lock['lockToken'], 'baseRevisionNo': 1, 'requestId': uuid.uuid4().hex})
        publication = accepted('/dim3d/scene/publication/publish?' + urllib.parse.urlencode({'sceneId': scene_id, 'revisionNo': 2, 'requestId': marker}), 'POST')['publicationId']
        publications.append(publication)
        for name, revoke in (('活动发布', False), ('已撤销发布历史', True)):
            if revoke:
                accepted('/dim3d/scene/publication/' + publication + '/revoke?reason=' + marker, 'POST')
            deletion = operation('DELETE', 3)
            response = call('/dim3d/resource-operations/' + deletion['operationId'] + '/commit', 'POST', {})
            check(name + '引用阻止删除', response.get('code') == 2030001020)
            check(name + '拒绝后指针和失败回执正确', db("SELECT current_revision,del_flag FROM wx_3d_resource WHERE id=" + str(int(resource))) == [['3', '0']]
                  and db("SELECT status,error_code FROM wx_3d_resource_operation WHERE id='" + deletion['operationId'] + "';") == [['FAILED', '2030001020']])
        # 仅尝试删除专用样本前缀中不存在的探针，不以破坏真实对象检验拒绝规则。
        current = db("SELECT storage_prefix FROM wx_3d_resource WHERE id=" + str(int(resource)))[0][0]
        probe = current + '__legacy_delete_probe_' + uuid.uuid4().hex
        check('删除探针对象原本不存在', s3.request('HEAD', '/' + BUCKET + '/' + probe)[0] == 404)
        response = call('/dimensions/minio/file/deleteFile?' + urllib.parse.urlencode({'bucket': BUCKET, 'objectName': probe}))
        report['legacyNamespaceGuardVerified'] = response.get('code') == 2030001021
        report['legacyPermissionRejected'] = response.get('code') == 403
        check('旧通用入口拒绝删除探针', response.get('code') in (2030001021, 403))
        report['passed'] = True
    except Exception as error:
        report['failure'] = str(error) if type(error) is RuntimeError else type(error).__name__
    finally:
        cleanup = []
        if 'tenant_admin' in tokens:
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
            after = baseline()
            check('既有资源指针和提交快照保全', all(after.get(key) == value for key, value in before.items()))
        except Exception:
            report['passed'] = False
        for role in tokens:
            try:
                call('/system/auth/logout', 'POST', role=role)
            except Exception:
                cleanup.append(False)
        report.update(resourceId=resource, sceneId=scene_id, publicationIds=publications, operationIds=operations,
                      sceneCleanupPassed=bool(scene_id) and all(cleanup), retainedProtectedSample=bool(publications))
        report['passed'] = report['passed'] and report['sceneCleanupPassed'] and all(item['passed'] for item in checks)
        output = ROOT / 'backups' / (marker + '-result.json')
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
