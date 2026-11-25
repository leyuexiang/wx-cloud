"""从原包替换已测认证类，提供隔离候选、授权后的逐节点切换及摘要保护回退。"""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import socket
import sys
import zipfile

ROOT=Path('/opt/wx/editor-test148/releases/gateway-auth-20261118')
spec=importlib.util.spec_from_file_location('resident','/opt/wx/editor-test148/releases/iot-m3-03a-20261114/connect-resident-iot.py')
r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
NODES=[('wx-editor-gateway-data08','wx-gateway-auth-check-a-1118',49525,12525),('wx-editor-gateway','wx-gateway-auth-check-b-1118',49522,12522)]

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def save(name,value):
    with os.fdopen(os.open(ROOT/name,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w',encoding='utf-8') as stream:json.dump(value,stream,indent=2)

def prepare():
    manifest=json.loads((ROOT/'patch.json').read_text())
    original=Path('/opt/wx/editor-test148/jars/gateway.jar');target=ROOT/'gateway.jar'
    assert sha(original)==manifest['baselineJarSha256']
    entry='BOOT-INF/classes/cn/iocoder/wx/gateway/filter/security/TokenAuthenticationFilter.class'
    patch=(ROOT/'TokenAuthenticationFilter.class').read_bytes()
    assert hashlib.sha256(patch).hexdigest()==manifest['classAfter']
    if not target.exists():
        with zipfile.ZipFile(original) as before,zipfile.ZipFile(target,'w') as after:
            assert hashlib.sha256(before.read(entry)).hexdigest()==manifest['classBefore']
            assert len(before.namelist())==len(set(before.namelist()))
            assert not any(n.startswith('META-INF/') and n.upper().endswith(('.SF','.RSA','.DSA')) for n in before.namelist())
            for item in before.infolist():after.writestr(item,patch if item.filename==entry else before.read(item.filename))
            after.comment=before.comment
    else:
        # 准备阶段中止后只能继续已经核对的相同补丁，禁止覆盖未知包。
        assert sha(target)==json.loads((ROOT/'package.json').read_text())['jarSha256']
    with zipfile.ZipFile(original) as before,zipfile.ZipFile(target) as after:
        assert before.namelist()==after.namelist()
        changed=[name for name in before.namelist() if before.read(name)!=after.read(name)]
        assert changed==[entry]
    if not (ROOT/'package.json').exists():save('package.json',{**manifest,'jarSha256':sha(target),'changedEntries':changed,'allOtherEntryContentsAndOrderIdentical':True})
    reports=[]
    for source,name,port,old_port in NODES:
        original_info=r.docker('GET','/containers/'+source+'/json')
        assert original_info['State']['Health']['Status']=='healthy'
        with socket.socket() as listener:listener.bind(('127.0.0.1',port))
        snapshot=ROOT/(source+'.private.json')
        if snapshot.exists():assert json.loads(snapshot.read_text())['Id']==original_info['Id']
        else:save(source+'.private.json',original_info)
        config=copy.deepcopy(original_info['Config']);config['Image']=original_info['Image'];config['Hostname']=''
        host=copy.deepcopy(original_info['HostConfig'])
        prefix='/opt/wx/editor-test148/jars/gateway.jar:/opt/wx/app.jar:'
        assert sum(bind.startswith(prefix) for bind in host['Binds'])==1
        host['Binds']=[str(target)+':/opt/wx/app.jar:'+bind[len(prefix):] if bind.startswith(prefix) else bind for bind in host['Binds']]
        host['RestartPolicy']={'Name':'no'}
        # 不注册候选节点，原静态路由和认证目标保持，避免影响已有流量。
        key='Cmd' if config['Cmd'] else 'Entrypoint'
        config[key]=[value for value in config[key] if not value.startswith(('--server.port=','--server.address='))]
        config[key]+=['--server.port='+str(port),'--server.address=127.0.0.1','--spring.cloud.nacos.discovery.enabled=false']
        # 现场旧 data08 探针误指另一网关，候选显式检查自身回环端口，不能沿用该错误。
        config['Healthcheck']['Test']=['CMD-SHELL','wget -q --spider http://127.0.0.1:'+str(port)+'/actuator/health || exit 1']
        config['HostConfig']=host
        r.docker('POST','/containers/create?name='+name,config);r.docker('POST','/containers/'+name+'/start')
        r.healthy(port,name)
        reports.append({'name':name,'port':port,'health':'UP','originalId':original_info['Id'],'originalStartedAt':original_info['State']['StartedAt']})
    report={'candidates':reports,'jarSha256':sha(target),'originalGatewaysRestarted':False,'formalRouteChanged':False}
    save('candidates.json',report);print(json.dumps(report))

def cleanup():
    report=json.loads((ROOT/'candidates.json').read_text());removed=[];formal=[]
    for source,name,port,old_port in NODES:
        original=json.loads((ROOT/(source+'.private.json')).read_text())
        current=r.docker('GET','/containers/'+source+'/json')
        deployed=ROOT/('deployment-'+('main' if source=='wx-editor-gateway' else 'data08')+'.json')
        if deployed.exists():
            manifest=json.loads(deployed.read_text())
            assert current['State']['Health']['Status']=='healthy'
            assert any(m['Source']==str(ROOT/'gateway.jar') and m['Destination']=='/opt/wx/app.jar' for m in current['Mounts'])
            assert sha(ROOT/'gateway.jar')==manifest['jarSha256']
            assert not r.docker('GET','/containers/'+manifest['backup']+'/json')['State']['Running']
            formal.append(source)
        else:assert current['Id']==original['Id'] and current['State']['StartedAt']==original['State']['StartedAt']
        info=r.docker('GET','/containers/'+name+'/json')
        assert any(m['Source']==str(ROOT/'gateway.jar') and m['Destination']=='/opt/wx/app.jar' for m in info['Mounts'])
        r.docker('POST','/containers/'+name+'/stop?t=20');r.docker('DELETE','/containers/'+name)
        removed.append(name)
    save('candidate-cleanup.json',{'removed':removed,'formalGatewaysDeployed':formal,'releaseAndRollbackInfoRetained':True})
    print('已核对正式入口及回退归属并回收两个候选')

def apply():
    # 仅在原入口切换获得授权后运行；每次只处理明确指定的一个网关。
    assert sys.argv[2] in ('main','data08')
    selected='wx-editor-gateway' if sys.argv[2]=='main' else 'wx-editor-gateway-data08'
    source,name,port,formal_port=next(row for row in NODES if row[0]==selected)
    baseline=json.loads((ROOT/(source+'.private.json')).read_text())
    package=json.loads((ROOT/'package.json').read_text())
    current=r.docker('GET','/containers/'+source+'/json')
    assert current['Id']==baseline['Id'] and current['State']['Health']['Status']=='healthy'
    assert sha(ROOT/'gateway.jar')==package['jarSha256']
    assert r.docker('GET','/containers/'+name+'/json')['State']['Health']['Status']=='healthy'
    config=copy.deepcopy(baseline['Config']);config['Image']=baseline['Image'];config['Hostname']=''
    host=copy.deepcopy(baseline['HostConfig'])
    prefix='/opt/wx/editor-test148/jars/gateway.jar:/opt/wx/app.jar:'
    assert sum(bind.startswith(prefix) for bind in host['Binds'])==1
    host['Binds']=[str(ROOT/'gateway.jar')+':/opt/wx/app.jar:'+bind[len(prefix):] if bind.startswith(prefix) else bind for bind in host['Binds']]
    # 同批纠正原 data08 健康探针误查 12522；检查自身端口才能作为回退依据。
    config['Healthcheck']['Test']=['CMD-SHELL','wget -q --spider http://127.0.0.1:'+str(formal_port)+'/actuator/health || exit 1']
    config['HostConfig']=host
    backup=source+'-before-auth-1118'
    r.docker('POST','/containers/'+source+'/stop?t=30');r.docker('POST','/containers/'+source+'/rename?name='+backup)
    created=False
    try:
        r.docker('POST','/containers/create?name='+source,config);created=True
        r.docker('POST','/containers/'+source+'/start');r.healthy(formal_port,source)
    except Exception:
        if created:r.docker('DELETE','/containers/'+source+'?force=true')
        r.docker('POST','/containers/'+backup+'/rename?name='+source);r.docker('POST','/containers/'+source+'/start')
        r.healthy(formal_port,source)
        raise RuntimeError('网关切换失败，已恢复本批原容器') from None
    save('deployment-'+sys.argv[2]+'.json',{'name':source,'port':formal_port,'health':'UP','jarSha256':package['jarSha256'],'sourceCommit':package['sourceCommit'],'backup':backup,'schemaMigration':False,'probeChecksOwnPort':True})
    print(json.dumps({'name':source,'health':'UP','port':formal_port}))

def rollback():
    assert sys.argv[2] in ('main','data08')
    report=json.loads((ROOT/('deployment-'+sys.argv[2]+'.json')).read_text())
    current=r.docker('GET','/containers/'+report['name']+'/json')
    assert any(m['Source']==str(ROOT/'gateway.jar') and m['Destination']=='/opt/wx/app.jar' for m in current['Mounts'])
    assert sha(ROOT/'gateway.jar')==report['jarSha256']
    r.docker('POST','/containers/'+report['name']+'/stop?t=30');r.docker('DELETE','/containers/'+report['name'])
    r.docker('POST','/containers/'+report['backup']+'/rename?name='+report['name']);r.docker('POST','/containers/'+report['name']+'/start')
    r.healthy(report['port'],report['name']);print('已恢复本批原网关')

assert sys.argv[1] in ('prepare','cleanup','apply','rollback')
globals()[sys.argv[1]]()
