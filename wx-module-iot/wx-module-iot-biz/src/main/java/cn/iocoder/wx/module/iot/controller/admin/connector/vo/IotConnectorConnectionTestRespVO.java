package cn.iocoder.wx.module.iot.controller.admin.connector.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 连接测试只返回脱敏结果，不回传地址、凭据、异常原文或客户端编号。 */
@Schema(description = "管理后台 - 物联网连接器连接测试结果")
public record IotConnectorConnectionTestRespVO(
        String connectorId,
        Outcome outcome,
        long elapsedMillis,
        int topicCount,
        String message) {
    public enum Outcome {
        SUCCEEDED,
        UNSUPPORTED_PROTOCOL,
        CONFIG_INVALID,
        CREDENTIALS_UNAVAILABLE,
        CONNECT_FAILED,
        SUBSCRIBE_FAILED,
        TIMEOUT
    }
}
