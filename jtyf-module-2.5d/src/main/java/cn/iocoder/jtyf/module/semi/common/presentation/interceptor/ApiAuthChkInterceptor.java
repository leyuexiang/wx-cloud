package cn.iocoder.jtyf.module.semi.common.presentation.interceptor;

import cn.iocoder.jtyf.module.semi.common.auth.ApiAuthorization;
import cn.iocoder.jtyf.module.semi.common.util.*;
import cn.iocoder.jtyf.module.semi.controller.admin.account.business.AccountMngService;
import cn.iocoder.jtyf.module.semi.controller.admin.account.businessinfo.AccountInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

public class ApiAuthChkInterceptor implements HandlerInterceptor {

    @Autowired
    private AccountMngService accountMngService;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object hdl) throws Exception {
        if (!(hdl instanceof HandlerMethod)) {
            return true;
        }
        boolean pass = false;
        String url = req.getRequestURI();
        if ((url.startsWith("/admin-api/api/file/") || url.startsWith("/admin-api/api/image/"))
                && req.getMethod().equals(RequestMethod.GET.name())) {
            pass = true;
        }
        if (url.startsWith("/admin-api/api/data/") && url.endsWith("/admin-api/list")
                && req.getMethod().equals(RequestMethod.POST.name())) {
            pass = true;
        }
        if (url.equals("/admin-api/api/account/password/update")
                && req.getMethod().equals(RequestMethod.POST.name())) {
            pass = true;
        }

        // 获取单点登录用户身份接口URL
        String ssoProfile = null;//AccountConfParser.getInstance().getSsoProfile();
        boolean isSsoflg = StrUtil.isNotEmpty(ssoProfile) ? true : false;
        String tokenExtendType = req.getHeader("Token-Extend-Type");

        AccountInfo accountInfo = null;
        try {
        } catch (Exception e) {
            if (!url.equals("/admin-api/api/account/profile")) {
                if (pass) {
                    return true;
                }
                ApiUtil.setUnauthorizedResponse(req, res);
                return false;
            }

            // 非单点登录的场合
            if (!isSsoflg) {
                ApiUtil.setUnauthorizedResponse(req, res);
                return false;
            }

            // 调用第三方接口，获取登录用户身份情报
            try {
                byte[] bytes = HttpUtil.forward(req, RequestMethod.GET.name(), ssoProfile, null);
                accountInfo = JsonUtil.toObj(new String(bytes), AccountInfo.class);
                if (StrUtil.isEmpty(accountInfo.getId()) || StrUtil.isEmpty(accountInfo.getUsername())) {
                    ApiUtil.setUnauthorizedResponse(req, res);
                    return false;
                }
            } catch (Exception exc) {
                ApiUtil.setUnauthorizedResponse(req, res);
                return false;
            }

            // 生成自己格式Token
            res.setHeader("Token", JwtUtil.buildToken(accountInfo, tokenExtendType));

            req.getSession().removeAttribute(ApiConst.SESSION_LOGIN_USER);
            req.getSession().setAttribute(ApiConst.SESSION_LOGIN_USER, accountInfo);

            return true;
        }

        if (!isSsoflg) {
            try {
                //accountInfo = accountMngService.getAccountDetail(null, account, false);
                accountInfo = new AccountInfo();
                accountInfo.setAccount("Administrator");
                accountInfo.setUsername("Administrator");
                accountInfo.setId("11111");
            } catch (Exception e) {
                if (pass) {
                    return true;
                }
                ApiUtil.setUnauthorizedResponse(req, res);
                return false;
            }
        } else {
            List<String> roles = accountInfo.getRoleNmList();
            if (ListUtil.isNotEmptyList(roles)) {
                List<Long> roleAuthCdList = accountMngService.getRoleAuthCdList(roles);
                if (ListUtil.isNotEmptyList(roleAuthCdList)) {
                    accountInfo.setRoleAuthCdList(roleAuthCdList);
                }
            }
        }

        // 获取请求URL
        String reqUrl = getReqUrl(req);

        if (StrUtil.isEmpty(reqUrl)) {
            ApiUtil.setBadRequestResponse(req, res);
            return false;
        }

    /*    // auth.xml权限判定
        if (!pass && !authCheck(accountInfo, reqUrl)) {
            ApiUtil.setForbiddenResponse(req, res);
            return false;
        }*/

        req.getSession().removeAttribute(ApiConst.SESSION_LOGIN_USER);
        req.getSession().setAttribute(ApiConst.SESSION_LOGIN_USER, accountInfo);

        return true;
    }

    private String getReqUrl(HttpServletRequest req) {
        String url = req.getRequestURI();
        return url;
        // return StrUtil.isEmpty(url)
        //         ? null : url.endsWith("/")
        //         ? url : url + "/";
    }

    private boolean authCheck(AccountInfo user, String reqUrl) throws Exception {
        List<Long> roleAuthCdList = user.getRoleAuthCdList();

        boolean isUrlMatcehed = false;
        for (String url : ApiAuthorization.getAuthMap().keySet()) {
            if (reqUrl.startsWith(url)) {
                isUrlMatcehed = true;
            } else if (url.contains("*")) {
                String[] arr = url.split("\\*");
                if (reqUrl.startsWith(arr[0]) && reqUrl.endsWith(arr[1])) {
                    isUrlMatcehed = true;
                }
            }
            if (isUrlMatcehed) {
                if (ListUtil.isNotEmptyList(roleAuthCdList)) {
                    for (Long roleAuthCd : roleAuthCdList) {
                        // 权限检证
                        boolean haveAuth = ApiAuthorization.auth(roleAuthCd, url);

                        if (haveAuth) {
                            return true;
                        }
                    }
                }

                break;
            }
        }

        if (isUrlMatcehed) {
            return false;
        } else {
            // API权限没有配置的场合，认为不需要权限检证。
            return true;
        }
    }
}
