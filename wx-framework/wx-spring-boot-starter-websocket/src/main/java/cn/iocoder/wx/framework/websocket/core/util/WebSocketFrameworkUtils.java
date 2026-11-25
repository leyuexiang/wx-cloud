package cn.iocoder.wx.framework.websocket.core.util;

import cn.iocoder.wx.framework.security.core.LoginUser;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;

/**
 * 专属于 web 包的工具类
 *
 * @author 九天芸菲
 */
public class WebSocketFrameworkUtils {

    public static final String ATTRIBUTE_LOGIN_USER = "LOGIN_USER";
    /** 由专用端点设置的出站类型范围；旧端点未设置时保留原有行为。 */
    public static final String ATTRIBUTE_ALLOWED_MESSAGE_TYPES = "ALLOWED_MESSAGE_TYPES";

    public static void setAllowedMessageTypes(Set<String> types, Map<String, Object> attributes) {
        attributes.put(ATTRIBUTE_ALLOWED_MESSAGE_TYPES, Set.copyOf(types));
    }

    /** 原文广播没有可信消息类型，不能进入声明了消息范围的会话。 */
    public static boolean isMessageTypeAllowed(WebSocketSession session, String type) {
        Object allowed = session.getAttributes().get(ATTRIBUTE_ALLOWED_MESSAGE_TYPES);
        return allowed == null || (allowed instanceof Set<?> types && type != null && types.contains(type));
    }

    /**
     * 设置当前用户
     *
     * @param loginUser 登录用户
     * @param attributes Session
     */
    public static void setLoginUser(LoginUser loginUser, Map<String, Object> attributes) {
        attributes.put(ATTRIBUTE_LOGIN_USER, loginUser);
    }

    /**
     * 获取当前用户
     *
     * @return 当前用户
     */
    public static LoginUser getLoginUser(WebSocketSession session) {
        return (LoginUser) session.getAttributes().get(ATTRIBUTE_LOGIN_USER);
    }

    /**
     * 获得当前用户的编号
     *
     * @return 用户编号
     */
    public static Long getLoginUserId(WebSocketSession session) {
        LoginUser loginUser = getLoginUser(session);
        return loginUser != null ? loginUser.getId() : null;
    }

    /**
     * 获得当前用户的类型
     *
     * @return 用户编号
     */
    public static Integer getLoginUserType(WebSocketSession session) {
        LoginUser loginUser = getLoginUser(session);
        return loginUser != null ? loginUser.getUserType() : null;
    }

    /**
     * 获得当前用户的租户编号
     *
     * @param session Session
     * @return 租户编号
     */
    public static Long getTenantId(WebSocketSession session) {
        LoginUser loginUser = getLoginUser(session);
        return loginUser != null ? loginUser.getTenantId() : null;
    }

}
