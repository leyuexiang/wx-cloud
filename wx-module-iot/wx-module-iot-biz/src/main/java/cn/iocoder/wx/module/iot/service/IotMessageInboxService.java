package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.module.iot.controller.admin.messageinbox.vo.IotMessageInboxPageReqVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotMessageInboxDO;

/** 遥测事件 Inbox 去重和投递状态服务。 */
public interface IotMessageInboxService {

    /** 尝试抢占事件；返回本次抢占令牌，返回 null 表示事件已被其他投递流程处理。 */
    String tryClaim(TwinTelemetryEvent event);

    PageResult<IotMessageInboxDO> getPage(IotMessageInboxPageReqVO pageReqVO);

    void replay(String eventId);

    void markPublished(Long tenantId, String eventId, String claimToken);

    void markFailed(Long tenantId, String eventId, String claimToken, Throwable throwable);
}
