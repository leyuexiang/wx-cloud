package cn.iocoder.jtyf.module.vegetation.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 海拔分带作用域；camera-view必须由请求的正式生成范围承载。 */
public enum VegetationRuleScope {
    POLYGON("polygon"),
    CAMERA_VIEW("camera-view");

    private final String value;

    VegetationRuleScope(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static VegetationRuleScope fromValue(String value) {
        return Arrays.stream(values())
                .filter(scope -> scope.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知植被规则作用域: " + value));
    }
}
