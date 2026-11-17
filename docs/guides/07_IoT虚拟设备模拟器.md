# IoT 虚拟设备模拟器

当前公司没有物理物联网设备时，使用 `deploy/iot/simulator/mqtt_simulator.sh` 作为设备端模拟器。它通过真实 MQTT Broker 发布与设备相同的单点 JSON 报文，IoT 服务、Inbox、Kafka、Redis 和三维服务继续使用真实链路。

## 148 双设备试运行与 MQTTX

当前测试租户的连接器为 `iot-resident-20261113`，页面名称为“两台虚拟设备 MQTT 试运行”。它通过 148 本机代理订阅下表两个主题。测试场景名为“IoT 虚拟设备试运行”，场景编号为 `2122269441737682946`。MQTTX 是设备端发布客户端，平台的接收记录和画面通过 148 网页查看。[MQTTX 连接和发送说明](https://mqttx.app/docs/get-started)

| 模拟设备 | MQTTX 用户名 | 发布主题 | 外部设备编号 | 平台点位 |
| --- | --- | --- | --- | --- |
| 开关 | `lab-switch` | `lab/iot/tenant-2/switch` | `virtual-switch` | `lab-switch-01 / visible` |
| 温度 | `lab-temperature` | `lab/iot/tenant-2/temperature` | `virtual-temperature` | `lab-temperature-01 / temperature` |

在有 148 受控 SSH 权限的 Windows 测试工作站，打开 Windows PowerShell 5.1 或 PowerShell 7，在仓库根目录执行下列命令。转发命令所在终端保持运行。`mqttx-lab.ps1` 使用带字节顺序标记（BOM）的 UTF-8，使 Windows PowerShell 5.1 能正确读取中文。

```powershell
./tools/iot/mqttx-lab.ps1 -Action tunnel
```

用另一个 PowerShell 终端下载测试代理的公开签发机构证书，并把当前设备密码复制到剪贴板。

```powershell
./tools/iot/mqttx-lab.ps1 -Action ca
./tools/iot/mqttx-lab.ps1 -Action password -Device switch
```

在 MQTTX 左侧 **Connections** 标题旁点击 **＋**，新建连接。Host 选 `mqtts://`，主机填 `127.0.0.1`，端口填 `18884`，高级设置选 MQTT 3.1.1，使用独立客户端编号。用户名按上表填写，密码粘贴。开启 **SSL Secure**，证书类型选 **CA or Self-signed certificates**，CA File 选择上一步下载的证书。服务器使用单向证书认证和设备账号，无需上传客户端证书。保存并连接。发布区选 `JSON`、`QoS 1`，关闭 `Retain`。

生成并复制一条开关报文。在对应主题的发布框中粘贴，点击右下角发送。将 `false` 改为 `true` 后重新生成并发送，可恢复模型显示。

```powershell
./tools/iot/mqttx-lab.ps1 -Action payload -Device switch -Value false
./tools/iot/mqttx-lab.ps1 -Action payload -Device temperature -Value 26.125
```

温度报文用温度连接发送。每次执行会生成当前时间和新的追踪编号。重发同一份报文时保留这两个字段，才能验证去重。发布账号只写自己的主题，因此从 MQTTX 的 **Published** 查看已发送报文，从平台 **物联网管理 → 接收记录** 依据设备编号和已发布状态确认已处理。

### 温度读数预览

2026-11-17 试运行场景草稿修订 4 已增加“温度读数”数字计数器。进入场景的“现有编辑模式”，点击“预览”，可查看三位小数及 °C 单位。该组件使用通用动态请求，携带当前系统登录凭证，查询地址为同源 `/dts-api/admin-api/dimensions/iot-state/get?deviceId=lab-temperature-01&pointCode=temperature`，间隔为 1 秒，返回字段 `value` 映射到组件 `value`。设备编号、点位和间隔均保存在组件配置中。

使用 `lab-temperature` 向 `lab/iot/tenant-2/temperature` 上报 `26.125`、`29.5` 后，预览自动显示 `26.125`、`29.500`。编辑态“发送请求”用于单次检查，自动轮询在运行预览中验证。该数值展示采用状态查询轮询；模型显隐采用已认证 WebSocket 定向推送。当前固定发布 `PUB-2122272471983910914` 保存修订 3，查看新增温度组件使用修订 4 的草稿预览。温度固定发布和二维部件的通用 WebSocket 消费仍待后续接入。

MQTTX 提示 `unable to verify the first certificate` 时，核对本连接的 CA File 为 `lab148-mqtt-ca.crt`，该文件是测试 MQTT 代理的签发机构证书。连接被拒绝时先核对本机 `18884` 转发是否运行。转发仅绑定本机，电脑重启或转发进程退出后重新执行 `-Action tunnel`。MQTTX 顶部输入框用于搜索，发布主题和 JSON 正文填写在下方发送区。

## 脚本复验

也可使用标准库脚本，经同一回环转发和证书向真实代理发布。脚本交互读取密码，支持 `normal`、`duplicate`、`out-of-order`，以及 `--count` 和 `--interval` 控制次数及频率。关闭后再次启动同一设备可模拟发布端重新连接。

```powershell
python tools/iot/simulate-mqtt-lab.py switch false --ca <证书路径> --scenario duplicate
python tools/iot/simulate-mqtt-lab.py temperature 29.5 --ca <证书路径> --scenario out-of-order
python tools/iot/simulate-mqtt-lab.py temperature 26.125 --ca <证书路径> --count 12 --interval 5
```

温度映射保持数值，开关在三维场景的 **结构 → 模型 → 数据 → 扩展数据源 → 物联网状态** 中选择 `lab-switch-01 / visible`，交付方式为 **WebSocket 实时推送**，属性映射为 `value → visible`。实际发布使用服务器保存后的修订。发布端重新连接、重复和乱序分别取证，MQTT 发布确认还需要平台接收记录和状态快照对账。

## 停用试运行

在管理页停用试运行连接器，停止上报脚本或关闭 MQTTX，再按需停止 148 的 `wx-iot-mqtt-lab148`。恢复时启动原容器并启用连接器。代理持久数据在 `/home/wx-iot-mqtt-lab148/data`，配置及凭据在同一受控根目录。只保留测试租户的数据。当前单连接下主题发布权限和业务报文设备编号是两个边界。两个测试发布账号属于同一授权团队，未据此验证设备级身份绑定。

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
