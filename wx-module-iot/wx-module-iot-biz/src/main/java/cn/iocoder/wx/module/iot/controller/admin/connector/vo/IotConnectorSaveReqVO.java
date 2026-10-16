package cn.iocoder.wx.module.iot.controller.admin.connector.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 连接器创建或修改请求。凭据仅提交密钥服务中的引用编号。 */
@Schema(description = "管理后台 - 物联网连接器创建/修改 Request VO")
@Data
public class IotConnectorSaveReqVO {

    @Schema(description = "连接器主键", example = "1024")
    private Long id;

    @Schema(description = "连接器业务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "park-mqtt")
    @NotBlank(message = "连接器编号不能为空")
    @Size(max = 64, message = "连接器编号不能超过 64 个字符")
    private String connectorId;

    @Schema(description = "连接器名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "园区 MQTT 服务")
    @NotBlank(message = "连接器名称不能为空")
    @Size(max = 128, message = "连接器名称不能超过 128 个字符")
    private String name;

    @Schema(description = "协议类型：MQTT、HTTPS、MOCK", requiredMode = Schema.RequiredMode.REQUIRED, example = "MQTT")
    @NotBlank(message = "协议类型不能为空")
    private String connectorType;

    @Schema(description = "外部服务地址", example = "mqtts://broker.example.com:8883")
    @Size(max = 512, message = "服务地址不能超过 512 个字符")
    private String endpoint;

    @Schema(description = "凭据引用；HTTPS 回调使用 IOT_WEBHOOK_ 开头的环境变量名", example = "IOT_WEBHOOK_PARK_SECRET")
    @Size(max = 128, message = "凭据引用不能超过 128 个字符")
    private String credentialRef;

    @Schema(description = "协议扩展配置 JSON")
    private String configJson;

    @Schema(description = "兼容旧客户端的启用字段；创建和修改时忽略，请调用启用/停用接口", example = "false")
    private Boolean enabled;
}
