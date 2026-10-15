package cn.iocoder.wx.module.vegetation.service;

import cn.iocoder.wx.module.vegetation.api.VegetationDynamicTileWindowRequest;
import cn.iocoder.wx.module.vegetation.api.VegetationDynamicTileWindowResponse;
import cn.iocoder.wx.module.vegetation.api.VegetationGenerationRequest;
import cn.iocoder.wx.module.vegetation.api.VegetationTaskResponse;
import cn.iocoder.wx.module.vegetation.api.VegetationTaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 动态九宫格的逐块幂等、状态汇总和失败隔离测试。 */
class VegetationDynamicTileServiceTest {

    @Test
    void createsNineIndependentTasksAndReturnsReadyBlocks() {
        VegetationTaskService taskService = mock(VegetationTaskService.class);
        when(taskService.create(any())).thenReturn(response("task-1", VegetationTaskStatus.SUCCESS));
        VegetationDynamicTileService service = new VegetationDynamicTileService(taskService, new ObjectMapper());

        VegetationDynamicTileWindowResponse result = service.create(request(false));

        assertEquals(1, result.protocolVersion());
        assertEquals(9, result.tiles().size());
        assertEquals(9, result.tiles().stream()
                .filter(tile -> tile.status() == VegetationDynamicTileWindowResponse.Status.HIT)
                .count());
        verify(taskService, times(9)).create(any());
        // 已完成块不再额外轮询，避免缓存命中时重复访问Python服务。
        verify(taskService, times(0)).get(any());
    }

    @Test
    void oneFailedBlockDoesNotDiscardOtherEightBlocks() {
        VegetationTaskService taskService = mock(VegetationTaskService.class);
        AtomicInteger calls = new AtomicInteger();
        when(taskService.create(any())).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("模拟单块生成失败");
            }
            return response("task-ready", VegetationTaskStatus.SUCCESS);
        });
        VegetationDynamicTileService service = new VegetationDynamicTileService(taskService, new ObjectMapper());

        List<VegetationDynamicTileWindowRequest.Tile> tiles = new ArrayList<>(request(false).tiles());
        // 通过重复请求身份验证失败路径，不能让异常扩散为整个窗口500。
        VegetationDynamicTileWindowRequest.Tile original = tiles.get(0);
        tiles.set(0, new VegetationDynamicTileWindowRequest.Tile(
                original.tileId() + "-bad", original.latitudeRow(), original.longitudeColumn(),
                original.longitudeColumnCount(), original.polygon(), original.originLongitude(),
                original.originLatitude(), new VegetationDynamicTileWindowRequest.CacheIdentity(
                        1, "scene-1", "rule-v1", 7L, original.tileId() + "-bad", null)));

        VegetationDynamicTileWindowResponse result = service.create(
                new VegetationDynamicTileWindowRequest(1, request(false).generation(), tiles));

        assertEquals(1, result.tiles().stream()
                .filter(tile -> tile.status() == VegetationDynamicTileWindowResponse.Status.FAILED)
                .count());
        assertEquals(8, result.tiles().stream()
                .filter(tile -> tile.status() == VegetationDynamicTileWindowResponse.Status.HIT)
                .count());
    }

    @Test
    void rejectsDuplicateTileIdsBeforeCallingTaskService() {
        VegetationTaskService taskService = mock(VegetationTaskService.class);
        VegetationDynamicTileService service = new VegetationDynamicTileService(taskService, new ObjectMapper());
        List<VegetationDynamicTileWindowRequest.Tile> tiles = new ArrayList<>(request(false).tiles());
        VegetationDynamicTileWindowRequest.Tile first = tiles.get(0);
        tiles.set(1, first);

        assertThrows(IllegalArgumentException.class,
                () -> service.create(new VegetationDynamicTileWindowRequest(1, request(false).generation(), tiles)));
        verify(taskService, times(0)).create(any());
    }

    private VegetationTaskResponse response(String taskId, VegetationTaskStatus status) {
        return new VegetationTaskResponse(taskId, status, 1, 1, 0, 1, 1,
                "/vegetation/manifest.json", null, null, "/vegetation/tileset.json",
                "sha256:test", "0.3.1");
    }

    private VegetationDynamicTileWindowRequest request(boolean ignored) {
        VegetationGenerationRequest generation = new VegetationGenerationRequest(
                "scene-1", "rule-v1", "0.3.1", 7L, 100D, 120D, 30D, null, null,
                List.of(new VegetationGenerationRequest.VegetationRule(
                        "rule-1", polygon(), 10D, 2D, species())), null);
        List<VegetationDynamicTileWindowRequest.Tile> tiles = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            String tileId = "tile-" + index;
            List<VegetationGenerationRequest.GeoPoint> tilePolygon = polygon(120D + index * 0.001D, 30D);
            tiles.add(new VegetationDynamicTileWindowRequest.Tile(tileId, 1, index, 100,
                    tilePolygon, tilePolygon.get(0).longitude(), tilePolygon.get(0).latitude(),
                    new VegetationDynamicTileWindowRequest.CacheIdentity(
                            1, "scene-1", "rule-v1", 7L, tileId, null)));
        }
        return new VegetationDynamicTileWindowRequest(1, generation, tiles);
    }

    private List<VegetationGenerationRequest.GeoPoint> polygon() {
        return polygon(120D, 30D);
    }

    private List<VegetationGenerationRequest.GeoPoint> polygon(double longitude, double latitude) {
        return List.of(
                new VegetationGenerationRequest.GeoPoint(longitude, latitude),
                new VegetationGenerationRequest.GeoPoint(longitude + 0.001D, latitude),
                new VegetationGenerationRequest.GeoPoint(longitude + 0.001D, latitude + 0.001D),
                new VegetationGenerationRequest.GeoPoint(longitude, latitude + 0.001D),
                new VegetationGenerationRequest.GeoPoint(longitude, latitude));
    }

    private List<VegetationGenerationRequest.Species> species() {
        return List.of(new VegetationGenerationRequest.Species(
                "species-1", "/files/tree.glb", "asset-tree", "v1", "glb", 1D, 0.8D, 1.2D));
    }
}
