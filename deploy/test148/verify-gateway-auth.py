"""真实双网关撤销验收；临时身份经受限夹具创建，输出不含任何令牌。"""
from concurrent.futures import ThreadPoolExecutor
import importlib.util
import json
import math
from pathlib import Path
import sys
import time
import traceback
from urllib.parse import quote
from uuid import uuid4

fixture_path=Path(__file__).with_name('auth-fixture.py')
if not fixture_path.exists():
    # 仓库保留可审计夹具，部署到独立工具目录时仍优先复用同目录副本。
    fixture_path=Path(__file__).resolve().parents[2]/'docs/evidence/gateway-auth-20261118/auth-fixture.py'
spec=importlib.util.spec_from_file_location('fixture',str(fixture_path))
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
A='http://127.0.0.1:49525/admin-api';B='http://127.0.0.1:49522/admin-api';OLD='http://127.0.0.1:12525/admin-api'
PERMISSION='/system/auth/get-permission-info'
FORMAL='--formal' in sys.argv
if FORMAL:
    # 正式验收使用另一套隔离租户与受限清单，不覆盖候选轮的失败或成功证据。
    A='http://127.0.0.1:12525/admin-api';B='http://127.0.0.1:12522/admin-api'
    f.ROOT=Path('/home/wx-gateway-auth-formal-20261118');f.STATE=f.ROOT/'state.private.json'
    f.LIVE=A;f.Api.__init__.__defaults__=(A,)

def run():
    state=json.loads(f.STATE.read_text());admin=f.Api({**state['admin'],'tenantId':state['tenantId']},A);sessions=[]
    report={'tenantId':state['tenantId'],'phase':'formal' if FORMAL else 'candidate','gatewayPorts':[12525,12522] if FORMAL else [49525,49522],'formalGatewaysChanged':FORMAL,'cases':[]}
    try:
        state['adminUserId']=admin.user_id
        state['adminRoleId']=next(row['id'] for row in admin.call('/system/role/list-all-simple') if row['code']=='tenant_admin');f.save(state)
        if not state['deleteRoleId']:
            state['deleteRoleId']=admin.call('/system/role/create','POST',{'name':'网关撤销临时角色','code':state['tag']+'delete','sort':1,'status':0});f.save(state)

        def create(label):
            name=state['tag']+uuid4().hex[:6];secret=f.password()
            identifier=admin.call('/system/user/create','POST',{'username':name,'password':secret,'nickname':'网关撤销'+label,'remark':state['tag']})
            state['accounts'].append({'id':identifier,'username':name});f.save(state)
            user=f.Api({'tenantId':state['tenantId'],'username':name,'password':secret},A);sessions.append(user)
            assert str(user.user_id)==str(identifier)
            return user

        for action in ['logout','administratorRevoke']:
            user=create(action);token=user.token;refresh=user.refresh
            for origin in [A,B,OLD]:
                user.origin=origin;assert user.request(PERMISSION)['code'] in (0,200)
            assert user.request(PERMISSION,tenant=2)['code']==403
            user.origin=A
            if action=='logout':user.logout()
            else:
                admin.origin=B
                admin.call('/system/oauth2-token/delete?accessToken='+quote(token,safe=''),'DELETE');admin.origin=A
            codes=[]
            for origin in [A,B]:
                user.origin=origin;code=user.request(PERMISSION,token=token)['code'];assert code==401;codes.append(code)
            # 保留原正式网关的差异；该轮明确不切换它。
            user.origin=OLD;old_code=user.request(PERMISSION,token=token)['code']
            user.origin=A
            refresh_code=user.request('/system/auth/refresh-token?refreshToken='+quote(refresh,safe=''),'POST',token='')['code']
            assert refresh_code not in (0,200)
            assert user.request('/system/auth/logout','POST',token=token)['code'] in (0,200)
            user.token=None
            report['cases'].append({'action':action,'createdUserId':user.user_id,'bothNodesWarmed':True,'postRevocationCodes':codes,'wrongTenantRejected':True,'oldRefreshRejected':True,'refreshBusinessCode':refresh_code,'repeatLogoutSucceeded':True,'comparisonGateway12525OldTokenCode':old_code})

        # 有界小样本记录逐请求认证成本，不将本机微测写成正式容量验收。
        user=create('latency');samples=[]
        def measure(index):
            started=time.perf_counter()
            # 每线程独立客户端属性，令牌只在内存中共享读取，不共享可变 origin。
            client=object.__new__(f.Api);client.origin=A if index%2==0 else B;client.tenant=user.tenant;client.token=user.token
            code=client.request(PERMISSION)['code'];assert code in (0,200)
            return (time.perf_counter()-started)*1000
        started=time.perf_counter()
        with ThreadPoolExecutor(max_workers=4) as pool:samples=list(pool.map(measure,range(100)))
        samples.sort()
        report['latencySmoke']={'location':'test server loopback','requests':100,'concurrency':4,'wallSeconds':round(time.perf_counter()-started,3),'p50Ms':round(samples[49],3),'p95Ms':round(samples[94],3),'maxMs':round(samples[-1],3),'failed':0,'productionCapacityClaim':False}
        user.logout()
        report['passed']=True
        (f.ROOT/'acceptance.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
    finally:
        for user in reversed(sessions):user.origin=A;user.logout()
        admin.origin=A;admin.logout()

try:
    mode=sys.argv[1];assert mode in ('setup','run','cleanup')
    f.setup() if mode=='setup' else f.cleanup() if mode=='cleanup' else run()
except Exception as error:
    print(json.dumps({'failed':True,'type':type(error).__name__,'businessCode':str(error) if str(error).isdigit() else None,'locations':[{'function':frame.name,'line':frame.lineno} for frame in traceback.extract_tb(error.__traceback__)]}),file=sys.stderr)
    sys.exit(1)
