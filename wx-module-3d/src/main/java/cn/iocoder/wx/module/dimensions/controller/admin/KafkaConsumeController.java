//package cn.iocoder.wx.module.dimensions.controller.admin;
//
//import cn.iocoder.wx.module.dimensions.service.KafkaConsumeSwitchService;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import jakarta.annotation.Resource;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
///**
// * @author akon
// * @desc 该控制器类提供了 RESTFul 接口，用于控制 Kafka 消息消费的开启和关闭。
// *       前端可以通过调用这些接口，间接调用 KafkaConsumeSwitchService 中的方法，
// *       从而灵活地管理 Kafka 消息的消费状态。
// * @create 2025-05-20
// */
//@RestController
//@RequestMapping("/dimensions/kafka-consume")
//@Slf4j
//@Tag(name = "Kafka 消息消费的开启和关闭接口")
//public class KafkaConsumeController {
//
//    @Resource
//    private KafkaConsumeSwitchService kafkaConsumeSwitchService;
//
//    /**
//     * 开启 Kafka 消息消费的接口。
//     * 调用此接口将调用 KafkaConsumeSwitchService 中的 enableConsume 方法，
//     * 开启所有依赖此开关状态的 Kafka 消费者的消息消费。
//     *
//     * @return 开启成功的提示信息
//     */
//    @Operation(summary="开启 Kafka 消息消费的接口")
//    @GetMapping("/enable")
//    public String enableConsume(@RequestParam String topic) {
//        kafkaConsumeSwitchService.enableConsume(topic);
//        return "Kafka message consumption enabled for topic: " + topic;
//    }
//
//    /**
//     * 关闭 Kafka 消息消费的接口。
//     * 调用此接口将调用 KafkaConsumeSwitchService 中的 disableConsume 方法，
//     * 关闭所有依赖此开关状态的 Kafka 消费者的消息消费。
//     *
//     * @return 关闭成功的提示信息
//     */
//    @Operation(summary="关闭 Kafka 消息消费的接口")
//    @GetMapping("/disable")
//    public String disableConsume(@RequestParam String topic) {
//        kafkaConsumeSwitchService.disableConsume(topic);
//        return "Kafka message consumption disabled for topic: " + topic;
//    }
//
//    /**
//     * 获取 Kafka 消息消费的状态。
//     * 调用此接口将调用 KafkaConsumeSwitchService 中的 isConsumeEnabled 方法，
//     * 获取指定主题的 Kafka 消息消费状态。
//     *
//     * @return 消费状态的提示信息
//     */
//    @Operation(summary="获取 Kafka 消息消费的状态")
//    @GetMapping("/status")
//    public String statusConsume(@RequestParam String topic) {
//        boolean isEnabled = kafkaConsumeSwitchService.isConsumeEnabled(topic);
//        String status = isEnabled ? "enabled" : "disabled";
//        return "Kafka message consumption " + status + " for topic: " + topic;
//    }
//}