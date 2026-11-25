package cn.iocoder.wx.module.dimensions.iot;

import lombok.Data;

import java.util.List;

/** 前端撤销物联网状态订阅请求。 */
@Data
public class IotStateUnsubscriptionMessage {
    private List<String> subscriptionIds;
}
