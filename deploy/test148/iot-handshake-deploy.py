"""专用物联网端点：先回环候选，后切换 data08；完整校验挂载、摘要和站点回退。"""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import re

ROOT=Path('/opt/wx/editor-test148/releases/iot-handshake-20261117')
SITE=Path('/www/server/panel/vhost/nginx/jtyf-editor-web148.conf')
NAME='wx-editor-dimensions-data08';CANDIDATE='wx-iot-handshake-check-1117';BACKUP=NAME+'-before-handshake-1117'
OLD_WEB='/home/wx-editor-web148/releases/publication-ui-20261117-web9057261/site'
NEW_WEB='/home/wx-editor-web148/releases/iot-handshake-20261117/site'
spec=importlib.util.spec_from_file_location('resident','/opt/wx/editor-test148/releases/iot-m3-03a-20261114/connect-resident-iot.py')
r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
docker=r.docker

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def save(name,value):
    path=ROOT/name;temporary=path.with_suffix('.pending')
    temporary.write_text(json.dumps(value,indent=2),encoding='utf-8');temporary.chmod(0o600);temporary.replace(path)
def body(original):
    config=copy.deepcopy(original['Config']);config['Image']=original['Image'];config['Hostname']=''
    host=copy.deepcopy(original['HostConfig'])
    mounts=[m for m in original['Mounts'] if m['Destination']=='/opt/wx/app.jar']
    assert len(mounts)==1 and not mounts[0]['RW']
    prefix=mounts[0]['Source']+':/opt/wx/app.jar:'
    assert sum(bind.startswith(prefix) for bind in host['Binds'])==1
    host['Binds']=[str(ROOT/'dimensions.jar')+':/opt/wx/app.jar:'+bind[len(prefix):] if bind.startswith(prefix) else bind for bind in host['Binds']]
    config['HostConfig']=host
    return config
def site_text(before):
    # 两个精确入口分别服务发布和 /dts-api 草稿客户端，旧共享代理保留。
    assert before.count('root '+OLD_WEB+';')==2 and 'location = /dimensions/ws/iot-state' not in before
    text=before.replace('root '+OLD_WEB+';','root '+NEW_WEB+';')
    # 当前运行网关只精确匹配旧路径；新入口直达本机三维过滤器，避免重启 Unity 正在使用的网关。
    for old,new,target in [('/dimensions/ws','/dimensions/ws/iot-state','http://127.0.0.1:48093/dimensions/ws/iot-state'),
        ('/dts-api/dimensions/ws','/dts-api/dimensions/ws/iot-state','http://127.0.0.1:48093/dimensions/ws/iot-state')]:
        marker='    location = '+old+' {'
        start=text.index(marker);end=text.index('\n    }',start)+6
        block=text[start:end]
        derived=block.replace(marker,'    location = '+new+' {',1)
        derived=re.sub(r'proxy_pass\s+[^;]+;', 'proxy_pass '+target+';',derived,count=1)
        if 'access_log off;' not in derived:derived=derived.replace('{\n','{\n        access_log off;\n',1)
        text=text[:end]+'\n'+derived+text[end:]
    return text
def nginx(text):
    pending=SITE.with_suffix('.iot-handshake.pending')
    pending.write_text(text,encoding='utf-8');pending.chmod(0o644);pending.replace(SITE)
    for args in [['nginx','-t'],['nginx','-s','reload']]:
        if subprocess.run(args,capture_output=True,timeout=15).returncode:raise RuntimeError('站点校验或重载失败')
