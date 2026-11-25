package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.common.enums.UserTypeEnum;
import cn.iocoder.wx.framework.common.util.json.JsonUtils;
import cn.iocoder.wx.framework.security.core.LoginUser;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.framework.tenant.core.util.TenantUtils;
import cn.iocoder.wx.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.wx.framework.websocket.core.util.WebSocketFrameworkUtils;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 Redis 的订阅注册表。
 *
 * <p>设备点位索引和会话索引都放在 Redis，状态投影事件到达任意三维服务节点时都能找到订阅，
 * 再由 WebSocket 发送器按会话编号路由到实际连接。</p>
 */
@Slf4j
public class IotStateSubscriptionRegistry {
    public static final String SUBSCRIBE_PERMISSION = "dimensions:iot-state:subscribe";
    private final WebSocketMessageSender sender;
    private final SecurityFrameworkService security;
    private final StringRedisTemplate redis;
    private final IotSceneBindingScope scope;
    private static final long LEASE_SECONDS = 90;
    // 索引使用有序集合，独立命名以兼容旧测试版本留下的集合键。
    private static final String KEY_PREFIX = "twin:iot-subscription:v2:";
    private static final DefaultRedisScript<Long> REGISTER = new DefaultRedisScript<>();
    static {
        REGISTER.setLocation(new ClassPathResource("iot/subscription-register.lua"));
        REGISTER.setResultType(Long.class);
    }

    public IotStateSubscriptionRegistry(WebSocketMessageSender sender, SecurityFrameworkService security,
                                        StringRedisTemplate redis, IotSceneBindingScope scope) {
        this.sender = sender;
        this.security = security;
        this.redis = redis;
        this.scope = scope;
    }

    public IotStateSubscriptionAck subscribe(WebSocketSession session, IotStateSubscriptionMessage request) {
        LoginUser user = requireUser(session);
        String requestId = request == null ? null : request.getRequestId();
        if (requestId == null || requestId.isBlank()) {
            return new IotStateSubscriptionAck(requestId, List.of(), List.of(new IotStateSubscriptionAck.Rejected(null, "INVALID_REQUEST", "订阅请求编号不能为空")));
        }
        boolean published = request.getPublicationId() != null || request.getPublicationTicket() != null;
        if (published && request.getSceneId() != null) {
            throw new AccessDeniedException("发布订阅的场景由票据确定");
        }
        var scene = withIdentity(user, () -> {
            if (!security.hasPermission(SUBSCRIBE_PERMISSION)) {
                throw new AccessDeniedException("没有物联网状态订阅权限");
            }
            return published ? scope.readPublished(request.getPublicationId(), request.getPublicationTicket(), user.getTenantId())
                    : new IotSceneBindingScope.ScopedScene(request.getSceneId(), null, scope.read(request.getSceneId()));
        });
        // 仅记录不可兑换的票据编号，后续节点无需持有原始票据也能复核撤销状态。
        Long loginExpiresAt = published ? user.getExpiresTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : null;
        List<IotStateSubscriptionMessage.Binding> bindings = request.getBindings() == null
                ? List.of() : request.getBindings();
        if (bindings.isEmpty() || bindings.size() > 100) {
            return new IotStateSubscriptionAck(requestId, List.of(), List.of(new IotStateSubscriptionAck.Rejected(null, "INVALID_REQUEST", "订阅数量必须为 1 到 100")));
        }
        List<String> accepted = new ArrayList<>();
        List<IotStateSubscriptionAck.Rejected> rejected = new ArrayList<>();
        for (IotStateSubscriptionMessage.Binding binding : bindings) {
            String reason = validate(binding, user.getTenantId());
            if (reason != null) {
                rejected.add(new IotStateSubscriptionAck.Rejected(binding == null ? null : binding.getSubscriptionId(), "INVALID_BINDING", reason));
                continue;
            }
            if (!IotSceneBindingScope.hasBinding(scene.savedScene(), binding.getDomain(), binding.getObjectId(), binding.getDeviceId(), binding.getPointCode())) {
                removeBinding(session.getId(), binding.getSubscriptionId());
                rejected.add(new IotStateSubscriptionAck.Rejected(binding.getSubscriptionId(), "FORBIDDEN", "场景实例没有配置此物联网点位"));
                continue;
            }
            RegisteredBinding registered = new RegisteredBinding(
                    binding.getSubscriptionId(), session.getId(), user.getTenantId(), scene.sceneId(),
                    binding.getObjectId(), binding.getDeviceId(), binding.getPointCode(), scene.ticketId(), loginExpiresAt, binding.getDomain());
            String serialized = JsonUtils.toJsonString(registered);
            Object previousRaw = redis.opsForHash().get(sessionKey(session.getId()), registered.subscriptionId);
            if (previousRaw != null) {
                RegisteredBinding previous = parse(previousRaw.toString());
                if (previous != null && !previousRaw.equals(serialized)) {
                    redis.opsForZSet().remove(indexKey(previous.tenantId, previous.deviceId, previous.pointCode), previousRaw.toString());
                }
            }
            Long registeredCount = redis.execute(REGISTER,
                    List.of(indexKey(registered.tenantId, registered.deviceId, registered.pointCode), sessionKey(session.getId())),
                    registered.subscriptionId, serialized, Long.toString(LEASE_SECONDS * 1000));
            if (registeredCount == null || registeredCount != 1) throw new IllegalStateException("物联网订阅注册失败");
            accepted.add(binding.getSubscriptionId());
        }
        IotStateSubscriptionAck ack = new IotStateSubscriptionAck(requestId, accepted, rejected);
        if (sender != null) sender.sendObject(session.getId(), "iot-state-subscribe-ack", ack);
        return ack;
    }

