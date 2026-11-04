package cn.iocoder.wx.module.iot.controller.admin.devicebinding.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 外部设备绑定创建或修改请求。 */
@Schema(description = "管理后台 - 物联网设备绑定创建/修改 Request VO")
@Data
public class IotDeviceBindingSaveReqVO {

    @Schema(description = "绑定主键", example = "1024")
    private Long id;

    @Schema(description = "连接器业务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "park-mqtt")
    @NotBlank(message = "连接器编号不能为空")
    private String connectorId;

    @Schema(description = "外部设备编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "device-001")
    @NotBlank(message = "外部设备编号不能为空")
    @Size(max = 128, message = "外部设备编号不能超过 128 个字符")
    private String externalDeviceId;

    @Schema(description = "内部设备编号", example = "device-1001")
    private String deviceId;

    @Schema(description = "数字孪生场景编号", example = "scene-001")
    private String sceneId;

    @Schema(description = "数字孪生对象编号", example = "object-001")
    private String objectId;

    @Schema(description = "点位映射版本", requiredMode = Schema.RequiredMode.REQUIRED, example = "v1")
    @NotBlank(message = "点位映射版本不能为空")
    private String mappingRevision;

    @Schema(description = "点位映射 JSON")
    private String pointMappingJson;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;
}
