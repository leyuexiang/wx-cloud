"""使用真实登录、转换器及私有对象存储验证模型五类接口，结果仅保留断言和清理状态。"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET

from server_support import mysql_command
import subprocess

ROOT = Path('/opt/wx/editor-test148')
RELEASE = Path('/home/wx-model09-release-20261117')
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))
SECURE = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(
    context=ssl.create_default_context(cafile='/home/wx-editor-web148/releases/iot-web-20261111/server.crt')))


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def sql(statement):
    result = subprocess.run(mysql_command() + ['-N', '-B'], input=statement.encode(), capture_output=True)
    if result.returncode:
        raise RuntimeError('数据库验收查询失败')
    return result.stdout.decode().strip()


def verify(target, formal, source_archive=None):
    """正式验收经两个各自网关，候选直接连接回环服务；每次登录和样例均独立回收。"""
    stage = json.loads((RELEASE / 'prepared.json').read_text())['targets'][target]
    database = stage['database'] if formal else stage['clone']
    port = (12522 if target == 'main' else 12525) if formal else stage['candidatePort']
    actors = {item['role']: dict(item) for item in json.loads((ROOT / 'test-identities.json').read_text())}
    result = {'target': target, 'formal': formal, 'port': port, 'database': database,
              'checks': [], 'passed': False, 'cleaned': False}
    resources = []
    directories = []
    operations = []
    prefixes = set()
    marker = 'MODEL09-' + uuid.uuid4().hex[:12]

    def check(name, condition):
        result['checks'].append({'name': name, 'passed': bool(condition)})
        if not condition:
            raise RuntimeError(name)

    def request(path, body=None, method='GET', actor=None, login=False, content_type='application/json'):
        headers = {'Content-Type': content_type, 'ClientId': 'admin-api'}
        if actor:
            headers['tenant-id'] = str(actor['tenantId'])
            if actor.get('token'):
                headers['Authorization'] = 'Bearer ' + actor['token']
        data = body if isinstance(body, bytes) else json.dumps(body).encode() if body is not None else None
        endpoint = 12522 if login else port
        req = urllib.request.Request('http://127.0.0.1:' + str(endpoint) + path,
                                     data=data, headers=headers, method=method)
        try:
            response = OPENER.open(req, timeout=45)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return json.load(response)

    def wait(operation, expected, actor):
        deadline = time.monotonic() + 150
        while time.monotonic() < deadline:
            state = request('/admin-api/dim3d/model-imports/' + operation, actor=actor)
            if state.get('code') != 200:
                raise RuntimeError('导入状态读取失败')
            state = state['data']
            if state['importStatus'] == expected:
                return state
            if state['importStatus'] in ('FAILED', 'CANCELLED', 'CONFLICT', 'EXPIRED'):
                result['failedImport'] = {key: state.get(key) for key in ('importStatus', 'errorNumber', 'errorCode')}
                raise RuntimeError('模型转换未达到预期阶段')
            time.sleep(1)
        raise RuntimeError('模型转换等待超时')

    def upload(actor, belong, archive=None):
        # 首个样例可使用本轮受限源包，其余场景继续复用小型 OBJ，兼顾实样与写入回归。
        if archive is None:
            payload, _ = load('model09_source', 'verify-model-import-source.py').source_payload('obj')
        else:
            payload = archive.read_bytes()
            result['sourceArchive'] = {'bytes': len(payload), 'sha256': hashlib.sha256(payload).hexdigest()}
        boundary = 'model09' + uuid.uuid4().hex
        body = ('--' + boundary + '\r\nContent-Disposition: form-data; name="resourceBelong"\r\n\r\n'
                + belong + '\r\n--' + boundary + '\r\nContent-Disposition: form-data; name="file"; '
                'filename="' + marker + '.zip"\r\nContent-Type: application/zip\r\n\r\n').encode()
        body += payload + ('\r\n--' + boundary + '--\r\n').encode()
        response = request('/admin-api/dim3d/resource/uploadConvert', body, 'POST', actor,
                           content_type='multipart/form-data; boundary=' + boundary)
        check('真实模型上传受理', response.get('code') == 200)
        state = response['data']
        operation, resource = state['operationId'], state['resourceId']
        operations.append((operation, actor))
        resources.append((resource, actor))
        for prefix in ('resources/staging/model-imports/', 'resources/model-imports/'):
            prefixes.add(prefix + operation + '/')
        wait(operation, 'PUBLISHING', actor)
        return operation, resource

    def compact_binding(resource, operation, scope, owner):
        """以数据库可信归属及当前发布尝试核对新布局，不从客户端拼接可信身份。"""
        value = json.loads(sql("SELECT JSON_OBJECT('prefix',r.storage_prefix,'attempt',i.attempt_id,"
            "'scope',r.resource_scope,'owner',r.tenant_id) FROM `" + database
            + "`.wx_3d_resource r JOIN `" + database + "`.wx_3d_resource_import i ON i.resource_id=r.id"
            + " WHERE r.id=" + resource + " AND i.operation_id='" + operation + "';"))
        expected = 'resources/' + scope + '/' + str(owner) + '/' + resource + '/' + value['attempt'] + '/'
        check('正式内容使用可信归属和单层唯一版本目录', value['prefix'] == expected
              and value['scope'] == scope and str(value['owner']) == str(owner))
        return expected

    try:
        for role in ('super_admin', 'tenant_admin', 'viewer'):
            actor = actors[role]
            response = request('/admin-api/system/auth/login', {'username': actor['username'],
                'password': actor['password'], 'captchaVerification': ''}, 'POST', actor, login=True)
            check(role + '真实登录', response.get('code') == 200)
            actor['token'] = response['data']['accessToken']
            actor['userId'] = str(response['data']['userId'])
        actor = actors['tenant_admin']
        check('匿名模型分页拒绝', request('/admin-api/dim3d/model/page').get('code') == 401)
        check('只读角色新增拒绝', request('/admin-api/dim3d/model/add', {}, 'POST', actors['viewer']).get('code') == 403)
        category = sql("SELECT value FROM wx_editor.wx_system_dict_data WHERE dict_type='dim3d_model_category'"
                       " AND status=0 AND deleted=0 ORDER BY sort,id LIMIT 1;")
        check('使用现有启用模型分类', bool(re.fullmatch(r'[A-Za-z0-9_-]{1,32}', category)))
        operation, resource = upload(actor, 'MINE', source_archive)
        body = {'modelName': marker, 'category': category, 'modelDesc': '真实发布接口验收',
                'resourceBelong': 'MINE', 'isPaid': 'PAID', 'price': 1299, 'modelFileId': resource}
        invalid = request('/admin-api/dim3d/model/add', {**body, 'price': 12.99}, 'POST', actor)
        check('小数分金额拒绝', invalid.get('code') == 400)
        response = request('/admin-api/dim3d/model/add', body, 'POST', actor)
        check('文档新增成功且数据为空', response.get('code') == 200 and response.get('data') is None)
        wait(operation, 'COMMITTED', actor)
        directory = sql('SELECT id FROM `' + database + '`.wx_3d_model WHERE resource_id=' + resource + " AND del_flag='0';")
        check('新增实际落库且编号有效', bool(re.fullmatch(r'[1-9][0-9]{0,18}', directory)))
        directories.append((directory, actor))
        check('相同模型表单可重试', request('/admin-api/dim3d/model/add', body, 'POST', actor).get('code') == 200)
        query = urllib.parse.urlencode({'modelName': marker, 'resourceBelong': 'MINE', 'category': category,
            'status': 'ONLINE', 'isPaid': 'PAID', 'pageNum': 1, 'pageSize': 10,
            'orderByColumn': 'priceYuan', 'isAsc': 'asc'})
        page = request('/admin-api/dim3d/model/page?' + query, actor=actor)
        check('根级分页及全部筛选有效', page.get('code') == 200 and page.get('total') == 1
              and len(page.get('rows', [])) == 1 and 'data' not in page)
        detail = request('/admin-api/dim3d/model/info/' + directory, actor=actor)['data']
        check('详情编号及元值符合契约', detail['id'] == directory and detail['modelFileId'] == resource
              and detail['priceYuan'] == '12.99' and 'price' not in detail)
        # 详情及分页严格核对本次参考字段，真实名称通过现有系统用户查询链路回读。
        expected_fields = {'id', 'modelName', 'modelDesc', 'category', 'resourceBelong', 'isPaid',
            'priceYuan', 'coverImageId', 'modelFileId', 'status', 'createBy', 'createByName',
            'createTime', 'updateBy', 'updateByName', 'updateTime'}
        check('详情与分页采用十六字段白名单', set(detail) == expected_fields and set(page['rows'][0]) == expected_fields)
        check('操作编号和时间使用参考字符串格式', detail['createBy'] == actor['userId']
              and detail['updateBy'] == actor['userId'] and bool(re.fullmatch(
                  r'\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}', detail['createTime'] or '')))
        nickname = sql('SELECT nickname FROM wx_editor.wx_system_users WHERE id=' + actor['userId'] + ' AND deleted=0;')
        check('操作人名称来自真实系统账号', bool(nickname)
              and detail['createByName'] == nickname and detail['updateByName'] == nickname)
        preview = request('/admin-api/dim3d/resource/info/' + resource, actor=actor)
        check('文件详情仅含五个业务字段', preview.get('code') == 200 and set(preview['data']) == {
            'id', 'resourceName', 'resourceType', 'assetName', 'resourceRelativePath'})
        relative = preview['data']['resourceRelativePath']
        binding = json.loads(sql("SELECT JSON_OBJECT('prefix',storage_prefix,'revision',current_revision,'hash',content_hash) "
                                 'FROM `' + database + '`.wx_3d_resource WHERE id=' + resource + ';'))
        prefix = compact_binding(resource, operation, 'TENANT_SHARED', actor['tenantId'])
        original_binding = binding.copy()
        check('文件相对路径属于真实冻结前缀', isinstance(relative, str) and relative.startswith(prefix)
              and relative.endswith('.glb') and '://' not in relative and '?' not in relative)
        if source_archive is not None:
            # 本地化入口的对象名由服务端统一，详情回读应指向实际写入的标准文件名。
            check('中文模型正式入口已归一', relative.rsplit('/', 1)[-1] == 'model.glb')
        # 相对路径接口只查询元信息，实际私有下载继续走原修订与摘要约束的受控文件接口。
        signed = request('/admin-api/dim3d/resources/' + resource + '/file?' + urllib.parse.urlencode({
            'revision': str(binding['revision']), 'contentHash': binding['hash'], 'path': relative[len(prefix):]}), actor=actor)
        check('原受控下载接口继续签发有效地址', signed.get('code') == 200
              and bool(signed['data'].get('fileUrl')) and bool(signed['data'].get('downloadExpiresAt')))
        with SECURE.open(signed['data']['fileUrl'], timeout=30) as response:
            model = response.read(8 * 1024 * 1024)
            check('私有预览真实回读为模型', response.status == 200 and model[:4] == b'glTF')
            if source_archive is not None:
                result['sourceArchive']['modelBytes'] = len(model)
                result['sourceArchive']['modelSha256'] = hashlib.sha256(model).hexdigest()
        if not formal:
            # 仅在空副本将本次独占样例投影为旧布局，真实复制对象并走原授权接口，随后恢复新指针。
            s3 = load('model13_legacy_s3', 'art-s3-test-support.py')
            legacy_prefix = 'resources/model-imports/' + operation + '/frozen/' + prefix.rstrip('/').split('/')[-1] + '/'
            status, listing = s3.request('GET', '/wx-art-test148', query=urllib.parse.urlencode({'list-type': '2', 'prefix': prefix}))
            tree = ET.fromstring(listing)
            keys = [node.text for node in tree.findall('{*}Contents/{*}Key')]
            check('旧布局样例来源仅限本次新版本', status == 200 and 0 < len(keys) <= 128
                  and tree.findtext('{*}IsTruncated') == 'false' and all(key.startswith(prefix) for key in keys))
            for key in keys:
                status, content = s3.request('GET', '/wx-art-test148/' + key)
                if status != 200 or s3.request('PUT', '/wx-art-test148/' + legacy_prefix + key[len(prefix):], content)[0] != 200:
                    raise RuntimeError('旧布局样例对象准备失败')
            def set_prefix(value):
                sql("UPDATE `" + database + "`.wx_3d_resource SET storage_prefix='" + value + "' WHERE id=" + resource + ";"
                    "UPDATE `" + database + "`.wx_3d_resource_operation SET after_snapshot=JSON_SET(after_snapshot,"
                    "'$.resource.storage_prefix','" + value + "') WHERE id='" + operation + "' AND status='COMMITTED';")
            try:
                set_prefix(legacy_prefix)
                old = request('/admin-api/dim3d/resource/info/' + resource, actor=actor)
                check('历史冻结路径按原指针回读', old.get('code') == 200
                      and old['data']['resourceRelativePath'] == legacy_prefix + relative[len(prefix):])
                old_file = request('/admin-api/dim3d/resources/' + resource + '/file?' + urllib.parse.urlencode({
                    'revision': str(binding['revision']), 'contentHash': binding['hash'], 'path': relative[len(prefix):]}), actor=actor)
                check('历史布局固定修订通过原授权签发', old_file.get('code') == 200)
                with SECURE.open(old_file['data']['fileUrl'], timeout=30) as response:
                    check('历史布局真实模型仍可下载', response.read(4) == b'glTF')
            finally:
                set_prefix(prefix)
        check('资源详情投影可回读', request('/admin-api/dim3d/resource/info/' + resource, actor=actor).get('code') == 200)
        denied = request('/admin-api/dim3d/model/info/' + directory, actor=actors['viewer'])
        check('同租户其他账号不可读个人模型', denied.get('code') != 200)
        denied = request('/admin-api/dim3d/model/info/' + directory, actor=actors['super_admin'])
        check('其他租户不可读个人模型', denied.get('code') != 200)
        # 先下线再编辑及换文件，验证状态接口与两条写入路径共同保全状态和审计。
        response = request('/admin-api/dim3d/model/OFFLINE/' + directory, method='PUT', actor=actor)
        check('受管模型下线返回空数据', response.get('code') == 200 and response.get('data') is None)
        before = sql('SELECT CONCAT(status,\':\',update_by,\':\',update_time) FROM `' + database + '`.wx_3d_model WHERE id=' + directory + ';')
        response = request('/admin-api/dim3d/model/OFFLINE/' + directory, method='PUT', actor=actor)
        after = sql('SELECT CONCAT(status,\':\',update_by,\':\',update_time) FROM `' + database + '`.wx_3d_model WHERE id=' + directory + ';')
        check('同状态重试保留审计', response.get('code') == 200 and before == after and before.startswith('OFFLINE:'))
        for name, identity in (('只读角色', actors['viewer']), ('其他租户', actors['super_admin'])):
            check(name + '无法修改个人状态', request('/admin-api/dim3d/model/ONLINE/' + directory,
                method='PUT', actor=identity).get('code') != 200)
        check('非法状态拒绝', request('/admin-api/dim3d/model/online/' + directory, method='PUT', actor=actor).get('code') == 400)
        changed = {**body, 'modelName': marker + '-编辑', 'modelDesc': ''}
        response = request('/admin-api/dim3d/model/edit/' + directory, changed, 'PUT', actor)
        check('编辑成功且数据为空', response.get('code') == 200 and response.get('data') is None)
        detail = request('/admin-api/dim3d/model/info/' + directory, actor=actor)['data']
        check('编辑字段及下线状态真实回读', detail['modelName'] == changed['modelName']
              and detail['modelDesc'] == '' and detail['status'] == 'OFFLINE')
        new_operation, new_resource = upload(actor, 'MINE')
        changed['modelFileId'] = new_resource
        response = request('/admin-api/dim3d/model/edit/' + directory, changed, 'PUT', actor)
        replaced = request('/admin-api/dim3d/model/info/' + directory, actor=actor)['data']
        check('新文件编辑保持目录编号及下线状态', response.get('code') == 200
              and replaced['modelFileId'] == new_resource and replaced['status'] == 'OFFLINE')
        wait(new_operation, 'COMMITTED', actor)
        compact_binding(new_resource, new_operation, 'TENANT_SHARED', actor['tenantId'])
        # 目录改绑后原资源及其固定修订仍可读，避免路径简化破坏旧引用。
        pinned = request('/admin-api/dim3d/resources/' + resource + '/file?' + urllib.parse.urlencode({
            'revision': str(original_binding['revision']), 'contentHash': original_binding['hash'],
            'path': relative[len(prefix):]}), actor=actor)
        check('目录换文件后原固定修订仍可签发', pinned.get('code') == 200)
        with SECURE.open(pinned['data']['fileUrl'], timeout=30) as response:
            check('原固定修订模型仍真实可读', response.read(4) == b'glTF')
        response = request('/admin-api/dim3d/model/ONLINE/' + directory, method='PUT', actor=actor)
        check('受管模型重新上线', response.get('code') == 200 and response.get('data') is None
              and request('/admin-api/dim3d/model/info/' + directory, actor=actor)['data']['status'] == 'ONLINE')
        # 仅在独立空副本插入本次资源的引用夹具，验证真实 MySQL 行锁下删除拒绝与回滚。
        if not formal:
            sql("INSERT INTO `" + database + "`.wx_3d_scene_publication_resource(id,publication_id,tenant_id,resource_key,"
                "resource_id,resource_revision,content_hash,manifest_sha256,operation_id,resource_scope,resource_tenant_id,snapshot_json) "
                "VALUES(900000000000000001,'" + marker + "',2,'model'," + new_resource + ",1,'" + '0'*64 + "','"
                + '0'*64 + "','" + new_operation + "','TENANT_SHARED','2','{}');")
            response = request('/admin-api/dim3d/model/remove/' + directory, method='DELETE', actor=actor)
            check('已发布引用阻止删除', response.get('code') != 200 and request(
                '/admin-api/dim3d/model/info/' + directory, actor=actor).get('code') == 200)
            sql("DELETE FROM `" + database + "`.wx_3d_scene_publication_resource WHERE publication_id='" + marker + "';")
        response = request('/admin-api/dim3d/model/remove/' + directory, method='DELETE', actor=actor)
        check('删除成功且数据为空', response.get('code') == 200 and response.get('data') is None)
        check('删除后详情不可读', request('/admin-api/dim3d/model/info/' + directory, actor=actor).get('code') != 200)
        check('已删除模型无法上线', request('/admin-api/dim3d/model/ONLINE/' + directory, method='PUT', actor=actor).get('code') != 200)
        administrator = actors['super_admin']
        operation, resource = upload(administrator, 'SYSTEM')
        legacy = {'operationId': operation, 'resourceId': resource, 'modelName': marker + '-兼容',
                  'category': category, 'isPaid': 'FREE', 'price': 0}
        response = request('/admin-api/dim3d/model/add', legacy, 'POST', administrator)
        check('既有网页保存返回导入回执', response.get('code') == 200
              and isinstance(response.get('data'), dict) and response['data'].get('operationId') == operation)
        wait(operation, 'COMMITTED', administrator)
        compact_binding(resource, operation, 'GLOBAL_SYSTEM', 0)
        legacy_id = sql('SELECT id FROM `' + database + '`.wx_3d_model WHERE resource_id=' + resource + " AND del_flag='0';")
        directories.append((legacy_id, administrator))
        check('系统模型跨租户可读', request('/admin-api/dim3d/model/info/' + legacy_id, actor=actor).get('code') == 200)
        check('租户账号无法上下线系统目录', request('/admin-api/dim3d/model/OFFLINE/' + legacy_id,
            method='PUT', actor=actor).get('code') != 200)
        response = request('/admin-api/dim3d/model/OFFLINE/' + legacy_id, method='PUT', actor=administrator)
        check('平台账号可下线系统目录', response.get('code') == 200 and response.get('data') is None)
        result['passed'] = True
    except Exception as error:
        result['errorType'] = type(error).__name__
    finally:
        # 业务接口回收所有本次目录及独立资源，再按已记录操作前缀逐个删除私有存储对象。
        cleaned = True
        try:
            for directory, actor in directories:
                response = request('/admin-api/dim3d/model/remove/' + directory, method='DELETE', actor=actor)
                cleaned = response.get('code') == 200 and cleaned
            for resource, actor in resources:
                # 只清理本次上传返回的独占资源；先核对可信归属，再确认业务删除或取消已完成。
                saved = json.loads(sql("SELECT JSON_OBJECT('revision',current_revision,'state',content_state,"
                    "'deleted',del_flag,'scope',resource_scope,'owner',tenant_id) FROM `"
                    + database + "`.wx_3d_resource WHERE id=" + resource + ';'))
                scope, owner = saved['scope'], str(saved['owner'])
                if (not re.fullmatch(r'[1-9][0-9]{0,18}', resource)
                        or not ((scope == 'GLOBAL_SYSTEM' and owner == '0')
                                or (scope == 'TENANT_SHARED' and owner == str(actor['tenantId'])))):
                    raise RuntimeError('测试资源归属无法核实')
                if saved['state'] == 'READY' and saved['deleted'] == '0':
                    deletion = str(uuid.uuid4())
                    request('/admin-api/dim3d/resource-operations', {'operationId': deletion,
                        'operationType': 'DELETE', 'resourceId': resource,
                        'expectedRevision': str(saved['revision'])}, 'POST', actor)
                    response = request('/admin-api/dim3d/resource-operations/' + deletion + '/commit', {}, 'POST', actor)
                    if response.get('code') != 200:
                        raise RuntimeError('测试资源业务删除失败')
                for operation, identity in operations:
                    if identity is actor:
                        request('/admin-api/dim3d/resource-operations/' + operation + '/cancel', {}, 'POST', actor)
                deleted = sql('SELECT del_flag FROM `' + database + '`.wx_3d_resource WHERE id=' + resource + ';')
                if deleted != '1':
                    raise RuntimeError('测试资源未完成业务删除')
                prefixes.add('resources/' + scope + '/' + owner + '/' + resource + '/')
            for operation, actor in operations:
                request('/admin-api/dim3d/resource-operations/' + operation + '/cancel', {}, 'POST', actor)
                rows = sql('SELECT storage_prefix FROM `' + database + "`.wx_3d_resource_operation WHERE id='" + operation + "';")
                if rows.startswith('resources/') and operation in rows:
                    prefixes.add(rows)
            s3 = load('model09_s3', 'art-s3-test-support.py')
            for prefix in prefixes:
                status, data = s3.request('GET', '/wx-art-test148', query=urllib.parse.urlencode({'list-type': '2', 'prefix': prefix}))
                tree = ET.fromstring(data)
                keys = [item.text for item in tree.findall('{*}Contents/{*}Key')]
                safe = status == 200 and len(keys) <= 128 and tree.findtext('{*}IsTruncated') == 'false'
                if not safe or not all(key.startswith(prefix) for key in keys):
                    raise RuntimeError('测试对象清理范围无效')
                for key in keys:
                    cleaned = s3.request('DELETE', '/wx-art-test148/' + key)[0] in (200, 204) and cleaned
                # 删除后重新列举，明确证明新目录及旧操作目录均无对象残留。
                status, data = s3.request('GET', '/wx-art-test148', query=urllib.parse.urlencode({'list-type': '2', 'prefix': prefix}))
                remaining = ET.fromstring(data)
                cleaned = status == 200 and not remaining.findall('{*}Contents') and cleaned
            result['cleaned'] = cleaned
        except Exception:
            result['cleanupFailed'] = True
        for actor in actors.values():
            if actor.get('token'):
                try:
                    request('/admin-api/system/auth/logout', {}, 'POST', actor, login=True)
                except Exception:
                    result['logoutFailed'] = True
    output = RELEASE / ('verify-' + ('formal-' if formal else 'candidate-') + target + '.json')
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))
    return 0 if result['passed'] and result['cleaned'] and not result.get('logoutFailed') else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', choices=('main', 'data08'), required=True)
    parser.add_argument('--formal', action='store_true')
    parser.add_argument('--batch', default='model09-20261117')
    parser.add_argument('--source-archive', type=Path, help='以指定源包作为首个上传样例并核对归一后的入口')
    args = parser.parse_args()
    if not re.fullmatch(r'model[0-9]{2}-[0-9]{8}', args.batch):
        parser.error('批次应为模型编号及八位日期')
    prefix, stamp = args.batch.split('-')
    RELEASE = Path('/home/wx-' + prefix + '-release-' + stamp)
    raise SystemExit(verify(args.target, args.formal, args.source_archive))
