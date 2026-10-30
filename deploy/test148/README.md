# 三维编辑器测试服务器部署

## 当前状态

以本地 `jtyf-cloud` 为唯一源码，已部署到 `192.168.10.148` 并替代旧 `metabase-backend` 测试入口。用户不需要准备授权文件：已复用本项目原有生成器生成测试宿主机授权，默认 30 天，通过机器绑定与有效期校验。

- 入口：`http://192.168.10.148:12522`
- 服务目录：`/opt/wx/editor-test148`
- 数据库：`wx_editor_test148`，初始 81 张表；ART-01 增量迁移后为 82 张，均使用 `wx_` 前缀。
- 内部服务：系统 `48081`、基础设施 `48082`、三维 `48092`，只监听本机回环地址。
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

ART-03已部署：HTTPS9445仅允许私有桶暂存前缀PUT；9444继续只读。新增 `setup-resource-upload.py`、`deploy-resource-art03.py` 与 `verify-resource-art03.py`；操作调用、冻结与数据库事务见[实现说明](../../docs/17_单资源上传替换事务实现.md)。99项本地测试、67项真实事务、60项权限/32项下载/20项原接口回归通过，详见[最终记录](../../docs/evidence/backend-art03-20261029/README.md)。此前各节“上传/commit尚未启用”为ART-02时点，当前以本节为准。

## ART-05 固定内容读取增量

已部署 `GET /admin-api/dim3d/resources/{resourceId}/file?revision=...&contentHash=...&path=...`。先核对资源现时可见性，再定位同范围的已提交快照并校验主清单与文件成员；旧上下文首次读取和续签均固定原内容，不能只按当前指针签发。沿用现有操作快照，不新增表或整库版本。

`deploy-resource-art05.py` 显式接收 `--candidate jars/dimensions-art05.candidate.jar --sha256 <实际摘要>`，部署前后核对文件，失败恢复备份。独立证据在 `/opt/wx/editor-test148/evidence-art05/`，历史报告恢复原字节。本次Java105项、真实汽车A/B读取34项、事务67项及权限/下载/业务60/32/20项通过；激活摘要见服务器 `deployment-art05.json`。

`setup-resource-api.py` 建立 `https://192.168.10.148:9446`，仅代理资源列表、当前描述、固定文件签发的GET/HEAD和OPTIONS到原网关；限制当前测试来源IP和浏览器来源，禁止业务写入和其它路由，正常校验证书，不记录认证或签名查询。9444继续下载，9445继续上传。证书仍至2026-11-11，正式宿主和长期证书单独接入。

当前Unity编辑器正式客户端已完成A/B全部文件、包目录适配和续签16项检查。原生AssetBundle加载、共享缓存、实例/灯光释放及WebGPU视觉尚未验收。
