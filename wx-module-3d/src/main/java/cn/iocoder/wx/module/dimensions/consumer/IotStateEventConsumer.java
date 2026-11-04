package cn.iocoder.wx.module.dimensions.consumer;

import cn.iocoder.wx.module.dimensions.iot.IotStateProjectionService;
import cn.iocoder.wx.module.iot.api.IotTopicsConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 标准遥测的独立投影消费者，异常交给专用错误处理器，不吞掉失败或打印载荷。 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "wx.iot-state", name = "enabled", havingValue = "true")
public class IotStateEventConsumer {
    private final IotStateProjectionService projectionService;

    @KafkaListener(topics = IotTopicsConstants.TELEMETRY_EVENT,
            groupId = "${wx.iot-state.consumer-group:dimensions-iot-state-v1}",
            containerFactory = "iotStateKafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        boolean changed = projectionService.project(record.value());
        log.debug("物联网状态投影完成，topic={}, partition={}, offset={}, changed={}",
                record.topic(), record.partition(), record.offset(), changed);
    }
}
