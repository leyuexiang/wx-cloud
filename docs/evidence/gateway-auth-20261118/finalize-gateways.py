"""核对本批正式网关配置、回退及原场景，然后精确回收测试秘密和工具。"""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import stat
import subprocess

ROOT=Path('/opt/wx/editor-test148/releases/gateway-auth-20261118')
TOOLS=Path('/home/wx-gateway-auth-tools-20261118')
spec=importlib.util.spec_from_file_location('fixture',str(TOOLS/'auth-fixture.py'))
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def inspect(name):return json.loads(subprocess.check_output(['docker','inspect',name]))[0]

package=json.loads((ROOT/'package.json').read_text());assert sha(ROOT/'gateway.jar')==package['jarSha256']
nodes=[]
for key,source in [('main','wx-editor-gateway'),('data08','wx-editor-gateway-data08')]:
    baseline=json.loads((ROOT/(source+'.private.json')).read_text())
    report=json.loads((ROOT/('deployment-'+key+'.json')).read_text())
    current=inspect(source);backup=inspect(report['backup'])
    assert current['State']['Health']['Status']=='healthy' and backup['Id']==baseline['Id'] and not backup['State']['Running']
    expected=copy.deepcopy(baseline['Config']);expected['Image']=baseline['Image'];expected['Healthcheck']['Test']=['CMD-SHELL','wget -q --spider http://127.0.0.1:'+str(report['port'])+'/actuator/health || exit 1']
    assert current['Config']==expected and current['Image']==baseline['Image']
    host=copy.deepcopy(baseline['HostConfig']);prefix='/opt/wx/editor-test148/jars/gateway.jar:/opt/wx/app.jar:'
    host['Binds']=[str(ROOT/'gateway.jar')+':/opt/wx/app.jar:'+item[len(prefix):] if item.startswith(prefix) else item for item in host['Binds']]
    assert current['HostConfig']==host
    assert stat.S_IMODE((ROOT/(source+'.private.json')).stat().st_mode)==0o600
    nodes.append({'name':source,'port':report['port'],'id':current['Id'],'startedAt':current['State']['StartedAt'],'health':'healthy','sameImageId':True,'configOnlyImageReferenceAndOwnPortProbeChanged':True,'hostConfigOnlyJarBindChanged':True,'backup':report['backup'],'backupStopped':True})
scene_count=f.sql('SELECT COUNT(*) FROM wx_editor_data08.wx_3d_scene WHERE tenant_id=2 AND deleted=0;')
publication=f.sql("SELECT revision_no,current_flag,status FROM wx_editor_data08.wx_3d_scene_publication WHERE tenant_id=2 AND publication_id='PUB-2122542758537261058';")
assert scene_count=='3' and publication=='5\t1\tPUBLISHED'
site=sha('/www/server/panel/vhost/nginx/jtyf-editor-web148.conf')
assert site=='c41d7400f759adec56012e38178d9e0a062a7e5541779f57be1653c4f6374a72'
for directory in ['/home/wx-gateway-auth-20261118','/home/wx-gateway-auth-formal-20261118']:
    path=Path(directory);report=json.loads((path/'cleanup.json').read_text())
    assert report['activeUsers']==report['activeAccessTokens']==report['activeRefreshTokens']==0
    secret=path/'state.private.json'
    assert not secret.is_symlink() and secret.is_file() and stat.S_IMODE(secret.stat().st_mode)==0o600
# 所有路径先核对，再删除精确的本批文件；不递归、不跟随符号链接。
assert TOOLS.resolve()==TOOLS and not TOOLS.is_symlink()
entries=list(TOOLS.iterdir())
assert {p.name for p in entries}=={'auth-fixture.py','verify-gateway-auth.py','check-connections.py','websocket-client.zip'}
assert all(p.is_file() and not p.is_symlink() for p in entries)
for directory in ['/home/wx-gateway-auth-20261118','/home/wx-gateway-auth-formal-20261118']:(Path(directory)/'state.private.json').unlink()
for path in entries:path.unlink()
TOOLS.rmdir()
report={'nodes':nodes,'sourceCommit':package['sourceCommit'],'jarSha256':package['jarSha256'],'siteSha256':site,'originalTenant2SceneCount':3,'originalPublication':'PUB-2122542758537261058','publicationRevision':5,'publicationStillCurrentAndPublished':True,'temporaryPrivateStatesRemoved':2,'temporaryToolsRemoved':True,'candidateContainersRemoved':True,'releaseAndRollbackInfoRetained':True,'sshForwardCreated':False,'schemaMigration':False}
(ROOT/'final-health-cleanup.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
