package cn.iocoder.wx.module.vegetation.service;

import cn.iocoder.wx.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.wx.module.vegetation.api.VegetationGenerationRequest;
import cn.iocoder.wx.module.vegetation.api.VegetationTaskResponse;
import cn.iocoder.wx.module.vegetation.api.VegetationTaskStatus;
import cn.iocoder.wx.module.vegetation.api.VegetationTaskProgress;
import cn.iocoder.wx.module.vegetation.api.VegetationRuleScope;
import cn.iocoder.wx.module.vegetation.api.VegetationRuleType;
import cn.iocoder.wx.module.vegetation.config.VegetationGeneratorProperties;
import cn.iocoder.wx.module.vegetation.dal.dataobject.VegetationGenerationTaskDO;
import cn.iocoder.wx.module.vegetation.dal.mysql.VegetationGenerationTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

/** 任务持久化、恢复和幂等行为测试。 */
@Import({VegetationTaskService.class, VegetationTaskServiceTest.TestObjectMapperConfiguration.class})
public class VegetationTaskServiceTest extends BaseDbUnitTest {

    /** 基础数据库测试不加载 Web 自动配置，这里提供与生产用途一致的 JSON 序列化器。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestObjectMapperConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        /** 单元测试显式注册服务端版本配置，保持与生产配置装配边界一致。 */
        @Bean
        VegetationGeneratorProperties vegetationGeneratorProperties() {
            return new VegetationGeneratorProperties();
        }
    }

    @Resource
    private VegetationTaskService taskService;

    @Resource
    private VegetationGenerationTaskMapper taskMapper;

    @MockBean
    private VegetationGeneratorClient generatorClient;

    @Test
    public void testCreateIsIdempotentAndPersisted() {
        VegetationGenerationRequest request = request("same-key");
        when(generatorClient.create(any(VegetationGenerationRequest.class), anyString(), anyString()))
                .thenReturn(response("remote-task", VegetationTaskStatus.RUNNING));

        VegetationTaskResponse first = taskService.create(request);
        VegetationTaskResponse second = taskService.create(request);

        assertEquals(first.taskId(), second.taskId());
        assertEquals("remote-task", first.taskId());
        assertEquals(VegetationTaskStatus.RUNNING, second.status());
        VegetationGenerationTaskDO stored = taskMapper.selectByGeneratorTaskId(first.taskId());
        assertNotNull(stored);
        assertEquals("same-key", stored.getIdempotencyKey());
        assertNotNull(stored.getSnapshotJson());
        // 版本必须来自Java服务端配置，不能沿用旧客户端默认值。
        assertTrue(stored.getSnapshotJson().contains("\"generatorVersion\":\"0.3.1\""));
        // 三档聚合边长必须随不可变快照落库，Python重试和发布回滚都不能丢失配置。
        assertTrue(stored.getSnapshotJson().contains("\"tileSizeM\":100"));
        assertTrue(stored.getSnapshotJson().contains("\"tileSizeM\":200"));
        assertTrue(stored.getSnapshotJson().contains("\"tileSizeM\":400"));
        verify(generatorClient, times(1)).create(any(VegetationGenerationRequest.class), anyString(), anyString());
    }

    @Test
    public void testRecoverUnfinishedTaskFromDatabase() {
        VegetationGenerationTaskDO task = VegetationGenerationTaskDO.builder()
                .id(9001L)
                .sceneId("scene-recover")
                .snapshotVersion("snapshot-recover")
                .ruleVersion("v1")
                .idempotencyKey("recover-key")
                .status(VegetationTaskStatus.RUNNING.name())
                .totalTileCount(2)
                .completedTileCount(1)
                .failedTileCount(0)
                .candidateCount(10L)
                .instanceCount(4L)
                .snapshotJson("{\"sceneId\":\"scene-recover\"}")
                .deleted(false)
                .build();
        taskMapper.insert(task);
        when(generatorClient.get("9001"))
                .thenReturn(new VegetationTaskResponse("9001", VegetationTaskStatus.SUCCESS,
                        2, 2, 0, 20, 8, "/files/manifest.json", null,
                        "publication-1", "/files/tileset.json", "sha256:source", "0.1.0",
                        java.util.Map.of("forest", "/files/forest/tileset.json"),
                        java.util.Map.of("forest", java.util.Map.of(
                                "high", "/files/forest/density/high/tileset.json",
                                "balanced", "/files/forest/density/balanced/tileset.json",
                                "performance", "/files/forest/density/performance/tileset.json")),
                        java.util.Map.of()));

        assertEquals(1, taskService.recoverUnfinishedTasks());

        VegetationGenerationTaskDO recovered = taskMapper.selectById(9001L);
        assertEquals(VegetationTaskStatus.SUCCESS.name(), recovered.getStatus());
        assertEquals(2, recovered.getCompletedTileCount());
        assertEquals(8L, recovered.getInstanceCount());
        assertEquals("/files/tileset.json", recovered.getTilesetUrl());
        assertEquals("sha256:source", recovered.getSourceHash());
        // 三档入口必须随任务落库，发布和回滚不得依赖Python任务仍在线。
        assertTrue(recovered.getLayerDensityTilesetUrlsJson().contains("density/performance"));
        verify(generatorClient).get("9001");
    }

    @Test
    public void testCorridorWithoutPathIsRejectedBeforePersistence() {
        VegetationGenerationRequest.VegetationRule rule = new VegetationGenerationRequest.VegetationRule(
                "corridor-1", VegetationRuleType.CORRIDOR, "roadside", 1,
                null, null, null, null, null, null, null, null, null,
                3D, 8D, 0D, 0D, true, null, null, species());
        VegetationGenerationRequest request = new VegetationGenerationRequest(
                "scene-1", "v2", "0.1.0", 1L, 100D, 120D, 30D, null, null,
                java.util.List.of(rule), "invalid-corridor");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> taskService.create(request));
        assertTrue(error.getMessage().contains("path至少需要2个点"));
    }

    @Test
    public void testOverlappingAltitudeZonesAreRejectedBeforeDemCheck() {
        VegetationGenerationRequest.AltitudeZone first = new VegetationGenerationRequest.AltitudeZone(
                "low", 0, 500, 0, 20, 100, 5, 0, species());
        VegetationGenerationRequest.AltitudeZone second = new VegetationGenerationRequest.AltitudeZone(
                "high", 400, 800, 0, 20, 100, 5, 0, species());
        VegetationGenerationRequest.VegetationRule rule = new VegetationGenerationRequest.VegetationRule(
                "altitude-1", VegetationRuleType.ALTITUDE_ZONATION, "forest", 2,
                polygon(), null, null, null, null, null, null, VegetationRuleScope.POLYGON,
                null, null, null, null, null, null, null, java.util.List.of(first, second), species());
        VegetationGenerationRequest request = new VegetationGenerationRequest(
                "scene-1", "v2", "0.1.0", 1L, 100D, 120D, 30D, null, null,
                java.util.List.of(rule), "invalid-altitude");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> taskService.create(request));
        assertTrue(error.getMessage().contains("不能实质重叠"));
    }

    @Test
    public void testAltitudeRuleWithoutElevationSourceIsRejected() {
        VegetationGenerationRequest.AltitudeZone zone = new VegetationGenerationRequest.AltitudeZone(
                "low", 0, 500, 0, 20, 100, 5, 0, species());
        VegetationGenerationRequest.VegetationRule rule = new VegetationGenerationRequest.VegetationRule(
                "altitude-1", VegetationRuleType.ALTITUDE_ZONATION, "forest", 2,
                polygon(), null, null, null, null, null, null, VegetationRuleScope.POLYGON,
                null, null, null, null, null, null, null, java.util.List.of(zone), null);
        VegetationGenerationRequest request = new VegetationGenerationRequest(
                "scene-1", "v2", "0.1.0", 1L, 100D, 120D, 30D, null, null,
                java.util.List.of(rule), "altitude-without-dem");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> taskService.create(request));
        assertTrue(error.getMessage().contains("数字高程模型"));
    }

    @Test
    public void testGltfSpeciesIsRejectedBeforePersistence() {
        // 植被生成只消费资源库已有GLB；Java必须在持久化和调用Python前阻断GLTF。
        java.util.List<VegetationGenerationRequest.Species> gltfSpecies = java.util.List.of(
                new VegetationGenerationRequest.Species(
                        "species-gltf", "/files/species/tree.gltf", "asset-gltf", "model-v1", "gltf",
                        1D, 0.8D, 1.2D));
        VegetationGenerationRequest request = new VegetationGenerationRequest(
                "scene-1", "rule-v1", 7L, 10D, 120D, 30D,
                java.util.List.of(new VegetationGenerationRequest.VegetationRule(
                        "rule-gltf", polygon(), 10D, 2D, gltfSpecies)), "reject-gltf");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> taskService.create(request));
        assertTrue(error.getMessage().contains("只接受GLB"));
    }

    private VegetationTaskResponse response(String taskId, VegetationTaskStatus status) {
        return new VegetationTaskResponse(taskId, status, 3, 1, 0, 12, 6, null, null);
    }

    @Test
    public void testProgressSurvivesDatabaseReadAndRemoteOutage() {
        // 使用字符串时间避免测试ObjectMapper依赖Java时间扩展，字段含义与Python响应一致。
        var progress = new VegetationTaskProgress("sampling", "2026-09-09T08:00:00Z",
                java.util.Map.of("preparing", 300L), java.util.Map.of("demRequests", 250L),
                1500L, "2026-09-09T07:59:59Z", "2026-09-09T08:00:00Z", null, null, false);
        var response = new VegetationTaskResponse("progress-task", VegetationTaskStatus.RUNNING,
                0, 0, 0, 0, 0, null, null, null, null, null, "0.3.1",
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), progress);
        when(generatorClient.create(any(VegetationGenerationRequest.class), anyString(), anyString()))
                .thenReturn(response);
        assertEquals(progress, taskService.create(request("progress-key")).progress());
        assertNotNull(taskMapper.selectByGeneratorTaskId("progress-task").getProgressJson());
        when(generatorClient.get("progress-task")).thenThrow(new IllegalStateException("模拟远端不可用"));
        var cached = taskService.get("progress-task");
        assertTrue(cached.progress().stale());
        assertEquals(1500L, cached.progress().totalDurationMs());
        assertEquals(VegetationTaskStatus.RUNNING, cached.status());
        // 远端记录缺失也属于未确认，不能把数据库中的旧耗时继续标记为实时。
        doReturn(null).when(generatorClient).get("progress-task");
        assertTrue(taskService.get("progress-task").progress().stale());
        // 恢复连接后旧生成器没有观测字段，必须清空旧RUNNING阶段，不能冒充完成态。
        doReturn(response("progress-task", VegetationTaskStatus.SUCCESS)).when(generatorClient).get("progress-task");
        assertNull(taskService.get("progress-task").progress());
        // updateById必须把数据库中的旧摘要同步置空，否则下一次远端故障会重新显示过期阶段。
        assertNull(taskMapper.selectByGeneratorTaskId("progress-task").getProgressJson());
    }

    private VegetationGenerationRequest request(String idempotencyKey) {
        return new VegetationGenerationRequest("scene-1", "rule-v1", 7L, 10D,
                120D, 30D, java.util.List.of(new VegetationGenerationRequest.VegetationRule(
                "rule-1", polygon(), 10D, 2D, species())), idempotencyKey);
    }

    /** 返回闭合且有面积的小范围测试多边形，满足服务层正式几何校验。 */
    private java.util.List<VegetationGenerationRequest.GeoPoint> polygon() {
        return java.util.List.of(
                new VegetationGenerationRequest.GeoPoint(120D, 30D),
                new VegetationGenerationRequest.GeoPoint(120.001D, 30D),
                new VegetationGenerationRequest.GeoPoint(120.001D, 30.001D),
                new VegetationGenerationRequest.GeoPoint(120D, 30.001D),
                new VegetationGenerationRequest.GeoPoint(120D, 30D));
    }

    /** 所有规则共用已有GLB资源，避免测试通过旧式缺省类型绕过正式协议校验。 */
    private java.util.List<VegetationGenerationRequest.Species> species() {
        return java.util.List.of(new VegetationGenerationRequest.Species(
                "species-1", "/files/species/tree.glb", "asset-tree", "model-v1", "glb",
                1D, 0.8D, 1.2D));
    }
}
