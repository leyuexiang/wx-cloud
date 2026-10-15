package cn.iocoder.jtyf.module.dimensions.listener;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * @author cheYu
 * @desc 监听kafka将业务数据写入到redis，然后将redis数据取出，发送到U3D
 * @create 2025-06-16 11:09
 */
@Component
@Slf4j
public class SendBusinessListener {
    @Resource
    private SendQueueManager sendQueueManager;

    /**
     * 响应发送U3D的事件
     * @param event 发送消息给U3D的事件； 若为Object event，则所有事件都可以监听到
     */
    @EventListener
    @Async
    public void asyncEvent(SendMessageEvent event) {
        if (sendQueueManager.isNotCanSend()) {
            return;
        }
        sendQueueManager.submitSend(event, sendQueueManager.getToken(event.getTopicName()));
    }

    /**
     * 响应发送U3D 转运流程消息事件
     * @param event 发送消息给U3D转运流程消息事件； 若为Object event，则所有事件都可以监听到
     */
    @EventListener
    @Async
    public void asyncTransportEvent(SendTransportMessageEvent event) {
        if (sendQueueManager.isNotCanSend()) {
            return;
        }
        sendQueueManager.submitSendTransport(event, sendQueueManager.getToken(event.getTopicName()));
    }

    /**
     * 响应U3D启动建立连接成功，发送全量消息事件
     * @param event 发送全量消息给U3D的事件； 若为Object event，则所有事件都可以监听到
     */
    @EventListener
    @Async
    public void asyncEvent(SendAllMessageEvent event) {
        sendQueueManager.submitSendAll();
    }
}
