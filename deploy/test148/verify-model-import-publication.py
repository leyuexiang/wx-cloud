"""在独立 MODEL-04 副本验证真实图片上传、模型发布和同编号替换，并回收本次测试文件。"""

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import ssl
import struct
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zlib

from server_support import mysql_command

ROOT = Path('/opt/wx/editor-test148')
CERTIFICATE = '/home/wx-editor-web148/releases/iot-web-20261111/server.crt'


def sql(statement):
    """数据库凭据沿用受限连接文件，外部失败仅回报类型，避免回显服务器诊断。"""
    result = subprocess.run(mysql_command() + ['-N', '-B'], input=statement, text=True,
                            capture_output=True, timeout=30)
    if result.returncode:
        raise RuntimeError('验收数据库查询失败')
    return result.stdout.strip()


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def png_fixture():
    """构造带正确校验和的单像素 PNG，仅作为上传与字节回读夹具。"""
    def chunk(kind, value):
        return struct.pack('>I', len(value)) + kind + value + struct.pack('>I', zlib.crc32(kind + value))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 1, 1, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(b'\0\x80\x80\x80\xff')) + chunk(b'IEND', b''))


def run(stage_path, role, output):
    stage = json.loads(stage_path.read_text(encoding='utf-8'))
    clone = stage['database']
    target = 'main' if stage['sourceDatabase'] == 'wx_editor' else 'data08'
    port = 49112 if target == 'main' else 49113
    if (not re.fullmatch(r'wx_model02_rehearsal_' + target + r'_[0-9a-f]{8}', clone)
            or stage['port'] != port or not stage.get('publicationEnabled')):
        raise ValueError('需要本批独立目录事务候选')
    inspected = json.loads(subprocess.check_output(['docker', 'inspect', stage['container']], text=True))[0]
    if '--dim3d.model-import.app-compatibility=model04-transaction-check' not in inspected['Config']['Cmd']:
        raise ValueError('需要专用测试兼容标识')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    secure = urllib.request.build_opener(urllib.request.ProxyHandler({}),
        urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=CERTIFICATE)))
    identities = json.loads((ROOT / 'test-identities.json').read_text(encoding='utf-8'))
    actor = dict(next(item for item in identities if item['role'] == role))
    checks = []
    result = {'target': target, 'role': role, 'passed': False, 'checks': checks,
              'runtimeCompatibilityVerified': False, 'artifactSha256': stage['artifactSha256']}
    operations = []
    resource_id = None
    cover_id = None
    cover_copied = False
    filename = 'MODEL04-cover-' + uuid.uuid4().hex + '.png'
    s3 = load_module('model04_s3', ROOT / 'art-s3-test-support.py')

    def check(name, passed):
        checks.append({'name': name, 'passed': bool(passed)})
        if not passed:
            raise RuntimeError(name)

    def request(path, body=None, method='GET', gateway=False, content_type='application/json', identity=None):
        current = actor if identity is None else identity
        headers = {'Content-Type': content_type, 'ClientId': 'admin-api', 'tenant-id': str(current['tenantId'])}
        if current.get('token'):
            headers['Authorization'] = 'Bearer ' + current['token']
        data = body if isinstance(body, bytes) else json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request('http://127.0.0.1:' + str(12522 if gateway else port) + path,
                                     data=data, headers=headers, method=method)
        try:
            response = opener.open(req, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return json.load(response)

    def wait(operation, expected):
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            state = request('/admin-api/dim3d/model-imports/' + operation)['data']
            if state['importStatus'] == expected:
                return state
            if state['importStatus'] in ('FAILED', 'CANCELLED', 'CONFLICT', 'EXPIRED'):
                raise RuntimeError('模型任务未到达预期阶段')
            time.sleep(1)
        raise RuntimeError('模型任务等待超时')

    def directory():
        value = sql("SELECT JSON_OBJECT('id',id,'resourceId',resource_id,'price',price,'isPaid',is_paid,"
                    "'coverImageId',cover_image_id,'modelDesc',model_desc) FROM `" + clone
                    + "`.wx_3d_model WHERE resource_id=" + resource_id + " AND del_flag='0';")
        return json.loads(value)

    try:
        login = request('/admin-api/system/auth/login', {'username': actor['username'], 'password': actor['password'],
                        'captchaVerification': ''}, 'POST', gateway=True)
        check('真实业务登录', login.get('code') == 200)
        actor['token'] = login['data']['accessToken']
        actor_id = str(login['data']['userId'])
        check('可信用户编号有效', bool(re.fullmatch(r'[1-9][0-9]{0,18}', actor_id)))
        image = png_fixture()
        boundary = 'model04' + uuid.uuid4().hex
        multipart = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + filename
                     + '"\r\nContent-Type: image/png\r\n\r\n').encode() + image + ('\r\n--' + boundary + '--\r\n').encode()
        uploaded = request('/admin-api/resource/oss/upload', multipart, 'POST', gateway=True,
                           content_type='multipart/form-data; boundary=' + boundary)
        check('通过现有图片接口上传封面', uploaded.get('code') == 200)
        cover_id = str(uploaded['data']['ossId'])
        check('封面保持字符串编号', bool(re.fullmatch(r'[1-9][0-9]{0,18}', cover_id)))
        url = urllib.parse.urlsplit(uploaded['data']['url'])
        check('封面签名指向现有存储', url.hostname == '192.168.10.148' and url.scheme in ('http', 'https'))
        with secure.open(uploaded['data']['url'], timeout=20) as response:
            check('真实私有封面回读摘要一致', hashlib.sha256(response.read()).digest() == hashlib.sha256(image).digest())
        # 图片服务仍使用正式文件库。仅复制本次刚上传的单条私有元数据到演练副本。
        # 原文件记录和系统配置保持正常接口维护，副本中的归属改变只供事务验收。
        copied = sql("INSERT INTO `" + clone + "`.wx_infra_file SELECT * FROM wx_editor.wx_infra_file WHERE id="
                     + cover_id + " AND uploader_id=" + actor_id + " AND tenant_id=" + str(int(actor['tenantId']))
                     + " AND visibility_scope='PRIVATE_UPLOAD' AND name='" + filename + "' AND deleted=0; SELECT ROW_COUNT();")
        check('只复制本次私有封面记录', copied == '1')
        cover_copied = True
        category = sql("SELECT value FROM wx_editor.wx_system_dict_data WHERE dict_type='dim3d_model_category'"
                       " AND status=0 AND deleted=0 ORDER BY sort,id LIMIT 1;")
        check('存在真实启用模型分类', bool(re.fullmatch(r'[A-Za-z0-9_-]{1,32}', category)))
        source = load_module('model04_source', Path(__file__).with_name('verify-model-import-source.py'))
        payload, _ = source.source_payload('obj')
        original_directory_id = None
        for replacing in (False, True):
            operation = str(uuid.uuid4())
            body = {'operationId': operation, 'operationType': 'REPLACE' if replacing else 'CREATE',
                    'targetFormat': 'glb', 'source': {'size': str(len(payload)), 'sha256': hashlib.sha256(payload).hexdigest()}}
            if replacing:
                body.update(resourceId=resource_id, expectedRevision='1')
            else:
                body.update(resourceScope='GLOBAL_SYSTEM' if role == 'super_admin' else 'TENANT_SHARED',
                            metadata={'resourceName': 'MODEL04-' + operation[:8], 'assetName': 'probe'})
            created = request('/admin-api/dim3d/model-imports', body, 'POST')
            check('创建原操作' if not replacing else '创建同编号替换', created.get('code') == 200)
            operations.append(operation)
            if not replacing:
                resource_id = created['data']['resourceId']
                check('资源编号为有效字符串', isinstance(resource_id, str) and bool(re.fullmatch(r'[1-9][0-9]{0,18}', resource_id)))
                result['resourceId'] = resource_id
            else:
                check('替换保持稳定资源编号', created['data']['resourceId'] == resource_id)
            path = '/admin-api/dim3d/model-imports/' + operation
            target_response = request(path + '/source-target', {}, 'POST')
            check('取得真实原包上传目标', target_response.get('code') == 200)
            with secure.open(urllib.request.Request(target_response['data']['uploadUrl'], data=payload, method='PUT'), timeout=30) as response:
                check('签名上传原始模型', response.status == 200)
            check('持久化受理转换', request(path + '/submit', {}, 'POST').get('code') == 200)
            wait(operation, 'PUBLISHING')
            fields = {'modelName': 'MODEL04-替换' if replacing else 'MODEL04-完整目录', 'category': category,
                      'modelDesc': '' if replacing else 'MODEL04-原始说明', 'coverImageId': None if replacing else cover_id,
                      'isPaid': 'PAID' if role == 'super_admin' and not replacing else 'FREE',
                      'price': 1299 if role == 'super_admin' and not replacing else 0}
            if not replacing:
                invalid = {**fields, 'category': 'MODEL04_INVALID_CATEGORY'}
                check('非法分类不能固定目录', request(path + '/publish', invalid, 'POST').get('code') == 400)
            check('完整目录受理', request(path + '/publish', fields, 'POST').get('code') == 200)
            state = wait(operation, 'COMMITTED')
            check('取得实际修订回执', state['committedRevision'] == ('2' if replacing else '1'))
            check('相同目录确认可重放', request(path + '/publish', fields, 'POST')['data']['committedRevision'] == state['committedRevision'])
            row = directory()
            check('封面及整数分价格共同保存', str(row['coverImageId']) == str(fields['coverImageId'])
                  and row['isPaid'] == fields['isPaid'] and row['price'] == fields['price'] and row['modelDesc'] == fields['modelDesc'])
            if replacing:
                check('替换保持原目录编号', row['id'] == original_directory_id)
            else:
                original_directory_id = row['id']
            detail = request('/admin-api/dim3d/model/info/' + str(row['id']))
            check('既有详情接口回读完整字段', detail.get('code') == 200 and detail['data']['isPaid'] == fields['isPaid']
                  and detail['data']['price'] == fields['price'])
            scope = sql("SELECT visibility_scope FROM `" + clone + "`.wx_infra_file WHERE id=" + cover_id + ';')
            check('封面归属随目录事务提交', scope == ('GLOBAL' if role == 'super_admin' else 'TENANT'))
        result['passed'] = True
    except Exception as error:
        # 网络错误可能携带签名，结果只记录类型和已脱敏的检查名称。
        result['errorType'] = type(error).__name__
    finally:
        prefixes = {'resources/staging/model-imports/' + item + '/' for item in operations}
        for operation in operations:
            prefixes.add('resources/model-imports/' + operation + '/')
        try:
            if resource_id:
                saved = json.loads(sql("SELECT JSON_OBJECT('revision',current_revision,'state',content_state,'deleted',del_flag) FROM `"
                                       + clone + "`.wx_3d_resource WHERE id=" + resource_id + ';'))
                if saved['state'] == 'READY' and saved['deleted'] == '0':
                    deletion = str(uuid.uuid4())
                    accepted = request('/admin-api/dim3d/resource-operations', {'operationId': deletion, 'operationType': 'DELETE',
                        'resourceId': resource_id, 'expectedRevision': str(saved['revision'])}, 'POST')
                    committed = request('/admin-api/dim3d/resource-operations/' + deletion + '/commit', {}, 'POST')
                    result['testResourceDeleted'] = accepted.get('code') == 200 and committed.get('code') == 200
                else:
                    result['testResourceDeleted'] = True
                for operation in operations:
                    request('/admin-api/dim3d/resource-operations/' + operation + '/cancel', {}, 'POST')
                rows = sql("SELECT storage_prefix FROM `" + clone + "`.wx_3d_resource_operation WHERE resource_id=" + resource_id + ';')
                for prefix in rows.splitlines():
                    if prefix.startswith('resources/') and any(item in prefix for item in operations):
                        prefixes.add(prefix)
            if result.get('testResourceDeleted', resource_id is None):
                removed = True
                for prefix in prefixes:
                    query = urllib.parse.urlencode({'list-type': '2', 'prefix': prefix})
                    status, listing = s3.request('GET', '/wx-art-test148', query=query)
                    tree = ET.fromstring(listing)
                    keys = [item.text for item in tree.findall('{*}Contents/{*}Key')]
                    safe = status == 200 and len(keys) <= 128 and tree.findtext('{*}IsTruncated') == 'false' and all(key.startswith(prefix) for key in keys)
                    outcomes = [s3.request('DELETE', '/wx-art-test148/' + key)[0] in (200, 204) for key in keys] if safe else [False]
                    removed = all(outcomes) and removed
                result['testObjectsRemoved'] = removed
        except Exception:
            result['testObjectsRemoved'] = False
        if cover_id:
            try:
                deleted = request('/admin-api/infra/file/delete?id=' + cover_id, method='DELETE', gateway=True)
                if deleted.get('code') == 403:
                    # 租户业务写权限不等于后台文件删除权限，回收时复用现有平台管理员。
                    administrator = dict(next(item for item in identities if item['role'] == 'super_admin'))
                    login = request('/admin-api/system/auth/login', {'username': administrator['username'],
                        'password': administrator['password'], 'captchaVerification': ''}, 'POST', gateway=True, identity=administrator)
                    if login.get('code') == 200:
                        administrator['token'] = login['data']['accessToken']
                        try:
                            deleted = request('/admin-api/infra/file/delete?id=' + cover_id, method='DELETE', gateway=True, identity=administrator)
                        finally:
                            request('/admin-api/system/auth/logout', {}, 'POST', gateway=True, identity=administrator)
                result['coverFileRemoved'] = deleted.get('code') == 200
                if cover_copied:
                    sql("DELETE FROM `" + clone + "`.wx_infra_file WHERE id=" + cover_id + " AND name='" + filename + "';")
            except Exception:
                result['coverFileRemoved'] = False
        if actor.get('token'):
            try:
                result['sessionLoggedOut'] = request('/admin-api/system/auth/logout', {}, 'POST', gateway=True).get('code') == 200
            except Exception:
                result['sessionLoggedOut'] = False
        result['passed'] = result['passed'] and all(result.get(key, False) for key in
            ('testResourceDeleted', 'testObjectsRemoved', 'coverFileRemoved', 'sessionLoggedOut'))
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(result, ensure_ascii=False))
    return result['passed']


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage-report', type=Path, required=True)
    parser.add_argument('--role', choices=('super_admin', 'tenant_admin'), required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if Path.cwd().resolve() != ROOT or ROOT / 'releases' not in args.output.resolve().parents:
        parser.error('仅允许本批受控工作目录及发布证据目录')
    raise SystemExit(0 if run(args.stage_report, args.role, args.output) else 1)
