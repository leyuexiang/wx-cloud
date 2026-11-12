"""9447管理端静态发布：不可变目录、精确同源代理、隔离验收及保留续期证书的回退。"""
import argparse
import base64
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile

from management_menus import save_json, read_json
from server_support import file_sha256

ROOT = Path('/opt/wx/editor-web148')
# 现场系统盘容量不足，静态制品及本批备份使用已有大容量数据盘，原站点仍留在原位置。
CONTENT_ROOT = Path('/home/wx-editor-web148')
RELEASE = CONTENT_ROOT / 'releases/iot-web-20261111'
TARGET = Path('/www/server/panel/vhost/nginx/jtyf-editor-web148.conf')
CHECK = TARGET.with_name('jtyf-iot-web-check.conf')
NGINX = '/www/server/nginx/sbin/nginx'
ANCHOR = '    location = / { return 302 /threeWeb/; }'


def rollback_config(original, certificate):
    """保留原私钥，只替换9447证书引用；回退页面时不降回即将到期证书。"""
    certificate = Path(certificate).as_posix()
    if not certificate.startswith(RELEASE.as_posix() + '/') or re.search(r'[\s;{}]', certificate):
        raise ValueError('证书路径不在本批发布目录')
    result, count = re.subn(r'(?m)^(\s*ssl_certificate )[^;]+;', lambda m: m[1] + str(certificate) + ';', original)
    if count != 1:
        raise ValueError('9447证书配置不唯一')
    return result


def render_config(original, site_root, certificate):
    """新入口代理只覆盖管理页面及既有资源/场景，不修改旧站点接口去向。"""
    site_root = Path(site_root).as_posix()
    if (site_root != (RELEASE / 'site').as_posix() or ANCHOR not in original
            or '# IOT-WEB-20261111' in original or 'location /dts/' in original):
        raise ValueError('发布目录或站点基线不匹配')
    result = rollback_config(original, certificate)
    snippet = r'''    # IOT-WEB-20261111：新增管理入口；旧threeWeb和旧API继续使用原后端。
    location = /dts { return 302 /dts/; }
    location /dts/ {
        root SITE;
        try_files $uri $uri/ /dts/index.html;
    }
    location ^~ /dts/assets/ {
        root SITE;
        try_files $uri =404;
        add_header Cache-Control "public, max-age=31536000, immutable" always;
        add_header Cross-Origin-Opener-Policy same-origin always;
        add_header Cross-Origin-Embedder-Policy credentialless always;
        add_header Referrer-Policy no-referrer always;
        add_header X-Content-Type-Options nosniff always;
    }
    # URI只剥离部署前缀，可信身份与租户仍由12525网关和业务服务逐次校验。
    location ~ "^/dts-api/admin-api/(?:system/(?:auth/(?:login|logout|get-permission-info|refresh-token)|dict-data/simple-list|dict/data/type/[A-Za-z0-9_-]{1,100})|iot/(?:connector|device-binding|message-inbox)/[A-Za-z0-9_-]+|dim3d/(?:model|icon|template|scene|resources|resource|model-imports)(?:/[A-Za-z0-9_./-]+)?|resource/oss(?:/[A-Za-z0-9_./-]+)?)$" {
        client_max_body_size 24m;
        rewrite ^/dts-api(/.*)$ $1 break;
        proxy_pass http://127.0.0.1:12525;
        proxy_set_header Authorization $http_authorization;
        proxy_set_header tenant-id $http_tenant_id;
        proxy_set_header x-bot-user "";
        proxy_set_header Cookie "";
        proxy_hide_header Access-Control-Allow-Origin;
        proxy_hide_header Cache-Control;
        proxy_read_timeout 30s;
    }
    location /dts-api/ { return 404; }

'''.replace('SITE', str(site_root))
    return result.replace(ANCHOR, snippet + ANCHOR, 1)