def prepare():
    assert not (ROOT/'original.private.json').exists()
    manifest=json.loads((ROOT/'manifest.json').read_text())
    assert sha(ROOT/'dimensions.jar')==manifest['jarSha256'] and sha(ROOT/'web.tar.gz')==manifest['webSha256']
    original=docker('GET','/containers/'+NAME+'/json')
    mounts=[m for m in original['Mounts'] if m['Destination']=='/opt/wx/app.jar']
    assert sha(Path(mounts[0]['Source']))=='ad0c484fd1dec5cd9aafb09d0867cc3ceb3e298a5b3491db7c851f7f2f6bd031'
    before=SITE.read_text();after=site_text(before)
    save('original.private.json',original);save('site.private.json',{'before':before,'after':after})
    config=body(original)
    config['Cmd']=[value for value in config['Cmd'] if not value.startswith(('--server.port=','--server.address=','--wx.iot-state.listener-auto-startup=','--wx.websocket.sender-kafka.auto-startup='))]
    # 候选不争抢状态投影；独立 UUID 扇出消费组只发送本机匹配会话，便于真实容量对照。
    config['Cmd']+=['--server.port=49497','--server.address=127.0.0.1','--wx.iot-state.listener-auto-startup=false','--wx.websocket.sender-kafka.auto-startup=true']
    config['HostConfig']['RestartPolicy']={'Name':'no'}
    docker('POST','/containers/create?name='+CANDIDATE,config);docker('POST','/containers/'+CANDIDATE+'/start')
    r.healthy(49497,CANDIDATE)
    save('candidate.json',{'health':'UP','port':49497,**manifest})
    print(json.dumps({'health':'UP','port':49497,'residentChanged':False}))
def restart_candidate():
    manifest=json.loads((ROOT/'manifest.json').read_text());assert sha(ROOT/'dimensions.jar')==manifest['jarSha256']
    docker('POST','/containers/'+CANDIDATE+'/restart?t=20');r.healthy(49497,CANDIDATE)
    save('candidate.json',{'health':'UP','port':49497,**manifest})
    print('候选已重新启动，常驻保持')
def apply():
    original=json.loads((ROOT/'original.private.json').read_text());site=json.loads((ROOT/'site.private.json').read_text())
    assert docker('GET','/containers/'+NAME+'/json')['Id']==original['Id']
    assert SITE.read_text()==site['before'] and (Path(NEW_WEB)/'dts/index.html').is_file()
    manifest=json.loads((ROOT/'manifest.json').read_text())
    assert sha(ROOT/'dimensions.jar')==manifest['jarSha256']
    assert json.loads((ROOT/'candidate.json').read_text())['health']=='UP'
    docker('POST','/containers/'+CANDIDATE+'/stop?t=20')
    docker('POST','/containers/'+NAME+'/stop?t=30');docker('POST','/containers/'+NAME+'/rename?name='+BACKUP)
    created=False
    try:
        docker('POST','/containers/create?name='+NAME,body(original));created=True
        docker('POST','/containers/'+NAME+'/start');r.healthy(48093,NAME)
        nginx(site['after'])
    except Exception:
        if created:docker('DELETE','/containers/'+NAME+'?force=true')
        docker('POST','/containers/'+BACKUP+'/rename?name='+NAME);docker('POST','/containers/'+NAME+'/start')
        nginx(site['before']);raise RuntimeError('切换失败，已恢复原应用与站点') from None
    manifest=json.loads((ROOT/'manifest.json').read_text());report={**manifest,'health':'UP','backupContainer':BACKUP,
        'siteSha256':sha(SITE),'previousJarSha256':'ad0c484fd1dec5cd9aafb09d0867cc3ceb3e298a5b3491db7c851f7f2f6bd031'}
    save('deployment.json',report);print(json.dumps(report))
def rollback():
    report=json.loads((ROOT/'deployment.json').read_text());site=json.loads((ROOT/'site.private.json').read_text())
    current=docker('GET','/containers/'+NAME+'/json')
    assert any(m['Source']==str(ROOT/'dimensions.jar') and m['Destination']=='/opt/wx/app.jar' for m in current['Mounts'])
    assert sha(ROOT/'dimensions.jar')==report['jarSha256'] and sha(SITE)==report['siteSha256']
    docker('POST','/containers/'+NAME+'/stop?t=30');docker('DELETE','/containers/'+NAME)
    docker('POST','/containers/'+BACKUP+'/rename?name='+NAME);docker('POST','/containers/'+NAME+'/start')
    r.healthy(48093,NAME);nginx(site['before']);print('原应用与站点已恢复，审计保留')
parser=argparse.ArgumentParser();parser.add_argument('action',choices=['prepare','restart_candidate','apply','rollback'])
globals()[parser.parse_args().action]()
