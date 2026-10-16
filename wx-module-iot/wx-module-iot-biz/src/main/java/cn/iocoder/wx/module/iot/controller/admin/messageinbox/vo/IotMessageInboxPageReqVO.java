package cn.iocoder.wx.module.iot.controller.admin.messageinbox.vo;

import cn.iocoder.wx.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.wx.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/** 管理后台 - 物联网接入事件分页查询参数。 */
@Schema(description = "管理后台 - 物联网接入事件分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class IotMessageInboxPageReqVO extends PageParam {

    @Schema(description = "连接器编号")
    private String connectorId;

    @Schema(description = "外部设备编号")
    private String externalDeviceId;

    @Schema(description = "事件状态：CLAIMED、PUBLISHED、FAILED")
    private String status;

    @Schema(description = "接收时间范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] receivedAt;
}
