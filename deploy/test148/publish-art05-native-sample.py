"""提交已通过Unity原生验收的汽车A/B；仅修改指定测试资源，操作号先落盘，可重试，不输出认证和签名。"""
from pathlib import Path
import hashlib, importlib.util, json, os, ssl, urllib.parse, urllib.request, uuid

ROOT = Path('/opt/wx/editor-test148')
SAMPLES = ROOT / 'samples/art05-native-5a4446632134'
RID = '2115735432249536513'
BASELINE = '4581db80dd6d9aa6efb8786ef137ea0a97f67b2653aeb6ca742efa1eb29c8f0f'


def main():
    os.umask(0o077)
    spec = importlib.util.spec_from_file_location('api', ROOT / 'verify-resource-art02.py')
    api = importlib.util.module_from_spec(spec); spec.loader.exec_module(api)
    actor = json.loads((ROOT / 'test-identities.json').read_text())[0]
    login = api.request(12522, '/admin-api/system/auth/login', actor, 'POST', {'username': actor['username'], 'password': actor['password']})
    if login.get('code') != 200:
        raise RuntimeError('真实平台管理员登录失败')
    actor['token'] = login['data']['accessToken']
    checks = []

    def check(name, passed):
        checks.append({'name': name, 'passed': bool(passed)})
        if not passed:
            raise RuntimeError(name)

    def request(path, body=None):
        response = api.request(12522, '/admin-api/dim3d/' + path, actor, 'POST' if body is not None else 'GET', body)
        if response.get('code') != 200:
            raise RuntimeError('资源接口未成功，业务码=' + str(response.get('code')))
        return response['data']

    packages = json.loads((SAMPLES / 'packages.json').read_text())
    candidates = [packages['aPackage'], packages['bPackage']]
    # 在任何提交前检查全部本地副本，不能拿损坏或其它资源的文件替换现有测试样本。
    for label, package in zip(['a', 'b'], candidates):
        folder = SAMPLES / ('package-' + label)
        for file in package['Plan']['files']:
            path = (folder / file['path']).resolve()
            if not path.is_relative_to(folder) or not path.is_file():
                raise RuntimeError('样本文件路径越界或缺失')
            raw = path.read_bytes()
            check('工具样本字节:' + label + ':' + file['path'], len(raw) == file['size'] and hashlib.sha256(raw).hexdigest() == file['sha256'])
        check('工具样本资源身份:' + label, json.loads((folder / 'manifest.json').read_text())['resourceId'] == RID)
    current = request('resources/' + RID + '/current')
    allowed = {BASELINE, *(p['Commit']['contentHash'] for p in candidates)}
    check('只替换指定ART04测试样本', current['resourceId'] == RID and current['resourceName'].startswith('ART04-') and
          current['resourceScope'] == 'GLOBAL_SYSTEM' and current['contentHash'] in allowed)
    journal = ROOT / 'art05-native-sample-journal.json'
    saved = json.loads(journal.read_text()) if journal.exists() else {'resourceId': RID, 'before': {key: current[key] for key in ['currentRevision', 'contentHash']}, 'operations': {}}

    def save():
        pending = journal.with_suffix('.tmp')
        pending.write_text(json.dumps(saved, ensure_ascii=False, indent=2), encoding='utf-8'); pending.replace(journal)

    context = ssl.create_default_context(cafile='/www/server/panel/vhost/cert/jtyf-web-art-20261028-133dd9a1e33e42c9/server.crt')
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl): return None
    opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=context), NoRedirect())
    for label, package in zip(['a', 'b'], candidates):
        if label not in saved['operations']:
            current = request('resources/' + RID + '/current')
            expected_hash = BASELINE if label == 'a' else candidates[0]['Commit']['contentHash']
            check('提交前指针匹配:' + label, current['contentHash'] == expected_hash)
            saved['operations'][label] = {'creation': {'operationId': str(uuid.uuid4()), 'operationType': 'REPLACE', 'resourceId': RID, 'expectedRevision': current['currentRevision']},
                                          'contentHash': package['Commit']['contentHash']}
            save()
        entry = saved['operations'][label]
        check('恢复操作绑定相同内容:' + label, entry['contentHash'] == package['Commit']['contentHash'])
        op = request('resource-operations', entry['creation']); operation = op['operationId']
        if op['status'] != 'COMMITTED':
            targets = request('resource-operations/' + operation + '/upload-targets', package['Plan'])
            for target in targets['targets']:
                url = urllib.parse.urlparse(target['url'])
                if url.scheme != 'https' or url.netloc != '192.168.10.148:9445' or not url.path.startswith('/wx-art-test148/resources/staging/GLOBAL_SYSTEM/0/' + RID + '/'):
                    raise RuntimeError('上传地址越界')
                raw = (SAMPLES / ('package-' + label) / target['path']).read_bytes()
                with opener.open(urllib.request.Request(target['url'], data=raw, method='PUT', headers=target['requiredHeaders']), timeout=60) as response:
                    check('受限HTTPS上传:' + label + ':' + target['path'], response.status == 200)
            op = request('resource-operations/' + operation + '/commit', package['Commit'])
        check('原子替换提交:' + label, op['status'] == 'COMMITTED')
        entry['committedRevision'] = op['committedRevision']; save()
    current = request('resources/' + RID + '/current')
    check('同ID当前为命名空间B', current['contentHash'] == candidates[1]['Commit']['contentHash'] and current['currentRevision'] == saved['operations']['b']['committedRevision'])
    # 即使B已提交，首次请求A的各个文件也须仍为A，不复用替换前已签发的URL。
    for label, package in zip(['a', 'b'], candidates):
        revision = saved['operations'][label]['committedRevision']
        for file in package['Plan']['files']:
            query = urllib.parse.urlencode({'revision': revision, 'contentHash': package['Commit']['contentHash'], 'path': file['path']})
            signed = request('resources/' + RID + '/file?' + query)
            with opener.open(signed['fileUrl'], timeout=45) as response:
                raw = response.read(32 * 1024 * 1024 + 1)
            check('B提交后固定内容下载:' + label + ':' + file['path'], len(raw) == file['size'] and hashlib.sha256(raw).hexdigest() == file['sha256'])
    report = {'passed': True, 'count': len(checks), 'checks': checks, 'resourceId': RID, 'before': saved['before'], 'operations': saved['operations'],
              'currentRevision': current['currentRevision'], 'currentContentHash': current['contentHash'], 'scope': '真实工具命名空间A/B的同ID提交和HTTPS字节验收；浏览器画面尚未验收'}
    (ROOT / 'verification-art05-native-publish.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'passed': True, 'count': len(checks), 'currentRevision': current['currentRevision']}))


if __name__ == '__main__':
    main()
