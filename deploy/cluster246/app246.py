"""246应用节点：仅管理独立Compose项目和本批受限通信服务，不操作植被项目。"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import tarfile

ROOT = Path('/home/jtyf-editor246')
RELEASE = ROOT / 'releases/iot-20261111'
FORWARDS = {14881: 48081, 14880: 16380, 13316: 3306, 14825: 12525}


def authorized_key(public):
    """节点密钥仅用于指定通道，不能获取交互Shell或执行远程命令。"""
    if '\n' in public.strip() or not re.fullmatch(r'ssh-ed25519 [A-Za-z0-9+/=]+(?: [^\r\n]+)?', public.strip()):
        raise ValueError('节点公钥格式无效')
    options = ['from="192.168.10.246"', 'command="/bin/false"', 'restrict', 'port-forwarding']
    options += ['permitopen="127.0.0.1:%s"' % p for p in FORWARDS.values()]
    options += ['permitlisten="127.0.0.1:48097"']
    return ','.join(options) + ' ' + ' '.join(public.strip().split()[:2]) + ' jtyf-editor246-20261111'


def iot_config(original):
    """配置只消费本机受限通道，数据库限定三表专用账号，安全规则原样保留。"""
    result = copy.deepcopy(original)
    spring = result['spring']
    master = spring['datasource']['dynamic']['datasource']['master']
    if '/wx_editor_data08?' not in master['url']:
        raise ValueError('目标库不是既有隔离库')
    master['url'] = re.sub(r'jdbc:mysql://[^/]+/', 'jdbc:mysql://127.0.0.1:13316/', master['url'])
    master.update(username='iot246_app', password='${IOT_DB_PASSWORD}')
    spring.setdefault('data', {})['redis'] = {'host': '127.0.0.1', 'port': 14880, 'database': 0,
                                            'password': '${WX_REDIS_PASSWORD}', 'repositories': {'enabled': False}}
    spring.setdefault('cloud', {})['discovery'] = {'client': {'simple': {'instances': {
        'system-server': [{'uri': 'http://127.0.0.1:14881'}]}}}}
    result['server'] = {'port': 48096, 'address': '127.0.0.1'}
    result['wx']['iot']['mqtt']['enabled'] = False
    # 不把系统服务的无关文件、媒体及社交凭据带到新节点。
    for key in ('minio', 'ptc', 'dim3d', 'justauth'):
        result.pop(key, None)
    for key in ('mp', 'miniapp'):
        result['wx'].pop(key, None)
    return result


def compose_config(java_image, web_image):
    """同机容器明确放置；host网络仅服务回环/指定IP，不发布数据库、缓存或Docker套接字。"""
    common = {'network_mode': 'host', 'restart': 'unless-stopped', 'read_only': True,
              'security_opt': ['no-new-privileges:true'], 'cap_drop': ['ALL'], 'pull_policy': 'never',
              'logging': {'driver': 'json-file', 'options': {'max-size': '10m', 'max-file': '3'}}}
    return {'services': {
        'iot': {**common, 'image': java_image, 'mem_limit': '768m', 'cpus': 1, 'pids_limit': 256,
                'env_file': [{'path': str(ROOT / 'secrets/runtime.env'), 'format': 'raw'}],
                'entrypoint': ['java', '-Xms64m', '-Xmx384m', '-Dfile.encoding=UTF-8', '-jar', '/opt/wx/app.jar',
                               '--spring.config.location=file:/opt/wx/application.yaml'],
                'volumes': [str(RELEASE / 'iot.jar') + ':/opt/wx/app.jar:ro,Z',
                            str(RELEASE / 'application.yaml') + ':/opt/wx/application.yaml:ro,Z',
                            str(ROOT / 'logs/iot') + ':/app/logs:rw,Z'],
                'tmpfs': ['/tmp:rw,size=64m,uid=100,gid=101'],
                'healthcheck': {'test': ['CMD', 'wget', '-q', '-O', '-', 'http://127.0.0.1:48096/actuator/health'],
                                'interval': '10s', 'timeout': '5s', 'retries': 8, 'start_period': '40s'}},
        'web': {**common, 'image': web_image, 'user': '101:101', 'mem_limit': '192m', 'cpus': 0.5, 'pids_limit': 128,
                'depends_on': {'iot': {'condition': 'service_healthy'}},
                'entrypoint': ['nginx', '-g', 'daemon off;'],
                'volumes': [str(RELEASE / 'site') + ':/srv/site:ro,Z',
                            str(RELEASE / 'nginx.conf') + ':/etc/nginx/nginx.conf:ro,Z',
                            str(ROOT / 'secrets/tls') + ':/etc/nginx/tls:ro,Z'],
                'tmpfs': ['/tmp:rw,size=32m,uid=101,gid=101'],
                'healthcheck': {'test': ['CMD', 'wget', '-q', '-O', '-', 'http://127.0.0.1:19447/healthz'],
                                'interval': '10s', 'timeout': '3s', 'retries': 5}}
    }}


def nginx_config(public=False):
    """前端沿用同一固定包；IoT直达本机，其余API走加密通道中的148网关。"""
    listen = '192.168.10.246:9447' if public else '127.0.0.1:9448'
    prefix = '''pid /tmp/nginx.pid;
error_log /dev/stderr warn;
worker_processes 2;
events { worker_connections 1024; }
http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;
    access_log off;
    client_body_temp_path /tmp/client-body;
    proxy_temp_path /tmp/proxy;
    fastcgi_temp_path /tmp/fastcgi;
    uwsgi_temp_path /tmp/uwsgi;
    scgi_temp_path /tmp/scgi;
    server { listen 127.0.0.1:19447; location = /healthz { return 200 "healthy"; } location / { return 404; } }
    server {
        listen LISTEN ssl;
        server_name 192.168.10.246;
        ssl_certificate /etc/nginx/tls/server.crt;
        ssl_certificate_key /etc/nginx/tls/server.key;
        ssl_protocols TLSv1.2 TLSv1.3;
        allow 127.0.0.1;
        allow 192.168.10.0/24;
        deny all;
        root /srv/site;
        add_header Cache-Control no-store always;
        add_header Cross-Origin-Opener-Policy same-origin always;
        add_header Cross-Origin-Embedder-Policy credentialless always;
        add_header Referrer-Policy no-referrer always;
        add_header X-Content-Type-Options nosniff always;
        location = / { return 302 /dts/; }
        location = /dts { return 302 /dts/; }
        location /dts/ { try_files $uri $uri/ /dts/index.html; }
        location ^~ /dts/assets/ {
            try_files $uri =404;
            add_header Cache-Control "public, max-age=31536000, immutable" always;
            add_header Cross-Origin-Opener-Policy same-origin always;
            add_header Cross-Origin-Embedder-Policy credentialless always;
            add_header Referrer-Policy no-referrer always;
            add_header X-Content-Type-Options nosniff always;
        }
'''.replace('LISTEN', listen)
    def proxy(pattern, upstream):
        return '''        location ~ "PATTERN" {
            client_max_body_size 24m;
            rewrite ^/dts-api(/.*)$ $1 break;
            proxy_pass http://127.0.0.1:PORT;
            proxy_set_header Authorization $http_authorization;
            proxy_set_header tenant-id $http_tenant_id;
            proxy_set_header x-bot-user "";
            proxy_set_header Cookie "";
            proxy_hide_header Access-Control-Allow-Origin;
            proxy_hide_header Cache-Control;
            proxy_read_timeout 30s;
        }
'''.replace('PATTERN', pattern).replace('PORT', str(upstream))
    return prefix + proxy('^/dts-api/admin-api/iot/(?:connector|device-binding|message-inbox)/[A-Za-z0-9_-]+$', 48096) + proxy(
        '^/dts-api/admin-api/(?:system/(?:auth/(?:login|logout|get-permission-info|refresh-token)|dict-data/simple-list|dict/data/type/[A-Za-z0-9_-]{1,100})|dim3d/(?:model|icon|template|scene|resources|resource|model-imports)(?:/[A-Za-z0-9_./-]+)?|resource/oss(?:/[A-Za-z0-9_./-]+)?)$', 14825) + '''        location /dts-api/ { return 404; }
        location / { return 404; }
    }
}
'''


def run(*args, data=None):
    """不将命令的敏感输出回显；调用方只输出脱敏阶段结果。"""
    result = subprocess.run(args, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError('节点命令失败：' + Path(args[0]).name)
    return result.stdout


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def install_link_unit(ssh_args, replacing=False):
    """使用系统服务标准入口标签，避免让init_t直接执行ssh_exec_t；不关闭SELinux。"""
    wrapper = Path('/usr/local/bin/jtyf-editor148-link')
    target = Path('/etc/systemd/system/jtyf-editor148-link.service')
    if not replacing and (wrapper.exists() or target.exists()):
        raise RuntimeError('节点通信服务已存在，须核对归属')
    wrapper.write_text('#!/bin/sh\nexec ' + ' '.join(ssh_args) + '\n')
    wrapper.chmod(0o755)
    run('restorecon', str(wrapper))
    unit = '[Unit]\nDescription=JTYF restricted 148 service link\nAfter=network-online.target\nWants=network-online.target\n[Service]\nType=simple\nExecStart=' + str(wrapper) + '\nRestart=always\nRestartSec=5\nNoNewPrivileges=true\nProtectSystem=strict\nProtectHome=read-only\n[Install]\nWantedBy=multi-user.target\n'
    target.write_text(unit); target.chmod(0o644)
    run('restorecon', str(target))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('init', 'prepare', 'start', 'publish', 'stop'))
    args = parser.parse_args()
    os.umask(0o077)
    if Path(__file__).resolve().parent != RELEASE:
        raise RuntimeError('246发布目录不匹配')
    secret = ROOT / 'secrets'; secret.mkdir(exist_ok=True, mode=0o700)
    link = secret / 'link'; link.mkdir(exist_ok=True, mode=0o700)
    key = link / 'id_ed25519'
    if args.action == 'init':
        if not key.exists():
            run('ssh-keygen', '-t', 'ed25519', '-N', '', '-f', str(key), '-C', 'jtyf-editor246-20261111')
        print(json.dumps({'publicKey': key.with_suffix('.pub').read_text().strip()}))
        return
    report_path = RELEASE / 'deployment.json'
    if args.action == 'prepare':
        if report_path.exists():
            raise RuntimeError('246已准备，不能覆盖原记录')
        payload = json.load(sys.stdin)
        for name, expected in [('iot.jar', 'bcfc854427d668dee2d63fb25ad50750c301caf190a4b06e9d997fcc352a2b9a'),
                               ('site.tar.gz', '939ebfe33d166e37b435c8a98d60902d8830ae7a0943d2c71e7e3df66af81567')]:
            digest = hashlib.sha256()
            with (RELEASE / name).open('rb') as stream:
                for block in iter(lambda: stream.read(1048576), b''): digest.update(block)
            if digest.hexdigest() != expected: raise RuntimeError('候选制品摘要不匹配')
        config = iot_config(payload['iotConfig'])
        save(RELEASE / 'application.yaml', config); (RELEASE / 'application.yaml').chmod(0o644)
        runtime = {'IOT_DB_PASSWORD': payload['dbPassword'], 'WX_REDIS_PASSWORD': payload['redisPassword'],
                   'WX_RUNTIME_SECRET': secrets.token_urlsafe(32)}
        (secret / 'runtime.env').write_text('\n'.join(k + '=' + v for k, v in runtime.items()) + '\n')
        (link / 'known_hosts').write_text('192.168.10.148 ' + payload['hostPublicKey'].strip() + '\n')
        tls = secret / 'tls'; tls.mkdir(mode=0o750); tls.chmod(0o750); os.chown(tls, 0, 101)
        run('openssl', 'req', '-new', '-x509', '-newkey', 'rsa:3072', '-nodes', '-days', '90',
            '-keyout', str(tls / 'server.key'), '-out', str(tls / 'server.crt'),
            '-subj', '/CN=192.168.10.246/O=JTYF Editor Application', '-addext', 'subjectAltName=IP:192.168.10.246')
        for file in tls.iterdir(): file.chmod(0o640); os.chown(file, 0, 101)
        (tls / 'server.crt').chmod(0o644)
        site = RELEASE / 'site'; site.mkdir(mode=0o755)
        with tarfile.open(RELEASE / 'site.tar.gz') as bundle:
            for m in bundle.getmembers():
                (site / m.name).resolve().relative_to(site.resolve())
                if not m.isfile() or not m.name.startswith('dts/'):
                    raise RuntimeError('静态包包含非预期项')
            bundle.extractall(site, filter='data')
        for p in [site, *site.rglob('*')]: p.chmod(0o755 if p.is_dir() else 0o644)
        logs = ROOT / 'logs/iot'; logs.mkdir(parents=True, exist_ok=True); os.chown(logs, 100, 101)
        (RELEASE / 'nginx.conf').write_text(nginx_config(), encoding='utf-8'); (RELEASE / 'nginx.conf').chmod(0o644)
        compose = compose_config(payload['javaImage'], payload['webImage']); save(RELEASE / 'compose.json', compose)
        forwards = []
        for local, remote in FORWARDS.items(): forwards += ['-L', '127.0.0.1:%s:127.0.0.1:%s' % (local, remote)]
        ssh_args = ['/usr/bin/ssh', '-NT', '-i', str(key), '-o', 'IdentitiesOnly=yes', '-o', 'BatchMode=yes',
                    '-o', 'ExitOnForwardFailure=yes', '-o', 'StrictHostKeyChecking=yes',
                    '-o', 'UserKnownHostsFile=' + str(link / 'known_hosts'), '-o', 'ServerAliveInterval=15',
                    '-o', 'ServerAliveCountMax=3', *forwards, '-R', '127.0.0.1:48097:127.0.0.1:48096', 'root@192.168.10.148']
        install_link_unit(ssh_args)
        save(report_path, {'status': 'PREPARED', 'javaImage': payload['javaImage'], 'webImage': payload['webImage'],
                           'backendHost': '192.168.10.148', 'mqttEnabled': False, 'composeProject': 'jtyf-editor246',
                           'vegetationBaseline': run('docker', 'ps', '--format', '{{.Names}} {{.ID}}').decode().splitlines()})
    else:
        report = json.loads(report_path.read_text())
        compose_args = ['docker', 'compose', '-p', 'jtyf-editor246', '-f', str(RELEASE / 'compose.json')]
        if args.action == 'start':
            run('systemctl', 'daemon-reload'); run('systemctl', 'enable', '--now', 'jtyf-editor148-link.service')
            run(*compose_args, 'config', '--quiet')
            run(*compose_args, 'up', '-d', '--wait', '--wait-timeout', '180')
            report['status'] = 'CANDIDATE_HEALTHY'
        elif args.action == 'publish':
            if report['status'] != 'CANDIDATE_HEALTHY' or not json.loads((RELEASE / 'candidate-browser.json').read_text()).get('passed'):
                raise RuntimeError('候选网页未验收')
            (RELEASE / 'nginx.conf').write_text(nginx_config(True))
            run(*compose_args, 'up', '-d', '--no-deps', '--force-recreate', '--wait', 'web')
            rule = 'rule family="ipv4" source address="192.168.10.0/24" port port="9447" protocol="tcp" accept'
            run('firewall-cmd', '--add-rich-rule=' + rule)
            run('firewall-cmd', '--permanent', '--add-rich-rule=' + rule)
            report.update(status='PUBLISHED', endpoint='https://192.168.10.246:9447/dts/')
        else:
            run(*compose_args, 'stop'); run('systemctl', 'stop', 'jtyf-editor148-link.service')
            report['status'] = 'STOPPED'
        save(report_path, report)
    print(json.dumps(json.loads(report_path.read_text()), ensure_ascii=False))


if __name__ == '__main__':
    try: main()
    except Exception as error:
        print('246节点操作未完成：' + (str(error) if isinstance(error, (RuntimeError, ValueError)) else type(error).__name__), file=sys.stderr)
        raise SystemExit(1)
