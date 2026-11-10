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

本轮验收覆盖发布和票据管理接口。匿名票据消费、资源依赖闭包、跨租户模板共享成功链路及故障注入尚未在真实环境验证，DATA-09 和 TPL-07 整体继续保持进行中。
