package cn.iocoder.wx.module.semi.common.prop;


import cn.iocoder.wx.module.semi.common.GlobalConst;
import cn.iocoder.wx.module.semi.common.exception.CmnSysException;
import cn.iocoder.wx.module.semi.common.msg.MessageConst;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class CmnPropertiesManager {
    /**
     * Properties file info, especially last modified time.
     */
    private class PropertiesFileInfo {

        private Properties properties;

        private long lastModified;

        public PropertiesFileInfo(Properties properties, long lastModified) {
            this.properties = properties;
            this.lastModified = lastModified;
        }

        public Properties getProperties() {
            return properties;
        }

        public long getLastModified() {
            return lastModified;
        }
    }

    /**
     * Instance of CmnPropertiesReader
     */
    private static CmnPropertiesManager instance = new CmnPropertiesManager();

    /**
     * classpath properties files cache
     */
    private Map<String, PropertiesFileInfo> classpathPropertiesCache = new HashMap<String, PropertiesFileInfo>();

    /**
     * file system conf files cache
     */
    private Map<String, PropertiesFileInfo> fileSystemPropertiesCache = new HashMap<String, PropertiesFileInfo>();

    /**
     * default constructor
     */
    private CmnPropertiesManager() { }

    /**
     * Exploit the double check locking code idiom.
     *
     * @return CmnPropertiesReader
     */
    public static CmnPropertiesManager getInstance() {
        return instance;
    }

    /**
     * classpath properties files
     */
    public String getPropValue(String pathName, String key) {
        return getClassPathProperties(pathName).getProperty(key);
    }

    public Properties getClassPathProperties(String pathName) {
        if (!classpathPropertiesCache.containsKey(pathName)) {
            PropertiesFileInfo propsFile = loadPropertiesFile(pathName);
            classpathPropertiesCache.put(pathName, propsFile);
        }
        return classpathPropertiesCache.get(pathName).getProperties();
    }

    /**
     * file system conf files (actually properties files)
     */
    public String getConfValue(String pathName, String key) {
        return getFileSystemProperties(pathName).getProperty(key);
    }

    public Properties getFileSystemProperties(String pathName) {
        if (!fileSystemPropertiesCache.containsKey(pathName)
                || fileSystemPropertiesCache.get(pathName).getLastModified()
                    != new File(pathName).lastModified()) {
            PropertiesFileInfo propsFile = loadConfFile(pathName);
            fileSystemPropertiesCache.put(pathName, propsFile);
        }
        return fileSystemPropertiesCache.get(pathName).getProperties();
    }

    /**
     * classpath properties files
     */
    private PropertiesFileInfo loadPropertiesFile(String pathname) {
        PropertiesFileInfo propsFile = null;
        Reader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream(
                    pathname), GlobalConst.SYS_FILE_ENCODING));
            Properties props = new Properties();
            props.load(reader);
            propsFile = new PropertiesFileInfo(props, 0);
        } catch (IOException e) {
            throw new CmnSysException(MessageConst.MSG_01030002, null, e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
        return propsFile;
    }

    /**
     * file system conf files
     */
    private PropertiesFileInfo loadConfFile(String pathname) {
        PropertiesFileInfo propsFile = null;
        Reader reader = null;
        try {
            File file = new File(pathname);
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),
                    GlobalConst.SYS_FILE_ENCODING));
            Properties props = new Properties();
            props.load( reader);
            propsFile = new PropertiesFileInfo(props, file.lastModified());
        } catch (IOException e) {
            throw new CmnSysException(MessageConst.MSG_01030002, null, e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
        return propsFile;
    }
}
