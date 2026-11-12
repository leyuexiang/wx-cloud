# IoT 连接器管理接口

源码与文档核对日期：2026-11-10。范围：IOT-M2 后端接口，包含分页、MQTT 配置预校验、数据库运行快照和受限连接测试。按用户最新指令，当前优先完成后端管理接口及网页管理端；本文列明现有合同和首批待补接口，不表示已完成页面或线上联调。完整凭据存储与轮换另属后续子项。

## 1. 使用前提

通过既有网关的 `/admin-api/iot/connector` 访问，沿用登录令牌、租户上下文和连接器权限。示例省略公共鉴权头，不能将租户请求头单独视为登录凭证。

新接口不要求启动 MQTT 工作进程。运行查询依赖 M1 的数据库字段，旧库仍须先执行[运行字段迁移脚本](../../wx-module-iot/db/iot-connector-runtime-migration.sql)，本次没有新增表结构或自动迁移。真实网关、令牌服务和数据库兼容性需实际联调。

## 2. 分页管理列表

```http
GET /admin-api/iot/connector/page?pageNo=1&pageSize=20&connectorType=MQTT&enabled=true
```

权限：`iot:connector:query`。参数：

| 字段 | 含义 |
|---|---|
| pageNo | 默认 1，至少为 1 |
| pageSize | 默认 10，只允许 1—100，不接受 -1 全量查询 |
| connectorId | 可选，按业务编号模糊筛选，最多 64 字符 |
| connectorType | 可选，确切值 MQTT、HTTPS 或 MOCK |
| enabled | 可选，筛选管理员期望的启用状态 |

返回现有 `CommonResult<PageResult<IotConnectorRespVO>>` 结构：`data.list` 和 `data.total`。分页、筛选及主键降序在数据库完成，显式限制当前租户；每页查询没有按连接器追加数据库请求。

原 `/list` 保留兼容。管理页面应优先使用 `/page`；其 `status` 仍是历史持久状态，不能单独用来判断当前网络状态。编辑时提交返回的 `version`，避免旧编辑页面覆盖新配置。

## 3. MQTT 配置预校验

```http
POST /admin-api/iot/connector/validate-mqtt-config
Content-Type: application/json

{
  "endpoint": "ssl://broker.example.com:8883",
  "credentialRef": "IOT_MQTT_PARK",
  "configJson": "{\"topics\":[\"park/telemetry\"],\"qos\":1}"
}
```

权限：`iot:connector:create` 或 `iot:connector:update`。三个字段均必填；地址最多 512 字符、凭据引用最多 128 字符、配置 JSON 最多 65536 字符。不要求连接器已保存或启用。

返回示例：

```json
{"code":200,"data":{"scope":"MQTT_STRUCTURE","topicCount":1,"qos":1},"msg":"成功"}
```

该接口复用保存、启用及运行时的 MQTT 解析规则：默认只接受 `ssl://主机:端口`，普通 `tcp://` 仍受部署的 `wx.iot.mqtt.allow-insecure` 开关限制；只支持 1—32 个确切主题和 QoS 1，主题去重后返回数量。配置不满足规则时使用既有 `CONNECTOR_CONFIG_INVALID` 业务错误；参数缺失或过长由参数校验处理。

接口不写数据库、不读取环境变量中的秘密、不解析目标 DNS、不连接消息代理（Broker）。`MQTT_STRUCTURE` 仅表示约定参数结构可接受；凭据引用是否已注入、证书是否可信、网络是否连通、账号和主题权限是否有效，以及设备绑定是否正确，仍需实际接入验证。响应不回显配置、凭据或草稿客户端编号。

## 4. 运行状态与租约快照

```http
GET /admin-api/iot/connector/runtime?id=1024
```

权限：`iot:connector:query`。连接器不存在或属于其他租户时返回相同的 `CONNECTOR_NOT_EXISTS` 业务错误。查询同时按主键、当前租户和未删除条件定位，不访问其他租户兜底。

`data` 示例：

```json
{
  "id": 1024,
  "connectorId": "park-mqtt",
  "connectorType": "MQTT",
  "enabled": true,
  "version": 4,
  "persistedStatus": "CONNECTED",
  "effectiveStatus": "LEASE_EXPIRED",
  "leaseActive": false,
  "runtimeLeaseUntil": "2026-10-21T09:00:00",
  "observedAt": "2026-10-21T09:00:01"
}
```

示例时间为便于阅读采用 ISO 字符串；当前公共 Jackson 配置实际输出毫秒时间戳，网页兼容两者，不按秒放大。`version` 是当前配置版本，不能解释为已加载的运行版本。`observedAt` 与期限比较取自同次查询的数据库时间，避免用管理服务机器的时钟判断数据库租约。

