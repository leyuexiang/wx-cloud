package cn.iocoder.jtyf.module.semi.common.conf;

import cn.hutool.core.util.StrUtil;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;



@Component
@ServerEndpoint(value = "/ws/service", configurator = WebsocketConfig.class)
public class WebsocktServer {
    /**
     * concurrent包的线程安全Set，用来存放每个客户端对应的ChatServer对象
     */
    private static final ConcurrentHashMap<String, WebsocktServer> WEB_SOCKET_MAP = new ConcurrentHashMap<>();

    private Long userId;
    private String from;

    /**
     * 与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    private Session session;

    private static String getKey(Long userId, String from) {
        return userId + ":" + from;
    }

    public static String getHeader(Session session, String headerName) {
        final String header = (String) session.getUserProperties().get(headerName);
        if (StrUtil.isBlank(header)) {
            try {
                session.close();
            } catch (IOException e) {
            }
        }
        return header;
    }

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session) {
        userId = Long.parseLong(getHeader(session, "userId"));
        from = String.valueOf(getHeader(session, "from"));
        String key = userId + ":" + from;
        this.session = session;
        WEB_SOCKET_MAP.put(key, this);
    }

    private static void close(WebsocktServer server) {
        try {
            CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "消息异常，服务器主动关闭链接！");
            server.session.close(closeReason);
        } catch (IOException e) {
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        WEB_SOCKET_MAP.remove(userId + ":" + from);
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param msg 客户端发送过来的消息
     *            格式：{
     *            "dialogueHistoryId":"对话ID，为空，表示需要创建一个对话",
     *            "prompt":"对话内容",
     *            }
     */
    @OnMessage
    public void onMessage(String msg, Session session) {
        try {
            //JSONObject message = JSONUtil.parseObj(msg);
            String regex = userId + ":.*";
            Pattern pattern = Pattern.compile(regex);
            for (String key : WEB_SOCKET_MAP.keySet()) {
                if (pattern.matcher(key).matches()) {
                    WebsocktServer server = WEB_SOCKET_MAP.get(key);
                    if (null != server && !key.equals(getKey(userId, from))) {
                        sendMessage(server, msg, userId);
                    }
                }
            }
        } catch (Exception e) {
            close(this);
        }
    }

    /**
     * 发生错误时调用
     */
    @OnError
    public void onError(Session session, Throwable error) {
        close(this);
    }

    public void sendMessage(String message) {
        try {
            this.session.getBasicRemote().sendText(message);
        } catch (IOException e) {
        }
    }

    public static void sendMessage(WebsocktServer server, String message, Long userId) {
        try {
            if (null != server) {
                server.session.getBasicRemote().sendText(message);
            } else {
            }
        } catch (IOException e) {
        }
    }
}
