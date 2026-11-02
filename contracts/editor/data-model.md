# 编辑器数据结构与增量迁移设计

任务：M0-04。日期：2026-10-31。状态：设计进行中；本文不执行迁移，不修改服务器数据库。

## 1. 设计目标

在已部署 `wx_editor_test148` 的 82 张表基础上增加编辑器领域结构。迁移必须向前兼容、可重复核验、有备份和回退边界。旧场景表、旧资源目录和 ART 资源操作继续保留，不能通过重初始化覆盖已有数据。

数据权威分工：

- 系统用户、租户、OAuth2 客户端和角色继续由系统模块维护。
- 场景主记录、当前草稿、编辑锁、保存幂等、发布快照和场景授权由三维模块维护。
- 普通文件由基础设施模块维护；Unity 受控包由 ART 资源操作维护。
- 对象存储保存四包附件或发布产物的不可变对象；数据库保存元数据、摘要和引用。
- Redis 只保存可重建锁/热点缓存；MySQL 行记录是锁、草稿书签和幂等结果的权威来源。

编号对外始终使用十进制字符串。数据库可以使用 `BIGINT UNSIGNED` 或项目现有雪花编号机制，但不把编号转换为 JavaScript 数字。

## 2. 现有表增量

### 2.1 租户稳定编码

在现有 `wx_system_tenant` 增加：

| 字段 | 建议类型 | 约束 |
| --- | --- | --- |
| `tenant_code` | `varchar(64)` | 非空、规范化为小写 ASCII，正则 `^[a-z0-9][a-z0-9._-]{1,63}$` |

增加唯一索引 `uk_system_tenant_code`。历史租户迁移时先生成审计可追溯的临时代码，再由业务确认正式代码；重复或无法生成的租户进入迁移阻塞清单，不能自动取企业名称截断覆盖。

租户编码默认不可由普通租户用户修改。平台管理员变更时记录审计，并明确旧登录链接是否保留；登录会话始终绑定解析出的 `tenantId`，不把编码长期写入访问令牌之外的权限判断。

### 2.2 OAuth2 客户端扩展

复用 `wx_system_oauth2_client.additional_information` 的 JSON，不新增应用表。登记结构：

```json
{
  "platformType": "operation",
  "enabledForPasswordLogin": true,
  "tenantScope": "PLATFORM",
  "contractVersion": "editor.v1"
}
```

允许值：`platformType=operation|tenant`；`tenantScope=PLATFORM|TENANT`。服务端读取并校验结构，缺失或非法值的客户端不能用于编辑器独立登录。真实 `clientId`、密钥和状态由部署配置/受限迁移注入，不写入合同样例和 Git。

现有刷新令牌表继续保存令牌链。首期 `VITE_AUTH_REFRESH_ENABLED=false`；启用轮换前需增加旧刷新令牌重放检测、会话撤销和并发测试。

## 3. 新增场景表

表名前缀统一为 `wx_dim3d_`。所有表带项目已有审计字段；下表只列领域字段。

### 3.1 `wx_dim3d_scene`

