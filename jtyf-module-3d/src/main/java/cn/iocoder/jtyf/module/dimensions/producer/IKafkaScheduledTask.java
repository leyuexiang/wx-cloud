package cn.iocoder.jtyf.module.dimensions.producer;

import cn.iocoder.jtyf.module.dimensions.websocket.message.GeneralReceiveMessage;

/**
 * @author akon
 * @create 2025-04-25
 * @desc 抽象的定时任务接口
 * 该接口用于定义定时任务的启动和停止方法，具体的定时任务实现类需要实现该接口。
 * 该接口的实现类需要在 Spring 容器中被管理，以便在需要时启动和停止定时任务。
 */
public interface IKafkaScheduledTask {

    void startTask();

    void stopTask();

    void sendMessage(GeneralReceiveMessage receiveMessage);
}
