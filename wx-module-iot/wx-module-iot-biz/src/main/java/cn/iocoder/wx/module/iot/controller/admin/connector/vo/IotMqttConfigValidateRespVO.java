package cn.iocoder.wx.module.iot.controller.admin.connector.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 只返回结构校验结论，不能将其解释为账号、订阅权限或网络连通已验证。 */
@Schema(description = "管理后台 - MQTT 配置预校验结果")
public record IotMqttConfigValidateRespVO(
        @Schema(description = "校验范围，固定为 MQTT_STRUCTURE") String scope,
        @Schema(description = "去重后的确切主题数量") int topicCount,
        @Schema(description = "传输质量等级，当前固定为 1") int qos) {
}
