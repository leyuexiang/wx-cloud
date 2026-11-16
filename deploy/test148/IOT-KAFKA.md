# 148 物联网持久化消息服务

本工具仅部署 148 测试节点的专用 Kafka，应用接入、246 通道及网页入口单独切换。它不部署设备消息代理，也不让 Unity 或浏览器直接连接 Kafka。后端链路为 MQTT 接入 → Kafka 遥测 → 三维状态投影 → Kafka 定向分发 → 已认证 WebSocket → 前端与 Unity。

## 部署边界

| 项目 | 配置 |
| --- | --- |
| 容器及持久卷 | `wx-iot-kafka148`、`wx-iot-kafka148-data` |
| 客户端及控制器 | 仅 `127.0.0.1:19092`、`127.0.0.1:19093`；主机网络 |
| 认证和授权 | 双向传输层安全协议（mTLS），证书主机名校验开启，主题访问控制列表（ACL）默认拒绝 |
| 配置和证书 | `/opt/wx/editor-test148/iot-kafka148`；私钥和密码仅在目标主机受限文件中 |
| 镜像 | `apache/kafka:3.9.2`；首次部署保存本机镜像内容编号，恢复固定使用该编号 |
| 资源及日志 | 1200 MiB 容器上限、512 MiB 堆上限；日志 10 MiB × 3；`unless-stopped` |
| 数据边界 | 单节点、单分区、单副本；遥测及失败隔离保留 7 天，定向分发 2 小时，预检 1 小时；每分区保留字节上限 512 MiB，分段 64 MiB/1 小时 |

保留时间和字节限制都参与删除判断，不保证完整保留天数。当前部署不是高可用集群，重启策略也不能替代备份和整机故障恢复。证书有效期为 365 天、内部签发机构为 730 天，应在到期前安排受控轮换；本批未验收证书轮换。

## 初始化与恢复

在 148 以受控管理员身份执行，先核对镜像、端口、磁盘空间和同名资源。本工具不拉取镜像，不覆盖已有目录或卷。

```sh
python3 deploy/test148/provision-iot-kafka.py provision
python3 deploy/test148/provision-iot-kafka.py verify
```

首次初始化中断后使用 `resume` 接续。它复用原证书、镜像、卷及集群编号，检查已有容器的镜像、网络和挂载；原卷丢失、集群编号不匹配或已交付卷缺少元信息时拒绝恢复，禁止用同名空卷掩盖数据丢失。首次格式化前中断允许继续原初始化。

```sh
python3 deploy/test148/provision-iot-kafka.py resume
```

`verify` 执行带证书的健康和主题可见范围检查，不重启。`verify-restart` 会写入一条独立预检消息、检查两种服务身份的越权写入拒绝，然后重启专用容器并读取原偏移记录；只在授权的故障演练时运行。结果写入节点的 `verification.json`，无密码和私钥。

备份必须同时保留受限配置目录、集群编号及对应持久卷；数据一致性备份先有序停服务，按现场备份制度保存加密副本。恢复时禁止删卷后重跑 `provision`。本批验证的是容器重启留存，尚未执行备份还原演练。

## 服务身份及最小权限

| 身份 | 允许范围 |
| --- | --- |
| `wx-kafka-ingest` | 写及描述 `twin.event.v1`；集群幂等写权限 |
| `wx-kafka-dimensions` | 读及描述遥测；写及描述 `twin.event.v1.dimensions.dlt`；读写及描述 `dimensions-iot-websocket.v1`；读取组 `dimensions-iot-state-v1` 和前缀 `dimensions-iot-websocket-`；集群幂等写权限 |
| `wx-kafka-management246` | 供 246 管理服务处理失败重放。写及描述 `twin.event.v1`，具备集群幂等写权限，使用独立于设备接入的证书 |
| `wx-kafka-admin` | 仅节点内初始化和检查使用，不挂载进业务应用 |

应用仅挂载自身的证书包和签发机构公钥，禁止挂载整个节点证书目录。Kafka 端口不向公网开放；246 接入应扩展已有受限通道的确切端口权限，并为对应进程配置受控证书，不能放开任意转发。

Spring 应用配置如下，密码通过受限外部配置注入。证书路径和密码沿用节点生成文件，不写进仓库。

```properties
spring.kafka.bootstrap-servers=127.0.0.1:19092
spring.kafka.properties.security.protocol=SSL
spring.kafka.properties.ssl.keystore.type=PKCS12
spring.kafka.properties.ssl.truststore.type=PEM
spring.kafka.properties.ssl.endpoint.identification.algorithm=https
spring.kafka.properties.ssl.enabled.protocols=TLSv1.3,TLSv1.2
spring.kafka.listener.auto-startup=false
```

三维应用另外启用本批两个消费者，保持历史业务监听关闭。省略专用开关时仍继承原全局开关，兼容既有部署。

```properties
wx.iot-state.enabled=true
wx.iot-state.consumer-group=dimensions-iot-state-v1
wx.iot-state.listener-auto-startup=true
wx.websocket.sender-type=kafka
wx.websocket.sender-kafka.topic=dimensions-iot-websocket.v1
wx.websocket.sender-kafka.consumer-group=dimensions-iot-websocket
wx.websocket.sender-kafka.auto-startup=true
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=cn.iocoder.wx.framework.websocket.core.sender.kafka.KafkaWebSocketMessageSerializer
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=cn.iocoder.wx.framework.websocket.core.sender.kafka.KafkaWebSocketMessageDeserializer
spring.kafka.consumer.auto-offset-reset=latest
```

## 切换与回退

先保存应用容器配置、原包和外部配置的受限备份，在隔离端口验证相同身份、数据库、租户认证、遥测及发布订阅，再逐个切换常驻应用。148/246 仍沿用既有分机职责，网页和网关单独备份及切换。Kafka 正常不代表这些入口已经交付。

在各应用节点用 `connect-resident-iot.py prepare <服务> --artifact <绝对路径> --sha256 <包摘要>` 生成受限预检记录，再执行 `apply <服务>`。148 服务参数为 `dimensions148`、`ingest148`，246 服务参数为 `management246`。工具检查容器身份、源配置摘要、传输层认证以及三维消息编解码。应用就绪后还要用认证订阅验证确认帧和实时状态，健康端点无法证明投递正确。

使用 `rollback <服务>` 恢复本批保留的原容器，工具先比对两代容器编号。246 回退时同步恢复该节点受限发布目录中的 `compose.before.private.json`，用于后续编排操作。Kafka 复用 246 的受限 SSH 通道，新增 `127.0.0.1:19092 → 148 回环 19092`。通道回退须先停用 246 的 Kafka 生产者，再恢复 246 的 `link.before` 和 148 对应节点公钥的原端口许可，随后重启 `jtyf-editor148-link.service`。

应用切换失败时停止新消费者和接入进程，恢复原包、配置及容器，不删除 Kafka 卷或修改业务接收记录。故障期间已接收消息按既有失败重放流程恢复；不能把 `CLAIMED` 当作已发布，不能未经确认批量重放。停用本批依赖时执行 `docker stop wx-iot-kafka148`，保留证书与数据；常驻应用接入后先停止其写入和消费。

本批实际执行结果见[验收证据](../../docs/evidence/iot-m3-03c-20261116/README.md)。
