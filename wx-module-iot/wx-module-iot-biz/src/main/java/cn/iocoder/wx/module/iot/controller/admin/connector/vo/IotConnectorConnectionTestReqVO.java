package cn.iocoder.wx.module.iot.controller.admin.connector.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 受限连接测试请求；测试只针对已保存配置，不允许调用方提交地址绕过连接器校验。 */
@Schema(description = "管理后台 - 物联网连接器连接测试请求")
@Data
public class IotConnectorConnectionTestReqVO {
    @NotNull(message = "连接器编号不能为空")
    @Min(value = 1, message = "连接器编号必须为正数")
    private Long id;

    @Schema(description = "连接测试超时时间，单位秒，默认 10 秒")
    @Min(value = 1, message = "超时时间最小为 1 秒")
    @Max(value = 15, message = "超时时间最大为 15 秒")
    private Integer timeoutSeconds = 10;
}
