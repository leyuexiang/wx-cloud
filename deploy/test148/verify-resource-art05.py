"""ART-05只读验收：真实工作台汽车的A/B固定读取及短时地址续签，不输出任何签名或令牌。"""
from pathlib import Path
import importlib.util, json, os, ssl, hashlib, urllib.request, urllib.error, urllib.parse

ROOT = Path('/opt/wx/editor-test148')
RESOURCE = '2115735432249536513'


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / file)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def main():
    os.umask(0o077)
    api = module('api', 'verify-resource-art02.py')
    s3 = module('s3', 'art-s3-test-support.py')
    context = ssl.create_default_context(cafile='/www/server/panel/vhost/cert/jtyf-web-art-20261028-133dd9a1e33e42c9/server.crt')
    identities = json.loads((ROOT / 'test-identities.json').read_text())
    other = json.loads((ROOT / 'art02-identities.json').read_text())[1]
    viewers = [identities[2], other]
    checks = []

    def check(name, ok):
        checks.append({'name': name, 'passed': bool(ok)})
        if not ok:
            raise AssertionError(name)

    def request(path, who=viewers[0]):
        return api.request(12522, '/admin-api/dim3d/' + path, who)

    def get(url):
        # 禁止重定向，不能把签名带向其它入口；保持证书验证。
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, headers, newurl):
                return None
        opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=context), NoRedirect())
        with opener.open(url, timeout=45) as response:
            raw = response.read(32 * 1024 * 1024 + 1)
        if len(raw) > 32 * 1024 * 1024:
            raise RuntimeError('对象超过Web内存限额')
        return raw

    for who in viewers:
        login = api.request(12522, '/admin-api/system/auth/login', who, 'POST', {'username': who['username'], 'password': who['password']})
        check('真实普通用户登录:' + str(who['tenantId']), login.get('code') == 200)
        who['token'] = login['data']['accessToken']

    current = request('resources/' + RESOURCE + '/current')
    check('真实系统汽车当前为B', current.get('code') == 200 and current['data']['currentRevision'] == '2')
    hashes = {}
    for revision in ['1', '2']:
        stored = api.sql("SELECT JSON_UNQUOTE(JSON_EXTRACT(after_snapshot,'$.resource.content_hash')) FROM wx_dim3d_resource_operation WHERE resource_id=" + RESOURCE + " AND committed_revision=" + revision + " AND status='COMMITTED';")
        if len(stored) != 64 or any(c not in '0123456789abcdef' for c in stored):
            raise RuntimeError('缺少真实汽车已提交快照')
        hashes[revision] = stored

    def file(revision, path, who=viewers[0], hash_override=None):
        query = urllib.parse.urlencode({'revision': revision, 'contentHash': hash_override or hashes[revision], 'path': path})
        return request('resources/' + RESOURCE + '/file?' + query, who)

    for revision in ['1', '2']:
        signed = file(revision, 'manifest.json')
        check('固定修订清单签发:' + revision, signed.get('code') == 200)
        manifest = json.loads(get(signed['data']['fileUrl']))
        check('固定内容身份:' + revision, manifest['contentHash'] == hashes[revision] and manifest['resourceId'] == RESOURCE)
        for item in manifest['files']:
            signed = file(revision, item['path'])
            check('闭包文件签发:' + revision + ':' + item['path'], signed.get('code') == 200)
            raw = get(signed['data']['fileUrl'])
            check('真实文件大小和摘要:' + revision + ':' + item['path'], len(raw) == int(item['size']) and hashlib.sha256(raw).hexdigest() == item['sha256'])
        # 重新签发而非复用第一次URL；仍然必须是原来的A或B。
        renewed = file(revision, 'manifest.json')
        check('重新签发固定内容:' + revision, renewed.get('code') == 200 and json.loads(get(renewed['data']['fileUrl']))['contentHash'] == hashes[revision])
        global_read = file(revision, 'manifest.json', viewers[1])
        check('另一租户普通用户可读系统资源:' + revision, global_read.get('code') == 200)
        url_path = urllib.parse.urlparse(signed['data']['fileUrl']).path
        try:
            get(s3.presigned(url_path, expires=1, age=600))
            expired = False
        except urllib.error.HTTPError as error:
            expired = error.code == 403
        check('过期签名被对象存储拒绝:' + revision, expired)

    check('摘要不能与其它修订混用', file('1', 'manifest.json', hash_override=hashes['2']).get('code') != 200)
    check('清单外文件不能签发', file('1', 'private-sidecar.json').get('code') != 200)
    check('路径越界拒绝', file('1', '../manifest.json').get('code') != 200)
    check('缺少固定修订拒绝', request('resources/' + RESOURCE + '/file?path=manifest.json').get('code') != 200)
    check('匿名不能签发', api.request(12522, '/admin-api/dim3d/resources/' + RESOURCE + '/file?' + urllib.parse.urlencode({'revision': '1', 'contentHash': hashes['1'], 'path': 'manifest.json'})).get('code') == 401)
    report = {'passed': True, 'count': len(checks), 'checks': checks, 'resourceId': RESOURCE, 'databaseChanged': False,
              'scope': '真实工作台A/B全部文件经正式网关及受信HTTPS下载；不等于Unity原生加载或浏览器视觉通过'}
    (ROOT / 'verification-art05.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'passed': True, 'count': len(checks)}))


if __name__ == '__main__':
    main()
