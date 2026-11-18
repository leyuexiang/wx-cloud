"""核对本批部署与原业务边界后，精确回收候选及临时秘密，保留回退材料。"""
import hashlib
import importlib.util
import json
from pathlib import Path
import stat
import subprocess

ROOT=Path('/opt/wx/editor-test148/releases/auth-consistency-20261118')
WORK=Path('/home/wx-auth-consistency-20261118')
TOOLS=Path('/home/wx-auth-consistency-tools-20261118')
spec=importlib.util.spec_from_file_location('accept',str(TOOLS/'accept-auth.py'))
a=importlib.util.module_from_spec(spec);spec.loader.exec_module(a)

def inspect(name):
    return json.loads(subprocess.check_output(['docker','inspect',name]))[0]

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()

original=json.loads((ROOT/'original.private.json').read_text())
current=inspect('wx-editor-system');backup=inspect('wx-editor-system-before-auth-1118')
assert current['State']['Health']['Status']=='healthy' and not backup['State']['Running']
assert backup['Id']==original['Id']
expected_config=dict(original['Config']);expected_config['Image']=original['Image']
assert current['Config']==expected_config and current['Image']==original['Image']
expected=dict(original['HostConfig'])
expected['Binds']=[str(ROOT/'system.jar')+':/opt/wx/app.jar:ro' if bind=='/opt/wx/editor-test148/jars/system.jar:/opt/wx/app.jar:ro' else bind for bind in expected['Binds']]
assert current['HostConfig']==expected
assert sha(ROOT/'system.jar')==json.loads((ROOT/'package.json').read_text())['jarSha256']
others=[]
for name in ['wx-editor-gateway','wx-editor-gateway-data08','wx-editor-dimensions-data08']:
    info=inspect(name);assert info['State']['Health']['Status']=='healthy'
    if name.startswith('wx-editor-gateway'):assert info['State']['StartedAt']<current['State']['StartedAt']
    item={'name':name,'id':info['Id'],'startedAt':info['State']['StartedAt'],'health':'healthy'}
    if name=='wx-editor-dimensions-data08':
        jar=next(m['Source'] for m in info['Mounts'] if m['Destination']=='/opt/wx/app.jar')
        item['jarPath']=jar;item['jarSha256']=sha(jar)
        # 其他任务可能并行发布三维；仅记录实况，禁止用旧摘要覆盖其发布。
        item['matchesPreviousIoTRelease']=item['jarSha256']=='c92ccb8bc71bf32dec200f0ebcf2cc38203fe02141e712bac9fcffd0fc4a56fb'
    others.append(item)
site=sha('/www/server/panel/vhost/nginx/jtyf-editor-web148.conf')
scene_count=a.sql('SELECT COUNT(*) FROM wx_editor_data08.wx_3d_scene WHERE tenant_id=2 AND deleted=0;')
publication=a.sql("SELECT revision_no,current_flag,status FROM wx_editor_data08.wx_3d_scene_publication WHERE tenant_id=2 AND publication_id='PUB-2122542758537261058';")
assert publication.split('\t')[0]=='5'
cleanup=json.loads((WORK/'cleanup.json').read_text())
assert cleanup['activeUsers']==cleanup['activeAccessTokens']==cleanup['activeRefreshTokens']==0
candidate=inspect('wx-auth-consistency-check-1118')
assert not candidate['State']['Running']
assert any(m['Source']==str(ROOT/'system.jar') and m['Destination']=='/opt/wx/app.jar' for m in candidate['Mounts'])
subprocess.run(['docker','rm',candidate['Id']],check=True,capture_output=True)
# 仅删除本批已核实的常规文件；不递归删除、不跟随符号链接。
private=WORK/'state.private.json'
assert not private.is_symlink() and private.is_file() and stat.S_IMODE(private.stat().st_mode)==0o600
assert TOOLS.resolve()==TOOLS and TOOLS.is_dir() and not TOOLS.is_symlink()
entries=list(TOOLS.iterdir())
assert {p.name for p in entries}=={'accept-auth.py'} and all(p.is_file() and not p.is_symlink() for p in entries)
private.unlink()
for path in entries:path.unlink()
TOOLS.rmdir()
report={'health':'UP','systemId':current['Id'],'systemStartedAt':current['State']['StartedAt'],'systemConfigOnlyImageReferencePinnedToSameId':True,'hostConfigOnlyJarBindChanged':True,'jarSha256':sha(ROOT/'system.jar'),'backupStopped':True,'backupId':backup['Id'],'otherServices':others,'siteSha256':site,'sceneCount':int(scene_count),'originalPublication':'PUB-2122542758537261058','publicationRevision':5,'publicationCurrentFlagAndStatus':publication.split('\t')[1:],'candidateRemoved':True,'temporaryPrivateStateRemoved':True,'temporaryToolsRemoved':True,'releaseAndRollbackConfigRetained':True,'sshForwardCreated':False}
(WORK/'final-health-cleanup.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report))
