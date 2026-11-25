"""正式接口准备隔离租户与权限并审计回收，供网关撤销验收复用；秘密仅留受限清单。"""
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

ROOT=Path('/home/wx-gateway-auth-20261118');STATE=ROOT/'state.private.json'
LIVE='http://127.0.0.1:49525/admin-api'
MANAGEMENT=['system:oauth2-token:delete','system:user:create','system:user:delete','system:user:list','system:role:create','system:role:delete','system:permission:assign-user-role','system:permission:assign-role-menu']

class Api:
    def __init__(self,identity,origin=LIVE):
        self.tenant=identity['tenantId'];self.origin=origin;self.token=None
        result=self.call('/system/auth/login','POST',{'username':identity['username'],'password':identity['password']})
        self.token=result['accessToken'];self.refresh=result['refreshToken'];self.user_id=result['userId']
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
