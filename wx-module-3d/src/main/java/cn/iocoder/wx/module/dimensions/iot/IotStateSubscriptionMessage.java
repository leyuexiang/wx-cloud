package cn.iocoder.wx.module.dimensions.iot;

import lombok.Data;

import java.util.List;

/** 前端物联网状态订阅请求；外层 WebSocket 帧的 content 使用 JSON 字符串。 */
@Data
public class IotStateSubscriptionMessage {
    private String requestId;
    private String sceneId;
    private List<Binding> bindings;

    @Data
    public static class Binding {
        private String subscriptionId;
        private String objectId;
        private String deviceId;
        private String pointCode;
    }
}
