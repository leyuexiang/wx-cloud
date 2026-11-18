"""正式接口准备隔离租户，验证真实账号边界及删除后的退出审计；秘密仅留受限清单。"""
import hashlib
import json
import os
from pathlib import Path
import secrets
import string
import subprocess
import sys
import time
import traceback
import urllib.error
import urllib.request
from uuid import uuid4

ROOT=Path('/home/wx-auth-consistency-20261118');STATE=ROOT/'state.private.json'
LIVE='http://127.0.0.1:12525/admin-api';CANDIDATE='http://127.0.0.1:49581/admin-api'
MANAGEMENT=['system:user:create','system:user:delete','system:user:list','system:role:create','system:role:delete','system:permission:assign-user-role','system:permission:assign-role-menu']

class Api:
    def __init__(self,identity,origin=LIVE):
        self.tenant=identity['tenantId'];self.origin=origin;self.token=None
        result=self.call('/system/auth/login','POST',{'username':identity['username'],'password':identity['password']})
        self.token=result['accessToken'];self.user_id=result['userId']
    def request(self,path,method='GET',body=None,token=None,tenant=None):
        headers={'tenant-id':str(self.tenant if tenant is None else tenant),'Content-Type':'application/json'}
        credential=self.token if token is None else token
        if credential:headers['Authorization']='Bearer '+credential
        req=urllib.request.Request(self.origin+path,headers=headers,method=method,data=None if body is None else json.dumps(body).encode())
        try:
            with urllib.request.urlopen(req,timeout=20) as response:return json.load(response)
        except urllib.error.HTTPError as error:
            try:return json.loads(error.read())
            except ValueError:return {'code':error.code}
    def call(self,path,method='GET',body=None):
        result=self.request(path,method,body)
        if result.get('code') not in (0,200):raise RuntimeError(str(result.get('code')))
        return result.get('data')
    def logout(self):
        if self.token:self.call('/system/auth/logout','POST');self.token=None

def save(state):
    temporary=STATE.with_suffix('.pending')
    with os.fdopen(os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600),'w',encoding='utf-8') as stream:json.dump(state,stream)
    temporary.replace(STATE)
def password():return ''.join(secrets.choice(string.ascii_letters+string.digits) for _ in range(16))
def digest(value):return hashlib.sha256(json.dumps(value,sort_keys=True).encode()).hexdigest()
def sql(text):
    result=subprocess.run(['docker','exec','-i','mysql8','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B wx_editor'],input=text,text=True,capture_output=True,timeout=20)
    assert result.returncode==0,'只读审计失败'
    return result.stdout.strip()
def role_snapshot():
    return sql('SELECT r.id,r.tenant_id,r.code,r.status,rm.menu_id FROM wx_system_role r LEFT JOIN wx_system_role_menu rm ON rm.role_id=r.id AND rm.deleted=0 WHERE r.deleted=0 ORDER BY r.id,rm.menu_id;').splitlines()
def platform():
    identities=json.loads(Path('/opt/wx/editor-test148/test-identities.json').read_text())
    return Api(next(row for row in identities if row['role']=='super_admin'))

def setup():
    assert not ROOT.exists();ROOT.mkdir(mode=0o700)
    api=platform()
    state={'tag':'ac'+uuid4().hex[:8],'menus':[],'accounts':[],'packageId':None,'tenantId':None,'deleteRoleId':None}
    try:
        state['before']={'menus':api.call('/system/menu/list'),'packages':api.call('/system/tenant-package/page?pageNo=1&pageSize=100')['list'],'tenant2':api.call('/system/tenant/get?id=2'),'roles':role_snapshot()}
        permissions={row['permission']:row['id'] for row in state['before']['menus'] if row.get('permission')}
        save(state)
        for index,permission in enumerate(MANAGEMENT):
            if permission in permissions:continue
            identifier=api.call('/system/menu/create','POST',{'name':state['tag']+'-'+str(index),'permission':permission,'type':3,'sort':0,'parentId':0,'status':0,'visible':False,'keepAlive':False,'alwaysShow':False})
            state['menus'].append({'id':identifier,'permission':permission});permissions[permission]=identifier;save(state)
        business=next(row['menuIds'] for row in state['before']['packages'] if str(row['id'])=='2')
        state['packageId']=api.call('/system/tenant-package/create','POST',{'name':state['tag'],'status':0,'remark':'身份合同隔离验收，结束回收','menuIds':list(set(business+[permissions[p] for p in MANAGEMENT]))});save(state)
        state['admin']={'username':(state['tag']+'administrator').ljust(30,'a'),'password':password()};save(state)
        state['tenantId']=api.call('/system/tenant/create','POST',{'name':state['tag'],'contactName':'身份验收','status':0,'packageId':state['packageId'],'expireTime':int((time.time()+86400)*1000),'accountCount':20,**state['admin']});save(state)
        state['permissionIds']=permissions;save(state)
        print(json.dumps({'tenantId':state['tenantId'],'adminUsernameLength':30,'createdTemporaryMenus':len(state['menus']),'originalTenant2Changed':False}))
    finally:api.logout()

