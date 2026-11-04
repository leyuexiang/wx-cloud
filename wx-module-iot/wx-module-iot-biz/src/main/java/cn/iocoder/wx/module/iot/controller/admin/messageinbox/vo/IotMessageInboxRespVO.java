package cn.iocoder.wx.module.iot.controller.admin.messageinbox.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理后台 - 物联网接入事件响应。 */
@Schema(description = "管理后台 - 物联网接入事件 Response VO")
@Data
public class IotMessageInboxRespVO {

    private Long id;
    private String eventId;
    private String connectorId;
    private String externalDeviceId;
    private String pointCode;
    private String status;
    private Integer retryCount;
    private LocalDateTime receivedAt;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
