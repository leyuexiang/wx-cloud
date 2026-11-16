package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.service.Dim3dSceneService;
import cn.iocoder.wx.module.dimensions.service.ScenePublicationTicketService;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 以认证租户及对应的草稿或发布修订确定实例可使用的设备点位。 */
final class IotSceneBindingScope {
    private final Dim3dSceneService scenes;
    private final ScenePublicationTicketService tickets;
    private final ObjectMapper json;

    IotSceneBindingScope(Dim3dSceneService scenes, ScenePublicationTicketService tickets, ObjectMapper json) {
        this.scenes = scenes;
        this.tickets = tickets;
        this.json = json;
    }

    record SavedScene(JsonNode threeD, JsonNode dependencies) { }
    record ScopedScene(String sceneId, String ticketId, SavedScene savedScene) { }

    SavedScene read(String sceneId) {
        if (sceneId == null || !sceneId.matches("[1-9][0-9]{0,18}")) throw denied();
        try {
            var scene = scenes.info(sceneId);
            var revision = (Map<?, ?>) scene.get("currentRevision");
            return readRevision(revision);
        } catch (ServiceException error) {
            if (error.getCode() == 400 || error.getCode() == 401 || error.getCode() == 403 || error.getCode() == 404) {
                throw denied();
            }
            throw error;
        }
    }

    /** 发布范围只使用票据固定正文，避免访问失效后回退到同场景的可变草稿。 */
    ScopedScene readPublished(String publicationId, String token, long tenant) {
        try {
            var opened = tickets.consumeLiveSnapshot(token, publicationId, tenant);
            return new ScopedScene((String) opened.snapshot().get("sceneId"), opened.ticketId(), readRevision(opened.snapshot()));
        } catch (ServiceException error) {
            if (error.getCode() == 401) throw new AccessDeniedException("发布票据无效或已失效");
            throw error;
        }
    }

    Set<String> activeLiveTickets(long tenant, Set<String> ticketIds) {
        return tickets.activeLiveTickets(tenant, ticketIds);
    }

    private SavedScene readRevision(Map<?, ?> revision) {
        try {
            return new SavedScene(json.readTree((String) revision.get("threeDJson")),
                    json.readTree((String) revision.get("dependenciesJson")));
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("无法解析场景修订", error);
        }
    }

    static boolean hasBinding(SavedScene scene, String objectId, String deviceId, String pointCode) {
        if (scene == null || scene.threeD() == null || objectId == null || objectId.isBlank()) return false;
        JsonNode target = findInstance(scene.threeD().path("objects"), objectId);
        if (target == null) target = findInstance(scene.threeD().path("components"), objectId);
        if (target == null) return false;

        // 四包格式的模型、几何体分别落在 objects、components，绑定以同修订依赖包为准。
        JsonNode dependencies = scene.dependencies();
        if (dependencies != null) {
            for (JsonNode binding : dependencies.path("dataBindings")) {
                JsonNode source = binding.path("source");
                if (isActiveOwner(binding, objectId)
                        && matchesRequest(source.path("type"), source.path("requestConfig"), deviceId, pointCode)) return true;
            }
            for (JsonNode binding : dependencies.path("deviceBindings")) {
                if (isActiveOwner(binding, objectId) && matchesPoint(binding, deviceId, pointCode)) return true;
            }
        }
        // 仅对旧内嵌实例回读历史绑定，防止已从正式依赖包解绑的配置通过旧镜像恢复权限。
        if (target.has("targetType") || target.has("contentJson")) return false;
        JsonNode metadata = target.path("metadata");
        JsonNode data = metadata.path("dataConfig");
        if (matchesLegacyRequest(data.path("request"), deviceId, pointCode)) return true;
        for (JsonNode item : data.path("requests")) {
            if (!item.path("enabled").isBoolean() || item.path("enabled").asBoolean()) {
                if (matchesLegacyRequest(item.path("request"), deviceId, pointCode)) return true;
            }
        }
        return matchesPoint(metadata.path("deviceBinding"), deviceId, pointCode);
    }

    private static JsonNode findInstance(JsonNode nodes, String objectId) {
        for (JsonNode node : nodes) {
            if (objectId.equals(node.path("instanceId").asText(node.path("id").asText()))) return node;
        }
        return null;
    }

    private static boolean isActiveOwner(JsonNode binding, String objectId) {
        JsonNode owner = binding.path("owner");
        return "threeD".equals(owner.path("domain").asText())
                && "instance".equals(owner.path("ownerType").asText())
                && objectId.equals(owner.path("instanceId").asText())
                && (!binding.has("enabled") || binding.path("enabled").booleanValue());
    }

    private static boolean matchesLegacyRequest(JsonNode request, String deviceId, String pointCode) {
        return matchesRequest(request.path("type"), request.path("config"), deviceId, pointCode);
    }

    private static boolean matchesRequest(JsonNode type, JsonNode config, String deviceId, String pointCode) {
        return "adapter".equals(type.asText()) && "iot-state".equals(config.path("adapterId").asText())
                && matchesPoint(config.path("config"), deviceId, pointCode);
    }

    private static boolean matchesPoint(JsonNode point, String deviceId, String pointCode) {
        return deviceId.equals(point.path("deviceId").asText()) && pointCode.equals(point.path("pointCode").asText());
    }

    static boolean matchesEvent(TwinTelemetryEvent event, String sceneId, String objectId) {
        // 事件目标由接入服务映射，不能由订阅请求替遥测指定场景或实例。
        return sceneId != null && objectId != null && Objects.equals(event.getSceneId(), sceneId)
                && Objects.equals(event.getObjectId(), objectId);
    }

    static AccessDeniedException denied() {
        return new AccessDeniedException("场景实例没有配置此物联网点位");
    }
}
