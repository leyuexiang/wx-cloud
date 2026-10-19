# IoT 服务开发环境部署与联调

适用日期：2026-10-17。服务：`iot-server`，端口：`48096`。本说明用于已有微服务体系的开发联调；需要系统服务提供令牌验证、租户校验和权限查询，不将 IoT 当作无认证的独立接口运行。

## 一、环境配置方案

复用既有动态数据源、Redis、Nacos 和 Kafka 自动配置，新增 `application-local.yaml` 补齐当前默认 `local` 环境。不修改其他服务的配置。数据库及 Nacos 密码必须从环境变量提供，仓库模板不包含真实凭据。

Kafka 生产者使用 `acks=all` 与 `enable.idempotence=true`，保留既有重试次数；这可以加强生产端确认及重试行为，但不能保证跨数据库和 Kafka 的恰好一次处理。数据库抢占、异步令牌回调及下游幂等仍需保留。

不关闭租户校验，不开启模拟登录，不加入匿名管理接口。无实际使用场景的 XXL-Job 执行器默认关闭；访问日志沿用现有框架，需要基础设施服务支持。

配置测试验证 YAML 加载、部署参数覆盖、必填密码和生产者关键配置；现有接入与重放行为测试同时回归。不在测试中连接真实数据库或泄露宿主机凭据。

## 二、启动前条件

| 依赖 | 需要核对的内容 |
|---|---|
| Java | 使用 Java 17 |
| 数据库 | 专用应用账号可访问 IoT 三张表，不能仅测试 3306 端口 |
| Redis | 地址、数据库编号、认证及连通性 |
| Nacos | 8848 与 9848 客户端通信可达，账号具备配置与注册权限 |
| Kafka | 可用 Broker 地址、客户端可达的 advertised.listeners、主题权限 |
| 系统服务 | 有效租户，管理登录和 IoT 权限已配置 |
| 基础设施服务 | 框架访问日志等远程接口按项目环境部署 |

截至 2026-10-18 只读核验，`192.168.10.148` 有 Redis、Nacos、MinIO；Kafka UI 的 `KAFKA_CLUSTERS_0_BOOTSTRAP_SERVERS` 配置为 `kafka:9092`，但其 Docker 网络内不存在 `kafka` 主机，容器内解析失败；本机也未监听 9092/29092/9093。Kafka UI 正常不等于 Broker 可用，当前不能进行 IoT 实时链路联调。外部 Kafka 应显式填写实际地址，不默认假定 9092 已运行。

## 三、环境变量

参见 `deploy/iot/iot.env.example`。示例使用服务器本机地址；如果在 Windows 启动应用，应将依赖地址改成 `192.168.10.148` 或实际服务地址。

| 变量 | 说明 |
|---|---|
| `IOT_DB_URL` | 完整 JDBC 地址，默认数据库名 `wx_iot`，不会自动创建数据库 |
| `IOT_DB_USERNAME` | 应用账号，默认 `iot_app` |
| `IOT_DB_PASSWORD` | 必填，无默认口令 |
| `IOT_REDIS_HOST` / `IOT_REDIS_PORT` | Redis 地址及端口 |
| `IOT_REDIS_DATABASE` / `IOT_REDIS_PASSWORD` | 数据库编号及认证密码；无认证时密码可为空 |
| `IOT_NACOS_SERVER_ADDR` | Nacos 地址 |
| `IOT_NACOS_USERNAME` / `IOT_NACOS_PASSWORD` | Nacos 账号及必填密码 |
| `IOT_NACOS_NAMESPACE` | 配置及注册命名空间 ID，默认空值为 public；应与系统服务一致 |
| `IOT_NACOS_GROUP` | 配置和注册分组，默认 `DEFAULT_GROUP` |
| `IOT_KAFKA_BOOTSTRAP_SERVERS` | Kafka 客户端入口，可用逗号分隔多个地址 |
| `IOT_ADMIN_UI_URL` | 管理前端地址，用于满足 Web 框架配置约束 |
| `IOT_WEBHOOK_...` | 每个 HTTPS 连接器引用的独立签名密钥，至少 32 字节 |

