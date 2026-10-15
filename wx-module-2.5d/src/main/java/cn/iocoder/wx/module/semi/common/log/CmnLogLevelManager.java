package cn.iocoder.wx.module.semi.common.log;


import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class CmnLogLevelManager {

    private static CmnLogger logger = CmnLogger.getLogger(CmnLogLevelManager.class);

    /**
     * log levels of all log messages
     */
    private static Properties messageLogLevels = new Properties();

    static {
        initConfig();
    }

    // could execute static block in advance
    public static void init() {}

    /**
     * constructor
     */
    private CmnLogLevelManager() {}

    /**
     * Get the log level according to the message code.
     *
     * @param messageKey
     *            message code
     * @return log level of this message code
     */
    public static int getLogLevel(String messageKey) {
        if (messageLogLevels.isEmpty()) {
            initConfig();
        }

        String level = messageLogLevels.getProperty(messageKey);
        if (level == null) {
            return 5;
        }

        try {
            return Integer.parseInt(level);
        } catch (Exception e) {
            return 5;
        }
    }

    /**
     * Initialization for loading log level config file.
     */
    private static void initConfig() {
        InputStream is = null;

        try {
            // load common properties to read path log level config file
            is =CmnLogLevelManager.class.getClassLoader().getResourceAsStream("logLevel.properties");
            messageLogLevels.load(is);

        } catch (Exception e) {
            logger.debug("Failed to initial log level : " + e);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                logger.debug("Failed to close the stream : " + e);
            }
        }
    }
}
