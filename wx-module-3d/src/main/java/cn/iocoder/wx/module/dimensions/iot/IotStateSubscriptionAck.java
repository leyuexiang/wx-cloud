package cn.iocoder.wx.module.dimensions.iot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 订阅确认结果，只返回被接受或拒绝的订阅编号。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IotStateSubscriptionAck {
    private String requestId;
    private List<String> accepted;
    private List<Rejected> rejected;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rejected {
        private String subscriptionId;
        private String code;
        private String message;
    }
}
