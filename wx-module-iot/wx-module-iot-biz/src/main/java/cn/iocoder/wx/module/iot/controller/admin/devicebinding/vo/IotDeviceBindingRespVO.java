package cn.iocoder.wx.module.iot.controller.admin.devicebinding.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 外部设备绑定响应。 */
@Schema(description = "管理后台 - 物联网设备绑定 Response VO")
@Data
public class IotDeviceBindingRespVO {

    private Long id;
    private String connectorId;
    private String externalDeviceId;
    private String deviceId;
    private String sceneId;
    private String objectId;
    private String mappingRevision;
    private String pointMappingJson;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
