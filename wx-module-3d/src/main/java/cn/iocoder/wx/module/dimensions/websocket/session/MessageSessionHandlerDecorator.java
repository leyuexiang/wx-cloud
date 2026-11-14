package cn.iocoder.wx.module.dimensions.websocket.session;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.wx.framework.websocket.core.session.WebSocketSessionHandlerDecorator;
import cn.iocoder.wx.framework.websocket.core.session.WebSocketSessionManager;
import cn.iocoder.wx.module.dimensions.listener.SendAllMessageEvent;
import cn.iocoder.wx.module.dimensions.listener.SendQueueManager;
import cn.iocoder.wx.module.dimensions.iot.IotStateSubscriptionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

/**
 * @author: cheYu
 * @desc: TODO
 * @create: 2025-06-18 13:50
 */
@Slf4j
public class MessageSessionHandlerDecorator extends WebSocketSessionHandlerDecorator {

    public MessageSessionHandlerDecorator(WebSocketHandler delegate, WebSocketSessionManager sessionManager) {
        super(delegate, sessionManager);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("U3D WebSocket连接成功***********");
        // 需要对正在执行的webSocket发送任务进行失效
        SendQueueManager sendQueueManager = SpringUtil.getBean(SendQueueManager.class);
        sendQueueManager.interruptRunningSend();

        super.afterConnectionEstablished(session);
        // 发现U3D连接成功，设置监听事件，即推送redis内积攒的kafka业务流程数据给U3D；ApplicationContext实现了发布者同样可以发布事件
        ApplicationContext applicationContext = SpringUtil.getApplicationContext();
        applicationContext.publishEvent(new SendAllMessageEvent(this));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        // 连接关闭时释放该会话的物联网订阅，避免失效会话继续占用注册表并接收推送。
        try {
            SpringUtil.getBean(IotStateSubscriptionRegistry.class).removeSession(session);
        } catch (Exception ignored) {
            // 物联网状态投影关闭时没有注册表，旧 WebSocket 流程仍可正常关闭。
        }
        super.afterConnectionClosed(session, closeStatus);
    }
}
