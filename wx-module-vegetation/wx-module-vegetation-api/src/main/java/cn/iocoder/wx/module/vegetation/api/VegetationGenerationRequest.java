package cn.iocoder.wx.module.vegetation.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 前端创建生成任务时提交的不可变植被规则快照。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VegetationGenerationRequest(
        @NotBlank String sceneId,
        @NotBlank String ruleVersion,
        /** 参与快照哈希的生成器协议版本，旧请求缺省时使用按档位聚合版本0.3.1。 */
        String generatorVersion,
        long seed,
        @Positive double gridSizeM,
        @DecimalMin("-180") @DecimalMax("180") double originLongitude,
        @DecimalMin("-90") @DecimalMax("90") double originLatitude,
        @Valid ElevationSource elevationSource,
        @Valid GenerationRegion generationRegion,
        @NotEmpty @Valid List<VegetationRule> rules,
        /** 同一发布包内的稳定密度子集；high/balanced/performance分别对应100/75/55%。 */
        @Valid Map<String, DensityProfile> densityProfiles,
        /** 动态地块才开启的范围裁剪；旧圈选、行道树和城市级任务保持false，避免改变既有语义。 */
        Boolean clipToGenerationRegion,
        /** 动态地块稳定编号；参与Python确定性种子，静态任务为空。 */
        String dynamicTileId,
        /** 幂等键与数据库唯一索引长度一致，空值时由服务按快照自动生成。 */
        @Size(max = 128) String idempotencyKey
) {
    /** 将缺失版本规范化为稳定值，确保Java和Python使用同一份冻结快照。 */
    public VegetationGenerationRequest {
        generatorVersion = generatorVersion == null || generatorVersion.isBlank()
                ? "0.3.1" : generatorVersion.trim();
        if (densityProfiles == null || densityProfiles.isEmpty()) {
            densityProfiles = Map.of(
                    "high", new DensityProfile(1D, "stable-subset", 100),
                    "balanced", new DensityProfile(0.75D, "stable-subset", 200),
                    "performance", new DensityProfile(0.55D, "stable-subset", 400));
        } else {
            densityProfiles = withDefaultTileSizes(densityProfiles);
        }
        // 静态请求保持字段缺省并被JsonInclude剔除，避免自动幂等哈希因新增兼容字段发生漂移；动态请求只传true。
        clipToGenerationRegion = Boolean.TRUE.equals(clipToGenerationRegion) ? Boolean.TRUE : null;
        dynamicTileId = dynamicTileId == null || dynamicTileId.isBlank() ? null : dynamicTileId.trim();
    }

    /** 为旧客户端缺失的tileSizeM按档位补齐默认值，确保快照和Python生成器得到同一契约。 */
    public static Map<String, DensityProfile> withDefaultTileSizes(Map<String, DensityProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return Map.of();
        return profiles.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> {
                    DensityProfile profile = entry.getValue();
                    if (profile == null || profile.tileSizeM() > 0) return profile;
                    int defaultSize = switch (entry.getKey()) {
                        case "high" -> 100;
                        case "balanced" -> 200;
                        case "performance" -> 400;
                        default -> 100;
                    };
                    return new DensityProfile(profile.densityRatio(), profile.selection(), defaultSize);
                }));
    }

    /** 兼容旧版请求构造；缺少生成范围时仅允许使用规则自身范围。 */
    public VegetationGenerationRequest(String sceneId, String ruleVersion, long seed, double gridSizeM,
                                       double originLongitude, double originLatitude,
                                       List<VegetationRule> rules, String idempotencyKey) {
        this(sceneId, ruleVersion, "0.3.1", seed, gridSizeM, originLongitude, originLatitude,
                null, null, rules, null, false, null, idempotencyKey);
    }

    /** 兼容已有全参数调用方；密度档缺省时由Python生成器补齐推荐比例。 */
    public VegetationGenerationRequest(String sceneId, String ruleVersion, String generatorVersion, long seed,
                                       double gridSizeM, double originLongitude, double originLatitude,
                                       ElevationSource elevationSource, GenerationRegion generationRegion,
                                       List<VegetationRule> rules, String idempotencyKey) {
        this(sceneId, ruleVersion, generatorVersion, seed, gridSizeM, originLongitude, originLatitude,
                elevationSource, generationRegion, rules, null, false, null, idempotencyKey);
    }

    /** 兼容包含密度档但尚未包含动态地块字段的源码调用方。 */
    public VegetationGenerationRequest(String sceneId, String ruleVersion, String generatorVersion, long seed,
                                       double gridSizeM, double originLongitude, double originLatitude,
                                       ElevationSource elevationSource, GenerationRegion generationRegion,
                                       List<VegetationRule> rules, Map<String, DensityProfile> densityProfiles,
                                       String idempotencyKey) {
        this(sceneId, ruleVersion, generatorVersion, seed, gridSizeM, originLongitude, originLatitude,
                elevationSource, generationRegion, rules, densityProfiles, false, null, idempotencyKey);
    }

    /** 单发布包密度层配置；比例由Java和Python两侧共同校验。 */
    public record DensityProfile(
            @DecimalMin("0.0001") @DecimalMax("1.0") double densityRatio,
            String selection,
            @Min(1) @Max(10000) int tileSizeM
    ) {
        public DensityProfile {
            selection = selection == null || selection.isBlank() ? "stable-subset" : selection.trim();
        }

        /** 兼容旧源码调用；新请求应显式使用按档位推荐的100/200/400米边长。 */
        public DensityProfile(double densityRatio, String selection) {
            // 使用0标记“未提供”，由外层按high/balanced/performance档位补齐正确默认值。
            this(densityRatio, selection, 0);
        }
    }

    /** 数字高程模型引用；版本和垂直基准共同参与不可变快照哈希。 */
    public record ElevationSource(
            @NotBlank String url,
            @NotBlank String version,
            @NotBlank String verticalDatum
    ) {}

    /** 正式生成范围；camera-view海拔规则必须使用该范围，不能依赖浏览器临时镜头。 */
    public record GenerationRegion(@NotEmpty @Valid List<GeoPoint> polygon) {
    }

    public record VegetationRule(
            @NotBlank String ruleId,
            VegetationRuleType type,
            String layerId,
            Integer seedOffset,
            @Valid List<GeoPoint> polygon,
            @Valid List<GeoPoint> path,
            String distribution,
            Double densityPerHectare,
            Double minimumSpacingM,
            Double minimumSlopeDegrees,
            Double maximumSlopeDegrees,
            VegetationRuleScope scope,
            VegetationCorridorSide side,
            Double offsetM,
            Double spacingM,
            Double startOffsetM,
            Double endOffsetM,
            Boolean alignToPath,
            VegetationGapPolicy gapPolicy,
            @Valid List<AltitudeZone> zones,
            @Valid List<Species> species
    ) {
        /** 兼容旧版仅包含多边形、密度、株距和物种的请求。 */
        public VegetationRule(String ruleId, List<GeoPoint> polygon, double densityPerHectare,
                              double minimumSpacingM, List<Species> species) {
            this(ruleId, null, null, null, polygon, null, null, densityPerHectare,
                    minimumSpacingM, null, null, null, null, null, null, null, null,
                    null, null, null, species);
        }
    }

    /** 海拔分带的单一连续区间；分带顺序和间隙策略由任务服务校验。 */
    public record AltitudeZone(
            @NotBlank String zoneId,
            double minimumAltitudeM,
            double maximumAltitudeM,
            double minimumSlopeDegrees,
            double maximumSlopeDegrees,
            @Positive double densityPerHectare,
            @Positive double minimumSpacingM,
            double upperTransitionWidthM,
            @NotEmpty @Valid List<Species> species
    ) {}

    /** 经纬度点；范围校验在HTTP参数绑定阶段完成，几何关系在任务服务完成。 */
    public record GeoPoint(
            @DecimalMin("-180") @DecimalMax("180") double longitude,
            @DecimalMin("-90") @DecimalMax("90") double latitude
    ) {}

    public record Species(
            @NotBlank String speciesId,
            @NotBlank String assetUrl,
            /** 资源市场稳定编号；正式任务必须冻结编号，禁止仅凭地址猜测或转换模型。 */
            @NotBlank String assetId,
            String assetVersion,
            /** 植被协议只接受glb；普通场景对gltf的兼容不经过本请求。 */
            @NotBlank String assetKind,
            @Positive double weight,
            @Positive double minimumScale,
            @Positive double maximumScale
    ) {
        /** 统一清理资源字段并规范化类型，保证快照哈希不受大小写和首尾空格影响。 */
        public Species {
            assetId = assetId == null ? null : assetId.trim();
            assetUrl = assetUrl == null ? null : assetUrl.trim();
            assetVersion = assetVersion == null || assetVersion.isBlank() ? null : assetVersion.trim();
            assetKind = assetKind == null ? null : assetKind.trim().toLowerCase(Locale.ROOT);
        }

        /**
         * 仅保留旧代码的源代码兼容性。该构造缺少稳定资源编号，正式服务校验会明确拒绝，调用方必须
         * 改用完整构造并传入已有GLB的编号、地址、版本和类型。
         */
        @Deprecated
        public Species(String speciesId, String assetUrl, double weight,
                       double minimumScale, double maximumScale) {
            this(speciesId, assetUrl, null, null, null, weight, minimumScale, maximumScale);
        }
    }
}
