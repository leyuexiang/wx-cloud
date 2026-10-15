package cn.iocoder.jtyf.module.vegetation.service;

import cn.iocoder.jtyf.module.vegetation.api.VegetationGenerationRequest;
import cn.iocoder.jtyf.module.vegetation.api.VegetationGapPolicy;
import cn.iocoder.jtyf.module.vegetation.api.VegetationRuleScope;
import cn.iocoder.jtyf.module.vegetation.api.VegetationRuleType;
import cn.iocoder.jtyf.module.vegetation.api.VegetationTaskResponse;
import cn.iocoder.jtyf.module.vegetation.api.VegetationTaskProgress;
import cn.iocoder.jtyf.module.vegetation.api.VegetationTaskStatus;
import cn.iocoder.jtyf.module.vegetation.config.VegetationGeneratorProperties;
import cn.iocoder.jtyf.module.vegetation.dal.dataobject.VegetationGenerationTaskDO;
import cn.iocoder.jtyf.module.vegetation.dal.mysql.VegetationGenerationTaskMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java 侧任务编排服务。
 *
 * <p>任务状态以数据库为唯一事实来源。创建时先写入不可变快照，再调用 Python；查询时优先
 * 同步远端最新状态，生成器暂时不可用时返回数据库最后一次已确认状态。这样进程重启不会丢失
 * 任务索引，重复请求也不会重复创建远端任务。</p>
 */
@Service
public class VegetationTaskService {

    private static final Logger log = LoggerFactory.getLogger(VegetationTaskService.class);
    private static final double MAX_REGION_AREA_M2 = 10_000_000D;
    private static final int MAX_TILE_COUNT = 100_000;
    private static final int MAX_RULE_COUNT = 10_000;

    private static final Set<String> UNFINISHED_STATUSES = Set.of(
            VegetationTaskStatus.PENDING.name(), VegetationTaskStatus.RUNNING.name());
    private static final Set<String> TERMINAL_STATUSES = EnumSet.of(
            VegetationTaskStatus.SUCCESS, VegetationTaskStatus.FAILED,
            VegetationTaskStatus.PARTIAL_FAILED, VegetationTaskStatus.CANCELED)
            .stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private final VegetationGeneratorClient generatorClient;
    private final VegetationGenerationTaskMapper taskMapper;
    private final ObjectMapper objectMapper;
    private final VegetationGeneratorProperties generatorProperties;

    public VegetationTaskService(VegetationGeneratorClient generatorClient,
                                 VegetationGenerationTaskMapper taskMapper,
                                 ObjectMapper objectMapper,
                                 VegetationGeneratorProperties generatorProperties) {
        this.generatorClient = generatorClient;
        this.taskMapper = taskMapper;
        this.objectMapper = objectMapper;
        this.generatorProperties = generatorProperties;
    }

    /**
     * 幂等创建任务。数据库唯一索引负责处理并发请求，捕获重复键后重新读取已存在任务，
     * 避免在 JVM 内维护第二份任务索引。
     */
    public VegetationTaskResponse create(VegetationGenerationRequest request) {
        request = withServerGeneratorVersion(request);
        validateRequest(request);
        String snapshotJson = serializeSnapshot(request);
        String snapshotHash = sha256(snapshotJson);
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey(), snapshotHash);