| 字段 | 类型/约束 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` 主键 | 场景编号，对外字符串 |
| `tenant_id` | `BIGINT` 非空 | 真实拥有租户；平台场景按明确系统租户策略记录 |
| `owner_user_id` | `BIGINT` 非空 | 当前所有者，服务端填写 |
| `name` | `varchar(100)` 非空 | 场景名称 |
| `scene_desc` | `varchar(500)` | 描述 |
| `scene_category` | `varchar(64)` | 字典编码 |
| `scene_type` | `varchar(16)` | `local3d` 或 `gis` |
| `scene_kind` | `varchar(16)` | `INSTANCE` 或 `TEMPLATE` |
| `save_scope` | `varchar(16)` | 模板使用 `twoD/threeD/all` |
| `cover_asset_id` | `varchar(64)` | 稳定文件/资源编号，不保存临时签名 |
| `draft_revision_no` | `BIGINT UNSIGNED` | 当前草稿书签，初始 0 |
| `published_publication_id` | `BIGINT UNSIGNED NULL` | 当前不可变发布编号 |
| `publish_status` | `TINYINT` | 0 未发布，1 已发布 |
| `deleted` | `TINYINT` | 逻辑删除 |

索引：`(tenant_id, deleted, update_time, id)`、`(owner_user_id, deleted, update_time)`、`(scene_kind, tenant_id, deleted)`。列表查询必须带归属/授权范围和逻辑删除条件。

### 3.2 `wx_dim3d_scene_draft`

每个场景最多一条当前草稿，使用 `scene_id` 唯一。

| 字段 | 类型/约束 | 说明 |
| --- | --- | --- |
| `scene_id` | `BIGINT UNSIGNED` 主键 | 关联场景 |
| `revision_no` | `BIGINT UNSIGNED` | 与场景书签一致 |
| `schema_version` | `INT` | 当前为 1 |
| `scene_json` | `LONGTEXT` | 场景容器原文 |
| `three_d_json` | `LONGTEXT` | 三维包原文 |
| `two_d_json` | `LONGTEXT` | 二维包原文 |
| `dependencies_json` | `LONGTEXT` | 依赖包原文 |
| `content_hash` | `CHAR(64)` | 四包规范摘要 |
| `last_request_id` | `CHAR(36)` NULL | 最近成功保存请求 |
| `last_change_summary` | `VARCHAR(500)` | 变更说明 |

四个包必须成组存在。新场景可以没有草稿；一旦有任意一个包，其他三个都必须非空且通过 JSON/协议校验。未知属性在原文中保留，派生索引不能反向覆盖原文。

### 3.3 `wx_dim3d_scene_lock`

每个场景最多一条有效锁，过期锁由获取/心跳事务回收。

| 字段 | 类型/约束 | 说明 |
| --- | --- | --- |
| `scene_id` | `BIGINT UNSIGNED` 主键 | 锁对象 |
| `tenant_id` | `BIGINT` 非空 | 持锁租户 |
| `user_id` | `BIGINT` 非空 | 持锁用户 |
| `client_key` | `VARCHAR(128)` 非空 | 编辑页签身份 |
| `lock_token_hash` | `CHAR(64)` 非空 | 只存摘要，不存明文令牌 |
| `base_revision_no` | `BIGINT UNSIGNED` | 加锁时草稿书签 |
| `lock_version` | `BIGINT UNSIGNED` | 租约代次 |
| `expires_at` | `DATETIME(3)` | 20 分钟租约 |

唯一约束：`scene_id`。获取锁使用 `SELECT ... FOR UPDATE`，持有者、租户、页签和过期时间一起校验。普通解锁不能按场景编号强制清除他人锁。

### 3.4 `wx_dim3d_scene_save_request`

保存请求幂等记录，唯一键 `(scene_id, request_id)`。

| 字段 | 类型/约束 | 说明 |
| --- | --- | --- |
| `scene_id` | `BIGINT UNSIGNED` | 场景 |
| `request_id` | `CHAR(36)` | 客户端重试编号 |
| `tenant_id`、`user_id` | `BIGINT` | 首次请求身份快照 |
| `payload_hash` | `CHAR(64)` | 规范请求摘要 |
| `status` | `VARCHAR(16)` | `COMMITTED/FAILED/CONFLICT` |
| `result_json` | `LONGTEXT` | 成功回执，不含令牌 |
| `error_code` | `INT` NULL | 失败业务码 |
| `expires_at` | `DATETIME(3)` | 幂等记录保留期限 |

同编号同摘要回放原结果；同编号异摘要拒绝，不重写草稿。失败记录是否允许重试由状态和实现合同决定，不能无条件重放写操作。

## 4. 发布、依赖和授权表

### 4.1 `wx_dim3d_scene_publication`

发布快照不可变，发布后草稿更新不修改此表的四包。

关键字段：`id`、`scene_id`、`tenant_id`、`revision_no`、`schema_version`、`scene_json`、`three_d_json`、`two_d_json`、`dependencies_json`、`content_hash`、`access_mode`、`password_hash`、`status`、`published_by`、`published_at`、`expires_at`、`cancelled_at`。

`access_mode` 为 `PERSONAL/ENTERPRISE/PUBLIC_ENCRYPT/PUBLIC_SHARE`。密码只存安全散列；公开模式不把管理令牌或对象存储密钥写入快照。索引：`(scene_id, status, published_at)`、`(tenant_id, status, published_at)`。

### 4.2 `wx_dim3d_scene_dependency_index`

按发布编号和实例批量读取依赖，避免预览期间逐实例查库。

关键字段：`publication_id`、`instance_id`、`domain`、`owner_type`、`resource_key`、`content_json`、`sort_no`。唯一键 `(publication_id, instance_id, domain, sort_no)`；索引 `(publication_id, instance_id)`。

依赖单条原文保留，`resources/dataBindings/behaviors/deviceBindings` 的元素不能提前转换成不完整实体。发布时生成索引，重发布时生成新发布编号，不原地改旧索引。

### 4.3 `wx_dim3d_scene_grant`

场景授权关系：`scene_id`、`grant_tenant_id`、`granted_by`、`status`、`granted_at`、`revoked_at`。唯一键 `(scene_id, grant_tenant_id)`；有效授权为 `status=1`。授权只扩展读取范围，不转移场景所有权或资源维护权。

## 5. 事务时序

### 5.1 保存

1. 请求入口验证令牌、平台、租户、场景所有权/编辑权限和锁令牌。
2. 事务外解析四包、校验版本、引用、路径和大小，计算规范摘要。
3. 事务内锁定 `wx_dim3d_scene`、`wx_dim3d_scene_draft` 和 `wx_dim3d_scene_lock`；再次比较租户、持锁用户、页签、租约及 `base_revision_no`。
4. 读取幂等记录。相同编号/摘要返回原结果；异摘要返回冲突。
5. 更新草稿四包、书签、场景更新时间和幂等成功回执；提交一次事务。
6. 释放数据库锁。旧签名、预览票据和运行会话不随保存变更。

任一四包校验、权限、锁、摘要或数据库写入失败，草稿和书签保持原值。对象存储写入不嵌入长事务；临时对象由操作清理策略处理。

### 5.2 发布

1. 验证当前用户可发布目标场景；编辑者只允许自己的场景。
2. 读取当前草稿及依赖，按 `revision_no` 固化发布快照和依赖索引。
3. 写入发布记录并更新场景 `published_publication_id`，同一事务完成。
4. 生成短期票据时再次检查发布状态、访问模式、用户/租户授权及过期时间。

发布撤销只更新发布状态和场景指针，不删除仍被其他发布或资源引用的对象。

## 6. 迁移顺序与回退

1. 备份当前 `wx_editor_test148`，记录散列和权限；迁移前检查所有现有表仍为 `wx_` 前缀。
2. 新增租户编码字段并回填；回填失败或重复时停止，不自动猜测。
3. 更新现有 OAuth2 客户端 `additional_information`，只对登记的编辑器应用启用平台元数据。
4. 依次创建场景、草稿、锁、幂等、发布、依赖和授权表及索引；所有脚本带固定 SHA-256，不修改已经执行的旧脚本。
5. 执行结构、约束、重复迁移和回退演练；未有场景写入前可删除新增表回退，开始业务写入后优先使用前向兼容修复。
6. 隔离端口验证登录、范围、锁、保存、发布和回退，再生成候选程序包切换。

迁移不复制旧 `metabase_3d` 业务资源，也不把旧 `wx_scene_def` 记录自动转换为新四包场景。历史数据迁移另建审计清单和映射任务。

## 7. M0-04 验收

- 结构脚本可重复检查，未修改已执行 ART 脚本；备份和回退路径明确。
- 租户编码唯一、大小写规范、重复回填停止；应用扩展 JSON 可解析且非法平台被拒绝。
- 保存并发、幂等重试、旧锁/旧书签和完整回滚有 MySQL 集成测试设计。
- 发布快照与草稿隔离；授权回收不删除共享资源；预览索引可按发布和实例批量读取。
- 所有字段、索引、写入方、保留和清理策略进入 M0-03 冻结合同的后端实现说明。
