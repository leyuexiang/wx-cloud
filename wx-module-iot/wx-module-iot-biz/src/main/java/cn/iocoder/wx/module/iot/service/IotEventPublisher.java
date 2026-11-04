package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.module.iot.api.IotTopicsConstants;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/** 将标准化遥测事件发布到 Kafka，后续规则引擎和三维服务独立订阅。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CompletableFuture<SendResult<String, Object>> publish(TwinTelemetryEvent event) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(IotTopicsConstants.TELEMETRY_EVENT, event.getEventId(), event);
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("物联网遥测事件发布失败，eventId={}", event.getEventId(), throwable);
            }
        });
        return future;
    }
}
