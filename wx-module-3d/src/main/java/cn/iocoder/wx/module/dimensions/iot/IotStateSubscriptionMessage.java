package cn.iocoder.wx.module.dimensions.iot;

import lombok.Data;
import lombok.ToString;

import java.util.List;

/** 前端物联网状态订阅请求；外层 WebSocket 帧的 content 使用 JSON 字符串。 */
@Data
public class IotStateSubscriptionMessage {
    private String requestId;
    private String sceneId;
    // 发布模式由票据固定场景，票据原文不能进入对象日志。
    private String publicationId;
    @ToString.Exclude
    private String publicationTicket;
    private List<Binding> bindings;

    @Data
    public static class Binding {
        private String subscriptionId;
        private String objectId;
        private String deviceId;
        private String pointCode;
    }
}
