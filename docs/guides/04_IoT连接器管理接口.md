# IoT 连接器管理接口

核对日期：2026-10-23。范围：IOT-M2 后端接口，包含分页、MQTT 配置预校验、数据库运行快照和受限连接测试。管理页面、凭据存储与轮换仍待实施。

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
{"code":0,"data":{"scope":"MQTT_STRUCTURE","topicCount":1,"qos":1},"msg":""}
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

时间示意采用 ISO 字符串；实际序列化格式沿用平台公共时间配置。`version` 是当前配置版本，不能解释为已加载的运行版本。`observedAt` 与期限比较取自同次查询的数据库时间，避免用管理服务机器的时钟判断数据库租约。

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