    public void unsubscribe(WebSocketSession session, IotStateUnsubscriptionMessage request) {
        if (request == null || request.getSubscriptionIds() == null) return;
        for (String subscriptionId : request.getSubscriptionIds()) {
            if (subscriptionId != null) removeBinding(session.getId(), subscriptionId);
        }
        if (sender != null) sender.sendObject(session.getId(), "iot-state-unsubscribe-ack", Map.of("subscriptionIds", request.getSubscriptionIds()));
    }

    /** 根据当前会话索引撤销单条订阅，其他会话继续使用各自的租约。 */
    private void removeBinding(String sessionId, String subscriptionId) {
        String sessionKey = sessionKey(sessionId);
        Object raw = redis.opsForHash().get(sessionKey, subscriptionId);
        if (raw == null) return;
        RegisteredBinding binding = parse(raw.toString());
        if (binding != null) redis.opsForZSet().remove(indexKey(binding.tenantId, binding.deviceId, binding.pointCode), raw.toString());
        redis.opsForHash().delete(sessionKey, subscriptionId);
    }

    public void sendError(WebSocketSession session, String requestId, String code, String message) {
        if (sender == null) return;
        sender.sendObject(session.getId(), "iot-state-subscribe-error", Map.of(
                "requestId", requestId == null ? "" : requestId,
                "code", code == null ? "SUBSCRIBE_ERROR" : code,
                "message", message == null ? "订阅失败" : message));
    }

    public void removeSession(WebSocketSession session) {
        if (session == null) return;
        Map<Object, Object> current = redis.opsForHash().entries(sessionKey(session.getId()));
        current.values().forEach(raw -> {
            RegisteredBinding binding = parse(String.valueOf(raw));
            if (binding != null) redis.opsForZSet().remove(indexKey(binding.tenantId, binding.deviceId, binding.pointCode), String.valueOf(raw));
        });
        redis.delete(sessionKey(session.getId()));
    }

    public void publish(TwinTelemetryEvent event, String stateVersion) {
        if (sender == null || event == null || event.getTenantId() == null) return;
        long now = System.currentTimeMillis();
        String key = indexKey(event.getTenantId(), event.getDeviceId(), event.getPointCode());
        Set<String> members = redis.opsForZSet().rangeByScore(key, now + 1, Double.POSITIVE_INFINITY);
        if (members == null || members.isEmpty()) return;
        List<PublishedDelivery> publishedMembers = null;
        List<PublishedDelivery> deliveries = new ArrayList<>();
        Set<String> ticketIds = null;
        for (String raw : members) {
            RegisteredBinding binding = parse(raw);
            if (binding == null || !IotSceneBindingScope.matchesEvent(event, binding.sceneId, binding.objectId, binding.domain)) continue;
            if (binding.publicationTicketId == null) {
                deliveries.add(new PublishedDelivery(raw, binding));
            } else if (binding.loginExpiresAt != null && binding.loginExpiresAt > now) {
                if (publishedMembers == null) {
                    publishedMembers = new ArrayList<>();
                    ticketIds = new HashSet<>();
                }
                publishedMembers.add(new PublishedDelivery(raw, binding));
                ticketIds.add(binding.publicationTicketId);
            }
        }
        if (publishedMembers == null) {
            sendCurrentBatch(deliveries, event, stateVersion);
            return;
        }
        Set<String> activeTickets;
        try {
            activeTickets = scope.activeLiveTickets(event.getTenantId(), ticketIds);
        } catch (RuntimeException error) {
            // 发布状态无法核验时停止发布投递，草稿投递和已写入的最新状态不受影响。
            log.warn("物联网发布订阅票据状态检查失败，tenant={}", event.getTenantId(), error);
            sendCurrentBatch(deliveries, event, stateVersion);
            return;
        }
        for (PublishedDelivery delivery : publishedMembers) {
            RegisteredBinding binding = delivery.binding;
            if (activeTickets.contains(binding.publicationTicketId) && binding.loginExpiresAt > System.currentTimeMillis()) {
                deliveries.add(delivery);
            }
        }
        sendCurrentBatch(deliveries, event, stateVersion);
    }

