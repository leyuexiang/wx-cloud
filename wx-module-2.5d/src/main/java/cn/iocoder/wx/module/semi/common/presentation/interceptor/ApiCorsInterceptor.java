package cn.iocoder.wx.module.semi.common.presentation.interceptor;

import cn.iocoder.wx.module.semi.common.util.ApiUtil;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ApiCorsInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object hdl) throws Exception {
        return ApiUtil.isAllowedOrigin(req, res);
    }
}
