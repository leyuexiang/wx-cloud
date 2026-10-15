package cn.iocoder.jtyf.module.dimensions.listener;

import org.springframework.context.ApplicationEvent;

/**
 * @author cheYu
 * @desc 需要发送消息的事件
 * @create 2025-06-16 10:08
 */
public class SendMessageEvent extends ApplicationEvent {
    private final String businessKey;
    private final String topicName;
    private final long listIndex;

    public SendMessageEvent(Object source, String topicName, String businessKey, long size) {
        super(source);
        this.topicName = topicName;
        this.businessKey = businessKey;
        this.listIndex = size - 1;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public long getListIndex() {
        return listIndex;
    }
}
