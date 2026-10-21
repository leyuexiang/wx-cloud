package cn.iocoder.wx.module.iot.controller.admin.connector.vo;

import cn.iocoder.wx.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 管理列表复用平台分页上限，避免连接器增加后一次加载全部配置。 */
@Schema(description = "管理后台 - 物联网连接器分页请求")
@Data
@EqualsAndHashCode(callSuper = true)
public class IotConnectorPageReqVO extends PageParam {
    @Size(max = 64, message = "连接器编号不能超过 64 个字符")
    private String connectorId;
    @Pattern(regexp = "MQTT|HTTPS|MOCK", message = "协议类型必须为 MQTT、HTTPS 或 MOCK")
    private String connectorType;
    private Boolean enabled;
}
