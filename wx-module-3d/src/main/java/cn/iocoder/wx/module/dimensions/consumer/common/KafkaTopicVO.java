package cn.iocoder.wx.module.dimensions.consumer.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @desc Kafka主题信息VO，包含主题名称和中文描述
 * @date 2025-06-17
 * @author akon
 */
@Data
public class KafkaTopicVO {
    @Schema(description = "主题名称（不带前缀）", example = "FIRE_CENTER_TOPIC")
    private String topicName;

    @Schema(description = "主题的中文描述", example = "火灾相关的主题")
    private String description;

    @Schema(description = "带前缀的主题名称", example = "ZY_FIRE_CENTER_TOPIC")
    private String prefixedTopicName;
}
