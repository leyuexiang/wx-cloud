package cn.iocoder.wx.module.dimensions.consumer.common;

import lombok.Getter;

/**
 * @desc Kafka 主题常量枚举类，包含主题名称和中文含义
 * @author akon
 * @create 2025-06-17
 */
@Getter
public enum KafkaTopicsEnum {
    // 前缀常量
    PREFIX_TOPIC(KafkaTopicsConstants.PREFIX_TOPIC, "主题前缀"),

    // 摄像机相关主题
    CAMERA_CENTER_TOPIC(KafkaTopicsConstants.CAMERA_CENTER_TOPIC, "摄像机相关的主题"),

    // 建筑相关主题
    BUILDING_CENTER_TOPIC(KafkaTopicsConstants.BUILDING_CENTER_TOPIC, "建筑相关的主题"),

    // 通风相关主题
    VENTILATION_CENTER_TOPIC(KafkaTopicsConstants.VENTILATION_CENTER_TOPIC, "通风相关的主题"),

    // 给排水相关主题
    WATER_SUPPLY_AND_DRAINAGE_CENTER_TOPIC(KafkaTopicsConstants.WATER_SUPPLY_AND_DRAINAGE_CENTER_TOPIC, "给排水相关的主题"),

    // 电气相关主题
    ELECTRIC_CENTER_TOPIC(KafkaTopicsConstants.ELECTRIC_CENTER_TOPIC, "电气相关的主题"),

    // 转运相关主题
    TRANSPORTATION_CENTER_TOPIC(KafkaTopicsConstants.TRANSPORTATION_CENTER_TOPIC, "转运相关的主题"),

    // 环保相关主题
    ENVIRONMENTAL_PROTECTION_CENTER_TOPIC(KafkaTopicsConstants.ENVIRONMENTAL_PROTECTION_CENTER_TOPIC, "环保相关的主题"),

    // 火灾相关主题
    FIRE_CENTER_TOPIC(KafkaTopicsConstants.FIRE_CENTER_TOPIC, "火灾相关的主题"),

    // 安防相关主题
    SECURITY_CENTER_TOPIC(KafkaTopicsConstants.SECURITY_CENTER_TOPIC, "安防相关的主题"),

    // 人员相关主题
    PERSONNEL_CENTER_TOPIC(KafkaTopicsConstants.PERSONNEL_CENTER_TOPIC, "人员相关的主题"),

    // 消防栓（测试用）相关主题
    HYDRANT_TEST_CENTER_TOPIC(KafkaTopicsConstants.HYDRANT_TEST_CENTER_TOPIC, "消防栓测试相关的主题"),

    // 洗消（测试用）相关主题
    DECONTAMINATION_TEST_CENTER_TOPIC(KafkaTopicsConstants.DECONTAMINATION_TEST_CENTER_TOPIC, "洗消测试相关的主题"),

    // 定时任务主题
    COMMON_UNIVERSAL_TOPIC(KafkaTopicsConstants.COMMON_UNIVERSAL_TOPIC, "定时任务通用主题");

    /** 主题名称（不带前缀） */
    private final String topicName;
    /** 中文含义描述 */
    private final String description;

    /**
     * 枚举构造函数
     * @param topicName 主题名称（不带前缀）
     * @param description 中文描述
     */
    KafkaTopicsEnum(String topicName, String description) {
        this.topicName = topicName;
        this.description = description;
    }

    /**
     * 获取带前缀的主题名称
     * @return 带前缀的主题名称
     */
    public String getPrefixedTopicName() {
        if (this == PREFIX_TOPIC) {
            return topicName; // 前缀本身不带前缀
        }
        return PREFIX_TOPIC.getTopicName() + topicName;
    }

    /**
     * 获取所有非前缀的主题列表
     * @return 主题列表
     */
    public static KafkaTopicsEnum[] getNonPrefixTopics() {
        return java.util.Arrays.stream(values())
                .filter(topic -> topic != PREFIX_TOPIC)
                .toArray(KafkaTopicsEnum[]::new);
    }
}