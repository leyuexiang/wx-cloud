package cn.iocoder.wx.module.iot.dal.dataobject;

import cn.iocoder.wx.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 遥测事件接入去重和投递状态记录。 */
@TableName("iot_message_inbox")
@Data
@EqualsAndHashCode(callSuper = true)
public class IotMessageInboxDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String eventId;
    private String connectorId;
    private String externalDeviceId;
    private String pointCode;
    private String payloadJson;
    private String status;
    /** 每次抢占重新生成，用于隔离过期的异步发布回调。 */
    private String claimToken;
    private Integer retryCount;
    private LocalDateTime receivedAt;
    private String errorMessage;
}
