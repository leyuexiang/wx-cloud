package cn.iocoder.jtyf.module.vegetation.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 海拔分带空隙处理策略。 */
public enum VegetationGapPolicy {
    LEAVE_EMPTY("leave-empty"),
    REJECT("reject");

    private final String value;

    VegetationGapPolicy(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static VegetationGapPolicy fromValue(String value) {
        return Arrays.stream(values())
                .filter(policy -> policy.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知海拔分带空隙策略: " + value));
    }
}
