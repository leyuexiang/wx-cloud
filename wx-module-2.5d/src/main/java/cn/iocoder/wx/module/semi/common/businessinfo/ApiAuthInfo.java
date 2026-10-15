package cn.iocoder.wx.module.semi.common.businessinfo;

import java.io.Serializable;
import java.util.List;

public class ApiAuthInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * URL
     */
    private String url;

    /**
     * 别名
     */
    private String alias;

    /**
     * 权限Code列表
     */
    private List<Long> authCdList;

    public ApiAuthInfo(String url, String alias, List<Long> authCdList) {
        this.url = url;
        this.alias = alias;
        this.authCdList = authCdList;
    }

    // getter (read only)

    public String getUrl() {
        return url;
    }

    public String getAlias() {
        return alias;
    }

    public List<Long> getAuthCdList() {
        return authCdList;
    }

}
