package cn.iocoder.jtyf.module.dimensions.producer;

import cn.iocoder.jtyf.module.dimensions.websocket.message.GeneralReceiveMessage;
import org.springframework.stereotype.Component;

/**
 * @author akon
 * @create 2025-05-08
 * @desc 给排水任务
 */
@Component
public class WaterSupDraKafkaScheduledTask extends AbstractKafkaScheduledTask {

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