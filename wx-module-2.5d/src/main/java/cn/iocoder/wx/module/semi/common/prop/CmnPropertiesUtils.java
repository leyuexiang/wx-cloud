package cn.iocoder.wx.module.semi.common.prop;


import cn.iocoder.wx.module.semi.common.GlobalConst;
import cn.iocoder.wx.module.semi.common.util.StrUtil;

/**
 * Convenience methods for processing properties.
 */
public class CmnPropertiesUtils implements GlobalConst {

    private static String CONF_PATH = "conf_path";

    private static String APP_CONF_FILE = "app_conf_file";

    private static String LOG_LEVEL_CONF_FILE = "log_level_conf_file";

    private static String MSG_RESOURCE_FILE = "msg_resource_file";

    private static String TRACE_MODE = "trace_mode";

    private static String FILE_SEPARATOR = "/";

    public static String getConfPath() {
        return getValue(CONF_PATH);
    }

    public static String getAppConfFile() {
        return getConfPath() + FILE_SEPARATOR +
                getValue(APP_CONF_FILE);
    }

    public static String getLogLevelConfFile() {
        return getConfPath() + FILE_SEPARATOR +
                getValue(LOG_LEVEL_CONF_FILE);
    }

    public static String getMsgResFile() {
        return getConfPath() + FILE_SEPARATOR +
                getValue(MSG_RESOURCE_FILE);
    }

    public static String getTraceMode() {
        String val = getValue(TRACE_MODE);
        if (StrUtil.isNotEmpty(val)
                && GlobalConst.TRACE.equals(val)) {
            return GlobalConst.TRACE;
        }
        return GlobalConst.NO_TRACE;
    }

    public static String getValue(String key) {
        String defaultPath = SYS_PROPERTIES_FILE;
        String userPath = System.getProperty(USER_PROP_PARAM);
        if (userPath != null) {
            return CmnPropertiesManager.getInstance()
                    .getConfValue(userPath, key);
        } else {
            return CmnPropertiesManager.getInstance()
                    .getPropValue(defaultPath, key);
        }
    }
}
