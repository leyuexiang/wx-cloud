//package cn.iocoder.jtyf.module.dimensions.service;
//
//import org.springframework.stereotype.Service;
//
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * @author akon
// * @desc 该服务类用于控制 Kafka 消息消费的开关状态。
// *       通过该类，可以统一管理所有 Kafka 消费者的消费状态，
// *       前端可以通过调用相关控制器接口，间接调用此类的方法，
// *       从而灵活地开启或关闭 Kafka 消息的消费。
// * @create 2025-05-20
// */
//@Service
//public class KafkaConsumeSwitchService {
//    private final Map<String, Boolean> consumeEnabledMap = new HashMap<>();
//
//    /**
//     * 检查 Kafka 消息消费是否开启。
//     *
//     * @return 如果消费开启，返回 true；否则返回 false。
//     */
//    public boolean isConsumeEnabled(String topic) {
//        return consumeEnabledMap.getOrDefault(topic, true);
//    }
//
//    /**
//     * 开启 Kafka 消息消费。
//     * 调用此方法后，所有依赖此开关状态的 Kafka 消费者将开始消费消息。
//     */
//    public void enableConsume(String topic) {
//        consumeEnabledMap.put(topic, true);
//    }
//
//    /**
//     * 关闭 Kafka 消息消费。
//     * 调用此方法后，所有依赖此开关状态的 Kafka 消费者将停止消费消息。
//     */
//    public void disableConsume(String topic) {
//        consumeEnabledMap.put(topic, false);
//    }
//}