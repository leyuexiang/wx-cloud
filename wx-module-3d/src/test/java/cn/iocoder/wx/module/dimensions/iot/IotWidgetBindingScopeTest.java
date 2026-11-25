package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 二维消费目标与遥测来源实例不同，仍须严格限定同修订、同场景、同设备点位。 */
class IotWidgetBindingScopeTest {
    @Test
    void shouldAuthorizeOnlyExistingEnabledWidgetInItsOwnDomain() throws Exception {
        var json = new ObjectMapper();
        var threeD = json.readTree("{\"objects\":[{\"instanceId\":\"same\",\"targetType\":\"model\"}]}");
        var twoD = json.readTree("{\"components\":[{\"instanceId\":\"same\"},{\"instanceId\":\"disabled\"}]}");
        var dependencies = json.readTree("""
                {"dataBindings":[
                 {"owner":{"domain":"twoD","ownerType":"instance","instanceId":"same"},"enabled":true,
                  "source":{"type":"adapter","requestConfig":{"adapterId":"iot-state","config":{"deviceId":"d1","pointCode":"temperature"}}}},
                 {"owner":{"domain":"twoD","ownerType":"instance","instanceId":"disabled"},"enabled":false,
                  "source":{"type":"adapter","requestConfig":{"adapterId":"iot-state","config":{"deviceId":"d1","pointCode":"temperature"}}}},
                 {"owner":{"domain":"twoD","ownerType":"instance","instanceId":"removed"},
                  "source":{"type":"adapter","requestConfig":{"adapterId":"iot-state","config":{"deviceId":"d1","pointCode":"temperature"}}}}]}
                """);
        var saved = new IotSceneBindingScope.SavedScene(threeD, dependencies, twoD);
        assertTrue(IotSceneBindingScope.hasBinding(saved, "twoD", "same", "d1", "temperature"));
        for (String target : new String[]{"removed", "disabled"})
            assertFalse(IotSceneBindingScope.hasBinding(saved, "twoD", target, "d1", "temperature"));
        assertFalse(IotSceneBindingScope.hasBinding(saved, "threeD", "same", "d1", "temperature"));
        assertFalse(IotSceneBindingScope.hasBinding(saved, "invalid", "same", "d1", "temperature"));
        assertFalse(IotSceneBindingScope.hasBinding(saved, "twoD", "same", "d2", "temperature"));
        assertFalse(IotSceneBindingScope.hasBinding(saved, "twoD", "same", "d1", "pressure"));
        // 发布快照没有此组件时，不能借草稿组件取得授权。
        assertFalse(IotSceneBindingScope.hasBinding(new IotSceneBindingScope.SavedScene(threeD, dependencies), "twoD", "same", "d1", "temperature"));
    }

    @Test
    void shouldRouteWidgetBySceneWhileKeepingLegacyObjectIsolation() {
        var event = TwinTelemetryEvent.builder().sceneId("10").objectId("sensor-model").build();
        assertTrue(IotSceneBindingScope.matchesEvent(event, "10", "temperature-widget", "twoD"));
        assertFalse(IotSceneBindingScope.matchesEvent(event, "11", "temperature-widget", "twoD"));
        assertFalse(IotSceneBindingScope.matchesEvent(event, "10", "temperature-widget", null));
        assertFalse(IotSceneBindingScope.matchesEvent(event, "10", "temperature-widget", "unknown"));
        assertTrue(IotSceneBindingScope.matchesEvent(event, "10", "sensor-model"));
    }
}
