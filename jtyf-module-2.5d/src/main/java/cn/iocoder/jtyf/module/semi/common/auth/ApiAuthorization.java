package cn.iocoder.jtyf.module.semi.common.auth;

import cn.iocoder.jtyf.module.semi.common.msg.MessageConst;
import cn.iocoder.jtyf.module.semi.common.businessinfo.ApiAuthInfo;
import cn.iocoder.jtyf.module.semi.common.conf.AuthXmlPaser;
import cn.iocoder.jtyf.module.semi.common.exception.CmnSysException;
import cn.iocoder.jtyf.module.semi.common.log.CmnLogger;
import cn.iocoder.jtyf.module.semi.common.util.ApiConst;
import cn.iocoder.jtyf.module.semi.common.util.ListUtil;
import cn.iocoder.jtyf.module.semi.common.util.StrUtil;

import java.util.List;
import java.util.Map;

public class ApiAuthorization {

    private static CmnLogger logger = CmnLogger.getLogger(ApiAuthorization.class);

    private static Map<String, ApiAuthInfo> AUTH_MAP;

    static {
        initConfig(ApiConst.API_AUTH_FILE);
    }

    // could execute static block in advance
    public static void init() {}

    // private constructor
    private ApiAuthorization() {}

    /**
     * 权限设定文件初期化
     *
     * @param authFilePath 权限设定文件路径
     */
    public static void initConfig(String authFilePath) {
        try {
            AUTH_MAP = AuthXmlPaser.parse(authFilePath);
        } catch (Exception e) {
            throw new CmnSysException(MessageConst.MSG_01990001, null, e);
        }
    }

    /**
     * 权限判定
     *
     * @param roleAuthCd 角色权限Code值
     * @param url URL
     * @return true:有权限;false:无权限
     */
    public static boolean auth(Long roleAuthCd, String url) {
        if (roleAuthCd == null || StrUtil.isEmpty(url)) {
            return false;
        }
        ApiAuthInfo authInfoFromFile = getAuthInfoByUrl(url);
        if (authInfoFromFile == null) {
            return false;
        }
        List<Long> fileAuthCdList = authInfoFromFile.getAuthCdList();
        if (ListUtil.isEmptyList(fileAuthCdList)) {
            return false;
        }
        boolean haveAuth = false;
        for (Long fileAuthCd : fileAuthCdList) {
            if (authComp(roleAuthCd, fileAuthCd)) {
                haveAuth = true;
                break;
            }
        }
        return haveAuth;
    }

    /**
     * 权限比较
     *
     * @param roleAuthCd 角色权限Code值
     * @param fileAuthCd 文件权限Code值
     * @return true:检证OK;false:检证NG
     */
    public static boolean authComp(Long roleAuthCd, Long fileAuthCd) {
        if (roleAuthCd == null || fileAuthCd == null) {
            return false;
        }
        if ((roleAuthCd & fileAuthCd) == 0) {
            return false;
        }
        return true;
    }

    /**
     * 获取指定URL的权限情报
     *
     * @param url URL
     * @return 权限情报
     */
    public static ApiAuthInfo getAuthInfoByUrl(String url) {
        ApiAuthInfo authInfo = AUTH_MAP.get(url);
        if (authInfo == null) {
            logger.log(MessageConst.MSG_01000003, new String[] {
                    "URL:[" + url + "] is not defined in auth.xml"});
        }
        return authInfo;
    }

    /**
     * 获取权限设定Map内容
     * 
     * @return 权限设定Map
     */
    public static Map<String, ApiAuthInfo> getAuthMap() {
        return AUTH_MAP;
    }
}
