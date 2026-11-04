package cn.iocoder.wx.module.iot.api;

/** 数字孪生物联网统一消息主题。主题版本变更时新增版本，避免直接破坏消费者。 */
public interface IotTopicsConstants {

    /** 设备遥测、状态和告警等标准化事件。 */
    String TELEMETRY_EVENT = "twin.event.v1";
    /** 规则引擎、流程引擎或人工操作下发的设备命令。 */
    String COMMAND = "twin.command.v1";
    /** 设备命令执行结果。 */
    String COMMAND_ACK = "twin.command.ack.v1";
    /** 外部连接器连接状态变化。 */
    String CONNECTOR_STATUS = "twin.connector.status.v1";
}
