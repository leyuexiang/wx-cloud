package cn.iocoder.wx.module.semi.controller.admin.account.conf;

import cn.iocoder.wx.module.semi.common.conf.BaseConfParser;
import cn.iocoder.wx.module.semi.common.conf.ParserStgy;
import cn.iocoder.wx.module.semi.common.log.CmnLogger;
import cn.iocoder.wx.module.semi.common.msg.MessageConst;
import cn.iocoder.wx.module.semi.common.util.ApiConst;
import cn.iocoder.wx.module.semi.common.util.StrUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;


public class EmailConfParser extends BaseConfParser {

    private CmnLogger logger = CmnLogger.getLogger(this.getClass());

    public static final String filePath = ApiConst.ACCOUNT_EMAIL_FILE;

    //--------------------
    // conf key
    //--------------------
    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String FROM = "from";
    public static final String AUTHENTICATION = "authentication";
    public static final String CHARSET = "charset";
    public static final String SUBJECT_TYPE_SIGNUP = "subject_type_signup";
    public static final String SUBJECT_TYPE_FORGET = "subject_type_forget";
    public static final String SUBJECT_TYPE_EMAIL  = "subject_type_email";

    private static final List<String> KEYS = Arrays.asList(new String[] {
            HOST, PORT, FROM, AUTHENTICATION, CHARSET,
            SUBJECT_TYPE_SIGNUP, SUBJECT_TYPE_FORGET, SUBJECT_TYPE_EMAIL
    });

    private static final Pattern ANY_PATTERN = Pattern.compile("^(" + ANY_PAT + ")=(" + ANY_PAT + ")$");
    private static final Pattern PORT_PATTERN = Pattern.compile("^(" + ANY_PAT + ")=(" + PORT_PAT + ")$");

    private static final Map<String, Pattern> CHK_REGEX_MAP = new HashMap<String, Pattern>();

    static {
        CHK_REGEX_MAP.put(HOST, ANY_PATTERN);
        CHK_REGEX_MAP.put(PORT, PORT_PATTERN);
        CHK_REGEX_MAP.put(FROM, ANY_PATTERN);
        CHK_REGEX_MAP.put(AUTHENTICATION, ANY_PATTERN);
        CHK_REGEX_MAP.put(CHARSET, ANY_PATTERN);
        CHK_REGEX_MAP.put(SUBJECT_TYPE_SIGNUP, ANY_PATTERN);
        CHK_REGEX_MAP.put(SUBJECT_TYPE_FORGET, ANY_PATTERN);
        CHK_REGEX_MAP.put(SUBJECT_TYPE_EMAIL, ANY_PATTERN);
    }

    //don not leak this data map
    private Map<String, String> dataMap = new HashMap<String, String>();

    private ParserStgy currentParser = new EmailParserStgy();

    private final ReadWriteLock rwlock = new ReentrantReadWriteLock();

    private final Lock rLock = rwlock.readLock();

    private final Lock wLock = rwlock.writeLock();

    private static EmailConfParser instance = new EmailConfParser(filePath);

    public EmailConfParser(String filePath) {
        super(filePath);
    }

    public static EmailConfParser getInstance() {
        // write lock
        instance.init();
        return instance;
    }

    //--------------------
    // Override base
    //--------------------
    @Override
    protected void init() {
        wLock.lock();
        try {
            super.init();
        } finally {
            wLock.unlock();
        }
    }

    @Override
    protected void clearElements() {
        dataMap.clear();
        setFlgMap.clear();
    }

    @Override
    protected boolean checkLine(String lineStr, int lineNum) {
        return currentParser.check(lineStr, lineNum);
    }

    @Override
    protected void parseLine(String lineStr, int lineNum) {
        currentParser.parse(lineStr, lineNum);
    }

    //--------------------
    // get value by key
    //--------------------
    public String getHost() {
        rLock.lock();
        try {
            return readConfVal(HOST);
        } finally {
            rLock.unlock();
        }
    }

    public String getPort() {
        rLock.lock();
        try {
            return readConfVal(PORT);
        } finally {
            rLock.unlock();
        }
    }

    public String getFrom() {
        rLock.lock();
        try {
            return readConfVal(FROM);
        } finally {
            rLock.unlock();
        }
    }

    public String getAuthentication() {
        rLock.lock();
        try {
            return readConfVal(AUTHENTICATION);
        } finally {
            rLock.unlock();
        }
    }

    public String getCharset() {
        rLock.lock();
        try {
            return readConfVal(CHARSET);
        } finally {
            rLock.unlock();
        }
    }

    public String getSubjectTypeSignup() {
        rLock.lock();
        try {
            return readConfVal(SUBJECT_TYPE_SIGNUP);
        } finally {
            rLock.unlock();
        }
    }

    public String getSubjectTypeForget() {
        rLock.lock();
        try {
            return readConfVal(SUBJECT_TYPE_FORGET);
        } finally {
            rLock.unlock();
        }
    }

    public String getSubjectTypeEmail() {
        rLock.lock();
        try {
            return readConfVal(SUBJECT_TYPE_EMAIL);
        } finally {
            rLock.unlock();
        }
    }

    public String readConfVal(String key) {
        if (setFlgMap.get(key) == 0) {
            logger.log(MessageConst.MSG_01040004, new Object[]{
                    filePath
                    ,key
            });
        }
        return dataMap.get(key);
    }

    //--------------------
    // parser strategies
    //--------------------
    private class EmailParserStgy implements ParserStgy {

        @Override
        public boolean check(String lineStr, int lineNum) {
            if (lineStr.startsWith(COMMENT)) {
                return false;
            }

            if (lineStr == null || lineStr.length() == 0) {
                return false;
            }

            String key = StrUtil.splitAll(lineStr, "=")[0];
            if (!KEYS.contains(key)) {
                logger.log(MessageConst.MSG_01040002, new Object[] {
                        getFilePath(), String.valueOf(lineNum)});
                return false;
            }

            Pattern chkRegex = CHK_REGEX_MAP.get(key);
            if (!chkRegex.matcher(lineStr).matches()) {
                logger.log(MessageConst.MSG_01040002, new Object[] {
                        getFilePath(), String.valueOf(lineNum)});
                return false;
            }

            return true;
        }

        @Override
        public void parse(String lineStr, int lineNum) {
            String[] items = StrUtil.splitAll(lineStr, "=");
            String pKey = items[0].trim();
            String pVal = items[1].trim();
            dataMap.put(pKey, pVal);
            setFlgMap.put(pKey, 1);
        }
    }
}
