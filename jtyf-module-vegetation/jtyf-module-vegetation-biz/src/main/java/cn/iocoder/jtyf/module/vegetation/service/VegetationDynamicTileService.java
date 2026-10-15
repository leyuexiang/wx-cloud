package cn.iocoder.jtyf.module.vegetation.service;

import cn.iocoder.jtyf.module.vegetation.api.VegetationDynamicTileWindowRequest;
import cn.iocoder.jtyf.module.vegetation.api.VegetationDynamicTileWindowResponse;
import cn.iocoder.jtyf.module.vegetation.api.VegetationGenerationRequest;
import cn.iocoder.jtyf.module.vegetation.api.VegetationTaskResponse;
import cn.iocoder.jtyf.module.vegetation.api.VegetationTaskStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 动态九宫格任务编排服务。
 *
 * <p>该服务只负责窗口校验、逐块幂等和结果汇总；实际候选点、真实高程采样、坡度和三维瓦片仍由
 * 现有 Python 生成器执行。每块都调用 {@link VegetationTaskService#create(VegetationGenerationRequest)}，
 * 因而不会改写圈选、行道树原有任务的请求或状态机。</p>
 */
@Service
public class VegetationDynamicTileService {

    /** 当前前后端共同冻结的九宫格协议版本。协议变更必须递增，禁止复用旧缓存。 */
    private static final int PROTOCOL_VERSION = 1;
    private static final int TILE_COUNT = 9;

    private final VegetationTaskService taskService;
    private final ObjectMapper objectMapper;

    public VegetationDynamicTileService(VegetationTaskService taskService, ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验并提交九块独立任务。
     *
     * <p>单块调用异常被转换为该块 FAILED，而不是让整个窗口返回 500；这样相机仍可保留其余八块或
     * 上一窗口的可用瓦片。幂等键由服务端计算，包含规则快照、块边界、协议和高程版本。</p>
     */
    public VegetationDynamicTileWindowResponse create(VegetationDynamicTileWindowRequest request) {
        validateWindow(request);
        List<VegetationDynamicTileWindowResponse.Tile> result = new ArrayList<>(TILE_COUNT);
        for (VegetationDynamicTileWindowRequest.Tile tile : request.tiles()) {
            try {
                VegetationGenerationRequest tileRequest = toTileRequest(request.generation(), tile,
                        dynamicIdempotencyKey(request, tile));
                VegetationTaskResponse task = taskService.create(tileRequest);
                // 创建接口只负责投递；对未结束块立即读取一次远端状态，避免窗口重复请求长期停留在PENDING。
                if (task.status() != VegetationTaskStatus.SUCCESS
                        && task.status() != VegetationTaskStatus.FAILED
                        && task.status() != VegetationTaskStatus.CANCELED) {
                    VegetationTaskResponse latest = taskService.get(task.taskId());
                    if (latest != null) {
                        task = latest;
                    }
                }
                result.add(toTileResponse(tile, task));
            } catch (RuntimeException exception) {
                // 单块失败必须隔离；错误信息保留在响应中，便于客户端决定回退或重试。
                result.add(new VegetationDynamicTileWindowResponse.Tile(
                        tile.tileId(), VegetationDynamicTileWindowResponse.Status.FAILED,
                        null, null, null, exception.getMessage()));
            }
        }
        return new VegetationDynamicTileWindowResponse(PROTOCOL_VERSION, result);
    }

    private VegetationGenerationRequest toTileRequest(VegetationGenerationRequest generation,
                                                      VegetationDynamicTileWindowRequest.Tile tile,
                                                      String idempotencyKey) {
        List<VegetationGenerationRequest.GeoPoint> polygon = tile.polygon();
        return new VegetationGenerationRequest(
                generation.sceneId(), generation.ruleVersion(), generation.generatorVersion(), generation.seed(),
                generation.gridSizeM(), tile.originLongitude(), tile.originLatitude(), generation.elevationSource(),
                new VegetationGenerationRequest.GenerationRegion(polygon), generation.rules(),
                generation.densityProfiles(), true, tile.tileId(), idempotencyKey);
    }

    /** 将动态缓存身份与完整块快照哈希为数据库允许长度内的稳定幂等键。 */
    private String dynamicIdempotencyKey(VegetationDynamicTileWindowRequest request,
                                         VegetationDynamicTileWindowRequest.Tile tile) {
        try {
            String payload = objectMapper.writeValueAsString(new Object[] {request.protocolVersion(),
                    request.generation(), tile.cacheIdentity(), tile.tileId(), tile.polygon(),
                    tile.originLongitude(), tile.originLatitude()});
            return "dynamic-v" + request.protocolVersion() + "-" + sha256(payload).substring(0, 64);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法冻结动态植被地块缓存身份", exception);
        }
    }

    private VegetationDynamicTileWindowResponse.Tile toTileResponse(
            VegetationDynamicTileWindowRequest.Tile tile, VegetationTaskResponse task) {
        VegetationDynamicTileWindowResponse.Status status = switch (task.status()) {
            case SUCCESS -> VegetationDynamicTileWindowResponse.Status.HIT;
            case PENDING -> VegetationDynamicTileWindowResponse.Status.PENDING;
            case RUNNING -> VegetationDynamicTileWindowResponse.Status.RUNNING;
            default -> VegetationDynamicTileWindowResponse.Status.FAILED;
        };
        return new VegetationDynamicTileWindowResponse.Tile(tile.tileId(), status, task.taskId(),
                task.tilesetUrl(), task.sourceHash(), task.errorMessage());
    }

    private void validateWindow(VegetationDynamicTileWindowRequest request) {
        if (request == null || request.generation() == null || request.tiles() == null
                || request.tiles().size() != TILE_COUNT) {
            throw new IllegalArgumentException("动态植被请求必须包含完整九宫格");
        }
        if (request.protocolVersion() != PROTOCOL_VERSION) {
            throw new IllegalArgumentException("不支持的动态植被协议版本: " + request.protocolVersion());
        }
        if (request.generation().generationRegion() != null
                || request.generation().idempotencyKey() != null
                || request.generation().clipToGenerationRegion() != null
                || request.generation().dynamicTileId() != null) {
            throw new IllegalArgumentException("动态植被generation不得携带静态范围、幂等键或内部地块字段");
        }
        Set<String> ids = new HashSet<>();
        for (VegetationDynamicTileWindowRequest.Tile tile : request.tiles()) {
            if (tile == null || tile.tileId() == null || tile.tileId().isBlank() || !ids.add(tile.tileId())) {
                throw new IllegalArgumentException("动态植被地块编号不能为空且不能重复");
            }
            VegetationDynamicTileWindowRequest.CacheIdentity identity = tile.cacheIdentity();
            if (identity == null || identity.protocolVersion() != PROTOCOL_VERSION
                    || !tile.tileId().equals(identity.tileId())
                    || !request.generation().sceneId().equals(identity.sceneId())
                    || !request.generation().ruleVersion().equals(identity.ruleVersion())
                    || request.generation().seed() != identity.seed()
                    || !Objects.equals(request.generation().elevationSource(), identity.elevationSource())) {
                throw new IllegalArgumentException("动态植被地块缓存身份与规则快照不一致: " + tile.tileId());
            }
            validateClosedPolygon(tile.polygon(), tile.tileId());
        }
    }

    private void validateClosedPolygon(List<VegetationGenerationRequest.GeoPoint> polygon, String tileId) {
        if (polygon == null || polygon.size() < 4) {
            throw new IllegalArgumentException("动态地块边界至少需要4个点: " + tileId);
        }
        VegetationGenerationRequest.GeoPoint first = polygon.get(0);
        VegetationGenerationRequest.GeoPoint last = polygon.get(polygon.size() - 1);
        if (first == null || last == null || Double.compare(first.longitude(), last.longitude()) != 0
                || Double.compare(first.latitude(), last.latitude()) != 0) {
            throw new IllegalArgumentException("动态地块边界必须闭合: " + tileId);
        }
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少SHA-256算法", exception);
        }
    }
}