def command(*args, data=None):
    """标准输出只返回调用方，不打印环境文件、私钥或原始请求。"""
    result = subprocess.run(args, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError('发布命令未通过：' + Path(args[0]).name)
    return result.stdout


def activate(path, content):
    """配置原子替换后校验再热重载；失败恢复原文件并重新加载，不重启后端服务。"""
    previous = path.read_bytes() if path.exists() else None
    pending = path.with_suffix('.pending')
    pending.write_bytes(content)
    pending.chmod(0o644)
    pending.replace(path)
    try:
        command(NGINX, '-t')
        command(NGINX, '-s', 'reload')
    except Exception:
        if previous is None:
            path.unlink()
        else:
            path.write_bytes(previous)
        command(NGINX, '-t')
        command(NGINX, '-s', 'reload')
        raise


def prepare():
    """先验证完整包摘要及路径，再解包；私有备份不与公开静态目录混放。"""
    state_path = RELEASE / 'deployment.json'
    if state_path.exists() or CHECK.exists():
        raise RuntimeError('发布或隔离记录已存在，不能重跑')
    manifest = read_json(RELEASE / 'build-manifest.json')
    archive = RELEASE / 'site.tar.gz'
    if file_sha256(archive) != manifest['archiveSha256']:
        raise RuntimeError('静态包摘要不匹配')
    site = RELEASE / 'site'
    site.mkdir(mode=0o755)
    with tarfile.open(archive) as bundle:
        members = bundle.getmembers()
        if sum(m.size for m in members) > 1024 * 1024 * 1024:
            raise RuntimeError('解包体积超过边界')
        for member in members:
            (site / member.name).resolve().relative_to(site.resolve())
            if not (member.isfile() or member.isdir()) or not member.name.startswith('dts/'):
                raise RuntimeError('静态包包含非预期路径或链接')
        if hasattr(tarfile, 'data_filter'):
            bundle.extractall(site, filter='data')
        else:
            # 老解释器没有过滤器；此前已逐项拒绝链接和越界路径。
            bundle.extractall(site)
    for directory in [site, *[p for p in site.rglob('*') if p.is_dir()]]:
        directory.chmod(0o755)
    actual = {}
    for file in site.rglob('*'):
        if file.is_file():
            file.chmod(0o644)
            actual[str(file.relative_to(site))] = file_sha256(file)
    if actual != manifest['files']:
        raise RuntimeError('静态目录与构建清单不一致')
    original = TARGET.read_text(encoding='utf-8')
    if 'listen 192.168.10.148:9447 ssl;' not in original:
        raise RuntimeError('9447站点基线已变化')
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup = CONTENT_ROOT / 'backups' / ('iot-web-' + stamp)
    backup.mkdir(mode=0o700, parents=True)
    shutil.copy2(TARGET, backup / TARGET.name)
    key = re.search(r'(?m)^\s*ssl_certificate_key ([^;]+);', original).group(1)
    cert = RELEASE / 'server.crt'
    # 复用现场私钥，续发仅供9447使用的90天证书；私钥不复制、不进入静态包或日志。
    command('openssl', 'req', '-new', '-x509', '-key', key, '-out', str(cert), '-days', '90',
            '-subj', '/CN=192.168.10.148/O=JTYF Editor Management',
            '-addext', 'subjectAltName=IP:192.168.10.148')
    cert.chmod(0o644)
    public_key = command('openssl', 'x509', '-in', str(cert), '-pubkey', '-noout')
    der = command('openssl', 'pkey', '-pubin', '-outform', 'DER', data=public_key)
    candidate = render_config(original, site, cert)
    rollback = rollback_config(original, cert)
    (RELEASE / 'candidate.conf').write_text(candidate, encoding='utf-8')
    (RELEASE / 'rollback.conf').write_text(rollback, encoding='utf-8')
    # 隔离vhost仅绑定回环，移除全局map，防止与原站点重复定义变量。
    isolated = candidate[candidate.index('\nserver {') + 1:]
    isolated = isolated.replace('listen 192.168.10.148:9447 ssl;', 'listen 127.0.0.1:9448 ssl;')
    isolated = isolated.replace('    allow 192.168.10.0/24;', '    allow 127.0.0.1;\n    allow 192.168.10.0/24;')
    state = {'status': 'PREPARED', 'backup': str(backup), 'previousConfigSha256': file_sha256(TARGET),
             'candidateConfigSha256': hashlib.sha256(candidate.encode()).hexdigest(),
             'rollbackConfigSha256': hashlib.sha256(rollback.encode()).hexdigest(),
             'archiveSha256': manifest['archiveSha256'], 'certificateSha256': file_sha256(cert),
             'certificateSpkiSha256Base64': base64.b64encode(hashlib.sha256(der).digest()).decode(),
             'certificateInfo': command('openssl', 'x509', '-in', str(cert), '-noout', '-dates').decode(),
             'privateKeyChanged': False, 'databaseChanged': False, 'oldThreeWebIndexSha256':
             file_sha256(ROOT / 'site/threeWeb/index.html'), 'apiTarget': '127.0.0.1:12525'}
    save_json(state_path, state)
    activate(CHECK, isolated.encode())
    state['status'] = 'ISOLATED'
    save_json(state_path, state)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'apply', 'rollback'))
    args = parser.parse_args()
    if Path(__file__).resolve().parent != RELEASE:
        raise RuntimeError('发布目录不匹配')
    os.umask(0o077)
    if args.action == 'prepare':
        prepare()
    else:
        state_path = RELEASE / 'deployment.json'
        state = read_json(state_path)
        if args.action == 'apply':
            verification = read_json(RELEASE / 'isolation-verification.json')
            if state['status'] != 'ISOLATED' or not verification.get('passed') or file_sha256(TARGET) != state['previousConfigSha256']:
                raise RuntimeError('隔离验收或现场摘要不满足切换条件')
            activate(TARGET, (RELEASE / 'candidate.conf').read_bytes())
            state.update(status='DEPLOYED', endpoint='https://192.168.10.148:9447/dts/')
        else:
            if state['status'] != 'DEPLOYED' or file_sha256(TARGET) != state['candidateConfigSha256']:
                raise RuntimeError('现场有后续变化，不能覆盖回退')
            rollback = RELEASE / 'rollback.conf'
            if file_sha256(rollback) != state['rollbackConfigSha256']:
                raise RuntimeError('回退配置摘要不匹配')
            activate(TARGET, rollback.read_bytes())
            state.update(status='ROLLED_BACK', renewedCertificatePreserved=True)
        if CHECK.exists():
            CHECK.unlink()
            command(NGINX, '-t')
            command(NGINX, '-s', 'reload')
        if file_sha256(ROOT / 'site/threeWeb/index.html') != state['oldThreeWebIndexSha256']:
            raise RuntimeError('原站点静态文件发生变化，须核对')
        save_json(state_path, state)
    print(json.dumps(read_json(RELEASE / 'deployment.json'), ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('网页发布未完成：' + str(error) if isinstance(error, (ValueError, RuntimeError)) else type(error).__name__)
        raise SystemExit(1)
