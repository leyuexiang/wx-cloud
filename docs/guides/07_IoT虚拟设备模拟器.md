# IoT 虚拟设备模拟器

当前公司没有物理物联网设备时，使用 `deploy/iot/simulator/mqtt_simulator.sh` 作为设备端模拟器。它通过真实 MQTT Broker 发布与设备相同的单点 JSON 报文，IoT 服务、Inbox、Kafka、Redis 和三维服务继续使用真实链路。

## 运行前准备

在安装了 `mosquitto_pub` 的测试主机或 Mosquitto 容器中运行。凭据通过进程环境注入，不写入脚本、命令历史或日志。

```bash
export MQTT_HOST='192.168.10.246'
export MQTT_PORT='48883'
export MQTT_TLS='true'
export MQTT_CA_FILE='/run/secrets/iot-test-ca.pem'
export MQTT_TOPIC='acceptance/iot/tenant-2/telemetry'
export MQTT_USERNAME='writer'
export MQTT_PASSWORD='由受控凭据注入'
export EXTERNAL_DEVICE_ID='virtual-device-001'
export POINT_CODE='rawTemperature'
```

## 模拟场景

### 正常遥测

每五秒发布一个递增温度值。

```bash
SCENARIO=normal INTERVAL_SECONDS=5 START_VALUE=25 STEP=0.5 \
  ./deploy/iot/simulator/mqtt_simulator.sh
```

### 重复消息

使用同一追踪编号发布两次，验证接收去重和业务效果幂等。

```bash
SCENARIO=duplicate MAX_MESSAGES=1 \
  ./deploy/iot/simulator/mqtt_simulator.sh
```

### 乱序消息

先发布当前值，再发布三秒前的旧值，验证最新状态不会回退。

```bash
SCENARIO=out_of_order MAX_MESSAGES=1 \
  ./deploy/iot/simulator/mqtt_simulator.sh
```

### 非法报文

发布版本无效且缺少必要字段的报文，验证失败记录或死信处理。

```bash
SCENARIO=invalid MAX_MESSAGES=1 \
  ./deploy/iot/simulator/mqtt_simulator.sh
```

### 断线恢复

发布一条消息后退出模拟器，重新启动同一设备编号，验证连接释放和恢复后的继续处理。

```bash
SCENARIO=disconnect MAX_MESSAGES=1 \
  ./deploy/iot/simulator/mqtt_simulator.sh
```

## 验收记录

每个场景记录 `traceId`、服务端 `eventId`、外部设备编号、内部设备编号、点位、事件时间、Inbox 状态、Kafka 分区偏移、Redis 快照和 Unity 画面结果。模拟器覆盖软件链路、映射和故障语义，不能替代传感器精度、固件行为和现场无线网络验收。