        VegetationGenerationTaskDO existing = taskMapper.selectByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            String expectedSnapshotVersion = "veg-snapshot-" + snapshotHash.substring(0, 32);
            if (!expectedSnapshotVersion.equals(existing.getSnapshotVersion())) {
                throw new IllegalArgumentException("相同幂等键不能对应不同的植被规则快照");
            }
            // 进程可能在任务落库后、远端响应前崩溃；再次提交时用同一规范幂等键恢复投递。
            if (VegetationTaskStatus.PENDING.name().equals(existing.getStatus())
                    && (existing.getGeneratorTaskId() == null || existing.getGeneratorTaskId().isBlank())) {
                VegetationTaskResponse remoteResponse = generatorClient.create(
                        request, existing.getId().toString(), idempotencyKey);
                applyRemoteResponse(existing, remoteResponse);
                taskMapper.updateById(existing);
            }
            return toResponse(existing);
        }

        VegetationGenerationTaskDO task = VegetationGenerationTaskDO.builder()
                .sceneId(request.sceneId())
                .snapshotVersion("veg-snapshot-" + snapshotHash.substring(0, 32))
                .ruleVersion(request.ruleVersion())
                .idempotencyKey(idempotencyKey)
                .status(VegetationTaskStatus.PENDING.name())
                .totalTileCount(0)
                .completedTileCount(0)
                .failedTileCount(0)
                .candidateCount(0L)
                .instanceCount(0L)
                .snapshotJson(snapshotJson)
                .deleted(false)
                .build();
        try {
            taskMapper.insert(task);
        } catch (DataIntegrityViolationException duplicate) {
            // 两个实例可能同时通过前置查询；唯一索引胜出的一方就是幂等结果。
            VegetationGenerationTaskDO concurrentTask = taskMapper.selectByIdempotencyKey(idempotencyKey);
            if (concurrentTask != null) {
                return toResponse(concurrentTask);
            }
            throw duplicate;
        }

        VegetationTaskResponse remoteResponse = generatorClient.create(
                request, task.getId().toString(), idempotencyKey);
        applyRemoteResponse(task, remoteResponse);
        taskMapper.updateById(task);
        return toResponse(task);
    }

    /**
     * 复制请求并覆盖服务端生成器版本。浏览器只提交业务规则，不能伪造生成器版本影响幂等哈希、
     * 审计记录或回滚判断；部署升级时只调整配置并与Python镜像同步。
     */
    private VegetationGenerationRequest withServerGeneratorVersion(VegetationGenerationRequest request) {
        if (request == null) return null;
        return new VegetationGenerationRequest(
                request.sceneId(), request.ruleVersion(), generatorProperties.getVersion(), request.seed(),
                request.gridSizeM(), request.originLongitude(), request.originLatitude(), request.elevationSource(),
                request.generationRegion(), request.rules(), request.densityProfiles(),
                request.clipToGenerationRegion(), request.dynamicTileId(), request.idempotencyKey());
    }

    /** 查询任务；生成器不可用时返回数据库中的最后确认状态，保证重启恢复期间接口仍可读。 */
    public VegetationTaskResponse get(String taskId) {
        VegetationGenerationTaskDO task = findTask(taskId);
        if (task == null) {
            return getRemoteOnly(taskId);
        }
        try {
            VegetationTaskResponse remoteResponse = generatorClient.get(remoteTaskId(task));
            if (remoteResponse == null) {
                // 远端任务缺失同样不能把本地旧摘要标记为实时确认。
                return toResponse(task).withStaleProgress();
            }
            applyRemoteResponse(task, remoteResponse);
            taskMapper.updateById(task);
        } catch (IllegalStateException unavailable) {
            // 数据库状态是可恢复的权威快照，远端短暂不可用不应把查询变成 500。
            return toResponse(task).withStaleProgress();
        }
        return toResponse(task);
    }

    /** 取消任务；对已结束任务重复取消直接返回原状态，避免重复访问生成器。 */
    public VegetationTaskResponse cancel(String taskId) {
        VegetationGenerationTaskDO task = findTask(taskId);
        if (task == null) {
            return generatorClient.cancel(taskId);
        }
        if (TERMINAL_STATUSES.contains(task.getStatus())) {
            return toResponse(task);
        }
        VegetationTaskResponse remoteResponse = generatorClient.cancel(remoteTaskId(task));
        if (remoteResponse == null) {
            task.setStatus(VegetationTaskStatus.CANCELED.name());
        } else {
            applyRemoteResponse(task, remoteResponse);
            // 取消请求已被生成器接受时，Java 状态必须至少达到 CANCELED。
            if (!TERMINAL_STATUSES.contains(task.getStatus())) {
                task.setStatus(VegetationTaskStatus.CANCELED.name());
            }
        }
        taskMapper.updateById(task);
        return toResponse(task);
    }

    /**
     * 恢复所有未完成任务。方法可由启动事件或运维重试调用，按任务逐条查询远端，单个任务失败
     * 不会阻断其他任务；重复执行只会覆盖绝对统计值，不会累加，天然幂等。
     */
    public int recoverUnfinishedTasks() {
        List<VegetationGenerationTaskDO> tasks = taskMapper.selectUnfinishedTasks(UNFINISHED_STATUSES);
        int recovered = 0;
        for (VegetationGenerationTaskDO task : tasks) {
            try {
                VegetationTaskResponse response = generatorClient.get(remoteTaskId(task));
                if (response != null) {
                    applyRemoteResponse(task, response);
                    taskMapper.updateById(task);
                    recovered++;
                }
            } catch (IllegalStateException unavailable) {
                // 远端不可用时保留 PENDING/RUNNING，下一次恢复继续处理。
            }
        }
        return recovered;
    }

    /** 应用启动后立即恢复中断任务；恢复失败只保留数据库状态，不阻塞服务启动。 */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recoverUnfinishedTasks();
    }

    private VegetationGenerationTaskDO findTask(String taskId) {
        try {
            VegetationGenerationTaskDO task = taskMapper.selectById(Long.valueOf(taskId));
            if (task != null) {
                return task;
            }
        } catch (NumberFormatException ignored) {
        }
        return taskMapper.selectByGeneratorTaskId(taskId);
    }

    /** 远端任务号存在时必须沿用它，避免 Python 自己生成编号后无法继续查询。 */
    private String remoteTaskId(VegetationGenerationTaskDO task) {
        return task.getGeneratorTaskId() == null || task.getGeneratorTaskId().isBlank()
                ? task.getId().toString() : task.getGeneratorTaskId();
    }

    private VegetationTaskResponse getRemoteOnly(String taskId) {
        try {
            return generatorClient.get(taskId);
        } catch (IllegalStateException unavailable) {
            return null;
        }
    }

    private void applyRemoteResponse(VegetationGenerationTaskDO task, VegetationTaskResponse response) {
        if (response.taskId() != null && !response.taskId().isBlank()) {
            task.setGeneratorTaskId(response.taskId());
        }
        if (response.status() != null) {
            task.setStatus(response.status().name());
        }
        task.setTotalTileCount(response.totalTileCount());
        task.setCompletedTileCount(response.completedTileCount());
        task.setFailedTileCount(response.failedTileCount());
        task.setCandidateCount(response.candidateCount());
        task.setInstanceCount(response.instanceCount());
        task.setPublicationVersion(response.publicationVersion());
        task.setManifestUrl(response.manifestUrl());
        task.setTilesetUrl(response.tilesetUrl());
        try {
            // 旧生成器缺少观测字段时返回空值，不能把上次RUNNING摘要误当成本次完成态。
            task.setProgressJson(response.progress() == null ? null
                    : objectMapper.writeValueAsString(response.progress()));
            task.setLayerTilesetUrlsJson(objectMapper.writeValueAsString(
                    response.layerTilesetUrls() == null ? Map.of() : response.layerTilesetUrls()));
            // 固定三档入口跟随任务结果持久化；后续查询、发布和回滚都不需要重新访问Python服务。
            task.setLayerDensityTilesetUrlsJson(objectMapper.writeValueAsString(
                    response.layerDensityTilesetUrls() == null ? Map.of() : response.layerDensityTilesetUrls()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存植被图层瓦片地址", exception);
        }
        task.setSourceHash(response.sourceHash());
        task.setGeneratorVersion(response.generatorVersion());
        task.setErrorMessage(response.errorMessage());
    }

    private VegetationTaskResponse toResponse(VegetationGenerationTaskDO task) {
        VegetationTaskStatus status;
        try {
            status = VegetationTaskStatus.valueOf(task.getStatus());
        } catch (RuntimeException ignored) {
            status = VegetationTaskStatus.FAILED;
        }
        String taskId = task.getGeneratorTaskId() == null || task.getGeneratorTaskId().isBlank()
                ? task.getId().toString() : task.getGeneratorTaskId();
        return new VegetationTaskResponse(taskId, status,
                valueOrZero(task.getTotalTileCount()), valueOrZero(task.getCompletedTileCount()),
                valueOrZero(task.getFailedTileCount()), valueOrZero(task.getCandidateCount()),
                valueOrZero(task.getInstanceCount()), task.getManifestUrl(), task.getErrorMessage(),
                task.getPublicationVersion(), task.getTilesetUrl(), task.getSourceHash(), task.getGeneratorVersion(),
                parseLayerTilesetUrls(task.getLayerTilesetUrlsJson()),
                parseLayerDensityTilesetUrls(task.getLayerDensityTilesetUrlsJson()),
                parseDensityProfiles(task.getSnapshotJson()), parseProgress(task.getProgressJson()));
    }

    /** 损坏观测数据不影响已有正式结果，且日志不打印任务快照或高程源凭据。 */
    private VegetationTaskProgress parseProgress(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, VegetationTaskProgress.class);
        } catch (JsonProcessingException exception) {
            log.warn("植被阶段摘要JSON损坏，按缺失观测返回");
            return null;
        }
    }

    /** 任务恢复时读取持久化图层入口；坏数据只降级为空映射，不影响基础任务查询。 */
    private Map<String, String> parseLayerTilesetUrls(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
        } catch (JsonProcessingException exception) {
            log.warn("植被任务图层瓦片地址JSON损坏，按空映射返回", exception);
            return Map.of();
        }
    }

    /** 任务恢复时读取每图层三档入口；损坏数据按空映射降级为旧单入口，不能阻断任务查询。 */
    private Map<String, Map<String, String>> parseLayerDensityTilesetUrls(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, objectMapper.getTypeFactory().constructMapType(
                    Map.class, objectMapper.getTypeFactory().constructType(String.class),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class)));
        } catch (JsonProcessingException exception) {
            log.warn("植被任务多档瓦片地址JSON损坏，按空映射返回", exception);
            return Map.of();
        }
    }

    /** 从Java持久化的不可变快照恢复密度层，避免为兼容字段新增数据库列。 */
    private Map<String, VegetationGenerationRequest.DensityProfile> parseDensityProfiles(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            var snapshot = objectMapper.readTree(value);
            var profiles = snapshot.get("densityProfiles");
            if (profiles == null || !profiles.isObject()) return Map.of();
            return VegetationGenerationRequest.withDefaultTileSizes(objectMapper.convertValue(
                    profiles, objectMapper.getTypeFactory().constructMapType(
                            Map.class, String.class, VegetationGenerationRequest.DensityProfile.class)));
        } catch (JsonProcessingException exception) {
            log.warn("植被任务快照密度档案JSON损坏，按空映射返回", exception);
            return Map.of();
        }
    }

    private String serializeSnapshot(VegetationGenerationRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法冻结植被规则快照", exception);
        }
    }

    /**
     * 在落库前完成类型、几何、物种和资源边界校验。Python仍会重复校验，防止内部接口绕过Java边界；
     * 海拔规则必须携带可访问的真实DEM元数据；实际采样和坡度计算由Python生成器执行。
     */
    private void validateRequest(VegetationGenerationRequest request) {
        if (request == null || request.rules() == null || request.rules().isEmpty()) {
            throw new IllegalArgumentException("植被规则不能为空");
        }
        if (request.sceneId() == null || request.sceneId().isBlank()
                || request.ruleVersion() == null || request.ruleVersion().isBlank()) {
            throw new IllegalArgumentException("场景编号和规则版本不能为空");
        }
        validateDensityProfiles(request.densityProfiles());
        if (!Double.isFinite(request.gridSizeM()) || request.gridSizeM() <= 0) {
            throw new IllegalArgumentException("gridSizeM必须大于0");
        }
        if (request.rules().size() > MAX_RULE_COUNT) {
            throw new IllegalArgumentException("植被规则数量超过上限");
        }
        Set<String> ruleIds = new HashSet<>();
        boolean hasCameraAltitudeRule = false;
        for (VegetationGenerationRequest.VegetationRule rule : request.rules()) {
            if (rule == null || rule.ruleId() == null || rule.ruleId().isBlank()) {
                throw new IllegalArgumentException("规则编号不能为空");
            }
            if (!ruleIds.add(rule.ruleId())) {
                throw new IllegalArgumentException("规则编号重复: " + rule.ruleId());
            }
            VegetationRuleType type = rule.type() == null ? VegetationRuleType.POLYGON : rule.type();
            if (rule.type() == null) {
                log.warn("规则 {} 未填写type，按旧版polygon兼容解释", rule.ruleId());
            }
            switch (type) {
                case POLYGON -> validatePolygonRule(rule);
                case CORRIDOR -> validateCorridorRule(rule);
                case ALTITUDE_ZONATION -> {
                    validateAltitudeRule(request, rule);
                    hasCameraAltitudeRule |= rule.scope() == VegetationRuleScope.CAMERA_VIEW;
                }
            }
        }
        if (hasCameraAltitudeRule && request.generationRegion() == null) {
            throw new IllegalArgumentException("camera-view海拔规则必须填写generationRegion");
        }
        if (request.generationRegion() != null) {
            validateClosedPolygon(request.generationRegion().polygon(), "generationRegion");
            double regionAreaM2 = polygonAreaM2(request.generationRegion().polygon());
            if (regionAreaM2 > MAX_REGION_AREA_M2) {
                throw new IllegalArgumentException("generationRegion面积超过上限");
            }
            long estimatedTiles = (long) Math.ceil(regionAreaM2 / (request.gridSizeM() * request.gridSizeM()));
            if (estimatedTiles > MAX_TILE_COUNT) {
                throw new IllegalArgumentException("生成地块数量超过上限");
            }
        }
    }

    /** Java入口先校验三档比例，避免非法密度配置进入数据库或远端队列。 */
    private void validateDensityProfiles(Map<String, VegetationGenerationRequest.DensityProfile> profiles) {
        if (profiles == null || profiles.size() != 3
                || !profiles.keySet().containsAll(Set.of("high", "balanced", "performance"))) {
            throw new IllegalArgumentException("densityProfiles必须包含high、balanced和performance");
        }
        VegetationGenerationRequest.DensityProfile high = profiles.get("high");
        VegetationGenerationRequest.DensityProfile balanced = profiles.get("balanced");
        VegetationGenerationRequest.DensityProfile performance = profiles.get("performance");
        if (high == null || balanced == null || performance == null
                || !Double.isFinite(high.densityRatio()) || !Double.isFinite(balanced.densityRatio())
                || !Double.isFinite(performance.densityRatio())
                || high.densityRatio() != 1D
                || balanced.densityRatio() >= high.densityRatio()
                || performance.densityRatio() >= balanced.densityRatio()) {
            throw new IllegalArgumentException("densityProfiles必须满足high=100%、balanced低于high、performance低于balanced");
        }
        if (high.tileSizeM() < 1 || high.tileSizeM() > 10_000
                || balanced.tileSizeM() < 1 || balanced.tileSizeM() > 10_000
                || performance.tileSizeM() < 1 || performance.tileSizeM() > 10_000
                || high.tileSizeM() > balanced.tileSizeM()
                || balanced.tileSizeM() > performance.tileSizeM()) {
            throw new IllegalArgumentException("densityProfiles聚合边长必须在1～10000米内且满足high<=balanced<=performance");
        }
        for (Map.Entry<String, VegetationGenerationRequest.DensityProfile> entry : profiles.entrySet()) {
            if (entry.getValue() == null || !"stable-subset".equals(entry.getValue().selection())) {
                throw new IllegalArgumentException("densityProfiles仅支持stable-subset选择方式");
            }
        }
    }

    private void validatePolygonRule(VegetationGenerationRequest.VegetationRule rule) {
        validateSpecies(rule.species(), rule.ruleId());
        validateClosedPolygon(rule.polygon(), "规则 " + rule.ruleId() + " polygon");
        if (polygonAreaM2(rule.polygon()) > MAX_REGION_AREA_M2) {
            throw new IllegalArgumentException("规则 " + rule.ruleId() + " polygon面积超过上限");
        }
        requireRange(rule.densityPerHectare(), 0D, 10_000D,
                "规则 " + rule.ruleId() + " densityPerHectare");
        requireRange(rule.minimumSpacingM(), 0D, 1_000D,
                "规则 " + rule.ruleId() + " minimumSpacingM");
        if (rule.distribution() != null && !Set.of("poisson", "grid", "random").contains(rule.distribution())) {
            throw new IllegalArgumentException("不支持的polygon distribution: " + rule.distribution());
        }
        validateSlope(rule.minimumSlopeDegrees(), rule.maximumSlopeDegrees(), rule.ruleId());
    }

    private void validateCorridorRule(VegetationGenerationRequest.VegetationRule rule) {
        validateSpecies(rule.species(), rule.ruleId());
        if (rule.path() == null || rule.path().size() < 2) {
            throw new IllegalArgumentException("行道树path至少需要2个点");
        }
        for (int index = 1; index < rule.path().size(); index++) {
            if (samePoint(rule.path().get(index - 1), rule.path().get(index))) {
                throw new IllegalArgumentException("行道树path不能包含重复或零长度线段");
            }
        }
        if (rule.side() == null) {
            throw new IllegalArgumentException("行道树side不能为空");
        }
        requireNonNegativeRange(rule.offsetM(), 10_000D, "行道树offsetM");
        requireRange(rule.spacingM(), 0D, 10_000D, "行道树spacingM");
        requireNonNegativeRange(rule.startOffsetM(), 10_000D, "行道树startOffsetM");
        requireNonNegativeRange(rule.endOffsetM(), 10_000D, "行道树endOffsetM");
    }

    private void validateAltitudeRule(VegetationGenerationRequest request,
                                      VegetationGenerationRequest.VegetationRule rule) {
        if (rule.scope() == null) {
            throw new IllegalArgumentException("海拔规则scope不能为空");
        }
        if (rule.scope() == VegetationRuleScope.POLYGON) {
            validateClosedPolygon(rule.polygon(), "海拔规则 " + rule.ruleId() + " polygon");
        } else if (request.generationRegion() == null) {
            throw new IllegalArgumentException("camera-view海拔规则必须填写generationRegion");
        }
        if (rule.zones() == null || rule.zones().isEmpty()) {
            throw new IllegalArgumentException("海拔规则至少需要一个分带");
        }
        double previousMaximum = Double.NEGATIVE_INFINITY;
        Set<String> zoneIds = new HashSet<>();
        for (int index = 0; index < rule.zones().size(); index++) {
            VegetationGenerationRequest.AltitudeZone zone = rule.zones().get(index);
            if (zone == null || zone.zoneId() == null || zone.zoneId().isBlank() || !zoneIds.add(zone.zoneId())) {
                throw new IllegalArgumentException("海拔分带编号不能为空或重复");
            }
            if (zone.minimumAltitudeM() >= zone.maximumAltitudeM()
                    || zone.minimumAltitudeM() < previousMaximum) {
                throw new IllegalArgumentException("海拔分带必须按最低海拔升序且不能实质重叠");
            }
            if (rule.gapPolicy() == VegetationGapPolicy.REJECT && index > 0
                    && Double.compare(zone.minimumAltitudeM(), previousMaximum) != 0) {
                throw new IllegalArgumentException("gapPolicy=reject时海拔分带不能存在空隙");
            }
            if (zone.minimumSlopeDegrees() < 0 || zone.maximumSlopeDegrees() > 90
                    || zone.minimumSlopeDegrees() > zone.maximumSlopeDegrees()) {
                throw new IllegalArgumentException("海拔分带坡度范围不合法");
            }
            if (!(zone.densityPerHectare() > 0) || zone.densityPerHectare() > 10_000
                    || !(zone.minimumSpacingM() > 0) || zone.minimumSpacingM() > 1_000) {
                throw new IllegalArgumentException("海拔分带密度或最小株距不合法");
            }
            if (zone.upperTransitionWidthM() < 0
                    || index == rule.zones().size() - 1 && zone.upperTransitionWidthM() != 0) {
                throw new IllegalArgumentException("最后一个海拔分带的过渡宽度必须为0");
            }
            validateSpecies(zone.species(), rule.ruleId() + "/" + zone.zoneId());
            previousMaximum = zone.maximumAltitudeM();
        }
        if (request.elevationSource() == null || request.elevationSource().url() == null
                || request.elevationSource().url().isBlank() || request.elevationSource().version() == null
                || request.elevationSource().version().isBlank()
                || request.elevationSource().verticalDatum() == null
                || request.elevationSource().verticalDatum().isBlank()) {
            throw new IllegalArgumentException("海拔规则必须提供可采样的数字高程模型、版本和垂直基准");
        }
        // Java不执行高程采样，只校验来源元数据；真实DEM、垂直基准转换和坡度计算交给Python生成器。
    }

    private void validateClosedPolygon(List<VegetationGenerationRequest.GeoPoint> polygon, String name) {
        if (polygon == null || polygon.size() < 4) {
            throw new IllegalArgumentException(name + "至少需要4个点");
        }
        for (VegetationGenerationRequest.GeoPoint point : polygon) {
            if (point == null || !Double.isFinite(point.longitude()) || !Double.isFinite(point.latitude())
                    || point.longitude() < -180 || point.longitude() > 180
                    || point.latitude() < -90 || point.latitude() > 90) {
                throw new IllegalArgumentException(name + "包含无效经纬度");
            }
        }
        if (!samePoint(polygon.get(0), polygon.get(polygon.size() - 1))) {
            throw new IllegalArgumentException(name + "必须闭合");
        }
        if (polygonAreaM2(polygon) <= 0) {
            throw new IllegalArgumentException(name + "面积必须大于0");
        }
    }

    private void validateSpecies(List<VegetationGenerationRequest.Species> species, String ruleId) {
        if (species == null || species.isEmpty()) {
            throw new IllegalArgumentException("规则 " + ruleId + " 至少需要一个物种");
        }
        Set<String> ids = new HashSet<>();
        for (VegetationGenerationRequest.Species item : species) {
            if (item == null || item.speciesId() == null || item.speciesId().isBlank()
                    || !ids.add(item.speciesId())) {
                throw new IllegalArgumentException("规则 " + ruleId + " 存在重复或空物种编号");
            }
            // 旧版快照可能使用相对文件服务路径；正式环境由文件服务校验可访问性，Java只拒绝空地址。
            if (item.assetUrl() == null || item.assetUrl().isBlank()) {
                throw new IllegalArgumentException("规则 " + ruleId + " 物种资源地址不可访问: " + item.speciesId());
            }
            // 正式链路必须冻结资源市场编号，Python不得只凭URL猜测资源身份或重新解析模型。
            if (item.assetId() == null || item.assetId().isBlank()) {
                throw new IllegalArgumentException("规则 " + ruleId + " 物种缺少GLB资源编号: " + item.speciesId());
            }
            // GLTF仍可用于普通场景，但植被任务只能复用已有GLB生成I3DM，禁止触发任何转换服务。
            if (!"glb".equals(item.assetKind())) {
                throw new IllegalArgumentException("规则 " + ruleId + " 植被正式生成只接受GLB模型: "
                        + item.speciesId());
            }
            if (!(item.weight() > 0) || !(item.minimumScale() > 0) || item.maximumScale() < item.minimumScale()) {
                throw new IllegalArgumentException("规则 " + ruleId + " 物种权重或缩放范围不合法");
            }
        }
    }

    private void validateSlope(Double minimum, Double maximum, String ruleId) {
        if (minimum == null && maximum == null) {
            return;
        }
        double min = minimum == null ? 0D : minimum;
        double max = maximum == null ? 90D : maximum;
        if (min < 0 || max > 90 || min > max) {
            throw new IllegalArgumentException("规则 " + ruleId + " 坡度范围不合法");
        }
    }

    private void requireRange(Double value, double exclusiveMinimum, double maximum, String name) {
        if (value == null || !Double.isFinite(value) || value <= exclusiveMinimum || value > maximum) {
            throw new IllegalArgumentException(name + "必须大于" + exclusiveMinimum + "且不超过" + maximum);
        }
    }

    private void requireNonNegativeRange(Double value, double maximum, String name) {
        if (value == null || !Double.isFinite(value) || value < 0 || value > maximum) {
            throw new IllegalArgumentException(name + "必须为非负数且不超过" + maximum);
        }
    }

    private boolean samePoint(VegetationGenerationRequest.GeoPoint first,
                              VegetationGenerationRequest.GeoPoint second) {
        return first != null && second != null
                && Double.compare(first.longitude(), second.longitude()) == 0
                && Double.compare(first.latitude(), second.latitude()) == 0;
    }

    private double polygonAreaM2(List<VegetationGenerationRequest.GeoPoint> polygon) {
        double referenceLat = polygon.get(0).latitude();
        double scale = Math.cos(Math.toRadians(referenceLat));
        double area = 0D;
        for (int index = 0; index < polygon.size() - 1; index++) {
            VegetationGenerationRequest.GeoPoint first = polygon.get(index);
            VegetationGenerationRequest.GeoPoint second = polygon.get(index + 1);
            double firstX = Math.toRadians(first.longitude()) * 6_378_137D * scale;
            double firstY = Math.toRadians(first.latitude()) * 6_378_137D;
            double secondX = Math.toRadians(second.longitude()) * 6_378_137D * scale;
            double secondY = Math.toRadians(second.latitude()) * 6_378_137D;
            area += firstX * secondY - secondX * firstY;
        }
        return Math.abs(area / 2D);
    }

    private String normalizeIdempotencyKey(String requestedKey, String snapshotHash) {
        if (requestedKey != null && !requestedKey.isBlank()) {
            return requestedKey.trim();
        }
        return "sha256:" + snapshotHash;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少SHA-256算法", exception);
        }
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
