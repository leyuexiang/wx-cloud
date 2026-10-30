"""ART-03真实工具产物的上传/校验/提交验收；报告不记录令牌或签名地址。"""
from pathlib import Path
import importlib.util,json,os,urllib.request,urllib.error,urllib.parse,ssl,hashlib,uuid,concurrent.futures
ROOT=Path("/opt/wx/editor-test148");PORT=int(os.environ.get("WX_ART03_PORT","48094"))
def module(name,file):
    spec=importlib.util.spec_from_file_location(name,ROOT/file);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

def main():
    os.umask(0o077);api=module("api","verify-resource-art02.py");s3=module("s3","art-s3-test-support.py")
    context=ssl.create_default_context(cafile="/www/server/panel/vhost/cert/jtyf-web-art-20261028-133dd9a1e33e42c9/server.crt")
    source="https://192.168.10.148:9443/web-art-verification/20261028-133dd9a1e33e42c9/workbench-car-6c55a34da349/A/"
    source_files={}
    # 可使用原生成工具的只读样本副本，避免旧验证站点故障阻塞事务回归；下载验收仍走真实HTTPS。
    source_dir=os.environ.get("WX_ART03_SOURCE_DIR")
    if source_dir:
        directory=Path(source_dir).resolve()
        if not directory.is_relative_to(ROOT/"samples") or not directory.is_dir():raise RuntimeError("样本副本目录不在测试范围")
    def get(url):
        if source_dir and url.startswith(source):
            file=(directory/url[len(source):]).resolve()
            if not file.is_relative_to(directory):raise RuntimeError("样本路径越界")
            return file.read_bytes()
        return urllib.request.urlopen(url,context=context,timeout=40).read()
    release=json.loads(get(source+"ArtReleaseManifest.json"));catalog=json.loads(get(source+release["catalogFile"]));packs=json.loads(get(source+release["packManifestFile"]))
    for key,size,hashkey in [("catalogFile","catalogSize","catalogSha256"),("packManifestFile","packManifestSize","packManifestSha256"),("bundleManifestFile","bundleManifestSize","bundleManifestSha256")]:
        raw=get(source+release[key]);assert len(raw)==release[size] and hashlib.sha256(raw).hexdigest()==release[hashkey].removeprefix("sha256:");source_files[release[key]]=raw
    for pack in packs["packs"]:
        raw=get(source+pack["file"]);assert len(raw)==pack["size"] and hashlib.sha256(raw).hexdigest()==pack["sha256"].removeprefix("sha256:");source_files[pack["file"]]=raw
    model=next(row for row in catalog["resources"] if row["kind"]=="Model")
    identities=json.loads((ROOT/"test-identities.json").read_text());actor=identities[1];viewer=identities[2]
    for who in [actor,viewer]:
        login=api.request(12522,"/admin-api/system/auth/login",who,"POST",{"username":who["username"],"password":who["password"]});assert login.get("code")==200;who["token"]=login["data"]["accessToken"]
    checks=[];rid=None;opids=[];allkeys=set();tag=uuid.uuid4().hex[:12]
    def check(name,ok):
        checks.append({"name":name,"passed":bool(ok)})
        if not ok:raise AssertionError(name)
    def request(path,body=None,who=None,method="POST"):
        return api.request(PORT,"/admin-api/dim3d/"+path,who or actor,method,body)
    def operation(kind="CREATE",revision=None,snapshot=None):
        body={"operationId":str(uuid.uuid4()),"operationType":kind}
        if kind=="CREATE":body.update(resourceScope="TENANT_SHARED",metadata={"resourceName":"ART03-汽车事务-"+tag,"resourceType":"MODEL","assetName":"model.prefab"})
        else:body.update(resourceId=rid,expectedRevision=str(revision))
        if snapshot:body["snapshotOperationId"]=snapshot
        response=request("resource-operations",body);check("创建操作:"+kind,response.get("code")==200);opids.append(body["operationId"]);return response["data"]
    def content(op,version):
        files=dict(source_files)
        if version!="A":
            modified=json.loads(files[release["catalogFile"]]);modified["resources"][0]["sceneName"]="ART03-元数据内容"+version
            files[release["catalogFile"]]=json.dumps(modified,ensure_ascii=False,sort_keys=True,separators=(",",":")).encode()
        manifest={"schemaVersion":"wx.art-resource.v1","resourceId":rid,"contentFormat":"unity.assetbundle.v1","platform":"WebGL","appCompatibility":release["appCompatibility"],"entry":{"kind":"MODEL","assetName":model["assetName"],"packagePath":next(p["file"] for p in packs["packs"] if p["packId"]==model["packId"]),"nativeManifestPath":release["bundleManifestFile"],"catalogPath":release["catalogFile"],"packManifestPath":release["packManifestFile"]},"files":[{"path":name,"size":str(len(raw)),"sha256":hashlib.sha256(raw).hexdigest()} for name,raw in sorted(files.items())]}
        manifest["contentHash"]=hashlib.sha256(json.dumps(manifest,ensure_ascii=False,sort_keys=True,separators=(",",":")).encode()).hexdigest()
        raw=json.dumps(manifest,ensure_ascii=False,sort_keys=True,separators=(",",":")).encode();files["manifest.json"]=raw
        body={"manifestPath":"manifest.json","manifestSha256":hashlib.sha256(raw).hexdigest(),"contentHash":manifest["contentHash"],"contentFormat":"unity.assetbundle.v1","platform":"WebGL","appCompatibility":release["appCompatibility"]}
        plan={"files":[{"path":name,"size":len(raw),"sha256":hashlib.sha256(raw).hexdigest()} for name,raw in sorted(files.items())]}
        targets=request("resource-operations/"+op["operationId"]+"/upload-targets",plan);check("签发绑定上传计划",targets.get("code")==200)
        for target in targets["data"]["targets"]:
            req=urllib.request.Request(target["url"],data=files[target["path"]],headers=target["requiredHeaders"],method="PUT")
            check("受限HTTPS上传:"+target["path"],urllib.request.urlopen(req,context=context,timeout=60).status==200)
        for name in files:allkeys.add("resources/staging/TENANT_SHARED/"+str(actor["tenantId"])+"/"+rid+"/"+op["operationId"]+"/"+name)
        return body,targets["data"],files
    def commit(op,body):return request("resource-operations/"+op["operationId"]+"/commit",body)
    def current():return request("resources/"+rid+"/current",method="GET",who=viewer)
    try:
        created=operation();rid=created["resourceId"];body,targets,files=content(created,"A")
        result=commit(created,body);check("真实汽车闭包首次提交",result.get("code")==200 and result["data"]["status"]=="COMMITTED" and result["data"]["committedRevision"]=="1")
        directory_id=int(api.sql("INSERT INTO wx_dim3d_model(model_name,category,model_file_id,resource_id,tenant_id,resource_belong,status) VALUES('ART03-关联目录-"+tag+"','TEST',"+rid+","+rid+",'"+str(actor["tenantId"])+"','MINE','ONLINE'); SELECT LAST_INSERT_ID();").splitlines()[-1])
        check("受控目录关联可追溯",api.sql("SELECT COUNT(*) FROM wx_dim3d_model WHERE id="+str(directory_id)+" AND resource_id="+rid)=="1")
        first=current();check("普通用户取得当前描述",first.get("code")==200 and first["data"]["contentHash"]==body["contentHash"])
        old_url=first["data"]["manifestUrl"];check("提交后清单可下载",hashlib.sha256(get(old_url)).hexdigest()==body["manifestSha256"])
        replay=commit(created,body);check("响应丢失重试不递增",replay.get("code")==200 and replay["data"]["committedRevision"]=="1")
        # 旧暂存地址仍可写，但冻结后的当前内容不应改变。
        target=next(row for row in targets["targets"] if row["path"]=="manifest.json")
        urllib.request.urlopen(urllib.request.Request(target["url"],data=b"changed-staging",headers=target["requiredHeaders"],method="PUT"),context=context).read()
        check("重放旧PUT不能改写冻结内容",hashlib.sha256(get(old_url)).hexdigest()==body["manifestSha256"])
        replacement=operation("REPLACE",1);bbody,_,_=content(replacement,"B");result=commit(replacement,bbody)
        check("同ID替换内容B",result.get("code")==200 and result["data"]["committedRevision"]=="2" and current()["data"]["contentHash"]==bbody["contentHash"])
        check("旧内容保留可读",hashlib.sha256(get(old_url)).hexdigest()==body["manifestSha256"])
        failed=operation("REPLACE",2);bad,_,_=content(failed,"C");bad["manifestSha256"]="a"*64
        check("坏摘要提交拒绝",commit(failed,bad).get("code")==2030001008)
        check("坏提交保留B指针",current()["data"]["currentRevision"]=="2" and current()["data"]["contentHash"]==bbody["contentHash"])
        left=operation("REPLACE",2);right=operation("REPLACE",2);leftbody,_,_=content(left,"D");rightbody,_,_=content(right,"E")
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            results=list(pool.map(lambda args:commit(*args),[(left,leftbody),(right,rightbody)]))
        check("并发替换仅一项成功",sorted(r.get("code") for r in results)==[200,2030001003])
        check("并发后只递增一次",current()["data"]["currentRevision"]=="3")
        delete=operation("DELETE",3);deleted=commit(delete,{})
        check("逻辑删除提交",deleted.get("code")==200 and deleted["data"]["committedRevision"]=="4")
        check("删除后新增消费拒绝",current().get("code")==2030001001)
        check("删除事务同步下线关联目录",api.sql("SELECT CONCAT(del_flag,':',status) FROM wx_dim3d_model WHERE id="+str(directory_id))=="1:OFFLINE")
        restored=operation("RESTORE",4,delete["operationId"]);restored_result=commit(restored,{})
        check("受控快照恢复",restored_result.get("code")==200 and restored_result["data"]["committedRevision"]=="5" and current().get("code")==200)
        check("快照恢复同步还原关联目录",api.sql("SELECT CONCAT(del_flag,':',status) FROM wx_dim3d_model WHERE id="+str(directory_id))=="0:ONLINE")
        rollback_op=operation("REPLACE",5);rollback_body,rollback_targets,_=content(rollback_op,"F")
        trigger="wx_art03_reject_"+tag
        try:
            api.sql("DELIMITER $$\nCREATE TRIGGER "+trigger+" BEFORE UPDATE ON wx_dim3d_resource_operation FOR EACH ROW BEGIN IF NEW.resource_id="+rid+" AND NEW.status='COMMITTED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ART03 synthetic receipt failure'; END IF; END$$\nDELIMITER ;\n")
            denied=commit(rollback_op,rollback_body)
            check("真实MySQL写回执失败拒绝提交",denied.get("code")==2030001015)
            check("数据库失败保留原指针及修订",current()["data"]["currentRevision"]=="5")
        finally:api.sql("DROP TRIGGER IF EXISTS "+trigger+";")
        retried=commit(rollback_op,rollback_body)
        check("数据库恢复后同操作可重试",retried.get("code")==200 and retried["data"]["committedRevision"]=="6")
        # HTTPS上传地址的签名只能用于该暂存对象，不能修改成最终对象路径。
        target=rollback_targets["targets"][0]
        try:
            urllib.request.urlopen(urllib.request.Request(target["url"].replace("/resources/staging/","/resources/final/"),data=b"x",method="PUT"),context=context)
            final_status=200
        except urllib.error.HTTPError as error:final_status=error.code
        check("上传入口不能写最终前缀",final_status==404)
        try:
            urllib.request.urlopen(urllib.request.Request(target["url"].split("?")[0],data=b"x",method="PUT"),context=context)
            anonymous_status=200
        except urllib.error.HTTPError as error:anonymous_status=error.code
        check("上传必须携带有效签名",anonymous_status==403)
        check("普通用户不能替换提交",request("resource-operations/"+created["operationId"]+"/commit",body,who=viewer).get("code")==2030001001)
        result={"passed":True,"checks":checks,"count":len(checks),"port":PORT,"sourceReleaseHash":release["releaseHash"],"modelBytes":sum(len(v) for v in source_files.values()),"scope":"既有Unity工作台真实汽车包；B/D/E只改目录元数据，不代表材质视觉更新；没有浏览器画面验收","cleanup":False}
    finally:
        if rid:
            # 只回收本验收创建的记录；先收集服务端冻结键，避免清理用户或其它操作目录。
            snapshots=api.sql("SELECT storage_prefix FROM wx_dim3d_resource_operation WHERE resource_id="+rid+";").splitlines()
            for operation_id in opids:
                source_prefix="resources/TENANT_SHARED/"+str(actor["tenantId"])+"/"+rid+"/operations/"+operation_id+"/"
                # ListObjects仅使用服务端私有凭据，精确前缀范围，不记录对象内容。
                import xml.etree.ElementTree as ET
                code,raw=s3.request("GET","/wx-art-test148",query=urllib.parse.urlencode({"list-type":"2","prefix":source_prefix}))
                if code!=200:raise RuntimeError("冻结验收前缀枚举失败，保留记录待检查")
                if code==200:
                    for key in ET.fromstring(raw).findall('.//{*}Key'):
                        if key.text.startswith(source_prefix):allkeys.add(key.text)
            api.sql("DELETE FROM wx_dim3d_model WHERE resource_id="+rid+" AND model_name='ART03-关联目录-"+tag+"';")
            api.sql("START TRANSACTION; UPDATE wx_dim3d_resource SET content_state='PENDING',current_operation_id=NULL WHERE id="+rid+" AND resource_name='ART03-汽车事务-"+tag+"'; DELETE FROM wx_dim3d_resource_operation WHERE resource_id="+rid+"; DELETE FROM wx_dim3d_resource WHERE id="+rid+" AND resource_name='ART03-汽车事务-"+tag+"'; COMMIT;")
        for key in allkeys:
            code,_=s3.request("DELETE","/wx-art-test148/"+key)
            if code not in [200,204]:raise RuntimeError("测试对象清理失败")
    result["cleanup"]=True
    (ROOT/"verification-art03.json").write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps({"passed":True,"checks":len(checks),"cleanup":True}))
if __name__=="__main__":main()