def audit_logout(user_id,expected):
    rows=sql('SELECT username,result,user_type FROM wx_system_login_log WHERE user_id='+str(int(user_id))+' AND log_type=200 AND deleted=0 ORDER BY id;').splitlines()
    assert len(rows)==1 and rows[0].split('\t')==[expected,'0','2'], '退出审计不匹配'
    return {'userId':user_id,'auditCount':1,'usernameMatches':True,'result':0,'userType':2}

def accept(phase):
    state=json.loads(STATE.read_text());origin=CANDIDATE if phase=='candidate' else LIVE
    # 重试保留旧账号和审计，以独立运行标识避免覆盖失败现场。
    run_key=uuid4().hex[:4]
    identity={**state['admin'],'tenantId':state['tenantId']};apis=[]
    report={'phase':phase,'tenantId':state['tenantId'],'adminUsernameLength':30,'accounts':[]}
    try:
        if phase=='candidate':
            try:probe=Api(identity,LIVE)
            except RuntimeError as error:assert str(error)=='400';report['baselineLongLoginCode']=400
            else:probe.logout();raise AssertionError('原服务应复现长账号拒绝')
        admin=Api(identity,origin);apis.append(admin)
        state['adminUserId']=admin.user_id
        state['adminRoleId']=next(row['id'] for row in admin.call('/system/role/list-all-simple') if row['code']=='tenant_admin');save(state)
        if not state['deleteRoleId']:
            state['deleteRoleId']=admin.call('/system/role/create','POST',{'name':'身份自删验收','code':state['tag']+'delete','sort':1,'status':0,'remark':'仅删除本租户测试账号'});save(state)
            admin.call('/system/permission/assign-role-menu','POST',{'roleId':state['deleteRoleId'],'menuIds':[state['permissionIds']['system:user:delete']]})
        for length in [4,16,17,30]:
            name=(phase[0]+''.join(secrets.choice(string.digits) for _ in range(3))) if length==4 else (state['tag']+phase[0]+run_key).ljust(length,'b')
            assert len(name)==length
            secret=password()
            identifier=admin.call('/system/user/create','POST',{'username':name,'password':secret,'nickname':'身份边界'+str(length),'remark':state['tag']})
            state['accounts'].append({'id':identifier,'username':name});save(state)
            user=Api({'tenantId':state['tenantId'],'username':name,'password':secret},origin);apis.append(user)
            assert str(user.user_id)==str(identifier)
            token=user.token
            if length==17:
                admin.call('/system/user/delete?id='+str(identifier),'DELETE')
                expected='user#'+str(identifier)
            else:expected=name
            user.logout()
            count=audit_logout(identifier,expected)
            # 身份服务必须立即拒绝旧令牌；单独记录网关缓存行为，不能把后者伪写为通过。
            gateway_code=user.request('/system/auth/get-permission-info',token=token)['code']
            if phase=='candidate':assert gateway_code==401
            user.origin='http://127.0.0.1:48081/admin-api' if phase!='candidate' else CANDIDATE
            direct_code=user.request('/system/auth/get-permission-info',token=token)['code']
            user.origin=origin
            assert direct_code==401
            assert user.request('/system/auth/logout','POST',token=token)['code'] in (0,200)
            assert audit_logout(identifier,expected)==count
            report['accounts'].append({'usernameLength':length,'userId':identifier,'loginMatchesCreatedId':True,'deletedBeforeLogout':length==17,'logoutCode':0,'identityServiceOldTokenCode':direct_code,'gatewayOldTokenCode':gateway_code,'gatewayOldTokenImmediatelyRejected':gateway_code==401,'repeatLogoutAuditUnchanged':True,**count})
        self_name=(state['tag']+phase[0]+run_key+'s').ljust(17,'c')
        secret=password();identifier=admin.call('/system/user/create','POST',{'username':self_name,'password':secret,'nickname':'自删验收','remark':state['tag']})
        state['accounts'].append({'id':identifier,'username':self_name});save(state)
        admin.call('/system/permission/assign-user-role','POST',{'userId':identifier,'roleIds':[state['deleteRoleId']]})
        user=Api({'tenantId':state['tenantId'],'username':self_name,'password':secret},origin);apis.append(user)
        assert user.request('/system/auth/get-permission-info',tenant=2)['code']==403
        user.call('/system/user/delete?id='+str(identifier),'DELETE');user.logout()
        report['selfDelete']={**audit_logout(identifier,'user#'+str(identifier)),'logoutCode':0,'wrongTenantRejected':True}
        for username,pwd in [('x'*31,password()),('user_name',password()),('validuser','p'*17)]:
            assert admin.request('/system/auth/login','POST',{'username':username,'password':pwd})['code']==400
        report['invalidLengthCharactersAndPasswordRejected']=True
        report['longTenantAdminLoggedIn']=True
        (ROOT/(phase+'-acceptance.json')).write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
    finally:
        for api in reversed(apis):api.logout()

