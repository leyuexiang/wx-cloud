"""增加仅资源只读API的HTTPS测试入口；认证仍经原网关，检查失败恢复原配置。"""
from pathlib import Path
import datetime, hashlib, json, os, shutil, subprocess

ROOT = Path('/opt/wx/editor-test148')


def main():
    os.umask(0o077)
    if Path.cwd().resolve() != ROOT:
        raise RuntimeError('工作目录错误')
    config = Path('/www/server/panel/vhost/nginx/jtyf-resource-api.conf')
    cert = '/www/server/panel/vhost/cert/jtyf-web-art-20261028-133dd9a1e33e42c9'
    content = r'''# ART-05：只读资源API；对象存储签名读取继续使用9444，不代理任意业务路由。
server {
    listen 192.168.10.148:9446 ssl;
    server_name 192.168.10.148;
    ssl_certificate CERT/server.crt;
    ssl_certificate_key CERT/server.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_tickets off;
    allow 192.168.10.148;
    allow 192.168.10.169;
    deny all;
    access_log off;
    error_log /opt/wx/editor-test148/data/resource-api-error.log crit;
    add_header Referrer-Policy no-referrer always;
    add_header Cache-Control "private, no-store" always;
    add_header Access-Control-Allow-Origin "http://127.0.0.1:8765" always;
    add_header Access-Control-Allow-Methods "GET, HEAD, POST, OPTIONS" always;
    add_header Access-Control-Allow-Headers "Authorization, tenant-id, Content-Type" always;
    add_header Vary Origin always;
    location ~ "^/admin-api/dim3d/resources(?:/[1-9][0-9]{0,18}/(?:current|file))?$" {
        if ($request_method = OPTIONS) { return 204; }
        if ($request_method !~ ^(GET|HEAD)$) { return 405; }
        proxy_pass http://127.0.0.1:12522;
        proxy_set_header Host $http_host;
        proxy_set_header Authorization $http_authorization;
        proxy_set_header tenant-id $http_tenant_id;
        proxy_set_header Cookie "";
        proxy_set_header Connection "";
        proxy_http_version 1.1;
        proxy_connect_timeout 5s;
        proxy_read_timeout 30s;
        proxy_redirect off;
        proxy_hide_header Cache-Control;
        proxy_hide_header Access-Control-Allow-Origin;
    }
    # 仅开放真实登录的精确路由，资源写接口仍不代理。
    location = /admin-api/system/auth/login {
        if ($request_method = OPTIONS) { return 204; }
        if ($request_method != POST) { return 405; }
        client_max_body_size 16k;
        proxy_pass http://127.0.0.1:12522;
        proxy_set_header Host $http_host;
        proxy_set_header tenant-id $http_tenant_id;
        proxy_set_header Cookie "";
        proxy_set_header Authorization "";
        proxy_read_timeout 30s;
        proxy_hide_header Access-Control-Allow-Origin;
        proxy_hide_header Cache-Control;
    }
    location / { return 404; }
}
'''.replace('CERT', cert)
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup = ROOT / 'backups' / ('resource-api-' + stamp)
    backup.mkdir(mode=0o700)
    previous = config.read_bytes() if config.exists() else None
    if previous is not None:
        shutil.copy2(config, backup / config.name)
    try:
        config.write_text(content, encoding='utf-8')
        subprocess.run(['/www/server/nginx/sbin/nginx', '-t'], check=True, capture_output=True)
        subprocess.run(['/www/server/nginx/sbin/nginx', '-s', 'reload'], check=True, capture_output=True)
    except Exception:
        if previous is None:
            config.unlink(missing_ok=True)
        else:
            config.write_bytes(previous)
        raise
    result = {'endpoint': 'https://192.168.10.148:9446', 'readOnly': True, 'gatewayUnchanged': True,
              'queryLogging': False, 'loginRouteEnabled': True, 'sourceIps': ['192.168.10.148', '192.168.10.169'],
              'browserOrigin': 'http://127.0.0.1:8765', 'backup': str(backup), 'sha256': hashlib.sha256(content.encode()).hexdigest()}
    (ROOT / 'resource-api-setup.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
