package cn.iocoder.wx.module.semi.common.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import cn.iocoder.wx.module.semi.common.msg.Message;
import cn.iocoder.wx.module.semi.common.msg.MsgResourceManager;
import org.slf4j.LoggerFactory;

public class CmnLogger {
    private static final String LOGGER_FQCN = CmnLogger.class.getName() + ".";

    /**
     * The enclosed Logger.
     */
    private Logger logger;

    static {
        CmnLogLevelManager.init();
    }

    // could execute static block in advance
    public static void init() { }

    /**
     * constructor
     */
    private CmnLogger() {}

    private CmnLogger(String name) {
        this.logger = (Logger) LoggerFactory.getLogger(name);
    }

    public static CmnLogger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    public static CmnLogger getLogger(String name) {
        return new CmnLogger(name);
    }

    public boolean isDebugEnabled() {
        return this.logger.isDebugEnabled();
    }

    /**
     * output log message, with log level defined in log level file.
     *
     * @param messageCode
     *            message id
     * @param arguments
     *            placeholders
     * @param t
     *            throwable
     * @return message content with placeholder processed
     */
    public String log(String messageCode, Object[] arguments, Throwable t) {
        return doLogWithMessageCode(messageCode, arguments, t);
    }

    /**
     * output log message, with log level defined in log level file.
     *
     * @param messageCode
     *            message id
     * @param arguments
     *            placeholders
     * @return message content with placeholder processed
     */
    public String log(String messageCode, Object[] arguments) {
        return log(messageCode, arguments, (Throwable) null);
    }

    /**
     * output log message, with log level defined in log level file.
     *
     * @param messageCode
     *            message id
     * @return message content with placeholder processed
     */
    public String log(String messageCode) {
        return log(messageCode, (Object[]) null);
    }

    public String log(Message msg) {
        return log(msg.getCode(), msg.getArgs());
    }

    public String log(Message msg, Throwable t) {
        return log(msg.getCode(), msg.getArgs(), t);
    }

    private String doLogWithMessageCode(String messageCode, Object[] arguments, Throwable t) {
        StringBuffer message = new StringBuffer();
        int messageLevel = CmnLogLevelManager.getLogLevel(messageCode);
        Level logLevel;
        switch (messageLevel) {
        case 0:
            logLevel = Level.DEBUG;
            break;
        case 1:
            logLevel = Level.INFO;
            break;
        case 3:
            logLevel = Level.WARN;
            break;
        case 5:
            logLevel = Level.ERROR;
            break;
        default:
            messageLevel = 0;
            logLevel = Level.DEBUG;
        }
        message.append(constructMessageContent(messageCode, messageLevel, arguments));
        doLog(LOGGER_FQCN, logLevel, message.toString(), t);

        return message.toString();
    }

    private String constructMessageContent(String msgCode, int intLevel, Object[] args) {
        StringBuffer message = new StringBuffer();
        message.append(msgCode);
        message.append(":");
        message.append(getRawMessageContent(msgCode, args));
        return message.toString();
    }

    /**
     * Get message content
     *
     * @param messageCode
     *            message id
     * @param arguments
     *            place holders
     * @return message content
     **/
    private String getRawMessageContent(String messageCode, Object[] arguments) {
        String msgContext = MsgResourceManager.getMessage(messageCode, arguments);
        msgContext = msgContext.replaceAll("\\n", "");
        return msgContext;
    }

    public void debug(Object message) {
        doLog(LOGGER_FQCN, Level.DEBUG, message, null);
    }

    public void debug(Object message, Throwable t) {
        doLog(LOGGER_FQCN, Level.DEBUG, message, t);
    }

    /**
     * This is the most generic printing method.
     *
     * @param callerFQCN
     *            The wrapper class' fully qualified class name.
     * @param level
     *            The level of the logging request.
     * @param message
     *            The message of the logging request.
     * @param t
     *            The Throwable of the logging request, may be null.
     */
    protected void doLog(String callerFQCN, Level level, Object message,
            Throwable t) {
        // if (!this.logger.isEnabledForLevel(getSlf4jLogLevel(level))) {
        //     return;
        // }
        if (!this.logger.isEnabledFor(level)) {
            return;
        }
        // if (level.isGreaterOrEqual(this.logger.getEffectiveLevel())) {
            processMessageBlock(callerFQCN, level, message, t);
        // }
    }

    /**
     * the max message size is 0.1M, otherwise the message will be divided into pieces.
     *
     * @param fqcn
     *            logger name
     * @param level
     *            log level
     * @param message
     *            message content
     * @param t
     *            exceptions
     */
    protected void processMessageBlock(String fqcn, Level level,
            Object message, Throwable t) {
        String msg = (message != null) ? message.toString() : "";
        int size = msg.length();
        int messageBlockSize = 1024 * 100;
        int count = size / messageBlockSize;
        int i;
        String messageBlock;
        LoggingEvent le;
        for (i = 0; i < count; i++) {
            messageBlock = msg.substring(messageBlockSize * i, messageBlockSize * (i + 1));
            le = new LoggingEvent(fqcn, this.logger, level, messageBlock, t, null);
            this.logger.callAppenders(le);
        }

        if (size % messageBlockSize != 0) {
            messageBlock = msg.substring(messageBlockSize * i);
            le = new LoggingEvent(fqcn, this.logger, level, messageBlock, t, null);
            this.logger.callAppenders(le);
        }
    }

    // private org.slf4j.event.Level getSlf4jLogLevel(Level logbackLevel) {
    //     if (Level.DEBUG.equals(logbackLevel)) {
    //         return org.slf4j.event.Level.DEBUG;
    //     }
    //     if (Level.INFO.equals(logbackLevel)) {
    //         return org.slf4j.event.Level.INFO;
    //     }
    //     if (Level.WARN.equals(logbackLevel)) {
    //         return org.slf4j.event.Level.WARN;
    //     }
    //     if (Level.ERROR.equals(logbackLevel)) {
    //         return org.slf4j.event.Level.ERROR;
    //     }
    //     throw new IllegalArgumentException("Unknown log level");
    // }
}
