package cn.iocoder.jtyf.module.dimensions.producer;

import cn.iocoder.jtyf.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.jtyf.module.dimensions.websocket.message.GeneralReceiveMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * @author akon
 * @create 2025-05-07
 * @desc 抽象的任务接口
 */
@Component
@Slf4j
public class AbstractKafkaScheduledTask implements IKafkaScheduledTask {

    @Resource
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Override
    public void startTask() {

    }

    @Override
    public void stopTask() {

    }

    @Override
    public void sendMessage(GeneralReceiveMessage receiveMessage) {
        KafkaWebSocketMessage socketMessage = buildKafkaMessage(receiveMessage);
        String topic = receiveMessage.getTopic();
        log.info("{}: Message sent to Kafka: {}", topic, socketMessage);


        CompletableFuture<SendResult<Object, Object>> future = kafkaTemplate.send(topic, socketMessage);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                RecordMetadata metadata = result.getRecordMetadata();
                String topicName = metadata.topic();
                int partition = metadata.partition();
                long offset = metadata.offset();
                log.info("消息发送成功，主题: {}, 分区: {}, 偏移量: {}", topicName, partition, offset);
            } else {
                log.error("消息发送失败，主题: {}, 发送信息: {}, 错误信息:", topic, socketMessage, ex);
            }
        });
    }

    protected KafkaWebSocketMessage buildKafkaMessage(GeneralReceiveMessage receiveMessage) {
        KafkaWebSocketMessage socketMessage = new KafkaWebSocketMessage();
        socketMessage.setType("v1");
        socketMessage.setVersion("request");
        socketMessage.setData(receiveMessage.getText());
        return socketMessage;
    }

}
