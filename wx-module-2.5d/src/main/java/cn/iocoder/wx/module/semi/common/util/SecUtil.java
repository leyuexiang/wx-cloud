package cn.iocoder.wx.module.semi.common.util;

import java.util.Base64;

public class SecUtil {

    /**
     * Base64加密
     * 
     * @param str 加密字符串
     * @return 加密结果
     */
    public static String encodeByBase64(String str){
        return new String(Base64.getEncoder().encode(str.getBytes()));
    }

    /**
     * Base64解密
     * 
     * @param str 解密字符串
     * @return 解密结果
     */
    public static String decodeByBase64(String str){
        return new String(Base64.getDecoder().decode(str));
    }
}
