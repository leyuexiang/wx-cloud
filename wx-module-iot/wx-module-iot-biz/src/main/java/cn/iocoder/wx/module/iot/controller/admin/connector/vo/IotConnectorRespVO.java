package cn.iocoder.wx.module.iot.controller.admin.connector.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 连接器管理响应。 */
@Schema(description = "管理后台 - 物联网连接器 Response VO")
@Data
public class IotConnectorRespVO {

    private Long id;
    private String connectorId;
    private String name;
    private String connectorType;
    private String status;
    private String endpoint;
    private String credentialRef;
    private String configJson;
    private Boolean enabled;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
