"""身份服务两类补丁：隔离候选、摘要保护切换与完整容器回退。"""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import socket
import sys
spec=importlib.util.spec_from_file_location('resident','/opt/wx/editor-test148/releases/iot-m3-03a-20261114/connect-resident-iot.py')
r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
docker=r.docker
ROOT=Path('/opt/wx/editor-test148/releases/auth-consistency-20261118')
NAME='wx-editor-system';CANDIDATE='wx-auth-consistency-check-1118';BACKUP=NAME+'-before-auth-1118'

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def save(name,value):
    with os.fdopen(os.open(ROOT/name,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600),'w',encoding='utf-8') as stream:json.dump(value,stream,indent=2)
def body(original,candidate=False):
    package=json.loads((ROOT/'package.json').read_text());assert sha(ROOT/'system.jar')==package['jarSha256']
    config=copy.deepcopy(original['Config']);config['Image']=original['Image'];config['Hostname']=''
    host=copy.deepcopy(original['HostConfig'])
    mounts=[mount for mount in original['Mounts'] if mount['Destination']=='/opt/wx/app.jar']
    assert len(mounts)==1 and not mounts[0]['RW']
    prefix=mounts[0]['Source']+':/opt/wx/app.jar:'
    assert sum(bind.startswith(prefix) for bind in host['Binds'])==1
    host['Binds']=[str(ROOT/'system.jar')+':/opt/wx/app.jar:'+bind[len(prefix):] if bind.startswith(prefix) else bind for bind in host['Binds']]
    if candidate:
        assert config['Entrypoint'][0]=='java' and '/opt/wx/app.jar' in config['Entrypoint']
        assert not config['Cmd']
        prefixes=('--server.port=','--server.address=')
        config['Entrypoint']=[value for value in config['Entrypoint'] if not value.startswith(prefixes)]
        config['Entrypoint']+=['--server.port=49581','--server.address=127.0.0.1','--xxl.job.enabled=false','--spring.kafka.listener.auto-startup=false','--spring.cloud.nacos.discovery.enabled=false']
        host['RestartPolicy']={'Name':'no'}
        # 容器自身健康探针也检查候选，而不是误读仍运行的原节点。
        probe=config['Healthcheck']['Test'];assert any('48081/actuator/health' in part for part in probe)
        config['Healthcheck']['Test']=[part.replace('48081/actuator/health','49581/actuator/health') for part in probe]
    config['HostConfig']=host
    return config
def prepare():
    original=docker('GET','/containers/'+NAME+'/json');package=json.loads((ROOT/'package.json').read_text())
    assert original['State']['Health']['Status']=='healthy'
    assert any(m['Source']=='/opt/wx/editor-test148/jars/system.jar' and m['Destination']=='/opt/wx/app.jar' for m in original['Mounts'])
    assert sha('/opt/wx/editor-test148/jars/system.jar')==package['baselineJarSha256']
    with socket.socket() as listener:listener.bind(('127.0.0.1',49581))
    config=body(original,True);save('original.private.json',original)
    docker('POST','/containers/create?name='+CANDIDATE,config);docker('POST','/containers/'+CANDIDATE+'/start')
    r.healthy(49581,CANDIDATE)
    report={'candidate':CANDIDATE,'port':49581,'health':'UP','originalContainerUnchanged':True,'jarSha256':package['jarSha256']}
    save('candidate.json',report);print(json.dumps(report))
def apply():
    original=json.loads((ROOT/'original.private.json').read_text());package=json.loads((ROOT/'package.json').read_text())
    assert docker('GET','/containers/'+NAME+'/json')['Id']==original['Id']
    assert json.loads((ROOT/'candidate.json').read_text())['health']=='UP'
    docker('POST','/containers/'+CANDIDATE+'/stop?t=20')
    docker('POST','/containers/'+NAME+'/stop?t=30');docker('POST','/containers/'+NAME+'/rename?name='+BACKUP)
    created=False
    try:
        docker('POST','/containers/create?name='+NAME,body(original));created=True
        docker('POST','/containers/'+NAME+'/start');r.healthy(48081,NAME)
    except Exception:
        if created:docker('DELETE','/containers/'+NAME+'?force=true')
        docker('POST','/containers/'+BACKUP+'/rename?name='+NAME);docker('POST','/containers/'+NAME+'/start');r.healthy(48081,NAME)
        raise RuntimeError('身份服务切换失败，已恢复原容器') from None
    report={**package,'health':'UP','backupContainer':BACKUP,'gatewayRestarted':False,'schemaMigration':False}
    save('deployment.json',report);print(json.dumps(report))
def rollback():
    report=json.loads((ROOT/'deployment.json').read_text());current=docker('GET','/containers/'+NAME+'/json')
    assert any(m['Source']==str(ROOT/'system.jar') and m['Destination']=='/opt/wx/app.jar' for m in current['Mounts'])
    assert sha(ROOT/'system.jar')==report['jarSha256']
    docker('POST','/containers/'+NAME+'/stop?t=30');docker('DELETE','/containers/'+NAME)
    docker('POST','/containers/'+BACKUP+'/rename?name='+NAME);docker('POST','/containers/'+NAME+'/start');r.healthy(48081,NAME)
    print('已恢复本批前身份服务；原三维、网页、网关及业务数据保持')
assert sys.argv[1] in ('prepare','apply','rollback')
globals()[sys.argv[1]]()
