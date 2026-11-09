# DATA-09 发布记录本地实现证据

日期：2026-11-09。范围为 DATA-09-02～DATA-09-05 和 TPL-07-05-04。全部验证在本地 H2、静态迁移门禁和离线 Maven 环境完成，未部署 148，未写入真实发布数据。

## 已交付

- 新增 `data09/001-scene-publication.up.sql` 和回退脚本，固定发布编号、场景修订、四包摘要、依赖快照、请求幂等、当前指针和撤销字段。
- 新增 `ScenePublicationService`，实现固定修订发布、同请求重放、当前指针切换、撤销和新发布编号回退。
- 新增模板共享迁移顺序门禁，确认 004 共享迁移依赖 003 版本迁移，不改变 148 数据。
- 发布管理路由已接入当前场景控制器，覆盖发布、当前摘要、编号读取、撤销和新编号回退。
- 依赖快照必须是 JSON 对象，非法数组或无效内容在写入发布记录前拒绝。
- 148 预检脚本门禁已固化专用库 `wx_editor_tpl04_test`、显式 `--apply` 写入开关和 `12522` API 网关入口。

## 验证

- `mvn.cmd -B -o -pl wx-module-3d -am -Dtest=ScenePublicationServiceTest,Dim3dSceneServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 24 项通过，其中发布服务 3 项，场景治理 21 项。
- `python -m unittest deploy/test148/test_data09_publication.py deploy/test148/test_tpl07_governance.py deploy/test148/test_tpl07_share_schema.py`
  - 9 项通过。
- `python -m unittest deploy/test148/test_data09_publication.py deploy/test148/test_tpl07_governance.py deploy/test148/test_tpl07_share_schema.py deploy/test148/test_tpl07_148_preflight.py`
  - 12 项通过。
- 最新发布服务和控制器定向回归共 6 项通过，包含依赖快照拒绝和五条路由参数投影。

## 边界

访问票据、依赖资源保留策略和真实 MySQL/148 联验仍未开放。148 当前只有网络端口连通性检查，没有可用的本机登录配置，因此未执行数据库读取。回退只在没有正式发布数据的隔离库执行，业务库不通过删表回退历史发布。
