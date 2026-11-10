# 148 DATA-09 只读核对

日期：2026-11-10。使用《服务器配置信息.md》中的编辑器业务账号，通过 `192.168.10.148:3306` 直连执行只读查询，未输出密码，未执行迁移或业务写入。

## 结果

- MySQL 认证成功，版本 `8.0.46`。
- 配置文档记录的 `wx_editor_test148` 当前不存在，连接返回数据库不存在。
- 当前业务账号可见的目标库包括 `wx_editor` 和 `wx_editor_data08`。
- `wx_editor_data08` 当前有 85 张表，已存在 `wx_3d_scene_template` 和 `wx_3d_scene_content`。
- `wx_editor_data08` 尚不存在 `wx_3d_scene_publication`、`wx_3d_scene_publication_ticket` 和 `wx_3d_scene_publication_audit`。

## 结论

148 的网络和业务账号已经解除，目标库已按确认使用现有 `wx_editor_data08`。迁移前备份已保存到 `D:\data08-pre-data09-20261110.sql`。随后按顺序完成 TPL-01 的治理、版本、共享迁移和 DATA-09 的发布、票据、审计迁移，脚本均成功。

迁移后只读复核显示 6 张治理/发布相关表已存在，发布记录和票据记录均为 0，原有 1 条模板记录保留，版本指针为首版且模板原状态为 `OFFLINE`。尚未创建测试发布、票据或跨租户样本，也未替换常驻服务包。

## 候选包

- `wx-module-3d/target/wx-module-3d.jar` 已按 `test148` profile 构建。
- SHA-256：`067EB80303A083A43C5224AFCDB850A263D33D87382A5AA84BD3E3C0B72E2098`。
- 包内已核对 `Dim3dSceneController`、`ScenePublicationService` 和 `ScenePublicationTicketService`。
- 当前未替换 148 常驻三维服务。现有配置未提供可用的 SSH 或候选包上传凭据，避免直接改动常驻容器。
