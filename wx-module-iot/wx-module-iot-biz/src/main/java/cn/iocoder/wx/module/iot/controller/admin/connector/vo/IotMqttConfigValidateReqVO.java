package cn.iocoder.wx.module.iot.controller.admin.connector.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 未保存的 MQTT 参数校验请求；不需要设备身份，不查询凭据或建立网络连接。 */
@Schema(description = "管理后台 - MQTT 配置预校验请求")
@Data
public class IotMqttConfigValidateReqVO {
    @NotBlank(message = "服务地址不能为空")
    @Size(max = 512, message = "服务地址不能超过 512 个字符")
    private String endpoint;
    @NotBlank(message = "凭据引用不能为空")
    @Size(max = 128, message = "凭据引用不能超过 128 个字符")
    private String credentialRef;
    @NotBlank(message = "协议配置不能为空")
    @Size(max = 65536, message = "协议配置不能超过 65536 个字符")
    private String configJson;
}
