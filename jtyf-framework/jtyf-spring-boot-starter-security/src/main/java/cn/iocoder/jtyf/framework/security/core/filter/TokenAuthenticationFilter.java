package cn.iocoder.jtyf.framework.security.core.filter;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.jtyf.framework.common.enums.UserTypeEnum;
import cn.iocoder.jtyf.framework.common.exception.ServiceException;
import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.framework.common.util.json.JsonUtils;
import cn.iocoder.jtyf.framework.common.util.servlet.ServletUtils;
import cn.iocoder.jtyf.framework.security.config.SecurityProperties;
import cn.iocoder.jtyf.framework.security.core.LoginUser;
import cn.iocoder.jtyf.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.jtyf.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.jtyf.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.jtyf.module.system.api.oauth2.OAuth2TokenApi;
import cn.iocoder.jtyf.module.system.api.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Token 过滤器，验证 token 的有效性
 * 验证通过后，获得 {@link LoginUser} 信息，并加入到 Spring Security 上下文
 *
 * @author 九天芸菲
 */
@RequiredArgsConstructor
@Slf4j
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final SecurityProperties securityProperties;

    private final GlobalExceptionHandler globalExceptionHandler;

    private final OAuth2TokenApi oauth2TokenApi;

    @Override
    @SuppressWarnings("NullableProblems")
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 设置当前用户
        LoginUser loginUser;
        try {
            loginUser = buildLoginUser(request);
        } catch (Throwable ex) {
            CommonResult<?> result = globalExceptionHandler.allExceptionHandler(request, ex);
            ServletUtils.writeJSON(response, result);
            return;
        }

        if (loginUser != null) {
            SecurityFrameworkUtils.setLoginUser(loginUser, request);
        }
        // 继续过滤链
        chain.doFilter(request, response);
    }

    private LoginUser buildLoginUser(HttpServletRequest request) {
        // 情况一，基于 header[login-user] 获得用户，例如说来自 Gateway 或者其它服务透传
        String loginUserStr = request.getHeader(SecurityFrameworkUtils.LOGIN_USER_HEADER);
        if (StrUtil.isNotEmpty(loginUserStr)) {
            return buildLoginUserByService(loginUserStr);
        }

        // 情况二，基于 无需校验的第三方服务，header[x-bot-user]
        String botUserType = request.getHeader(SecurityFrameworkUtils.X_BOT_USER_HEADER);
        if (StrUtil.isNotEmpty(botUserType) && String.valueOf(UserTypeEnum.BOT.getValue()).equals(botUserType)) {
            return buildLoginUserByHeader();
        }

        // 情况三，基于 Token 获得用户
        // 注意，这里主要满足直接使用 Nginx 直接转发到 Spring Cloud 服务的场景。
        return handleLoginUserByToken(request);
    }

    private LoginUser handleLoginUserByToken(HttpServletRequest request) {
        String token = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        if (StrUtil.isEmpty(token)) {
            return null;
        }

        Integer userType = WebFrameworkUtils.getLoginUserType(request);
        // 1.1 基于 token 构建登录用户
        LoginUser loginUser = buildLoginUserByToken(token, userType);
        // 1.2 模拟 Login 功能，方便日常开发调试
        if (loginUser == null) {
            loginUser = mockLoginUser(request, token, userType);
        }
        return loginUser;
    }

    private LoginUser buildLoginUserByToken(String token, Integer userType) {
        try {
            // 校验访问令牌
            OAuth2AccessTokenCheckRespDTO accessToken = oauth2TokenApi.checkAccessToken(token).getCheckedData();
            if (accessToken == null) {
                return null;
            }
            // 用户类型不匹配，无权限
            // 注意：只有 /admin-api/* 和 /app-api/* 有 userType，才需要比对用户类型
            // 类似 WebSocket 的 /ws/* 连接地址，是不需要比对用户类型的
            if (userType != null
                    && ObjectUtil.notEqual(accessToken.getUserType(), userType)) {
                throw new AccessDeniedException("错误的用户类型");
            }
            // 构建登录用户
            return new LoginUser().setId(accessToken.getUserId()).setUserType(accessToken.getUserType())
                    .setInfo(accessToken.getUserInfo()) // 额外的用户信息
                    .setTenantId(accessToken.getTenantId()).setScopes(accessToken.getScopes())
                    .setExpiresTime(accessToken.getExpiresTime());
        } catch (ServiceException serviceException) {
            // 校验 Token 不通过时，考虑到一些接口是无需登录的，所以直接返回 null 即可
            return null;
        }
    }

    /**
     * 模拟登录用户，方便日常开发调试
     *
     * 注意，在线上环境下，一定要关闭该功能！！！
     *
     * @param request 请求
     * @param token 模拟的 token，格式为 {@link SecurityProperties#getMockSecret()} + 用户编号
     * @param userType 用户类型
     * @return 模拟的 LoginUser
     */
    private LoginUser mockLoginUser(HttpServletRequest request, String token, Integer userType) {
        if (!securityProperties.getMockEnable()) {
            return null;
        }
        // 必须以 mockSecret 开头
        if (!token.startsWith(securityProperties.getMockSecret())) {
            return null;
        }
        // 构建模拟用户
        Long userId = Long.valueOf(token.substring(securityProperties.getMockSecret().length()));
        return new LoginUser().setId(userId).setUserType(userType)
                .setTenantId(WebFrameworkUtils.getTenantId(request));
    }

    private LoginUser buildLoginUserByHeader() {
        return new LoginUser().setId(-1L).setUserType(UserTypeEnum.BOT.getValue())
                .setInfo(Map.of("nickname", "子阳腾梦", "userName", "系统机器人")); // 额外的用户信息
    }

    private LoginUser buildLoginUserByService(String loginUserStr) {
        try {
            loginUserStr = URLDecoder.decode(loginUserStr, StandardCharsets.UTF_8); // 解码，解决中文乱码问题
            return JsonUtils.parseObject(loginUserStr, LoginUser.class);
        } catch (Exception ex) {
            log.error("[buildLoginUserByHeader][解析 LoginUser({}) 发生异常]", loginUserStr, ex);
            throw ex;
        }
    }

}
