package cn.iocoder.jtyf.module.semi.controller.admin.account.conf;

import cn.iocoder.jtyf.module.semi.common.conf.BaseConfParser;
import cn.iocoder.jtyf.module.semi.common.conf.ParserStgy;
import cn.iocoder.jtyf.module.semi.common.log.CmnLogger;
import cn.iocoder.jtyf.module.semi.common.msg.MessageConst;
import cn.iocoder.jtyf.module.semi.common.util.ApiConst;
import cn.iocoder.jtyf.module.semi.common.util.StrUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;


public class AccountConfParser extends BaseConfParser {

    private CmnLogger logger = CmnLogger.getLogger(this.getClass());

    public static final String filePath = ApiConst.ACCOUNT_FILE;

    //--------------------
    // conf key
    //--------------------
    public static final String JWT_SECRET = "jwt_secret";
    public static final String SIGNUP_AUTH_CODE_DISABLED = "signup_auth_code_disabled";
    public static final String SSO_PROFILE = "sso_profile";
    public static final String ACCOUNT_URL = "account_url";

    private static final List<String> KEYS = Arrays.asList(new String[] {
            JWT_SECRET, SIGNUP_AUTH_CODE_DISABLED, SSO_PROFILE, ACCOUNT_URL
    });

    private static final Pattern BOOL_PATTERN =
            Pattern.compile("^(" + ANY_PAT + ")=\\s*(true|false)\\s*$");

    private static final Map<String, Pattern> CHK_REGEX_MAP = new HashMap<String, Pattern>();

    static {
        CHK_REGEX_MAP.put(SIGNUP_AUTH_CODE_DISABLED, BOOL_PATTERN);
    }

    //don not leak this data map
    private Map<String, String> dataMap = new HashMap<String, String>();

    private ParserStgy currentParser = new AccountParserStgy();

    private final ReadWriteLock rwlock = new ReentrantReadWriteLock();

    private final Lock rLock = rwlock.readLock();

    private final Lock wLock = rwlock.writeLock();

    private static AccountConfParser instance = new AccountConfParser(filePath);

    public AccountConfParser(String filePath) {
        super(filePath);
    }

    public static AccountConfParser getInstance() {
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
        // set default
        setDefaultVal(JWT_SECRET, ApiConst.JWT_SECRET);
        setDefaultVal(SIGNUP_AUTH_CODE_DISABLED, Boolean.TRUE.toString());
    }

    private void setDefaultVal(String key, String val) {
        dataMap.put(key, val);
        setFlgMap.put(key, 0);
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
    public String getJwtSecret() {
        rLock.lock();
        try {
            return readConfVal(JWT_SECRET);
        } finally {
            rLock.unlock();
        }
    }

    public Boolean getSignupAuthCodeDisabled() {
        rLock.lock();
        try {
            return Boolean.valueOf(readConfVal(SIGNUP_AUTH_CODE_DISABLED));
        } finally {
            rLock.unlock();
        }
    }

    public String getSsoProfile() {
        rLock.lock();
        try {
            return readConfVal(SSO_PROFILE);
        } finally {
            rLock.unlock();
        }
    }

    public String getAccountUrl() {
        rLock.lock();
        try {
            return readConfVal(ACCOUNT_URL);
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
    private class AccountParserStgy implements ParserStgy {

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
            if (chkRegex != null && !chkRegex.matcher(lineStr).matches()) {
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
