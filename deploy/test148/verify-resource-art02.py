"""验证候选服务真实身份矩阵；只使用合成资源，凭据只保留服务器受限文件。"""
from pathlib import Path
import json,os,secrets,string,subprocess,urllib.request,urllib.error,uuid
ROOT=Path("/opt/wx/editor-test148")
PORT=int(os.environ.get("WX_ART02_PORT","48093"))
SQL=["docker","exec","-i","mysql8","sh","-c",'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot wx_editor_test148 "$@"',"--","-N","-B"]
def sql(q):return subprocess.check_output(SQL,input=q.encode()).decode().strip()
def request(port,path,who=None,method="GET",body=None,headers=None):
    h={"Content-Type":"application/json"}
    if who:h.update({"tenant-id":str(who["tenantId"]),"Authorization":"Bearer "+who.get("token","")})
    h.update(headers or {})
    req=urllib.request.Request("http://127.0.0.1:"+str(port)+path,headers=h,method=method,data=json.dumps(body).encode() if body is not None else None)
    try:r=urllib.request.urlopen(req,timeout=20)
    except urllib.error.HTTPError as e:r=e
    return json.loads(r.read())

def main():
    os.umask(0o077)
    private=ROOT/"art02-identities.json"
    valid_private=False
    if private.exists():
        candidate=json.loads(private.read_text())
        valid_private=all(item.get("username", "").isalnum() and 4 <= len(item.get("password", "")) <= 16 for item in candidate)
    if valid_private: extra=candidate
    else:
        if private.exists(): private.unlink()
        base=900000+secrets.randbelow(900000)
        password="".join(secrets.choice(string.ascii_letters+string.digits) for _ in range(16))
        hashed=subprocess.check_output(["java","-cp",str(ROOT/"bootstrap/*"),str(ROOT/"bootstrap/HashPassword.java")],input=(password+"\n").encode()).decode().strip()
        tenant=base
        extra=[{"id":base+i,"tenantId":tenant,"role":role,"username":"art02"+str(base+i),"password":password} for i,role in enumerate(["tenant_admin","viewer","super_admin"],1)]
        if int(sql("SELECT COUNT(*) FROM wx_system_tenant WHERE id="+str(tenant))) != 0 or int(sql("SELECT COUNT(*) FROM wx_system_users WHERE id BETWEEN "+str(base+1)+" AND "+str(base+3))) != 0: raise RuntimeError("随机测试身份编号碰撞，停止避免覆盖")
        statements=["START TRANSACTION;",f"INSERT INTO wx_system_tenant(id,name,contact_user_id,contact_name,status,package_id,expire_time,account_count) VALUES({tenant},'ART02隔离租户',{base+1},'测试管理员',0,1,'2099-12-31',100);"]
        for item in extra:
            ident=item["id"];role=item["role"]
            statements += [f"INSERT INTO wx_system_users(id,username,password,nickname,post_ids,status,tenant_id) VALUES({ident},'{item['username']}','{hashed}','ART02测试','[]',0,{tenant});",f"INSERT INTO wx_system_role(id,name,code,sort,data_scope,data_scope_dept_ids,status,type,tenant_id) VALUES({ident},'ART02角色','{role}',0,1,'[]',0,2,{tenant});",f"INSERT INTO wx_system_user_role(id,user_id,role_id,tenant_id) VALUES({ident},{ident},{ident},{tenant});"]
        sql("\n".join(statements+["COMMIT;"]))
        private.write_text(json.dumps(extra));private.chmod(0o600)
    original=json.loads((ROOT/"test-identities.json").read_text())
    actors=original+extra;checks=[];created=[];fixture_ids=[]
    def check(name,response,expected):
        code=response.get("code");passed=code in expected
        checks.append({"name":name,"passed":passed,"code":code})
        if not passed:raise AssertionError(name+": "+str(code)+" "+str(response.get("msg","")))
        return response.get("data")
    for who in actors:
        login=request(48081,"/admin-api/system/auth/login",who,"POST",{"username":who["username"],"password":who["password"]})
        who["token"]=check("登录:"+str(who["tenantId"])+":"+who["role"],login,{200})["accessToken"]
    platform,aadmin,aview,badmin,bview,forged=actors
    def start(who,scope):
        operation=str(uuid.uuid4());body={"operationId":operation,"operationType":"CREATE","resourceScope":scope,"metadata":{"resourceName":"ART02-验证","resourceType":"MODEL","assetName":"test"}}
        result=request(PORT,"/admin-api/dim3d/resource-operations",who,"POST",body)
        if result.get("code")==200:created.append((operation,result["data"]["resourceId"]))
        return result,body
    try:
        check("匿名不能读取",request(PORT,"/admin-api/dim3d/resources"),{401})
        for who in [aadmin,aview,badmin,bview,forged]:check("非平台不能上传全局:"+str(who["id"]),start(who,"GLOBAL_SYSTEM")[0],{2030001002})
        for who in [aview,bview,forged]:check("非租户管理员不能上传共享:"+str(who["id"]),start(who,"TENANT_SHARED")[0],{2030001002})
        for who,scope in [(platform,"GLOBAL_SYSTEM"),(aadmin,"TENANT_SHARED"),(badmin,"TENANT_SHARED")]:
            response,body=start(who,scope);data=check("授权创建:"+str(who["id"]),response,{200})
            replay=check("同号重试",request(PORT,"/admin-api/dim3d/resource-operations",who,"POST",body),{200})
            assert data["resourceId"]==replay["resourceId"]
            body["metadata"]["resourceName"]="ART02-改变载荷"
            check("同号异载荷拒绝",request(PORT,"/admin-api/dim3d/resource-operations",who,"POST",body),{2030001004})
            check("操作状态可查",request(PORT,"/admin-api/dim3d/resource-operations/"+body["operationId"],who),{200})
            other=badmin if who is aadmin else aadmin
            check("其他操作者不能查询",request(PORT,"/admin-api/dim3d/resource-operations/"+body["operationId"],other),{2030001001})
            check("未准备内容不能提交",request(PORT,"/admin-api/dim3d/resource-operations/"+body["operationId"]+"/commit",who,"POST",{}),{400,2030001012} if os.environ.get("WX_ART03_ENABLED")=="1" else {501})
        # 构造只用于范围过滤的元数据；不代表对象已上传，不执行真实下载。
        for scope,owner in [("GLOBAL_SYSTEM","0"),("TENANT_SHARED","2"),("TENANT_SHARED",str(badmin["tenantId"]))]:
            rid=3000000000000000000+secrets.randbelow(100000000);op=str(uuid.uuid4());fixture_ids.append(rid)
            sql(f"INSERT INTO wx_dim3d_resource(id,resource_name,resource_type,asset_name,resource_relative_path,resource_scope,tenant_id,content_state) VALUES({rid},'ART02-范围夹具','MODEL','test','test','{scope}','{owner}','PENDING'); INSERT INTO wx_dim3d_resource_operation(id,resource_id,operation_type,resource_scope,tenant_id,operator_id,request_hash,expected_revision,status,committed_revision,after_snapshot,expires_at) VALUES('{op}',{rid},'CREATE','{scope}','{owner}',1,'{'a'*64}',0,'COMMITTED',1,JSON_OBJECT('fixture',true),'2030-01-01'); UPDATE wx_dim3d_resource SET content_state='READY',storage_prefix='art02/fixture/',manifest_path='manifest.json',manifest_sha256='{'a'*64}',content_hash='{'b'*64}',content_format='unity.assetbundle.v1',platform='WebGL',app_compatibility='test',current_revision=1,current_operation_id='{op}' WHERE id={rid};")
        global_id,a_id,b_id=fixture_ids
        for who,visible,hidden in [(aview,{global_id,a_id},b_id),(bview,{global_id,b_id},a_id)]:
            data=check("可见列表:"+str(who["id"]),request(PORT,"/admin-api/dim3d/resources",who),{200})
            got={int(row["resourceId"]) for row in data["list"]};assert visible.issubset(got) and hidden not in got
            check("按ID不能跨租户",request(PORT,"/admin-api/dim3d/resources/"+str(hidden)+"/current",who),{2030001001})
            check("当前描述签名配置状态",request(PORT,"/admin-api/dim3d/resources/"+str(global_id)+"/current",who),{200} if os.environ.get("WX_ART02_DOWNLOAD_ENABLED")=="1" else {2030001014})
        for who,rid in [(platform,global_id),(aadmin,a_id),(badmin,b_id)]:
            for kind in ["REPLACE","DELETE"]:
                op=str(uuid.uuid4());body={"operationId":op,"operationType":kind,"resourceId":str(rid),"expectedRevision":"1"}
                data=check("授权"+kind+":"+str(who["id"]),request(PORT,"/admin-api/dim3d/resource-operations",who,"POST",body),{200})
                check("取消尚未提交操作",request(PORT,"/admin-api/dim3d/resource-operations/"+op+"/cancel",who,"POST"),{200})
                # 夹具操作会随所属合成资源在finally清理，不能将取消当作实际删除。
        for who,rid in [(aadmin,global_id),(aview,a_id),(badmin,a_id)]:
            for kind in ["REPLACE","DELETE"]:
                body={"operationId":str(uuid.uuid4()),"operationType":kind,"resourceId":str(rid),"expectedRevision":"1"}
                check("拒绝未授权"+kind+":"+str(who["id"]),request(PORT,"/admin-api/dim3d/resource-operations",who,"POST",body),{2030001001,2030001002})
        stale={"operationId":str(uuid.uuid4()),"operationType":"REPLACE","resourceId":str(a_id),"expectedRevision":"0"}
        check("拒绝过期修订",request(PORT,"/admin-api/dim3d/resource-operations",aadmin,"POST",stale),{2030001003})
        check("旧模型新增不能绑定受管资源",request(PORT,"/admin-api/dim3d/model/add",aadmin,"POST",{"modelName":"ART02-旧接口改绑","category":"TEST","modelFileId":str(a_id)}),{2030001001})
        check("请求租户不能替换认证租户",request(PORT,"/admin-api/dim3d/resources",aview,headers={"tenant-id":str(badmin["tenantId"])}),{403,2030001018})
        result={"passed":True,"checks":checks,"candidatePort":PORT,"scope":"候选真实登录、角色/范围和操作边界；未验收下载、提交或视觉","testTenantRetained":badmin["tenantId"]}
    finally:
        ids=fixture_ids+[int(value) for _,value in created]
        if ids:
            values=",".join(map(str,ids))
            sql("START TRANSACTION; UPDATE wx_dim3d_resource SET content_state='PENDING',current_operation_id=NULL WHERE id IN ("+values+") AND resource_name LIKE 'ART02-%'; DELETE FROM wx_dim3d_resource_operation WHERE resource_id IN ("+values+"); DELETE FROM wx_dim3d_resource WHERE id IN ("+values+") AND resource_name LIKE 'ART02-%'; COMMIT;")
    (ROOT/"verification-art02.json").write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps({"passed":True,"checks":len(checks)}))
if __name__=="__main__":main()
