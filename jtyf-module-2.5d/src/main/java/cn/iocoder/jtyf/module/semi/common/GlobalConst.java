package cn.iocoder.jtyf.module.semi.common;

public interface GlobalConst {

    public static final String SYS_FILE_ENCODING = "UTF-8";

    public static final String SYS_PROPERTIES_FILE = "/common.properties";

    public static final String USER_PROP_PARAM = "prop";

    public static final String NO_TRACE = "0";

    public static final String TRACE = "1";

    /**
     * Method start debug message
     */
    public static final String METHOD_DEBUG_START = "===== [Method Start] =====";

    /**
     * Method end debug message
     */
    public static final String METHOD_DEBUG_END = "===== [Method End] =====";

    /** 换行 */
    public static final String LINE_SEP = System.getProperty("line.separator");
}