Nacos 的 `iot-server-local.yaml` 仍可能提供共享配置。不要在 Nacos 中重复配置同名关键参数造成意外覆盖；实际生效值需在部署时核对。Kafka 若启用 SASL/TLS，应通过受限外部配置补充认证及证书，本文模板不适用于直接连接需要认证但尚未配置凭据的 Broker。

## 四、数据库准备

由数据库管理员创建目标库并授予应用账号所需权限；不要使用服务器 root 密码作为数据库密码。

新环境在确认目标数据库后执行：

```bash
# -p 交互输入数据库密码，命令行不包含真实口令；脚本中的 IF NOT EXISTS 不会更新旧表结构。
mysql -h 127.0.0.1 -u iot_app -p wx_iot < wx-module-iot/db/iot.sql
```

旧环境必须核对现有表结构与迁移历史，备份后按原规划执行快照、令牌及表前缀迁移；不要把新建表脚本当成自动升级脚本。当前服务器为 MariaDB 10.3.28，需验证 JDBC、JSON 字段、唯一键与时间表达式；没有完成 SQL 验证前不得宣布兼容。

## 五、构建与启动

在项目根目录执行：

```bash
# 构建并运行 IoT 及其依赖测试，不跳过本阶段回归检查。
mvn -pl wx-module-iot/wx-module-iot-biz -am package -Dtest=Iot*Test -Dsurefire.failIfNoSpecifiedTests=false -DskipTests=false
```

在 Linux 服务器上，将受限环境文件放在 `/etc/wx/iot.env`，由部署人员填入真实值。该文件应仅对运维账号可读，不能提交 Git。下列加载方式要求文件内容为有效 Bash 赋值，变量值应使用单引号包裹：

```bash
# 仅加载可信任、由部署人员维护的环境文件；不要从不可信来源获取并执行。
set -a
. /etc/wx/iot.env
set +a
java -Xms256m -Xmx1024m -Dfile.encoding=UTF-8 -jar wx-module-iot-biz.jar --spring.profiles.active=local
```

应用不会自动建表，不会自动部署 Kafka，不会创建 Nacos 命名空间。不要为绕过启动错误而关闭权限或租户校验。

## 六、验收顺序

1. 确认应用启动无异常，Nacos 中出现 `iot-server`，地址与端口可被网关访问。
2. 使用有效管理员令牌和 `tenant-id`，通过连接器管理接口创建、启用 HTTPS 连接器，配置对应签名密钥引用。
3. 建立外部设备绑定，明确 `pointMappingJson` 采用当前实现的点位编码对象结构。
4. 调用签名遥测接口 `POST /open-api/iot/v1/connectors/{connectorId}/telemetry`，验证签名错误、过期时间、错误租户均被拒绝。
5. 确认接收记录（Inbox）最终状态为 `PUBLISHED`，并在 Kafka 消费端核对事件；HTTP 成功仅表示接收，不等于 Broker 已确认。
6. 重复同一报文，确认相同事件编号不重复发布；模拟 Broker 不可用，验证失败查询和人工重放。
7. 使用其他租户查询记录和连接器，验证不可跨租户读取与重放。

签名原文：UTF-8 编码的 `timestamp + "\n" + tenantId + "\n" + connectorId + "\n"` 后追加原始请求体字节，算法为基于哈希的消息认证码（HMAC-SHA256）。请求头提供 `X-Iot-Timestamp` 和 `X-Iot-Signature`；时间戳为 Unix 秒。请求体必须包含稳定的 `eventTime` 与 `traceId`，重试不得重新生成它们。

## 七、完成标准

配置文件测试和单元测试通过仅说明本地配置与编排符合预期。真实数据库、Nacos 注册、网关鉴权、Kafka 发布及跨租户负向用例均有证据后，才能标记最小 HTTPS 接入链路验收完成。MQTT、三维投影、规则消费和下行回执仍按独立阶段开发。
