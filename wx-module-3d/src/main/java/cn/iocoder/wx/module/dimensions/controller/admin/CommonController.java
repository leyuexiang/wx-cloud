package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.hutool.json.JSONUtil;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.core.LicenseVerify;
import cn.iocoder.wx.module.dimensions.consumer.common.KafkaTopicVO;
import cn.iocoder.wx.module.dimensions.domain.dto.KafkaMessageDTO;
import cn.iocoder.wx.module.dimensions.utils.KafkaUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@Tag(name = "通用接口")
@Slf4j
@RequestMapping("/dimensions/common")
public class CommonController extends BaseCrudController<String, Long> {

    @Autowired
    private LicenseVerify licenseVerify;

    private KafkaTemplate<String, String> createStringKafkaTemplate() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092"); // 替换成实际地址
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(configProps));
    }

    @Operation(summary = "批量发送kafka数据（每条延迟200ms）")
    @PostMapping("/send/batch/{topic}/{isSleep}")
    public ResponseEntity<String> sendMessagesBatch(@PathVariable boolean isSleep,
                                                    @PathVariable String topic,
                                                    @RequestBody List<KafkaMessageDTO> messages) {

        if (!KafkaUtils.getSpecifiedTopicNames().contains(topic.toUpperCase())) {
            return ResponseEntity.badRequest().body("非法的 topic: " + topic);
        }

        KafkaTemplate<String, String> kafkaTemplate = createStringKafkaTemplate();

        CompletableFuture.runAsync(() -> {
            for (KafkaMessageDTO messageDTO : messages) {
                try {
                    String messageJson = JSONUtil.toJsonStr(messageDTO);
                    kafkaTemplate.send(topic, messageJson);
                    log.info("发送消息: {}", messageJson);
                    if (isSleep) {
                        Thread.sleep(200);
                    }
                } catch (Exception e) {
                    log.error("发送失败: {}", e.getMessage());
                }
            }
        });
        return ResponseEntity.ok("批量发送任务已启动，消息条数: " + messages.size());
    }

    @GetMapping("/query-all-topic")
    @Operation(summary = "获取所有的Kafka主题")
    public CommonResult<List<KafkaTopicVO>> getAllTopic(){
        return CommonResult.success(KafkaUtils.getAllTopicsWithDescription());
    }

    @GetMapping("/query-specific-topics")
    @Operation(summary = "获取指定的Kafka主题")
    public CommonResult<List<KafkaTopicVO>> getSpecificTopics() {
        return CommonResult.success(KafkaUtils.getSpecifiedTopicsWithDescription());
    }

    @GetMapping("/check-cs-license")
    @Operation(summary = "综合态势授权校验")
    public CommonResult<Boolean> checkCsLicense() {
        if (licenseVerify.verifyCs()) {
            return CommonResult.error(400,"license 已过期，请联系授权");
        }
        return CommonResult.success(true);
    }

}
