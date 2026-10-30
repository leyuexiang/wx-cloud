"""复用项目现有授权生成器，为测试宿主机签发默认 30 天的测试授权。"""

from pathlib import Path
import subprocess
import uuid
import zipfile

ROOT = Path("/opt/wx/editor-test148")
if Path.cwd().resolve() != ROOT:
    raise SystemExit("必须从 /opt/wx/editor-test148 运行")

machine_uuid = str(uuid.UUID(subprocess.check_output(
    ["dmidecode", "-s", "system-uuid"]).decode().strip()))
work = ROOT / "bootstrap/license-tool"
work.mkdir(parents=True, exist_ok=True, mode=0o700)
license_dir = ROOT / "data/license"
license_dir.mkdir(parents=True, exist_ok=True)
license_path = license_dir / "license.lic"

with zipfile.ZipFile(ROOT / "jars/dimensions.jar") as jar:
    for entry in jar.infolist():
        if entry.filename.startswith("BOOT-INF/lib/") and entry.filename.endswith(".jar"):
            target = work / Path(entry.filename).name
            if not target.exists():
                target.write_bytes(jar.read(entry))

source = work / "AuthorizeTestServer.java"
source.write_text('''import java.util.Date;
import cn.iocoder.wx.framework.core.LicenseCreator;
import cn.iocoder.wx.framework.core.LicenseVerify;
import cn.iocoder.wx.framework.core.param.LicenseCreatorParam;
import cn.iocoder.wx.framework.core.param.SystemInfo;
import cn.iocoder.wx.framework.core.prop.LicenseProperties;
// 使用项目原有生成器和校验器，按真实宿主机 UUID 生成授权，不修改业务校验。
class AuthorizeTestServer {
    static void inject(Object target, Object properties) throws Exception {
        var field = target.getClass().getDeclaredField("licenseProperties");
        field.setAccessible(true);
        field.set(target, properties);
    }
    public static void main(String[] args) throws Exception {
        var properties = new LicenseProperties();
        properties.setLicensePath(args[0]);
        var creator = new LicenseCreator();
        inject(creator, properties);
        if (!new java.io.File(args[0]).exists()) {
            var info = new SystemInfo();
            info.setUuid(args[1]);
            var params = new LicenseCreatorParam();
            params.setSystemInfo(info);
            params.setCompany("九天芸菲测试环境");
            params.setConsumerType("test");
            params.setDescription("192.168.10.148 三维编辑器测试授权");
            params.setCsExpiryTime(new Date(System.currentTimeMillis() + 30L*24*60*60*1000));
            creator.generateLicense(params);
        }
        var verifier = new LicenseVerify();
        inject(verifier, properties);
        verifier.install();
        if (verifier.verify()) throw new IllegalStateException("授权已过期");
        System.out.println("TEST_LICENSE_VALID");
    }
}
''', encoding="utf-8")
result = subprocess.run([
    "java", "-Dfile.encoding=UTF-8", "-Djava.security.egd=file:/dev/./urandom",
    "-cp", str(work / "*"), str(source), str(license_path), machine_uuid,
], check=True, capture_output=True, text=True)
license_path.chmod(0o644)
(ROOT / "license-verification.txt").write_text(result.stdout + result.stderr)
if "TEST_LICENSE_VALID" not in result.stdout:
    raise RuntimeError("授权校验未返回成功标记")
print("已用项目原有生成器安装测试授权，并通过宿主机绑定与有效期校验。")
print("默认有效期为 30 天；复用已有文件时不会自动延长有效期。")
