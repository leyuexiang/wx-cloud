"""将已验证的接入程序切换到测试常驻实例，原程序和配置以容器保留回退。"""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
root=Path('/opt/wx/editor-test148/releases/iot-concurrency-20261117')
spec=importlib.util.spec_from_file_location('resident','/opt/wx/editor-test148/releases/iot-m3-03a-20261114/connect-resident-iot.py')
resident=importlib.util.module_from_spec(spec);spec.loader.exec_module(resident)
docker=resident.docker
name='wx-editor-iot-data08';backup=name+'-before-concurrency-1117'
def apply():
    manifest=json.loads((root/'manifest.json').read_text())
    assert hashlib.sha256((root/'iot.jar').read_bytes()).hexdigest()==manifest['jarSha256']
    original=docker('GET','/containers/'+name+'/json');resident.save(root/'original.private.json',original)
    config=copy.deepcopy(original['Config']);config['Image']=original['Image'];config['Hostname']=''
    host=copy.deepcopy(original['HostConfig'])
    mounts=[m for m in original['Mounts'] if m['Destination']=='/opt/wx/app.jar']
    assert len(mounts)==1 and not mounts[0]['RW']
    prefix=mounts[0]['Source']+':/opt/wx/app.jar:'
    assert sum(value.startswith(prefix) for value in host['Binds'])==1
    host['Binds']=[str(root/'iot.jar')+':/opt/wx/app.jar:'+value[len(prefix):] if value.startswith(prefix) else value for value in host['Binds']]
    config['HostConfig']=host
    config['Cmd']=[value for value in config['Cmd'] if not value.startswith(('--wx.iot.mqtt.receive-concurrency=','--spring.datasource.dynamic.druid.max-active='))]
    config['Cmd']+=['--wx.iot.mqtt.receive-concurrency=16','--spring.datasource.dynamic.druid.max-active=24']
    docker('POST','/containers/'+name+'/stop?t=30');docker('POST','/containers/'+name+'/rename?name='+backup)
    created=False
    try:
        docker('POST','/containers/create?name='+name,config);created=True
        docker('POST','/containers/'+name+'/start');resident.healthy(48096,name)
    except Exception:
        if created:docker('DELETE','/containers/'+name+'?force=true')
        docker('POST','/containers/'+backup+'/rename?name='+name);docker('POST','/containers/'+name+'/start');resident.healthy(48096,name)
        raise RuntimeError('接入切换失败，已恢复原实例') from None
    report={**manifest,'health':'UP','backupContainer':backup,'receiveConcurrency':16,'receiveQueueCapacity':256,'receiveMaxInFlight':16,'jdbcMaxActive':24,'originalConnectorAndBrokerPreserved':True,'databaseMigration':False}
    resident.save(root/'deployment.json',report);print(json.dumps(report))
def rollback():
    current=docker('GET','/containers/'+name+'/json')
    assert any(m['Destination']=='/opt/wx/app.jar' and m['Source']==str(root/'iot.jar') for m in current['Mounts'])
    report=json.loads((root/'deployment.json').read_text())
    assert hashlib.sha256((root/'iot.jar').read_bytes()).hexdigest()==report['jarSha256']
    docker('POST','/containers/'+name+'/stop?t=30');docker('POST','/containers/'+name+'/rename?name='+name+'-concurrency-rolled-back')
    docker('POST','/containers/'+backup+'/rename?name='+name);docker('POST','/containers/'+name+'/start');resident.healthy(48096,name)
    print('原接入应用及连接配置已恢复')
parser=argparse.ArgumentParser();parser.add_argument('action',choices=['apply','rollback']);globals()[parser.parse_args().action]()
