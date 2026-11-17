# 有界容量测量工具

`measure-capacity.py` 通过真实 API 创建临时场景及设备绑定，用 MQTT 上报并核对认证 WebSocket 投递。工具不部署代理或接入应用，使用前必须准备隔离容量代理及其接入所有者，不能因管理端连接测试成功就假定有消费者。实际容量结果与剩余范围见[2026-11-17 证据](../../docs/evidence/iot-capacity-20261117/README.md)。

依赖 Python 3.10+ 与现有验收环境的 `websocket-client`，复用同目录 `probe-mqtt.py`。复制[配置样例](capacity-config.example.json)后填写真实网关、回环转发、信任证书及临时连接器。共享环境先建立隔离测试范围，不覆盖原连接器或设备。认证材料通过标准输入 JSON 提供，字段为 `username`、`password`、`mqttPassword`；从受限身份存储直接传入进程，不把材料写入配置、命令行或日志。

所有资源清单、报告和编译缓存放 D 盘。依次执行以下命令，每次重新通过标准输入提供认证材料：

```powershell
python tools/iot/measure-capacity.py prepare --config D:/iot-capacity/config.json --state D:/iot-capacity/resources.json --report D:/iot-capacity/prepare.json --devices 1000
python tools/iot/measure-capacity.py run --config D:/iot-capacity/config.json --state D:/iot-capacity/resources.json --report D:/iot-capacity/baseline.json --devices 100 --clients 5 --cycles 2
python tools/iot/measure-capacity.py run --config D:/iot-capacity/config.json --state D:/iot-capacity/resources.json --report D:/iot-capacity/target.json --devices 1000 --clients 50 --cycles 12
python tools/iot/measure-capacity.py cleanup --config D:/iot-capacity/config.json --state D:/iot-capacity/resources.json --report D:/iot-capacity/cleanup.json
```

以上展示参数，未展示秘密注入命令。`prepare` 不覆盖已有清单，创建成功后逐条记录绑定编号；失败时用 `cleanup` 按本批临时场景补查并清理。若场景中出现其他来源绑定则拒绝自动清理。只有等待已发布消息收敛后才清理绑定，避免将仍在代理排队的消息变为未绑定报文。接收记录继续保留审计。

每轮设备均匀上报，固定 5 秒周期；每条客户端连接订阅全部选定设备，分批 20 条兼容当前传输限制并每 25 秒续租。当前实现是一个网关连接、一个认证账号、若干协议客户端，不是浏览器渲染或独立设备连接测试。最多 1000 个逻辑设备、50 条连接、12 轮，不能用于无界压力。

积压超过 10 秒、客户端错误或发送调度落后 2 秒会停止上报，再观察最多 20 秒。报告区分发送确认数、收到的唯一投递、重复、缺失和延迟分位数。5 秒为工程观察门槛，正式业务指标仍需签收；消息收齐且第 95 百分位不超过门槛才通过。未通过退出码为 2，工具异常为 1。MQTT 发布确认只表示代理接收，不能替代后端持久接收、Kafka 发布或每个客户端收到的完整性验证。

```powershell
python tools/iot/test_measure_capacity.py
```

此命令仅验证分位数和订阅分批，不触达真实环境，也不替代容量测量。
