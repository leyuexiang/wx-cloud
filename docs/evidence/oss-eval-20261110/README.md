# X File Storage 评估证据

日期：2026-11-10。任务：OSS-EVAL-01。源码基准：项目 dev / `003f7669` 加当时未提交差异；第三方 tag `2.3.0` / `f7baa6e933398185096a5bff5e427593247b19d7`。

结论与接入边界见[对象存储设计第 12 节](../../10_专项设计/11_对象存储服务设计.md#12-x-file-storage-230-接入评估oss-eval-01)，任务状态只在[03](../../03_近期执行任务.md#oss-eval-01x-file-storage-接入评估2026-11-10)维护。

## 1. 实际产物

- [发布元数据](release.json)：从官方 GitHub 发布接口提取的版本、发布时间和正式发布标识。
- [固定源码清单](source-manifest.json)：所核查文件的路径、SHA-256 和固定提交链接，不复制整套第三方源码进业务仓库。
- [项目有效依赖](project-dependencies.json)：从实际基础设施模块有效模型中提取的相关受管版本。
- [原样依赖树](dependencies-raw.txt)与[排除重复类后的依赖树](dependencies-aligned.txt)。
- [独立探针](probe/pom.xml)及[探针源码](probe/src/test/java/evaluation/XFileStorageCompatibilityTest.java)。
- [检查摘要](test-results.json)：九项全部通过，含两个故障边界的预期复现、实际第三方包摘要及原始重复类提供者。

## 2. 复现命令

在项目根目录使用 Java 17 和 Maven 3.9.9。探针完全独立，不需要初始化数据库或连接服务器：

```powershell
git clone --depth 1 --branch 2.3.0 https://github.com/dromara/x-file-storage.git .temp/oss-eval-20261110/upstream
mvn -o -pl wx-module-infra/wx-module-infra-biz -am help:effective-pom '-Doutput=E:/Products/3D-editor/Server/jtyf-cloud/.temp/oss-eval-20261110/infra-effective-pom.xml' -DskipTests
mvn -B -ntp -f docs/evidence/oss-eval-20261110/probe/pom.xml -Praw dependency:tree '-Dverbose=true' '-DoutputFile=../dependencies-raw.txt'
mvn -B -ntp -f docs/evidence/oss-eval-20261110/probe/pom.xml -Paligned test dependency:tree '-Dverbose=true' '-DoutputFile=../dependencies-aligned.txt'
```

第一条生成的模型包含所选依赖模块，用于提取基础设施业务模块的有效管理版本；它不编译或证明所有模块启动。第三方源码克隆在项目已忽略的 `.temp/oss-eval-20261110/upstream`，构建和详细测试输出在探针 `target/`，均未加入交付文件。首次运行需要下载依赖。

## 3. 过程与限制

初次直接对模块单独离线读取模型因内部快照依赖未安装而失败；改为从根工程带依赖模块生成有效模型后成功。首轮九项中分片列举失败来自本地替身 XML 缺少 S3 必填 Initiator/Owner 字段，补齐协议替身后九项通过；没有修改第三方代码来掩盖不兼容。

探针连接本机随机端口的有限 HTTP S3 替身，只使用明确标注的假凭据；记录器为内存替身。签名地址不输出到证据。真实 MinIO、MySQL、桶策略、实际签名验证、HTTPS/跨域、全应用和负载均未验；失败回调测试不能代替真实数据库事务验收。未改业务源码/POM、数据库、共享服务、对象桶或 Unity。
