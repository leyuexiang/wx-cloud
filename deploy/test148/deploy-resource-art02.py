"""备份后仅替换三维测试服务；健康和两套回归失败立即恢复旧程序与配置。"""
from pathlib import Path
import datetime,subprocess,os,json,hashlib,time,urllib.request,shutil,re
ROOT=Path("/opt/wx/editor-test148")
def run(*args,**kw):return subprocess.run(args,check=True,capture_output=True,**kw)
def healthy():
    for _ in range(60):
        try:
            value=json.loads(urllib.request.urlopen("http://127.0.0.1:48092/actuator/health",timeout=2).read())
            if value.get("status")=="UP":return
        except Exception:pass
        time.sleep(2)
    raise RuntimeError("三维服务健康检查未通过")
def main():
    if Path.cwd().resolve()!=ROOT:raise RuntimeError("工作目录不匹配")
    os.umask(0o077)
    target=ROOT/"jars/dimensions.jar";candidate=ROOT/"jars/dimensions-art02.candidate.jar";config=ROOT/"config/dimensions.yaml"
    stamp=datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    backup=ROOT/"backups"/("art02-service-"+stamp);backup.mkdir(mode=0o700)
    shutil.copy2(target,backup/"dimensions.jar");shutil.copy2(config,backup/"dimensions.yaml")
    # 使用同目录新文件原子替换挂载目标；旧容器重建后才会读取新inode。
    pending=target.with_suffix(".art02-ready");shutil.copy2(candidate,pending);pending.replace(target)
    settings=config.read_text(encoding="utf-8")
    # 已有资源配置段只更新平台值，禁止重复追加根映射。
    matches=list(re.finditer(r"(?m)^    platform-tenant-id:.*$",settings))
    if len(matches)!=1:raise RuntimeError("平台配置项缺失或重复，请先合并配置再部署")
    settings=settings[:matches[0].start()]+"    platform-tenant-id: 1"+settings[matches[0].end():]
    config.write_text(settings,encoding="utf-8")
    report={"backup":str(backup),"gatewayUnchanged":True,"databaseChanged":False,"downloadConfigured":False}
    try:
        run("docker","compose","-f","compose.yaml","up","-d","--no-deps","--force-recreate","dimensions")
        healthy()
        run("python3","verify-resource-art02.py",env={**os.environ,"WX_ART02_PORT":"48092","WX_ART02_DOWNLOAD_ENABLED":"1" if "download-endpoint:" in settings else "0"})
        run("python3","verify-api.py",env={**os.environ,"WX_VERIFY_PORT":"12522"})
        with target.open("rb") as f:report["sha256"]=hashlib.file_digest(f,"sha256").hexdigest()
        report["status"]="DEPLOYED"
    except Exception as exc:
        pending=target.with_suffix(".art02-rollback");shutil.copy2(backup/"dimensions.jar",pending);pending.replace(target)
        shutil.copy2(backup/"dimensions.yaml",config)
        run("docker","compose","-f","compose.yaml","up","-d","--no-deps","--force-recreate","dimensions")
        healthy();report["status"]="ROLLED_BACK";report["errorType"]=type(exc).__name__
        (ROOT/"deployment-art02.json").write_text(json.dumps(report,indent=2));raise
    (ROOT/"deployment-art02.json").write_text(json.dumps(report,indent=2));print(json.dumps(report))
if __name__=="__main__":main()
