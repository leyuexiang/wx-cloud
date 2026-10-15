package cn.iocoder.jtyf.module.semi.common.util;

import cn.iocoder.jtyf.module.semi.common.exception.CmnSysException;
import cn.iocoder.jtyf.module.semi.common.msg.MessageConst;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonUtil {

    public static String toJsonStr(Map<String, ?> map) {
        String jsonStr = "";
        if (map != null) {
            ObjectMapper mp = new ObjectMapper();
            try {
                jsonStr = mp.writeValueAsString(map);
            } catch (Exception e) {
                throw new CmnSysException(MessageConst.MSG_01990001);
            }
        }
        return jsonStr;
    }

    public static String toJsonStr(Object obj) {
        String jsonStr = "";
        if (obj != null) {
            ObjectMapper mp = new ObjectMapper();
            try {
                jsonStr = mp.writeValueAsString(obj);
            } catch (Exception e) {
                throw new CmnSysException(MessageConst.MSG_01990001);
            }
        }
        return jsonStr;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> toMap(String jsonStr) {
        Map<String, String> map = new HashMap<String, String>();
        if (StrUtil.isNotEmpty(jsonStr)) {
            ObjectMapper mp = new ObjectMapper();
            try {
                map = mp.readValue(jsonStr, map.getClass());
            } catch (Exception e) {
                throw new CmnSysException(MessageConst.MSG_01990001);
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, String>> toMapWithMap(String jsonStr) {
        Map<String, Map<String, String>> map = new HashMap<String, Map<String, String>>();
        if (StrUtil.isNotEmpty(jsonStr)) {
            ObjectMapper mp = new ObjectMapper();
            try {
                map = mp.readValue(jsonStr, map.getClass());
            } catch (Exception e) {
                throw new CmnSysException(MessageConst.MSG_01990001);
            }
        }
        return map;
    }

    public static <T> T toObj(String jsonStr, Class<T> clz) {
        T obj = null;
        if (StrUtil.isNotEmpty(jsonStr)) {
            ObjectMapper mp = new ObjectMapper();
            mp.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            try {
                obj = mp.readValue(jsonStr, clz);
            } catch (Exception e) {
                throw new CmnSysException(MessageConst.MSG_01990001);
            }
        }
        return obj;
    }

    public static <T> List<T> toListObj(String jsonStr, Class<T> clz) {
        List<T> lst = null;
        if (StrUtil.isNotEmpty(jsonStr)) {
            ObjectMapper mp = new ObjectMapper();
            mp.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            try {
                JavaType javaType = mp.getTypeFactory().constructParametricType(ArrayList.class, clz);
                lst = mp.readValue(jsonStr, javaType);
            } catch (Exception e) {
                throw new CmnSysException(MessageConst.MSG_01990001);
            }
        }
        return lst;
    }
}
