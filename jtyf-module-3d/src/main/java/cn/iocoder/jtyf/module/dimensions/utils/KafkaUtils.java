package cn.iocoder.jtyf.module.dimensions.utils;

import cn.iocoder.jtyf.module.dimensions.consumer.common.KafkaTopicVO;
import cn.iocoder.jtyf.module.dimensions.consumer.common.KafkaTopicsEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author akon
 * @date 2025-06-13
 * @desc Kafka 工具类，提供主题相关的便捷操作
 */
@Slf4j
public class KafkaUtils {

    /**
     * 获取所有非前缀的 Kafka 主题名称（不带前缀）
     * @return 主题名称列表
     */
    public static List<String> getAllTopicNames() {
        return Arrays.stream(KafkaTopicsEnum.getNonPrefixTopics())
                .map(KafkaTopicsEnum::getTopicName)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有非前缀的 Kafka 主题名称（带前缀）
     * @return 带前缀的主题名称列表
     */
    public static List<String> getAllPrefixedTopicNames() {
        return Arrays.stream(KafkaTopicsEnum.getNonPrefixTopics())
                .map(KafkaTopicsEnum::getPrefixedTopicName)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定的 Kafka 主题名称（不带前缀）
     * @return 主题名称列表
     */
    public static List<String> getSpecifiedTopicNames() {
        return Arrays.asList(
                KafkaTopicsEnum.CAMERA_CENTER_TOPIC.getTopicName(),
                KafkaTopicsEnum.BUILDING_CENTER_TOPIC.getTopicName(),
                KafkaTopicsEnum.VENTILATION_CENTER_TOPIC.getTopicName(),
                KafkaTopicsEnum.WATER_SUPPLY_AND_DRAINAGE_CENTER_TOPIC.getTopicName(),
                KafkaTopicsEnum.ELECTRIC_CENTER_TOPIC.getTopicName(),
                KafkaTopicsEnum.TRANSPORTATION_CENTER_TOPIC.getTopicName(),
                KafkaTopicsEnum.ENVIRONMENTAL_PROTECTION_CENTER_TOPIC.getTopicName(),
                KafkaTopicsEnum.FIRE_CENTER_TOPIC.getTopicName(),
                KafkaTopicsEnum.SECURITY_CENTER_TOPIC.getTopicName(),
                KafkaTopicsEnum.PERSONNEL_CENTER_TOPIC.getTopicName(),
                KafkaTopicsEnum.COMMON_UNIVERSAL_TOPIC.getTopicName()

        );
    }

    /**
     * 根据主题名称获取枚举对象
     * @param topicName 主题名称（不带前缀）
     * @return 对应的枚举对象，未找到返回 Optional.empty()
     */
    public static Optional<KafkaTopicsEnum> getTopicByCode(String topicName) {
        if (topicName == null) {
            return Optional.empty();
        }

        return Arrays.stream(KafkaTopicsEnum.getNonPrefixTopics())
                .filter(topic -> topic.getTopicName().equals(topicName))
                .findFirst();
    }

    /**
     * 获取主题的中文描述
     * @param topicName 主题名称（不带前缀）
     * @return 主题的中文描述，未找到返回 Optional.empty()
     */
    public static Optional<String> getTopicDescription(String topicName) {
        return getTopicByCode(topicName)
                .map(KafkaTopicsEnum::getDescription);
    }

    /**
     * 获取所有非前缀主题及其描述
     * @return 主题信息列表
     */
    public static List<KafkaTopicVO> getAllTopicsWithDescription() {
        return Arrays.stream(KafkaTopicsEnum.getNonPrefixTopics())
                .map(topic -> {
                    KafkaTopicVO vo = new KafkaTopicVO();
                    vo.setTopicName(topic.getTopicName());
                    vo.setDescription(topic.getDescription());
                    vo.setPrefixedTopicName(topic.getPrefixedTopicName());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取指定主题及其描述
     * @return 主题信息列表
     */
    public static List<KafkaTopicVO> getSpecifiedTopicsWithDescription() {
        return Stream.of(
                        KafkaTopicsEnum.CAMERA_CENTER_TOPIC,
                        KafkaTopicsEnum.BUILDING_CENTER_TOPIC,
                        KafkaTopicsEnum.VENTILATION_CENTER_TOPIC,
                        KafkaTopicsEnum.WATER_SUPPLY_AND_DRAINAGE_CENTER_TOPIC,
                        KafkaTopicsEnum.ELECTRIC_CENTER_TOPIC,
                        KafkaTopicsEnum.TRANSPORTATION_CENTER_TOPIC,
                        KafkaTopicsEnum.ENVIRONMENTAL_PROTECTION_CENTER_TOPIC,
                        KafkaTopicsEnum.FIRE_CENTER_TOPIC,
                        KafkaTopicsEnum.SECURITY_CENTER_TOPIC,
                        KafkaTopicsEnum.PERSONNEL_CENTER_TOPIC
                )
                .map(topic -> {
                    KafkaTopicVO vo = new KafkaTopicVO();
                    vo.setTopicName(topic.getTopicName());
                    vo.setDescription(topic.getDescription());
                    vo.setPrefixedTopicName(topic.getPrefixedTopicName());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 根据带前缀的主题名称获取主题信息
     * @param prefixedTopicName 带前缀的主题名称
     * @return 主题信息，未找到返回 Optional.empty()
     */
    public static Optional<KafkaTopicVO> getTopicVOByPrefixedName(String prefixedTopicName) {
        if (prefixedTopicName == null) {
            return Optional.empty();
        }

        String prefix = KafkaTopicsEnum.PREFIX_TOPIC.getTopicName();
        if (prefixedTopicName.startsWith(prefix)) {
            String topicName = prefixedTopicName.substring(prefix.length());
            return getTopicByCode(topicName)
                    .map(topic -> {
                        KafkaTopicVO vo = new KafkaTopicVO();
                        vo.setTopicName(topic.getTopicName());
                        vo.setDescription(topic.getDescription());
                        vo.setPrefixedTopicName(prefixedTopicName);
                        return vo;
                    });
        }

        return Optional.empty();
    }

    /**
     * 检查主题是否存在
     * @param topicName 主题名称（不带前缀）
     * @return 是否存在
     */
    public static boolean existsTopic(String topicName) {
        return getTopicByCode(topicName).isPresent();
    }
}