def cleanup():
    state=json.loads(STATE.read_text());admin=Api({**state['admin'],'tenantId':state['tenantId']});owner=platform()
    try:
        active=admin.call('/system/user/page?pageNo=1&pageSize=100')['list']
        allowed={str(row['id']) for row in state['accounts']}|{str(state['adminUserId'])}
        assert all(str(row['id']) in allowed for row in active)
        for row in active:
            if str(row['id'])!=str(state['adminUserId']):admin.call('/system/user/delete?id='+str(row['id']),'DELETE')
        admin.call('/system/role/delete?id='+str(state['deleteRoleId']),'DELETE')
        admin.call('/system/permission/assign-role-menu','POST',{'roleId':state['adminRoleId'],'menuIds':[state['permissionIds']['system:user:delete']]})
        admin.call('/system/user/delete?id='+str(state['adminUserId']),'DELETE');admin.logout()
        target=owner.call('/system/tenant/get?id='+str(state['tenantId']));assert target['name']==state['tag']
        update={key:target[key] for key in ('id','name','contactName','contactMobile','status','website','packageId','expireTime','accountCount') if key in target}
        update['status']=1;owner.call('/system/tenant/update','PUT',update)
        owner.call('/system/tenant/delete?id='+str(state['tenantId']),'DELETE');owner.call('/system/tenant-package/delete?id='+str(state['packageId']),'DELETE')
        for menu in reversed(state['menus']):owner.call('/system/menu/delete?id='+str(menu['id']),'DELETE')
        assert digest(owner.call('/system/menu/list'))==digest(state['before']['menus'])
        assert digest(owner.call('/system/tenant-package/page?pageNo=1&pageSize=100')['list'])==digest(state['before']['packages'])
        assert digest(owner.call('/system/tenant/get?id=2'))==digest(state['before']['tenant2'])
        old_ids={row.split('\t')[0] for row in state['before']['roles']}
        assert [row for row in role_snapshot() if row.split('\t')[0] in old_ids]==state['before']['roles']
        tenant=str(int(state['tenantId']))
        for table in ['wx_system_users','wx_system_oauth2_access_token','wx_system_oauth2_refresh_token']:
            assert sql('SELECT COUNT(*) FROM '+table+' WHERE tenant_id='+tenant+' AND deleted=0;')=='0'
        effective=sql('SELECT COUNT(*) FROM wx_system_role_menu rm JOIN wx_system_menu m ON m.id=rm.menu_id AND m.deleted=0 WHERE rm.deleted=0 AND rm.role_id='+str(state['adminRoleId'])+';');assert effective=='0'
        report={'temporaryTenantId':state['tenantId'],'testUsersDeleted':len(state['accounts'])+1,'temporaryRoleRemoved':True,'tenantAndPackageRemoved':True,'temporaryMenusRemoved':len(state['menus']),'originalTenant2MenusPackagesAndRoleGrantsUnchanged':True,'activeUsers':0,'activeAccessTokens':0,'activeRefreshTokens':0,'protectedSystemRoleRetained':True,'effectiveProtectedRoleMenus':0,'selfDeletedAdminLogoutCode':0,'auditRetained':True}
        (ROOT/'cleanup.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
    finally:admin.logout();owner.logout()

if __name__=='__main__':
    try:
        mode=sys.argv[1];assert mode in ['setup','candidate','formal','cleanup']
        setup() if mode=='setup' else cleanup() if mode=='cleanup' else accept(mode)
    except Exception as error:
        # 仅记录断言位置，避免调试时输出局部变量中的凭据。
        print(json.dumps({'failed':True,'type':type(error).__name__,'businessCode':str(error) if str(error).isdigit() else None,'locations':[{'function':frame.name,'line':frame.lineno} for frame in traceback.extract_tb(error.__traceback__)]}),file=sys.stderr)
        sys.exit(1)
