package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.kafka.support.JacksonUtils;

/** 与 Kafka JSON 生产者保持时间解析一致；独立读取器避免被 Web 的毫秒时间配置影响。 */
public class IotTelemetryEventCodec {
    private final ObjectReader reader;
    private final ObjectWriter writer;

    public IotTelemetryEventCodec() {
        ObjectMapper mapper = JacksonUtils.enhancedObjectMapper();
        reader = mapper.readerFor(TwinTelemetryEvent.class)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        // 缓存统一使用 ISO 时间字符串，避免整数时间戳的秒/毫秒歧义及纳秒精度损失。
        writer = mapper.writerFor(TwinTelemetryEvent.class).without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public TwinTelemetryEvent decode(String payload) {
        if (payload == null || payload.isBlank() || payload.length() > 1_048_576) {
            throw new InvalidEventException("遥测内容为空或超过 1 MiB 字符限制");
        }
        try {
            return reader.readValue(payload);
        } catch (JsonProcessingException ex) {
            // 原始解析异常可能包含载荷片段，隔离主题保留原文，普通错误日志仅保留原因分类。
            throw new InvalidEventException("遥测 JSON 无法解析");
        }
    }

    public String encode(TwinTelemetryEvent event) {
        try {
            return writer.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new InvalidEventException("遥测状态无法序列化");
        }
    }

    /** 不可恢复的事件契约错误，由专用 Kafka 错误处理器转入隔离主题。 */
    public static class InvalidEventException extends IllegalArgumentException {
        public InvalidEventException(String message) {
            super(message);
        }
    }
}
