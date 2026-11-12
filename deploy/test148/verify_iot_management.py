"""通过真实网关验证管理闭环，只创建本次标记的停用样本，结束软删除留审计。"""
import argparse
import json
from pathlib import Path
import sys
import urllib.error
import urllib.request
import uuid

from management_menus import SystemApi, save_json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, choices=(12536, 12525), required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise RuntimeError('不能覆盖验收证据')
    base = 'http://127.0.0.1:' + str(args.port) + '/admin-api'
    identities = json.loads(Path('/opt/wx/editor-test148/test-identities.json').read_text())
    clients = {role: SystemApi(base, next(x for x in identities if x['role'] == role))
               for role in ('tenant_admin', 'viewer')}
    checks = []
    marker = 'iot-management-' + uuid.uuid4().hex[:12]
    report = {'passed': False, 'gatewayPort': args.port, 'marker': marker, 'checks': checks,
              'scope': '真实系统身份、网关、MySQL及服务权限；不代表Broker/Kafka或真实设备验收'}
    connector_id = binding_id = None

    def call(path, method='GET', body=None, role='tenant_admin', tenant=None, anonymous=False):
        api = clients[role]
        headers = {'Content-Type': 'application/json', 'tenant-id': str(tenant or api.tenant)}
        if not anonymous:
            headers['Authorization'] = 'Bearer ' + api.token
        request = urllib.request.Request(base + path, headers=headers, method=method,
                                         data=json.dumps(body).encode() if body is not None else None)
        try:
            response = api.opener.open(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read().decode()
            try:
                return json.loads(raw)
            except ValueError:
                return {'code': response.status}

    def check(name, condition):
        checks.append({'name': name, 'passed': bool(condition)})
        if not condition:
            raise RuntimeError(name)

    def accepted(path, method='GET', body=None):
        result = call(path, method, body)
        if result.get('code') != 200:
            raise RuntimeError('接口失败：' + path.split('?')[0] + ' code=' + str(result.get('code')))
        return result.get('data')

    try:
        info = accepted('/system/auth/get-permission-info')
        check('真实租户管理员具备10项IoT权限', len([p for p in info.get('permissions', []) if p.startswith('iot:')]) == 10)
        check('未登录访问拒绝', call('/iot/connector/page?pageNo=1&pageSize=10', anonymous=True).get('code') in (401, 403))
        check('原查看者查询权限未扩大', call('/iot/connector/page?pageNo=1&pageSize=10', role='viewer').get('code') == 403)
        check('伪造跨租户请求头拒绝', call('/iot/connector/page?pageNo=1&pageSize=10', tenant='987015').get('code') == 403)
        form = {'connectorId': marker, 'name': marker, 'connectorType': 'MOCK', 'configJson': '{}', 'enabled': True}
        connector_id = str(accepted('/iot/connector/create', 'POST', form))
        connector = accepted('/iot/connector/get?id=' + connector_id)
        check('真实创建读取且默认停用', connector['connectorId'] == marker and connector['enabled'] is False)
        page = accepted('/iot/connector/page?pageNo=1&pageSize=1&connectorId=' + marker)
        check('连接器数据库分页及筛选', page['total'] == 1 and len(page['list']) == 1)
        edit = dict(form, id=connector_id, version=connector['version'], name=marker + '-edited')
        accepted('/iot/connector/update', 'PUT', edit)
        check('旧版本更新冲突拒绝', call('/iot/connector/update', 'PUT', edit).get('code') == 1009001011)
        accepted('/iot/connector/enable?id=' + connector_id, 'POST')
        check('启用仅改变期望状态', accepted('/iot/connector/get?id=' + connector_id)['enabled'] is True)
        accepted('/iot/connector/disable?id=' + connector_id, 'POST')
        runtime = accepted('/iot/connector/runtime?id=' + connector_id)
        check('停用运行状态准确', runtime['effectiveStatus'] == 'DISABLED')
        binding = {'connectorId': marker, 'externalDeviceId': marker + '-device', 'mappingRevision': 'v1',
                   # 映射合同要求JSON数字；内层保留原始数字文本，不经Python浮点转换。
                   'pointMappingJson': '{"points":{"temperature":{"valueType":"NUMBER","scale":0.1000000000000000001}}}', 'enabled': True}
        binding_id = str(accepted('/iot/device-binding/create', 'POST', binding))
        saved = accepted('/iot/device-binding/get?id=' + binding_id)
        check('点位映射原始精度保留', '0.1000000000000000001' in saved['pointMappingJson'])
        page = accepted('/iot/device-binding/page?pageNo=1&pageSize=1&connectorId=' + marker)
        check('绑定数据库分页及筛选', page['total'] == 1 and len(page['list']) == 1)
        accepted('/iot/device-binding/update', 'PUT', dict(binding, id=binding_id, pointMappingJson=''))
        check('清空映射保存为有效JSON', json.loads(accepted('/iot/device-binding/get?id=' + binding_id)['pointMappingJson']) == {})
        invalid = dict(binding, id=binding_id, pointMappingJson='[]')
        check('非法点位映射拒绝', call('/iot/device-binding/update', 'PUT', invalid).get('code') != 200)
        check('查看者写权限拒绝', call('/iot/device-binding/update', 'PUT', invalid, role='viewer').get('code') == 403)
        inbox = accepted('/iot/message-inbox/page?pageNo=1&pageSize=10&connectorId=' + marker)
        check('接收记录真实空列表', inbox['total'] == 0 and inbox['list'] == [])
        check('不存在事件重放不伪报成功', call('/iot/message-inbox/replay?eventId=' + marker, 'POST').get('code') != 200)
        # 原网关的系统和三维入口仍可用，不通过只测新服务掩盖相邻路由回归。
        check('既有场景入口仍可读取', call('/dim3d/scene/page?pageNo=1&pageSize=1').get('code') == 200)
    finally:
        try:
            if binding_id:
                accepted('/iot/device-binding/delete?id=' + binding_id, 'DELETE')
                check('本次绑定样本已软删除', accepted('/iot/device-binding/page?pageNo=1&pageSize=1&connectorId=' + marker)['total'] == 0)
            if connector_id:
                accepted('/iot/connector/delete?id=' + connector_id, 'DELETE')
                check('本次连接器样本已软删除', accepted('/iot/connector/page?pageNo=1&pageSize=1&connectorId=' + marker)['total'] == 0)
        finally:
            report['passed'] = len(checks) == 19 and all(x['passed'] for x in checks)
            save_json(args.output, report)
    print(json.dumps({'passed': report['passed'], 'checks': len(checks), 'gatewayPort': args.port}))
    if not report['passed']:
        raise SystemExit(1)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(str(error) if isinstance(error, RuntimeError) else type(error).__name__, file=sys.stderr)
        raise SystemExit(1)
