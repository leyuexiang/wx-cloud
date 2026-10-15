package cn.iocoder.wx.module.vegetation.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 行道树种植侧。 */
public enum VegetationCorridorSide {
    LEFT("left"),
    RIGHT("right"),
    BOTH("both");

    private final String value;

    VegetationCorridorSide(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static VegetationCorridorSide fromValue(String value) {
        return Arrays.stream(values())
                .filter(side -> side.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知行道树种植侧: " + value));
    }
}
