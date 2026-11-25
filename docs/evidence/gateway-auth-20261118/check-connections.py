"""复用已有客户端检查网关旧连接与专用端点；只验证协议兼容，不操作 Unity。"""
import importlib.util
import json
from pathlib import Path
import sys
from urllib.parse import urlencode

sys.path.insert(0,str(Path(__file__).with_name('websocket-client.zip')))
import websocket
spec=importlib.util.spec_from_file_location('fixture',str(Path(__file__).with_name('auth-fixture.py')))
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
identities=json.loads(Path('/opt/wx/editor-test148/test-identities.json').read_text())
report={'legacyGateways':[],'dedicated':{}};sessions=[]
cert='/home/wx-editor-web148/releases/iot-web-20261111/server.crt'

def connect(base,path,token=None,tenant='2'):
    query={'tenant-id':tenant}
    if token:query['token']=token
    return websocket.create_connection(base+path+'?'+urlencode(query),timeout=8,sslopt={'ca_certs':cert},http_proxy_host=None)

def denied(name,token=None,tenant='2',expected=401):
    try:
        sock=connect('wss://192.168.10.148:9447','/dimensions/ws/iot-state',token,tenant)
        sock.close();raise AssertionError('专用入口意外接受握手')
    except websocket.WebSocketBadStatusException as error:
        assert error.status_code==expected
        report['dedicated'][name]=error.status_code

try:
    for role in ['tenant_admin','viewer']:
        api=f.Api(next(row for row in identities if row['role']==role and row['tenantId']==2),'http://127.0.0.1:12525/admin-api');sessions.append(api)
    admin,viewer=sessions
    for port in [12522,12525]:
        sock=connect('ws://127.0.0.1:'+str(port),'/dimensions/ws',admin.token)
        try:sock.send('ping');assert sock.recv()=='pong'
        finally:sock.close()
        report['legacyGateways'].append({'port':port,'authenticatedHeartbeat':True})
    denied('anonymous');denied('invalidToken','invalid-gateway-regression-token')
    denied('noPermission',viewer.token,expected=403);denied('wrongTenant',admin.token,'1',403)
    sock=connect('wss://192.168.10.148:9447','/dimensions/ws/iot-state',admin.token)
    try:
        sock.send('ping');assert sock.recv()=='pong'
        sock.send(json.dumps({'type':'old-task','content':'{}'}));sock.send('ping');assert sock.recv()=='pong'
    finally:sock.close()
    report['dedicated']['authorizedHeartbeat']=True;report['dedicated']['unknownMessageCompatibility']=True
    sock=connect('wss://192.168.10.148:9447','/dts-api/dimensions/ws/iot-state',admin.token);sock.close()
    report['dedicated']['draftProxyAuthorized']=True
    report['passed']=True
    Path('/home/wx-gateway-auth-formal-20261118/connections.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
except Exception as error:
    print(json.dumps({'failed':True,'type':type(error).__name__}),file=sys.stderr);sys.exit(1)
finally:
    for api in sessions:api.logout()