按以下优先顺序得出 `effectiveStatus`：

| 条件 | 状态 | 管理界面建议含义 |
|---|---|---|
| 未启用 | DISABLED | 管理员已停用；旧资源清理可能仍在途 |
| 已启用但非 MQTT | NOT_APPLICABLE | 此运行观测仅适用 MQTT 租约 |
| 已启用 MQTT，没有数据库所有者 | UNOBSERVED | 尚未接管、已正常释放或处于 SINGLE 模式；不能确认实时状态 |
| 有所有者，但期限缺失或已到期 | LEASE_EXPIRED | 历史状态失去有效租约支撑，不能显示为已连接 |
| 有效数据库租约 | CONNECTED / DISCONNECTED / AUTH_FAILED / ERROR | 展示运行器最近持久化的已知状态 |
| 有效租约，但状态为空或不是已知运行状态 | UNKNOWN | 缺少可识别的运行结果 |

响应不包含所有权令牌、配置 JSON、地址或凭据引用。`leaseActive` 只反映数据库租约，在管理员停用但旧清理未完成时也可能暂为 true；展示状态应使用 `effectiveStatus`。数据库租约有效不等于 Redis、套接字、设备、Kafka 或三维链路已通过主动探测；运行器故障检测与写回也存在周期延迟。

该接口用于选中连接器的详情刷新。列表不应为每一行不断调用 `/runtime`；后续需要列表实时监控时，再设计有上限的批量快照或定向推送。最近消息时间、实际运行版本、所属实例、重试时间和脱敏错误分类仍待补充，当前不伪造这些字段。

## 5. 已保存 MQTT 连接测试

```http
POST /admin-api/iot/connector/test-mqtt
Content-Type: application/json

{"id":1024,"timeoutSeconds":10}
```

权限：`iot:connector:update`。请求只能引用当前租户已保存的连接器，服务端重新读取并解析地址、主题、QoS 和凭据引用；不会接受请求体中的临时地址或密码。超时范围为 1—15 秒，默认 10 秒。

测试使用一次性随机客户端编号和 MQTT 清洁会话（`cleanSession=true`），连接并订阅所有已配置的确切主题，要求 Broker 对每个主题授予 QoS 1。测试不取得 Redis 租约、不写数据库运行状态、不进入 Inbox/Kafka、不处理遥测回调，结束时强制关闭客户端。

响应只返回连接器编号、结果分类、耗时、主题数量和脱敏说明：`SUCCEEDED`、`UNSUPPORTED_PROTOCOL`、`CREDENTIALS_UNAVAILABLE`、`CONNECT_FAILED`、`SUBSCRIBE_FAILED` 或 `TIMEOUT`。

连接测试成功只证明本次认证、网络和订阅权限可用，不表示正式动态会话已接管，也不证明设备上报、Inbox、Kafka 或三维链路正常。当前默认凭据仍由环境引用解析；测试不会把密码回传到接口或日志。

## 6. 后续验收

先完成真实数据库迁移与租约查询兼容验证，再经网关核对未登录、无权限及跨租户访问。管理页面可据以上契约开发，但当前尚未交付页面。主动连接测试需要独立资源上限、超时和部署出口策略；凭据轮换需要确定秘密存储、版本及实际重连生效方式。

进度和验证证据见[IoT 当前状态](../plan/02_IoT服务进度与后续计划.md)及[本轮记录](../records/04_20261021连接器管理接口验证记录.md)。

## 7. 首批网页管理接口交接

实际源码使用当前 `CommonResult` 的成功码 200；旧示例中的 0 已按源码纠正。网关 HTTP 成功不等于业务成功，页面须使用现有请求层约定统一处理；不得兼容性猜测所有非空 data 都是成功。编号、时间、分页和菜单权限的完整合同由 IOT-M2-01 收口。

| 页面 | 已有控制器与方法 | 首批待补/核验 |
| --- | --- | --- |
| 连接器管理 | `/admin-api/iot/connector` 下 page/get/create/update/delete/enable/disable/runtime/validate-mqtt-config/test-mqtt | 配置版本冲突、端到端权限、测试资源上限及前端请求契约；复用现有服务 |
| 设备绑定/点位映射 | `/admin-api/iot/device-binding` 下 create/update/delete/get/list/page | 本批补 page 和保存前整份映射校验；数据库分页与实际网关/身份验收分别登记 |
| 接收记录/失败诊断 | `/admin-api/iot/message-inbox/page` 与 POST replay，重放参数为 eventId | 页面安全字段、失败提示、重放状态、真实 Kafka 依赖及租户负向用例 |

