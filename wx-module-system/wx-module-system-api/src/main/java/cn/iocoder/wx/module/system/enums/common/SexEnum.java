package cn.iocoder.wx.module.system.enums.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 性别的枚举值
 *
 * @author 九天芸菲
 */
@Getter
@AllArgsConstructor
public enum SexEnum {

    /** 男 */
    MALE(1),
    /** 女 */
    FEMALE(2),
    /* 未知 */
    UNKNOWN(0);

    /**
     * 性别
     */
    private final Integer sex;

}
