package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.service.Dim3dSceneService;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;
import java.util.Objects;

/** 以当前已保存修订和认证租户确定实例可使用的设备点位。 */
final class IotSceneBindingScope {
    private final Dim3dSceneService scenes;
    private final ObjectMapper json;

    IotSceneBindingScope(Dim3dSceneService scenes, ObjectMapper json) {
        this.scenes = scenes;
        this.json = json;
    }

    JsonNode read(String sceneId) {
        if (sceneId == null || !sceneId.matches("[1-9][0-9]{0,18}")) throw denied();
        try {
            var scene = scenes.info(sceneId);
            var revision = (Map<?, ?>) scene.get("currentRevision");
            return json.readTree((String) revision.get("threeDJson"));
        } catch (ServiceException error) {
            if (error.getCode() == 400 || error.getCode() == 401 || error.getCode() == 403 || error.getCode() == 404) {
                throw denied();
            }
            throw error;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("无法解析场景修订", error);
        }
    }

    static boolean hasBinding(JsonNode scene, String objectId, String deviceId, String pointCode) {
        if (scene == null || objectId == null || objectId.isBlank()) return false;
        for (JsonNode object : scene.path("objects")) {
            if (!objectId.equals(object.path("id").asText()) && !objectId.equals(object.path("instanceId").asText())) continue;
            JsonNode metadata = object.path("metadata");
            JsonNode data = metadata.path("dataConfig");
            if (matchesRequest(data.path("request"), deviceId, pointCode)) return true;
            for (JsonNode item : data.path("requests")) {
                if (matchesRequest(item.path("request"), deviceId, pointCode)) return true;
            }
            return matchesPoint(metadata.path("deviceBinding"), deviceId, pointCode);
        }
        return false;
    }

    private static boolean matchesRequest(JsonNode request, String deviceId, String pointCode) {
        JsonNode config = request.path("config");
        return "adapter".equals(request.path("type").asText()) && "iot-state".equals(config.path("adapterId").asText())
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
