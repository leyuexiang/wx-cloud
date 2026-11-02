"""只验证HTTPS登录及资源读取边界；认证在进程内存中，不记录响应正文。"""
from pathlib import Path
import json, ssl, urllib.request, urllib.error, os

ROOT = Path('/opt/wx/editor-test148')


def main():
    # 真实连接使用本次服务器证书的签发 CA，保持证书及主机校验开启。
    context = ssl.create_default_context(cafile=os.environ.get('WX_TLS_CA_FILE', '/etc/wx/tls/ca.crt'))
    # 内网链路不经过宿主的公网代理，避免把代理连接失败误报为服务器故障。
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=context))
    who = json.loads((ROOT / 'test-identities.json').read_text())[2]
    checks = []

    def request(path, method='GET', body=None, token=None):
        headers = {'Content-Type': 'application/json', 'tenant-id': str(who['tenantId']), 'Origin': 'http://127.0.0.1:8765'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request('https://192.168.10.148:9446' + path, method=method, headers=headers,
                                     data=json.dumps(body).encode() if body is not None else None)
        try:
            response = opener.open(req, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        raw = response.read(65537)
        try:
            data = json.loads(raw)
        except Exception:
            data = {}
        return response.code, data, response.headers.get('Access-Control-Allow-Origin')

    def check(name, passed):
        checks.append({'name': name, 'passed': bool(passed)})
        if not passed:
            raise RuntimeError(name)

    status, login, origin = request('/admin-api/system/auth/login', 'POST', {'username': who['username'], 'password': who['password']})
    check('HTTPS真实普通用户登录及跨域', status == 200 and login.get('code') == 200 and origin == 'http://127.0.0.1:8765')
    token = login['data']['accessToken']
    # 全新库没有历史汽车样本；读取真实资源列表验证身份链路，允许合法的空列表。
    status, current, _ = request('/admin-api/dim3d/resources', token=token)
    check('HTTPS真实身份读取资源列表', status == 200 and current.get('code') == 200)
    status, data, _ = request('/admin-api/dim3d/resources')
    check('匿名资源读取仍拒绝', status == 401 or data.get('code') == 401)
    check('资源写入仍拒绝', request('/admin-api/dim3d/resources', 'POST', {}, token)[0] == 405)
    check('登录GET拒绝', request('/admin-api/system/auth/login')[0] == 405)
    check('登录浏览器预检', request('/admin-api/system/auth/login', 'OPTIONS')[0] == 204)
    check('其它业务路由仍拒绝', request('/admin-api/system/user/page', token=token)[0] == 404)
    result = {'passed': True, 'count': len(checks), 'checks': checks, 'tlsValidation': True,
              'scope': 'HTTPS真实身份和入口边界；不等于浏览器WASM或视觉验收'}
    (ROOT / 'verification-resource-api-login.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'passed': True, 'count': len(checks)}))


if __name__ == '__main__':
    main()
