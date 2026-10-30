"""只开放新资源桶暂存前缀的HTTPS PUT；不能写冻结内容或代理其它桶。"""
from pathlib import Path
import datetime,subprocess,os,shutil,json
ROOT=Path("/opt/wx/editor-test148")
def main():
    if Path.cwd().resolve()!=ROOT:raise RuntimeError("工作目录错误")
    os.umask(0o077)
    cert="/www/server/panel/vhost/cert/jtyf-web-art-20261028-133dd9a1e33e42c9"
    target=Path("/www/server/panel/vhost/nginx/jtyf-resource-upload.conf")
    backup=ROOT/"backups"/("upload-"+datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"));backup.mkdir(mode=0o700)
    if target.exists():shutil.copy2(target,backup/target.name)
    target.write_text("""server {
    listen 192.168.10.148:9445 ssl;
    server_name 192.168.10.148;
    ssl_certificate CERT/server.crt;
    ssl_certificate_key CERT/server.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    access_log off;
    error_log /opt/wx/editor-test148/data/resource-upload-error.log crit;
    client_max_body_size 2048m;
    add_header Access-Control-Allow-Origin "http://127.0.0.1:8765" always;
    add_header Access-Control-Allow-Methods "PUT, OPTIONS" always;
    add_header Access-Control-Allow-Headers "Content-Type" always;
    add_header Vary Origin always;
    add_header Cache-Control "private, no-store" always;
    location /wx-art-test148/resources/staging/ {
        if ($request_method = OPTIONS) { return 204; }
        limit_except PUT OPTIONS { deny all; }
        proxy_pass http://127.0.0.1:9000;
        proxy_set_header Host $http_host;
        proxy_set_header Connection "";
        proxy_http_version 1.1;
        proxy_request_buffering off;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
    location / { return 404; }
}
""".replace("CERT",cert),encoding="utf-8")
    try:
        subprocess.run(["/www/server/nginx/sbin/nginx","-t"],check=True,capture_output=True)
        subprocess.run(["/www/server/nginx/sbin/nginx","-s","reload"],check=True,capture_output=True)
    except Exception:
        if (backup/target.name).exists():shutil.copy2(backup/target.name,target)
        else:target.unlink()
        raise
    report={"endpoint":"https://192.168.10.148:9445","stagingOnly":True,"putOnly":True,"signedQueryLogging":False,"backup":str(backup)}
    (ROOT/"upload-setup.json").write_text(json.dumps(report,indent=2));print(json.dumps(report))
if __name__=="__main__":main()
