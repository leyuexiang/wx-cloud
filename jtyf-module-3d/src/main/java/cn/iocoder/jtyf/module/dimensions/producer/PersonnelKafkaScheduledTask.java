package cn.iocoder.jtyf.module.dimensions.producer;

import cn.iocoder.jtyf.module.dimensions.websocket.message.GeneralReceiveMessage;
import org.springframework.stereotype.Component;

/**
 * @author wtj
 * @create 2025-05-13
 * @desc 人员任务
 */
@Component
public class PersonnelKafkaScheduledTask extends AbstractKafkaScheduledTask {

    @Override
    public void startTask() {
        //不实现
    }

    @Override
    public void stopTask() {
        //不实现
    }

    @Override
    public void sendMessage(GeneralReceiveMessage receiveMessage) {
        super.sendMessage(receiveMessage);
    }
}