    private void sendCurrentBatch(List<PublishedDelivery> deliveries, TwinTelemetryEvent event, String stateVersion) {
        // 保留逐项会话登记校验，仅合并 Redis 网络往返；分批限制流水线缓冲大小。
        for (int offset = 0; offset < deliveries.size(); offset += 128) {
            List<PublishedDelivery> batch = deliveries.subList(offset, Math.min(offset + 128, deliveries.size()));
            List<Object> current = redis.executePipelined((RedisCallback<Object>) connection -> {
                for (PublishedDelivery delivery : batch) {
                    RegisteredBinding binding = delivery.binding;
                    connection.hashCommands().hGet(sessionKey(binding.sessionId).getBytes(StandardCharsets.UTF_8),
                            binding.subscriptionId.getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });
            if (current.size() != batch.size()) throw new IllegalStateException("订阅会话批量校验结果不完整");
            for (int i = 0; i < batch.size(); i++) {
                PublishedDelivery delivery = batch.get(i);
                RegisteredBinding binding = delivery.binding;
                if (!delivery.raw.equals(current.get(i))) continue;
                if (binding.publicationTicketId != null && (binding.loginExpiresAt == null
                        || binding.loginExpiresAt <= System.currentTimeMillis())) continue;
                sender.sendObject(binding.sessionId, "iot-state-update",
                        IotStateUpdateMessage.from(binding.subscriptionId, binding.sceneId, binding.objectId, event, stateVersion));
            }
        }
    }

    private LoginUser requireUser(WebSocketSession session) {
        // 消息处理线程只信任握手时保存的身份，续租也需要校验令牌有效期。
        LoginUser user = WebSocketFrameworkUtils.getLoginUser(session);
        if (user == null || user.getId() == null || !UserTypeEnum.ADMIN.getValue().equals(user.getUserType())
                || user.getTenantId() == null || user.getTenantId() < 0
                || user.getExpiresTime() == null || !user.getExpiresTime().isAfter(LocalDateTime.now())) {
            throw new AccessDeniedException("订阅物联网状态需要有效的管理员租户身份");
        }
        return user;
    }

    private <T> T withIdentity(LoginUser user, Supplier<T> action) {
        // 现有权限服务从安全上下文取用户编号，调用完成后恢复线程原有上下文。
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            // 使用 Runnable 重载，避免框架将权限异常包装成通用错误。
            var result = new AtomicReference<T>();
            TenantUtils.execute(user.getTenantId(), (Runnable) () -> result.set(action.get()));
            return result.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private String validate(IotStateSubscriptionMessage.Binding binding, Long tenantId) {
        if (binding == null || blank(binding.getSubscriptionId()) || blank(binding.getDeviceId()) || blank(binding.getPointCode()))
                return "订阅编号、设备编号和点位编码不能为空";
        if (binding.getSubscriptionId().length() > 128 || binding.getDeviceId().length() > 256 || binding.getPointCode().length() > 256)
                return "订阅标识或设备点位长度超出限制";
        return tenantId == null ? "租户身份缺失" : null;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private String indexKey(Long tenantId, String deviceId, String pointCode) {
        return KEY_PREFIX + "index:" + tenantId + ":" + encode(deviceId) + ":" + encode(pointCode);
    }

    private String sessionKey(String sessionId) { return KEY_PREFIX + "session:" + sessionId; }

    private RegisteredBinding parse(String raw) {
        try { return JsonUtils.parseObject(raw, RegisteredBinding.class); }
        catch (RuntimeException ignored) { return null; }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record RegisteredBinding(String subscriptionId, String sessionId, Long tenantId,
                                     String sceneId, String objectId, String deviceId, String pointCode,
                                     String publicationTicketId, Long loginExpiresAt, String domain) {}

    /** 保留原始索引值供会话核对，批量校验后无需再次解析。 */
    private record PublishedDelivery(String raw, RegisteredBinding binding) {}
}