当前 Web 已按本节合同实施三个正式 Vue 页面，复用布局、认证、组件及权限；实现、部署和真实联验分别登记。凭据仅使用已部署引用；不在网页保存明文秘密，不把数据库租约快照描述为设备/消息系统健康证明。完整首批范围及主状态见[专项执行顺序](../plan/02_IoT服务进度与后续计划.md#42-近期子项与执行顺序)。

### 7.1 首批合同冻结（IOT-M2-01，2026-11-10）

- 范围冻结为当前三个控制器的实际接口与本批新增绑定分页；统一通过 `/admin-api/iot/...` 请求，跳过旧运营/租户路径插段，令牌、租户头与失效处理复用共享请求层。平台名不赋予额外 IoT 权限。
- 响应 `{code:200,data,msg}`；分页 `data={list,total}`，pageNo 从 1 开始、pageSize 为 1～100。所有主键接收安全整数或字符串，进入页面即转字符串；已失精度的数值拒绝，业务编号不转换数字。日期按当前公共毫秒时间戳解析。
- 连接器编辑携带详情 version；1009001011 表示冲突，保留表单并要求重新读取后核对。创建/更新不改变启用意图，启停使用单独接口。绑定目前采用最后保存生效，没有版本条件，页面明确协作限制，不伪造乐观锁。
- 绑定分页 `GET /admin-api/iot/device-binding/page?pageNo=1&pageSize=10`；可选 connectorId（最长64）、externalDeviceId（最长128）为模糊匹配，enabled 为布尔值。Mapper 同时使用显式可信租户条件和现有租户插件，不接收浏览器提交 tenantId；保留旧 list。
- 绑定保存允许空映射，统一落为 `{}`，以便 JSON 列有效且能清除旧规则；points 必须是点位到对象的映射，scale/offset 只接受 JSON 数字或默认值，valueType 仅 NUMBER/BOOLEAN/STRING/JSON。文本上限65536，不在前端重写十进制原文。
- 接收记录只返回安全字段；底层异常原文不传浏览器，errorMessage 为固定失败摘要，载荷仍不出管理响应。人工重放仅适用于 FAILED/DEAD_LETTER，返回 true 表示已发起，不表示 Kafka 已确认；最终以重新查询的状态为准。
- 标准 `/system/auth/get-permission-info` 的 menus 跟随当前可信身份，独立会话恢复重读；标准后台的内嵌/交接身份以同一令牌补读。IoT组件标识为 `iotManage/connectors/index`、`iotManage/bindings/index`、`iotManage/messages/index`，菜单与query权限同时满足才可进入；按钮/处理器再校验写权限。用户随后要求全部统一，资源/场景也使用同一授权树与白名单，菜单缺失进入恢复页，具体规则由[统一设计](../../../../Web/web-unity/docs/design/02_管理导航统一设计.md)覆盖旧静态兼容约定。
- 菜单复用系统菜单及角色分配接口，不能直接写库绕过缓存，也不能自动给所有角色追加权限；租户套餐须包含所需菜单。实际目标租户/角色授权及部署后路由由 M2-04 记录，不影响本批代码合同冻结。

### 7.2 统一菜单配置执行（2026-11-11）

资源、场景与IoT清单统一采用`schemaVersion=1`和`groups/pages/buttons`，由`deploy/test148/management_menus.py`共同读取，不再由两个不同结构的脚本手工配置。固定三个同级分类和七页面组件，已有查询权限按钮按权限复用，不移动其原后台位置；停用/隐藏页面、重复标识、跨父级页面或页面权限不一致均停止并核对。

先经系统接口生成带目标/清单/现场摘要的计划，再执行并逐步留证；回退恢复修改项、新建项仅停用保留。角色仅生成显式增量、不自动授予，模板与资源写权限不由IoT身份推导。套餐变更会同步相关租户，必须先核对影响和备份。操作命令、18项回归和148现场缺失套餐/有效平台身份等前置见[本批记录](../evidence/iot-management-20261111/README.md)。记录只读数据库快照不代替标准系统接口执行。

### 7.3 点位映射精度修正（2026-11-11）

真实MySQL回读发现JSON列会将映射数字`0.1000000000000000001`存为`0.1`，此前仅比较前15位的用例不能证明精度。点位映射改为保留原文的LONGTEXT并增加JSON_VALID检查约束，服务仍限制65536字符、只接受JSON对象及数字类型的scale/offset；接口继续传递JSON字符串，不改变调用格式。运行时使用复用的精确十进制ObjectReader，不能先解析成double再转BigDecimal。

旧表通过独立增量脚本修改，不重跑初始化；先备份、停止本批IoT候选写入，比较迁移前后映射文本摘要与行数。已由旧JSON列舍入的历史值无法自动恢复，不伪称已恢复精度；本轮隔离库仅有本批已软删除的验收样本。MQTT仍关闭，精度修正经单元及真实数据库完整原文回读验证后才切换网关。
