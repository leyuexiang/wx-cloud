package cn.iocoder.jtyf.module.dimensions.listener;

import org.springframework.context.ApplicationEvent;

/**
 * @author: cheYu
 * @desc: U3D启动成功，全量推送业务流程数据事件
 * @create: 2025-06-18 15:03
 */
public class SendAllMessageEvent extends ApplicationEvent {
    public SendAllMessageEvent(Object source) {
        super(source);
    }
}
