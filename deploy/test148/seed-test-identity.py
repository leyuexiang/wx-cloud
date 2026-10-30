"""初始化全新测试库的最小登录数据；密码只写入服务器私有文件。"""

from pathlib import Path
import json
import secrets
import string
import subprocess
import zipfile

ROOT = Path("/opt/wx/editor-test148")
if Path.cwd().resolve() != ROOT:
    raise SystemExit("必须从 /opt/wx/editor-test148 执行")

identity_path = ROOT / "test-identities.json"
if identity_path.exists():
    identities = json.loads(identity_path.read_text())
else:
    alphabet = string.ascii_letters + string.digits
    identities = [
        {"id": 1, "tenantId": 1, "username": "wxadmin", "role": "super_admin"},
        {"id": 2, "tenantId": 2, "username": "tenantadmin", "role": "tenant_admin"},
        {"id": 3, "tenantId": 2, "username": "viewer", "role": "viewer"},
    ]
    for identity in identities:
        identity["password"] = "".join(secrets.choice(alphabet) for _ in range(16))
    identity_path.write_text(json.dumps(identities, ensure_ascii=False, indent=2))
    identity_path.chmod(0o600)

work = ROOT / "bootstrap"
work.mkdir(exist_ok=True, mode=0o700)
with zipfile.ZipFile(ROOT / "jars/system.jar") as jar:
    for name in jar.namelist():
        if name.startswith("BOOT-INF/lib/") and ("spring-security-crypto-" in name or "spring-jcl-" in name):
            (work / Path(name).name).write_bytes(jar.read(name))

source = work / "HashPassword.java"
source.write_text('''import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
// 通过标准输入接收密码，避免写入命令行参数或构建日志。
class HashPassword {
    public static void main(String[] args) throws Exception {
        var value = new BufferedReader(new InputStreamReader(System.in)).readLine();
        System.out.print(new BCryptPasswordEncoder(10).encode(value));
    }
}
''')

sql = ["SET NAMES utf8mb4;", "START TRANSACTION;"]
sql += [
    "INSERT IGNORE INTO wx_system_tenant (id,name,contact_user_id,contact_name,status,package_id,expire_time,account_count) VALUES (1,'平台测试租户',1,'测试管理员',0,0,'2099-12-31',100),(2,'租户隔离测试',2,'租户管理员',0,1,'2099-12-31',100);",
    "INSERT IGNORE INTO wx_system_role (id,name,code,sort,data_scope,data_scope_dept_ids,status,type,tenant_id) VALUES (1,'平台超级管理员','super_admin',0,1,'[]',0,1,1),(2,'租户管理员','tenant_admin',0,1,'[]',0,1,2),(3,'普通查看者','viewer',1,5,'[]',0,2,2);",
]
for identity in identities:
    hashed = subprocess.check_output([
        "java", "-Djava.security.egd=file:/dev/./urandom", "-cp", str(work / "*"), str(source)
    ], input=(identity["password"] + "\n").encode()).decode().strip()
    sql.append(
        "INSERT IGNORE INTO wx_system_users (id,username,password,nickname,post_ids,status,tenant_id) "
        f"VALUES ({identity['id']},'{identity['username']}','{hashed}','测试用户','[]',0,{identity['tenantId']});"
    )
    sql.append(
        "INSERT IGNORE INTO wx_system_user_role (id,user_id,role_id,tenant_id) "
        f"VALUES ({identity['id']},{identity['id']},{identity['id']},{identity['tenantId']});"
    )

oauth_secret_path = ROOT / "bootstrap/oauth-client-secret.txt"
if oauth_secret_path.exists():
    oauth_secret = oauth_secret_path.read_text().strip()
else:
    oauth_secret = secrets.token_urlsafe(32)
    oauth_secret_path.write_text(oauth_secret)
    oauth_secret_path.chmod(0o600)
sql.append(
    "INSERT IGNORE INTO wx_system_oauth2_client "
    "(id,client_id,secret,name,logo,status,access_token_validity_seconds,refresh_token_validity_seconds,redirect_uris,authorized_grant_types,scopes,auto_approve_scopes) "
    f"VALUES (1,'default','{oauth_secret}','测试编辑器','',0,1800,86400,'[]','[\"password\",\"refresh_token\"]','[]','[]');"
)

# 租户管理员的资源权限显式分配，查看者只获得查询权限。
permissions = ["query", "add", "edit", "delete"]
for offset, action in enumerate(permissions, start=100):
    sql.append(
        "INSERT IGNORE INTO wx_system_menu (id,name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show) "
        f"VALUES ({offset},'模型{action}','dim3d:model:{action}',3,{offset},0,'','','',0,1,1,1);"
    )
    sql.append(f"INSERT IGNORE INTO wx_system_role_menu (id,role_id,menu_id,tenant_id) VALUES ({offset},2,{offset},2);")
sql.append("INSERT IGNORE INTO wx_system_role_menu (id,role_id,menu_id,tenant_id) VALUES (200,3,100,2);")
sql.append("COMMIT;")
subprocess.run([
    "docker", "exec", "-i", "mysql8", "sh", "-c",
    'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot wx_editor_test148',
], check=True, input="\n".join(sql).encode())
print("测试身份已初始化：平台管理员、租户管理员、普通查看者。")
print(f"凭据仅存放于服务器 {identity_path}，未写入日志或仓库。")
