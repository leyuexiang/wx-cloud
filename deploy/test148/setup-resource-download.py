"""建立新桶专用HTTPS读入口，不改旧样本代理；配置先检查再重载。"""
from pathlib import Path
import importlib.util,subprocess,json,datetime,shutil,os
ROOT=Path("/opt/wx/editor-test148")
def main():
    os.umask(0o077)
    if Path.cwd().resolve()!=ROOT:raise RuntimeError("工作目录错误")
    spec=importlib.util.spec_from_file_location("s3",ROOT/"art-s3-test-support.py");s3=importlib.util.module_from_spec(spec);spec.loader.exec_module(s3)
    status,_=s3.request("HEAD","/wx-art-test148")
    if status==404:
        status,_=s3.request("PUT","/wx-art-test148")
        if status!=200:raise RuntimeError("新桶创建失败")
    elif status!=200:raise RuntimeError("新桶检查失败")
    status,data=s3.request("GET","/wx-art-test148",query="policy=")
    if status==200:raise RuntimeError("发现既有桶策略，先审查，不自动覆盖或扩大权限")
    if status!=404 or b"NoSuchBucketPolicy" not in data:raise RuntimeError("无法确认新桶私有策略")
    cert="/www/server/panel/vhost/cert/jtyf-web-art-20261028-133dd9a1e33e42c9"
    config=Path("/www/server/panel/vhost/nginx/jtyf-resource-download.conf")
    content="""# ART-02：只代理新私有资源桶的签名GET，保留Host以通过S3签名校验。
server {
    listen 192.168.10.148:9444 ssl;
    server_name 192.168.10.148;
    ssl_certificate CERT/server.crt;
    ssl_certificate_key CERT/server.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    access_log off;
    error_log /opt/wx/editor-test148/data/resource-download-error.log crit;
    add_header Referrer-Policy no-referrer always;
    # 当前测试页的明确来源；业务宿主域名接入时单独配置白名单。
    add_header Access-Control-Allow-Origin "http://127.0.0.1:8765" always;
    add_header Vary Origin always;
    add_header Access-Control-Expose-Headers "Content-Length,Content-Range,ETag" always;
    add_header Cache-Control "private, no-store" always;
    location /wx-art-test148/resources/ {
        limit_except GET { deny all; }
        proxy_pass http://127.0.0.1:9000;
        proxy_set_header Host $http_host;
        proxy_set_header Connection "";
        proxy_http_version 1.1;
        proxy_buffering off;
        proxy_hide_header Cache-Control;
        # 跨域许可由网关统一返回，避免对象存储重复响应头被浏览器拒绝。
        proxy_hide_header Access-Control-Allow-Origin;
    }
    location / { return 404; }
}
""".replace("CERT",cert)
    backup=ROOT/"backups"/("download-"+datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ"));backup.mkdir(mode=0o700)
    if config.exists():shutil.copy2(config,backup/config.name)
    config.write_text(content,encoding="utf-8")
    try:
        subprocess.run(["/www/server/nginx/sbin/nginx","-t"],check=True,capture_output=True)
        subprocess.run(["/www/server/nginx/sbin/nginx","-s","reload"],check=True,capture_output=True)
    except Exception:
        if (backup/config.name).exists():shutil.copy2(backup/config.name,config)
        else:config.unlink()
        raise
    result={"endpoint":"https://192.168.10.148:9444","bucket":"wx-art-test148","bucketPolicy":"NONE_PRIVATE","getOnly":True,"signedQueryLogging":False,"backup":str(backup),"certificateExpires":"2026-11-11T04:54:09Z"}
    (ROOT/"download-setup.json").write_text(json.dumps(result,indent=2));print(json.dumps(result))
if __name__=="__main__":main()
