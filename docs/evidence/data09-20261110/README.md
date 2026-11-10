# 148 DATA-09 迁移与隔离服务联验

日期：2026-11-10。使用配置中的业务账号核对数据库，按用户确认在 `wx_editor_data08` 完成迁移，随后复用本机已有 SSH 配置部署隔离三维服务并执行真实网关联验。

## 迁移前核对

- MySQL 认证成功，版本 `8.0.46`。
- 配置文档记录的 `wx_editor_test148` 当前不存在，连接返回数据库不存在。
- 当前业务账号可见的目标库包括 `wx_editor` 和 `wx_editor_data08`。
- `wx_editor_data08` 当前有 85 张表，已存在 `wx_3d_scene_template` 和 `wx_3d_scene_content`。
- `wx_editor_data08` 尚不存在 `wx_3d_scene_publication`、`wx_3d_scene_publication_ticket` 和 `wx_3d_scene_publication_audit`。

## 结论

148 的网络和业务账号已经解除，目标库已按确认使用现有 `wx_editor_data08`。迁移前备份已保存到 `D:\data08-pre-data09-20261110.sql`。随后按顺序完成 TPL-01 的治理、版本、共享迁移和 DATA-09 的发布、票据、审计迁移，脚本均成功。

迁移后只读复核显示 6 张治理/发布相关表已存在，首次核对发布记录和票据记录均为 0，原有 1 条模板记录保留，版本指针为首版且模板原状态为 `OFFLINE`。

## 候选包

- `wx-module-3d/target/wx-module-3d.jar` 已按 `test148` profile 构建。
- SHA-256：`067EB80303A083A43C5224AFCDB850A263D33D87382A5AA84BD3E3C0B72E2098`。
- 包内已核对 `Dim3dSceneController`、`ScenePublicationService` 和 `ScenePublicationTicketService`。
- 本机已有 SSH 密钥可用，先前缺少 SSH 凭据的判断已更正。候选包已激活到 `wx-editor-dimensions-data08`，端口 `48093`，健康检查为 `UP`，容器为 `healthy`。
- 隔离网关实际端口为 `12525`，容器环境变量覆盖了配置文件中的默认值 `12523`。
- 部署前备份目录为 `/opt/wx/editor-test148/backups/data09-isolation-20261110T060734Z`，包含旧包、容器配置和服务配置。失败可停止隔离容器、恢复旧包至原挂载文件并启动该容器。
- 业务服务包摘要仍为 `8143caa0a2d1343a055cdba6c3289c076dcf3cff13d4f3c4fb9ee32caecb2fa1`，本次仅更新隔离服务包。详见 [部署回执](deployment.json)。

## 真实联验结果

通过隔离网关执行 [验收脚本](../../../deploy/test148/verify-data09-isolation.py)，18 项检查通过，详见 [结构化结果](isolation-result.json)。

- 三类真实账号登录、未登录拒绝、只读角色发布及票据签发拒绝、跨租户发布编号查询拒绝通过。
- 固定修订发布、请求重放、当前发布查询、依赖快照回读、票据摘要与有效期落库通过。
- 票据撤销、发布撤销后的查询与签发拒绝、回退生成新编号、五类审计动作落库通过。
- 样本场景已软删除，两条发布和一条票据均为 `REVOKED`，保留七条生命周期审计。既有场景主记录核对一致。
- 更新后的只读核对器默认连接 `wx_editor_data08`，三张表共 37 列，复核得到发布 2 条、票据 1 条、审计 7 条。

上述首批验收覆盖发布和票据管理接口；随后匿名四包消费已按下节完成联验。资源依赖闭包、跨租户模板共享成功链路及正文损坏故障注入尚未在真实环境验证，DATA-09 和 TPL-07 整体继续保持进行中。

## 匿名固定四包消费（DATA-09-25）

2026-11-10 新增 `POST /admin-api/dim3d/scene/publication/preview`，真实票据仅通过 `X-Scene-Ticket` 请求头传递。服务在一次查询内校验 READ 范围、票据状态、过期时间、发布状态和固定修订绑定；复核原文四包摘要及依赖快照，禁止回读最新草稿，响应禁止缓存。

- 非 READ 票据失败用例先复现；修正后四组共 18 项 Java 回归通过（票据 8、发布 5、预览路由 2、管理路由 3），覆盖到期、撤销、固定租户/修订、缺失/损坏正文及禁止缓存。执行命令和构建记录见[本地验证](preview-validation.json)。正文损坏分支为本地数据库测试，未在真实库注入故障。
- 候选包 SHA-256 为 `4a12ab6d522a3b0d7c829b83e8bfe38bcc3c5856887646a09303005136fbe6b9`，已部署至既有 data08 隔离容器，健康状态 `UP/healthy`。回退包和配置位于 `/opt/wx/editor-test148/backups/data09-preview-20261110T062350589046Z`，启动失败由[部署脚本](../../../deploy/test148/deploy-data09-isolation.py)恢复原文件；本次未触发回退，未演练故障恢复。
- 外置租户配置仅新增专用预览路径，业务服务包摘要保持 `8143caa0a2d1343a055cdba6c3289c076dcf3cff13d4f3c4fb9ee32caecb2fa1`。无新增库、无结构迁移。详见[部署回执](preview-deployment.json)。
- 经网关 `12525` 的 17 项真实检查通过：无登录/无租户头消费、原文四包一致、伪造身份参数不能覆盖绑定、相邻管理接口拒绝匿名、草稿保存和当前发布切换不改变原票据结果、单票撤销隔离、真实 60 秒自然到期拒绝、发布撤销使活动票据失效。详见[验收脚本](../../../deploy/test148/verify-data09-preview.py)和[真实结果](preview-result.json)。
- 样本场景 `2120039866379071489` 已软删除，两条发布和三条票据均为 `REVOKED`，十条本轮生命周期审计保留，详见[清理后复核](preview-postflight.json)。联验前既有场景的租户/名称/当前修订/删除标记，以及修订/共享正文的身份和正文摘要核对一致；不据此声称所有表和全部字段均未改变。

本子项为后端匿名四包读取验收。真实依赖资源成员校验、固定版本文件签发、引用保留和 Web/Unity 实际加载仍待 DATA-09-26 及消费端实施。
