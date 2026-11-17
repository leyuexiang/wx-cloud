"""分发增量先验证回环候选，再切换测试三维应用；保留原容器供回退。"""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path

root=Path('/opt/wx/editor-test148/releases/iot-fanout-20261117')
spec=importlib.util.spec_from_file_location('resident','/opt/wx/editor-test148/releases/iot-m3-03a-20261114/connect-resident-iot.py')
resident=importlib.util.module_from_spec(spec);spec.loader.exec_module(resident)
docker=resident.docker
name='wx-editor-dimensions-data08';candidate='wx-iot-fanout-check-1117';backup=name+'-before-fanout-1117'

def body(original):
    config=copy.deepcopy(original['Config']);config['Image']=original['Image'];config['Hostname']=''
    host=copy.deepcopy(original['HostConfig'])
    mounts=[m for m in original['Mounts'] if m['Destination']=='/opt/wx/app.jar']
    assert len(mounts)==1 and not mounts[0]['RW']
    prefix=mounts[0]['Source']+':/opt/wx/app.jar:'
    assert sum(value.startswith(prefix) for value in host['Binds'])==1
    host['Binds']=[str(root/'dimensions.jar')+':/opt/wx/app.jar:'+value[len(prefix):] if value.startswith(prefix) else value for value in host['Binds']]
    config['Cmd']=[value for value in config['Cmd'] if not value.startswith('--wx.websocket.sender-kafka.max-pending=')]
    config['Cmd']+=['--wx.websocket.sender-kafka.max-pending=1024']
    config['HostConfig']=host
    return config

def prepare():
    manifest=json.loads((root/'manifest.json').read_text())
    assert hashlib.sha256((root/'dimensions.jar').read_bytes()).hexdigest()==manifest['dimensionsSha256']
    original=docker('GET','/containers/'+name+'/json')
    resident.save(root/'original.private.json',original)
    config=body(original)
    config['Cmd']=[value for value in config['Cmd'] if not value.startswith(('--server.port=','--server.address=','--wx.iot-state.listener-auto-startup=','--wx.websocket.sender-kafka.auto-startup='))]
    config['Cmd']+=['--server.port=49497','--server.address=127.0.0.1','--wx.iot-state.listener-auto-startup=false','--wx.websocket.sender-kafka.auto-startup=false']
    config['HostConfig']['RestartPolicy']={'Name':'no'}
    docker('POST','/containers/create?name='+candidate,config);docker('POST','/containers/'+candidate+'/start')
    resident.healthy(49497,candidate)
    resident.save(root/'candidate.json',{'health':'UP',**manifest})
    print('回环候选健康，遥测及定向消费未启动')

def apply():
    original=json.loads((root/'original.private.json').read_text())
    assert docker('GET','/containers/'+name+'/json')['Id']==original['Id']
    assert json.loads((root/'candidate.json').read_text())['health']=='UP'
    docker('POST','/containers/'+candidate+'/stop?t=20')
    docker('POST','/containers/'+name+'/stop?t=30');docker('POST','/containers/'+name+'/rename?name='+backup)
    created=False
    try:
        docker('POST','/containers/create?name='+name,body(original));created=True
        docker('POST','/containers/'+name+'/start');resident.healthy(48093,name)
    except Exception:
        if created:docker('DELETE','/containers/'+name+'?force=true')
        docker('POST','/containers/'+backup+'/rename?name='+name);docker('POST','/containers/'+name+'/start')
        resident.healthy(48093,name)
        raise RuntimeError('切换失败，已恢复原三维应用') from None
    report={'health':'UP','backupContainer':backup,'maxPending':1024,'staticSiteChanged':False,'databaseMigration':False,**json.loads((root/'manifest.json').read_text())}
    resident.save(root/'deployment.json',report)
    docker('DELETE','/containers/'+candidate)
    print(json.dumps(report))

def rollback():
    current=docker('GET','/containers/'+name+'/json')
    report=json.loads((root/'deployment.json').read_text())
    mounts=[m for m in current['Mounts'] if m['Destination']=='/opt/wx/app.jar']
    assert len(mounts)==1 and mounts[0]['Source'] in [str(root/'dimensions.jar'),str(root/'dimensions-logging.jar')]
    assert hashlib.sha256(Path(mounts[0]['Source']).read_bytes()).hexdigest()==report['dimensionsSha256']
    docker('POST','/containers/'+name+'/stop?t=30');docker('POST','/containers/'+name+'/rename?name='+name+'-fanout-rolled-back')
    docker('POST','/containers/'+backup+'/rename?name='+name);docker('POST','/containers/'+name+'/start')
    resident.healthy(48093,name)
    print('原三维服务已恢复')

parser=argparse.ArgumentParser();parser.add_argument('action',choices=['prepare','apply','rollback'])
globals()[parser.parse_args().action]()
