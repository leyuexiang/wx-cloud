package cn.iocoder.wx.module.vegetation.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 植被规则类型；接口值与Python生成器协议保持一致。 */
public enum VegetationRuleType {
    POLYGON("polygon"),
    CORRIDOR("corridor"),
    ALTITUDE_ZONATION("altitude-zonation");

    private final String value;

    VegetationRuleType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static VegetationRuleType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知植被规则类型: " + value));
    }
}
