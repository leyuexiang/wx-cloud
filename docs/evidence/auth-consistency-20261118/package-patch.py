"""严格校验现包只变两个类，依赖、资源、条目顺序和其他字节内容保持。"""
import hashlib
import json
from pathlib import Path
import zipfile
root=Path('/opt/wx/editor-test148/releases/auth-consistency-20261118')
original=Path('/opt/wx/editor-test148/jars/system.jar');manifest=json.loads((root/'patch-manifest.json').read_text())
assert hashlib.sha256(original.read_bytes()).hexdigest()==manifest['baselineJarSha256']
target=root/'system.jar';assert not target.exists()
with zipfile.ZipFile(original) as before, zipfile.ZipFile(root/'auth-classes.zip') as patches, zipfile.ZipFile(target,'w') as after:
    assert set(patches.namelist())==set(manifest['classes'])
    assert len(before.namelist())==len(set(before.namelist()))
    assert not any(name.startswith('META-INF/') and name.upper().endswith(('.SF','.RSA','.DSA')) for name in before.namelist())
    for entry in before.infolist():
        data=before.read(entry.filename)
        if entry.filename in manifest['classes']:
            expected=manifest['classes'][entry.filename]
            assert hashlib.sha256(data).hexdigest()==expected['before']
            data=patches.read(entry.filename);assert hashlib.sha256(data).hexdigest()==expected['after']
        after.writestr(entry,data)
    after.comment=before.comment
with zipfile.ZipFile(original) as before, zipfile.ZipFile(target) as after:
    assert before.namelist()==after.namelist()
    changed=[name for name in before.namelist() if hashlib.sha256(before.read(name)).digest()!=hashlib.sha256(after.read(name)).digest()]
    assert set(changed)==set(manifest['classes'])
report={**manifest,'jarSha256':hashlib.sha256(target.read_bytes()).hexdigest(),'changedEntries':changed,'allOtherEntryContentsIdentical':True,'dependencyUpgrade':False}
(root/'package.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report))
