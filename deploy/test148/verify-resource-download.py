"""通过真实API签名与TLS下载验证权限；合成文件和元数据清理，不伪称ART-03发布。"""
from pathlib import Path
import importlib.util,json,hashlib,ssl,urllib.request,urllib.error,uuid,time,subprocess,os
ROOT=Path("/opt/wx/editor-test148")
def load(name,file):
    spec=importlib.util.spec_from_file_location(name,ROOT/file);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

def main():
    os.umask(0o077)
    api=load("art02","verify-resource-art02.py");s3=load("s3","art-s3-test-support.py")
    context=ssl.create_default_context(cafile="/www/server/panel/vhost/cert/jtyf-web-art-20261028-133dd9a1e33e42c9/server.crt")
    checks=[];rows=[];objects=[];tag=uuid.uuid4().hex
    def check(name,passed):
        checks.append({"name":name,"passed":passed})
        if not passed:raise AssertionError(name)
    def download(url,method="GET"):
        req=urllib.request.Request(url,method=method)
        try:r=urllib.request.urlopen(req,context=context,timeout=15)
        except urllib.error.HTTPError as error:r=error
        return r.status,r.read(),r.headers
    actors=json.loads((ROOT/"test-identities.json").read_text());other=json.loads((ROOT/"art02-identities.json").read_text())
    a=actors[2];b=other[1]
    for who in [a,b]:
        result=api.request(12522,"/admin-api/system/auth/login",who,"POST",{"username":who["username"],"password":who["password"]})
        check("普通用户真实登录:"+str(who["tenantId"]),result.get("code")==200);who["token"]=result["data"]["accessToken"]
    try:
        for scope,owner in [("GLOBAL_SYSTEM","0"),("TENANT_SHARED",str(a["tenantId"]))]:
            rid=3100000000000000000+int(uuid.uuid4().hex[:8],16);op=str(uuid.uuid4());prefix="resources/art02-download-"+tag+"/"+str(rid)+"/"
            key=prefix+"manifest.json";payload=json.dumps({"kind":"ART02-download-fixture","resourceId":str(rid),"scope":scope},sort_keys=True).encode();digest=hashlib.sha256(payload).hexdigest()
            code,_=s3.request("PUT","/wx-art-test148/"+key,payload);check("上传隔离验收文件:"+scope,code==200);objects.append(key)
            code,raw=s3.request("GET","/wx-art-test148/"+key);check("内部回读摘要:"+scope,code==200 and hashlib.sha256(raw).hexdigest()==digest)
            rows.append(rid)
            api.sql(f"INSERT INTO wx_dim3d_resource(id,resource_name,resource_type,asset_name,resource_relative_path,resource_scope,tenant_id,content_state) VALUES({rid},'ART02-下载夹具','MODEL','test','test','{scope}','{owner}','PENDING'); INSERT INTO wx_dim3d_resource_operation(id,resource_id,operation_type,resource_scope,tenant_id,operator_id,request_hash,expected_revision,status,committed_revision,after_snapshot,expires_at) VALUES('{op}',{rid},'CREATE','{scope}','{owner}',1,'{'a'*64}',0,'COMMITTED',1,JSON_OBJECT('fixture',true),'2030-01-01'); UPDATE wx_dim3d_resource SET content_state='READY',storage_prefix='{prefix}',manifest_path='manifest.json',manifest_sha256='{digest}',content_hash='{'b'*64}',content_format='unity.assetbundle.v1',platform='WebGL',app_compatibility='download-fixture',current_revision=1,current_operation_id='{op}' WHERE id={rid};")
            result=api.request(12522,"/admin-api/dim3d/resources/"+str(rid)+"/current",a);check("当前描述签发:"+scope,result.get("code")==200)
            descriptor=result["data"];url=descriptor["manifestUrl"]
            check("签名HTTPS且限定五分钟:"+scope,url.startswith("https://192.168.10.148:9444/") and "X-Amz-Expires=300" in url)
            code,raw,headers=download(url);check("TLS主机及证书验证后实际下载:"+scope,code==200 and hashlib.sha256(raw).hexdigest()==descriptor["manifestSha256"])
            check("缓存禁止共享:"+scope,"no-store" in headers.get("Cache-Control",""))
            check("浏览器跨域限定测试来源:"+scope,headers.get("Access-Control-Allow-Origin")=="http://127.0.0.1:8765" and headers.get("Vary")=="Origin")
            bare=url.split("?")[0];check("去掉签名不能下载:"+scope,download(bare)[0]==403)
            changed=url.replace("manifest.json?","other.json?");check("篡改对象键签名失效:"+scope,download(changed)[0]==403)
            changed=url[:-1]+("1" if url[-1]!="1" else "2");check("篡改签名拒绝:"+scope,download(changed)[0]==403)
            signed=s3.presigned("/wx-art-test148/"+key,expires=2,age=20)
            check("真实过期签名被存储拒绝:"+scope,download(signed)[0]==403)
            check("代理拒绝PUT写入:"+scope,download(url,"PUT")[0]==403)
            other_result=api.request(12522,"/admin-api/dim3d/resources/"+str(rid)+"/current",b)
            if scope=="GLOBAL_SYSTEM":
                check("其它租户全局描述可读",other_result.get("code")==200)
                check("其它租户全局签名可下载",download(other_result["data"]["manifestUrl"])[0]==200)
            else:check("其它租户共享描述拒绝且无下载地址",other_result.get("code")==2030001001 and other_result.get("data") is None)
            anon=api.request(12522,"/admin-api/dim3d/resources/"+str(rid)+"/current")
            check("匿名无法取得签名:"+scope,anon.get("code")==401)
        check("下载入口拒绝桶列表",download("https://192.168.10.148:9444/wx-art-test148/")[0]==404)
        result={"passed":True,"checks":checks,"count":len(checks),"scope":"真实私有桶/TLS/签名/API授权；合成对象和手动元数据夹具，不是ART-03发布事务或模型包验收","certificateVerificationDisabled":False,"signedUrlsLogged":False,"cleanup":False}
    finally:
        if rows:
            ids=",".join(map(str,rows));api.sql("START TRANSACTION; UPDATE wx_dim3d_resource SET content_state='PENDING',current_operation_id=NULL WHERE id IN ("+ids+") AND resource_name='ART02-下载夹具'; DELETE FROM wx_dim3d_resource_operation WHERE resource_id IN ("+ids+"); DELETE FROM wx_dim3d_resource WHERE id IN ("+ids+") AND resource_name='ART02-下载夹具'; COMMIT;")
        for key in objects:
            code,_=s3.request("DELETE","/wx-art-test148/"+key)
            if code not in [200,204]:raise RuntimeError("验收对象清理失败")
    result["cleanup"]=True
    (ROOT/"download-verification.json").write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps({"checks":len(checks),"passed":True,"cleanup":True}))
if __name__=="__main__":main()
