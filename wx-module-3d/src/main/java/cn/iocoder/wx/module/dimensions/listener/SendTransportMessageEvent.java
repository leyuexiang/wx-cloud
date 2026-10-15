package cn.iocoder.wx.module.dimensions.listener;

import org.springframework.context.ApplicationEvent;

/**
 * @author: cheYu
 * @desc: 需要发送转运流程消息的事件
 * @create: 2025-06-30 10:38
 */
public class SendTransportMessageEvent extends ApplicationEvent {
    private final String businessKey;
    private final String topicName;
    private final String dataHashKey;

    public SendTransportMessageEvent(Object source, String topicName, String businessKey, String dataHashKey) {
        super(source);
        this.topicName = topicName;
        this.businessKey = businessKey;
        this.dataHashKey = dataHashKey;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getDataHashKey() {
        return dataHashKey;
    }
}
