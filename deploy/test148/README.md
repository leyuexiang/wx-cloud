# 三维编辑器测试服务器部署

## 2026-11-13 模型导入部署准备

当前模型新增专项采用已提交源码构建和部署。服务器已具备版本控制工具（Git）、构建工具（Maven）及 Java 17。先拉取指定提交，再从该提交执行构建、增量演练和配置切换。实际状态维护在 [MODEL-02](../../docs/03_近期执行任务.md#model-00-模型资源新增功能完善计划2026-11-12)。

服务器构建须显式指定 Linux 绝对依赖仓库路径，避免全局设置残留 Windows 盘符；冒号会被 Linux 当作编译路径分隔符，导致已经下载的依赖仍报类缺失。仅对本次命令覆盖，不修改其他项目的全局设置：

```sh
cd /opt/wx/checkouts/wx-cloud
mvn -B -ntp -Ptest148 -Dmaven.repo.local=/opt/wx/cache/maven -pl wx-module-3d -am -Dmaven.test.skip=true package
```

该命令只构建部署包，专项测试另行执行和记录。首次使用新仓库可以复用已核实完整的旧依赖缓存，不能把错误路径原样加入编译参数。

目标库为 `wx_editor` 和 `wx_editor_data08`。两库已有 ART 基础结构，需要补齐 `migrations/market01` 的 001～003。在部署根目录调用源码目录中的工具，分别对每个库预检和演练。

```sh
cd /opt/wx/editor-test148
python3 /opt/wx/checkouts/wx-cloud/deploy/test148/prepare-model-import.py --database wx_editor
python3 /opt/wx/checkouts/wx-cloud/deploy/test148/prepare-model-import.py --database wx_editor --rehearse
python3 /opt/wx/checkouts/wx-cloud/deploy/test148/prepare-model-import.py --database wx_editor_data08 --rehearse
```

演练工具备份相关表，在唯一命名的独立副本上顺序执行增量，并核对原有列数据摘要。备份和报告保存在部署根目录的受限 `backups` 子目录。正式切换时先停止对应三维写入实例，再使用同库的成功报告执行 `--apply --rehearsal-report /对应备份目录/report.json`。应用阶段重新备份，检查结构和原数据。发生部分结构语句成功时，按报告中的实际步骤修复。

接收候选使用 `stage-model-import.py --target main|data08 --rehearsal-report /对应报告 --artifact /服务器构建包 --sha256 实际摘要`。它仅连接同来源的演练副本，使用 `49092/49093` 回环端口，保留现场认证配置，开启接口而关闭工作线程；授权数据只读挂载，原始环境快照受限保存，临时环境文件在启动后删除。当前应用账号对原数据副本仅获得增删改查权限，不创建账号，不授予结构管理权限。启动后还需核对健康、接口注册和真实身份，报告生成不代表联调通过。结束后停止本批候选并撤销该副本授权；演练副本和备份按交接需要保留。

三维配置通过 `WX_MODEL_IMPORT_ENABLED`、`WX_MODEL_IMPORT_WORKER_ENABLED`、`WX_MODEL_IMPORT_CONVERTER_URL`、`WX_MODEL_IMPORT_APP_COMPATIBILITY` 及可选的超时、产物大小变量设置。入口和工作线程默认关闭，转换地址使用已验证的完整接口地址。现有服务器配置仅补齐该配置组，保留实际数据库、端口及其他功能配置。

当前浏览器来源为 `http://192.168.10.247:5180`。执行上传代理配置时，通过 `WX_UPLOAD_CORS_ORIGINS` 提供完整精确来源列表。该变量可以同时包含既有来源。`WX_TLS_CERT_DIR` 指向现场已核验的有效证书目录，浏览器需信任对应证书。2026-11-12 现场的上传代理仍使用旧来源和已到期证书，下方早期记录中的证书目录与到期日按其记录日期理解。

下载代理同步使用精确来源映射，默认继承 `WX_UPLOAD_CORS_ORIGINS`，也可由 `WX_DOWNLOAD_CORS_ORIGINS` 单独覆盖。上传与下载映射变量独立，能同时加载；不匹配的来源不返回跨域许可，私有桶签名、只读下载与暂存上传路径限制继续执行。

## 2026-10-30 现场适配

本轮服务器实际状态与后文历史记录不同：宿主使用 MariaDB 10.3，另有 WX 系统服务占用旧内部端口。当前方案使用独立 MySQL 8.0.44（`13306`）和 Redis 7.4.2（`16380`），内部服务统一为 `48181/48182/48192`。初始化脚本先生成完整环境并启动依赖，确认数据库就绪后再建表。切换及回退增加了网关重建失败时独立恢复旧容器的保障。

当前证书目录为 `/etc/wx/tls`，证书到期日为 2027-04-23，客户端需信任其中的内部 CA。后文 ART 各阶段的日期、证书及验收数字为历史记录，本轮结果另存部署证据。

本轮已部署并切换成功：四个核心服务健康，187 项关键 Java 测试、8 项部署单元测试、126 项服务器接口检查及 3 项工作站直连检查通过。实际回执及边界见[部署验收记录](../../docs/evidence/deployment148-20261030/README.md)。

具体架构、构建、凭据、切换及回退见[148 服务器部署设计与操作说明](../../docs/20_部署运维/01_148服务器部署与回退.md)。

## 当前状态

以本地 `jtyf-cloud` 为唯一源码，已部署到 `192.168.10.148` 并替代旧 `metabase-backend` 测试入口。用户不需要准备授权文件：已复用本项目原有生成器生成测试宿主机授权，默认 30 天，通过机器绑定与有效期校验。

- 入口：`http://192.168.10.148:12522`
- 服务目录：`/opt/wx/editor-test148`
- 数据库：`wx_editor_test148`，初始 81 张表；ART-01 增量迁移后为 82 张，均使用 `wx_` 前缀。
- 内部服务：系统 `48181`、基础设施 `48182`、三维 `48192`，只监听本机回环地址。
- 独立 Redis：仅监听 `127.0.0.1:16380`，不使用其他应用的缓存空间。
- 旧服务：停止并保留；旧 `metabase_3d` 和 7 条资源记录保持原样，没有自动转换到新资源模型。

本次部署覆盖三维业务、系统权限、基础设施和网关。商城、支付等无关模块不在本次部署范围。

## 构建

从仓库根目录执行：

```powershell
mvn.cmd -B -o -Ptest148 -pl wx-module-3d,wx-module-system/wx-module-system-biz,wx-module-infra/wx-module-infra-biz,wx-gateway -am '-Dmaven.test.skip=true' clean package
python deploy/test148/prepare-config.py
```

离线构建要求 Maven 缓存中已具备依赖。本轮本机已验证。`test148` 仅为三维模块的测试打包配置，生成普通可执行包；默认发行包的机器绑定加密流程保留。

部署包必须与对应源码一起保存，不对旧编译包做字节码修改。上传新的 `*.candidate` 文件后，使用原子重命名及容器重建切换，禁止原地截断正在运行的 JAR 文件。

## 初始化与隔离验证

将本目录脚本、`config/` 和四个新构建包放入服务器目录，包名分别为 `jars/system.jar`、`infra.jar`、`dimensions.jar` 和 `gateway.jar`。

服务器首次执行：

```sh
cd /opt/wx/editor-test148
python3 bootstrap-server.py
python3 migrate-resource-art01.py apply
python3 seed-test-identity.py
python3 authorize-test-server.py
docker compose -f compose.yaml up -d
python3 verify-api.py
```

首次网关使用隔离端口 `12532`。密钥由服务器生成或从现有 MinIO 配置中读取，仅保存在服务器 `.env`；不会写入仓库、测试报告或日志。测试账号凭据保存在服务器 `test-identities.json`，权限为仅管理员可读。账号为 `wxadmin`、`tenantadmin`、`viewer`，密码随机生成。

`schema-core.sql` 仅包含结构，不含历史用户、密码、令牌和业务数据。系统及基础设施结构来自本地工程原始 SQL 压缩包，三维结构来自当前源码，表名均同步为 `wx_`。`table-map.json` 给出完整 81 张表的映射。该脚本用于全新测试库初始化，不能替代已有生产数据库的增量迁移。

ART-01 使用独立的[增量迁移](migrations/art01/README.md)，增加当前资源字段、三类目录关联及一张操作快照表；初始结构和映射保留为 81 表部署基线，不修改已执行脚本。全新环境也要上传迁移执行器及 `migrations/art01/` 并按上面的顺序执行；已有测试库直接按迁移说明备份、演练、应用，不能重新初始化覆盖数据。

## 切换及回退

隔离验证通过后执行 `python3 cutover.py`。脚本停止旧容器，将新入口改到 `12522`，重建网关并运行完整接口检查；失败则恢复隔离端口和旧容器，保留失败诊断日志。

手动回退执行：

```sh
cd /opt/wx/editor-test148
python3 rollback.py
```

回退不删除新库；新服务仍可在隔离端口排查。旧服务的镜像保留为 `metabase-backend:before-wx-replacement`，旧配置和数据库快照保存在 `backups/` 下的带时间目录。

## 验证范围

- 四个核心服务从本地源码构建，部署后健康检查通过。
- 67 项角色、租户及权限单元测试，1 项短信工厂启动回归，2 项网关身份头回归，共 70 项通过。
- 切换后 20 项接口验证通过，覆盖三类登录、权限及目录读取、真实模型元数据写入回读删除、租户字段篡改、跨租户读取、匿名请求和伪造内部身份。
- API 使用本项目的 `code=200` 表示成功。
- 验收模型为临时元数据，已通过业务接口逻辑删除，不代表美术包生成、上传或视觉效果验证。

第一次入口切换中，管理员权限查询返回一次 500，脚本按设计自动恢复了旧服务。随后隔离网关重新启动验证、第二次切换及切换后 20 项验证均通过；最初异常未定位到明确根因，保留为冷启动观察项，不能声称已经完成根因修复。

ART-01 已完成结构迁移、测试库精确回退/再迁移和接口合同，见[验收记录](../../docs/evidence/backend-art01-20261029/README.md)。新 Java 持久化映射及旧目录请求改绑保护已编译和单元测试，但本轮未部署新 Java 包。实际跨租户全局资源查询、单资源内容替换事务、Unity 新接口接入、模型转换服务及 MinIO 美术闭包上传、三类代表资源浏览器视觉仍待 ART-02 起实施。旧 API `/api/assets` 与新 `/admin-api/...` 不兼容。

## ART-02 当前增量

权限/操作入口程序已部署，60 项资源接口与原网关 20 项回归通过，状态为部分完成；受控 HTTPS 下载仍待接通，上传/提交明确未启用。部署备份、首次配置错误回退及剩余范围见[ART-02 记录](../../docs/evidence/backend-art02-20261029/README.md)。配置生成器已保留平台租户 `1` 的显式配置，不应重复追加根节点。

## ART-02 下载验收收口

新私有桶读取入口为 `https://192.168.10.148:9444`，部署脚本为 `setup-resource-download.py`；服务配置生成器同步该入口。32 项真实下载、60 项资源接口和20项网关回归通过，ART-02 已完成，详见[下载记录](../../docs/evidence/backend-art02-download-20261029/README.md)。证书有效期至2026-11-11，跨域仅允许本机8765测试页；业务域名和长期证书仍须接入时配置。上传签名/真实commit属于ART-03，当前未启用。

## ART-03 上传与事务

ART-03已部署：HTTPS9445仅允许私有桶暂存前缀PUT；9444继续只读。新增 `setup-resource-upload.py`、`deploy-resource-art03.py` 与 `verify-resource-art03.py`；操作调用、冻结与数据库事务见[实现说明](../../docs/10_专项设计/08_资源上传替换事务实现.md)。99项本地测试、67项真实事务、60项权限/32项下载/20项原接口回归通过，详见[最终记录](../../docs/evidence/backend-art03-20261029/README.md)。上传入口只回显精确的浏览器来源白名单；默认兼容 `http://127.0.0.1:8765` 与当前 `http://localhost:5180`，正式站点应在运行脚本前通过 `WX_UPLOAD_CORS_ORIGINS` 配置逗号分隔的完整来源，禁止使用通配符。此前各节“上传/commit尚未启用”为ART-02时点，当前以本节为准。

## ART-05 固定内容读取增量

已部署 `GET /admin-api/dim3d/resources/{resourceId}/file?revision=...&contentHash=...&path=...`。先核对资源现时可见性，再定位同范围的已提交快照并校验主清单与文件成员；旧上下文首次读取和续签均固定原内容，不能只按当前指针签发。沿用现有操作快照，不新增表或整库版本。

`deploy-resource-art05.py` 显式接收 `--candidate jars/dimensions-art05.candidate.jar --sha256 <实际摘要>`，部署前后核对文件，失败恢复备份。独立证据在 `/opt/wx/editor-test148/evidence-art05/`，历史报告恢复原字节。本次Java105项、真实汽车A/B读取34项、事务67项及权限/下载/业务60/32/20项通过；激活摘要见服务器 `deployment-art05.json`。

`setup-resource-api.py` 建立 `https://192.168.10.148:9446`，仅代理资源列表、当前描述、固定文件签发的GET/HEAD和OPTIONS到原网关；限制当前测试来源IP和浏览器来源，禁止业务写入和其它路由，正常校验证书，不记录认证或签名查询。9444继续下载，9445继续上传。证书仍至2026-11-11，正式宿主和长期证书单独接入。

当前Unity编辑器正式客户端已完成A/B全部文件、包目录适配和续签16项检查。原生AssetBundle加载、共享缓存、实例/灯光释放及WebGPU视觉尚未验收。
