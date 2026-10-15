package cn.iocoder.wx.module.dimensions.consumer.common;

/**
 * @desc 该接口用于存放 Kafka 相关的 Topic 常量，方便集中管理和维护。
 * @author akon
 * @create 2024-02-29
 */
public interface KafkaTopicsConstants {

    String PREFIX_TOPIC = "ZY_";

    /**
     * 摄像机相关的 Kafka Topic
     */
    String CAMERA_CENTER_TOPIC = "CAMERA_CENTER_TOPIC";

    /**
     * 建筑相关的 Kafka Topic
     */
    String BUILDING_CENTER_TOPIC = "BUILDING_CENTER_TOPIC";

    /**
     * 通风相关的 Kafka Topic
     */
    String VENTILATION_CENTER_TOPIC = "VENTILATION_CENTER_TOPIC";

    /**
     * 给排水相关的 Kafka Topic
     */
    String WATER_SUPPLY_AND_DRAINAGE_CENTER_TOPIC = "WATER_SUPPLY_AND_DRAINAGE_CENTER_TOPIC";

    /**
     * 电气相关的 Kafka Topic
     */
    String ELECTRIC_CENTER_TOPIC = "ELECTRIC_CENTER_TOPIC";

    /**
     * 转运相关的 Kafka Topic
     */
    String TRANSPORTATION_CENTER_TOPIC = "TRANSPORTATION_CENTER_TOPIC";

    /**
     * 环保相关的 Kafka Topic
     */
    String ENVIRONMENTAL_PROTECTION_CENTER_TOPIC = "ENVIRONMENTAL_PROTECTION_CENTER_TOPIC";

    /**
     * 火灾相关的 Kafka Topic
     */
    String FIRE_CENTER_TOPIC = "FIRE_CENTER_TOPIC";

    /**
     * 安防相关的 Kafka Topic
     */
    String SECURITY_CENTER_TOPIC = "SECURITY_CENTER_TOPIC";

    /**
     * 人员相关的 Kafka Topic
     */
    String PERSONNEL_CENTER_TOPIC = "PERSONNEL_CENTER_TOPIC";

    /**
     * 消防栓（测试用）相关的 Kafka Topic
     */
    String HYDRANT_TEST_CENTER_TOPIC = "HYDRANT_TEST_CENTER_TOPIC";

    /**
     * 洗消（测试用）相关的 Kafka Topic
     */
    String DECONTAMINATION_TEST_CENTER_TOPIC = "DECONTAMINATION_TEST_CENTER_TOPIC";

    /**
     * 定时任务发送到kafka的消息
     */
    String COMMON_UNIVERSAL_TOPIC = "COMMON_UNIVERSAL_TOPIC";

}