package cn.iocoder.wx.module.semi.common.conf;

import cn.iocoder.wx.module.semi.common.log.CmnLogger;
import cn.iocoder.wx.module.semi.common.msg.MessageConst;
import cn.iocoder.wx.module.semi.common.exception.CmnSysException;
import cn.iocoder.wx.module.semi.common.util.StrUtil;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class BaseConfParser {

    private CmnLogger logger = CmnLogger.getLogger(this.getClass());

    private String filePath;

    private long lastModifyTime = 0;

    /** UTF-8 BOM */
    private static final String UTF8BOM = "\uFEFF";

    private static final String FILE_ENCODING = "UTF-8";

    protected static final String COMMENT = "#";

    protected static final String ANY_PAT = "[^#]+";

    protected static final String PORT_PAT = "[0-9]{1,5}";

    protected static final String COLOR_PAT = "#[0-9a-fA-F]{6}|[a-zA-z]{1,64}";

    protected Map<String, Integer> setFlgMap = new LinkedHashMap<String, Integer>();

    protected abstract void clearElements();

    protected abstract boolean checkLine(String lineStr, int lineNum);

    protected abstract void parseLine(String lineStr, int lineNum);

    protected void afterParse() {};

    protected BaseConfParser(String filePath) {
        this.filePath = filePath;
    }

    /**
     * 初始化
     * 
     * @throws
     */
    protected void init() {
        String[] logParams = new String[]{ filePath };

        try {
            File file = new File(filePath);
            if (file.exists() && file.isFile()) {
                // 取得文件更新日時
                long tmpTime = file.lastModified();
                // 比较文件更新日時
                if (tmpTime != lastModifyTime) {
                    clearElements();
                    // 保持文件更新日時
                    lastModifyTime = tmpTime;
                    // 读取文件内容
                    this.load(file);
                }
            } else {
                logger.log(MessageConst.MSG_01040001, logParams);
                clearElements();
                lastModifyTime = 0;
            }
        } catch (Exception e) {
            lastModifyTime = 0;
            if(e instanceof CmnSysException) {
                throw (CmnSysException)e;
            }
            logger.log(MessageConst.MSG_01040001, logParams);
            throw new CmnSysException(MessageConst.MSG_01040001, logParams);
        }
    }

    /**
     * 读取文件内容
     * 
     * @throws CmnSysException
     */
    private void load(File file) {
        String[] logParams = new String[]{ filePath };
        BufferedReader bfReader = null;
        try {
            bfReader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), FILE_ENCODING));
            String tmpLineStr = null;
            int lineNum = 1;
            while ((tmpLineStr = bfReader.readLine()) != null) {
                if (lineNum == 1) {
                    tmpLineStr = removeUTF8BOM(tmpLineStr);
                }
                tmpLineStr = tmpLineStr.trim();
                if (StrUtil.isEmpty(tmpLineStr)
                        || tmpLineStr.startsWith(COMMENT)) {
                    lineNum++;
                    continue;
                }
                if (checkLine(tmpLineStr, lineNum)) {
                    parseLine(tmpLineStr, lineNum);
                }
                lineNum++;
            }
        } catch (IOException e) {
            logger.log(MessageConst.MSG_01040001, logParams);
        } finally {
            if (bfReader != null) {
                try {
                    bfReader.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        checkKeySet();
        afterParse();
    }

    protected <T> void setDefaultVal(Map<String, T> map, String key, T val) {
        map.put(key, val);
        setFlgMap.put(key, 0);
    }

    protected void checkKeySet() {
        for (String key : setFlgMap.keySet()) {
            Integer setFlg = setFlgMap.get(key);
            if (setFlg == 0) {
                logger.log(MessageConst.MSG_01040003,
                        new Object[] {getFilePath(), key});
            }
        }
    }

    protected <K, V> Map<K, V> copyMap(Map<K, V> src) {
        Map<K, V> cpyMap = new LinkedHashMap<K, V>();
        cpyMap.putAll(src);
        return cpyMap;
    }

    // remove UTF-8 BOM
    private String removeUTF8BOM(String str) {
        if (str.startsWith(UTF8BOM)) {
            str = str.substring(1);
        }
        return str;
    }

    protected String getFilePath() {
        return filePath;
    }
}
