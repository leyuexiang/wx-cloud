"""只验证HTTPS登录及资源读取边界；认证在进程内存中，不记录响应正文。"""
from pathlib import Path
import json, ssl, urllib.request, urllib.error

ROOT = Path('/opt/wx/editor-test148')


def main():
    context = ssl.create_default_context(cafile='/www/server/panel/vhost/cert/jtyf-web-art-20261028-133dd9a1e33e42c9/server.crt')
    who = json.loads((ROOT / 'test-identities.json').read_text())[2]
    checks = []

    def request(path, method='GET', body=None, token=None):
        headers = {'Content-Type': 'application/json', 'tenant-id': str(who['tenantId']), 'Origin': 'http://127.0.0.1:8765'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request('https://192.168.10.148:9446' + path, method=method, headers=headers,
                                     data=json.dumps(body).encode() if body is not None else None)
        try:
            response = urllib.request.urlopen(req, context=context, timeout=20)
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
    status, current, _ = request('/admin-api/dim3d/resources/2115735432249536513/current', token=token)
    check('真实身份读取系统汽车当前描述', status == 200 and current.get('code') == 200)
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
