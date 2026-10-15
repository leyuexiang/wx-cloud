package cn.iocoder.wx.module.vegetation.service;

import cn.iocoder.wx.module.vegetation.api.VegetationGenerationRequest;
import cn.iocoder.wx.module.vegetation.config.VegetationGeneratorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Java到Python生成器传输契约测试。 */
class VegetationGeneratorClientTest {

    @Test
    void getReadsOptionalPythonProgressWithoutChangingOldResponseFields() {
        var restTemplate = new RestTemplate();
        var server = MockRestServiceServer.bindTo(restTemplate).build();
        var properties = new VegetationGeneratorProperties();
        properties.setBaseUrl("http://generator.test");
        var client = new VegetationGeneratorClient(restTemplate, properties, new ObjectMapper());
        server.expect(requestTo("http://generator.test/api/v1/generation-tasks/progress-task"))
                .andRespond(withSuccess("""
                        {"taskId":"progress-task","status":"RUNNING","totalTileCount":0,
                         "completedTileCount":0,"failedTileCount":0,"candidateCount":0,"instanceCount":0,
                         "progress":{"currentStage":"sampling","stageStartedAt":"2026-09-09T08:00:00Z",
                         "phaseDurationsMs":{"preparing":123},"metrics":{"demRequests":12},
                         "createdAt":"2026-09-09T07:59:00Z","totalDurationMs":61000}}
                        """, MediaType.APPLICATION_JSON));
        var response = client.get("progress-task");
        assertEquals("sampling", response.progress().currentStage());
        assertEquals(12L, response.progress().metrics().get("demRequests"));
        assertEquals(61000L, response.progress().totalDurationMs());
        server.verify();
    }

    @Test
    void createSeparatesTaskMetadataAndRemovesNullSnapshotFields() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        VegetationGeneratorProperties properties = new VegetationGeneratorProperties();
        properties.setBaseUrl("http://generator.test");
        VegetationGeneratorClient client = new VegetationGeneratorClient(
                restTemplate, properties, new ObjectMapper());

        /*
         * 使用旧版多边形构造器刻意产生type、seedOffset等空值，覆盖线上实际失败路径；
         * Python只应收到非空规则字段，而幂等键必须保留在CreateTaskRequest顶层。
         */
        VegetationGenerationRequest request = new VegetationGenerationRequest(
                "scene-contract", "rule-v1", 7L, 50D, 120D, 30D,
                List.of(new VegetationGenerationRequest.VegetationRule(
                        "rule-contract", polygon(), 100D, 5D, species())),
                "idempotency-contract");

        server.expect(requestTo("http://generator.test/api/v1/generation-tasks"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.taskId").value("task-contract"))
                .andExpect(jsonPath("$.idempotencyKey").value("idempotency-contract"))
                .andExpect(jsonPath("$.snapshot.idempotencyKey").doesNotExist())
                // 静态圈选和行道树请求不能因动态协议新增字段改变历史快照哈希。
                .andExpect(jsonPath("$.snapshot.clipToGenerationRegion").doesNotExist())
                .andExpect(jsonPath("$.snapshot.dynamicTileId").doesNotExist())
                .andExpect(jsonPath("$.snapshot.rules[0].type").doesNotExist())
                .andExpect(jsonPath("$.snapshot.rules[0].seedOffset").doesNotExist())
                .andRespond(withSuccess("""
                        {
                          "taskId":"task-contract",
                          "status":"PENDING",
                          "totalTileCount":0,
                          "completedTileCount":0,
                          "failedTileCount":0,
                          "candidateCount":0,
                          "instanceCount":0
                        }
                        """, MediaType.APPLICATION_JSON));

        assertEquals("task-contract",
                client.create(request, "task-contract", "idempotency-contract").taskId());
        server.verify();
    }

    /** 返回闭合多边形，避免测试数据与正式规则契约不一致。 */
    private List<VegetationGenerationRequest.GeoPoint> polygon() {
        return List.of(
                new VegetationGenerationRequest.GeoPoint(120D, 30D),
                new VegetationGenerationRequest.GeoPoint(120.001D, 30D),
                new VegetationGenerationRequest.GeoPoint(120.001D, 30.001D),
                new VegetationGenerationRequest.GeoPoint(120D, 30.001D),
                new VegetationGenerationRequest.GeoPoint(120D, 30D));
    }

    /** 传入完整已有GLB身份，确保测试不依赖模型转换服务。 */
    private List<VegetationGenerationRequest.Species> species() {
        return List.of(new VegetationGenerationRequest.Species(
                "tree", "https://assets.test/tree.glb", "asset-tree", "asset-v1", "glb",
                1D, 0.8D, 1.2D));
    }
}
