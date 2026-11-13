# IOT-M3-03 常驻链路候选验收（2026-11-13）

## 结论

在 148 隔离环境使用临时 MQTT Broker 与单节点 Kafka 完成一次端到端候选验收。消息从 MQTT 进入 IoT 接收记录，写入 `twin.event.v1`，由三维状态投影消费并写入 Redis。验收完成后已删除临时 Broker、测试连接器、设备绑定和测试环境变量，常驻服务恢复原配置。

## 现场证据

- MQTT 连接测试返回 `SUCCEEDED`，订阅主题数量为 1。
- 外部设备 `iot-resident-sensor-01` 上报 `rawTemperature=31.2`，映射为 `temperature`，单位为 `C`，质量为 `GOOD`。
- IoT Inbox 最新记录状态为 `PUBLISHED`，事件编号为 `3c68674d-d0cb-388c-996c-43129733cc02`。
- Kafka 消费组 `dimensions-iot-state-acceptance-v1` 已消费 `twin.event.v1` 分区 0，当前偏移与日志末端偏移均为 1。
- Redis 状态键 `twin:iot-state:v1:2:aW90LXJlc2lkZW50LWRldmljZS0wMQ:dGVtcGVyYXR1cmU` 保存了租户 2、设备 `iot-resident-device-01`、点位 `temperature` 的最新快照，TTL 约 24 小时。
- 租户 2 管理员已获得 `dimensions:iot-state:query`，查询接口返回 `31.2°C`；同一令牌伪造 `tenant-id: 1` 返回 `403`，证明查询租户取自认证身份。

## 清理结果

- 测试连接器和设备绑定已通过管理接口删除，按连接器筛选结果均为空。
- 临时 Mosquitto、Kafka 容器及数据目录已删除。
- `/opt/wx/editor-test148/.env` 中的临时 MQTT 变量已删除。
- `wx-editor-dimensions-data08` 的 Kafka 监听和 IoT 状态投影已恢复关闭，IoT 与三维容器均为健康状态。

## 剩余事项

- 源码菜单清单和租户 2 专用套餐已应用隐藏权限 `dimensions:iot-state:query`，授权查询已完成现场复验。
- 本次使用临时单节点依赖，只证明候选链路，不代表生产 Broker/Kafka 的高可用、认证和权限配置。
- Web 状态查询已实现并完成本地测试，Unity 画面联动和浏览器验收尚未执行。
