package cn.iocoder.wx.module.vegetation.api;

import java.util.Map;

/** 任务查询响应；实例统计和清单地址用于前端展示与正式发布。 */
public record VegetationTaskResponse(
        String taskId,
        VegetationTaskStatus status,
        int totalTileCount,
        int completedTileCount,
        int failedTileCount,
        long candidateCount,
        long instanceCount,
        String manifestUrl,
        String errorMessage,
        String publicationVersion,
        String tilesetUrl,
        String sourceHash,
        String generatorVersion,
        /** 图层编号到独立tileset入口的映射，前端按图层显隐时直接使用。 */
        Map<String, String> layerTilesetUrls,
        /** 每图层三档严格密度入口；切换档位时客户端整体替换入口，不依赖Cesium继续细化。 */
        Map<String, Map<String, String>> layerDensityTilesetUrls,
        /** 单发布包内三档密度比例；旧生成器返回空映射时保持兼容。 */
        Map<String, VegetationGenerationRequest.DensityProfile> densityProfiles,
        /** 旧任务为空；观测摘要独立于规则快照，不改变生成幂等身份。 */
        VegetationTaskProgress progress
) {
    /** 查询降级时仅替换观测摘要，正式结果及旧状态均保持不变。 */
    public VegetationTaskResponse withStaleProgress() {
        return new VegetationTaskResponse(taskId, status, totalTileCount, completedTileCount,
                failedTileCount, candidateCount, instanceCount, manifestUrl, errorMessage,
                publicationVersion, tilesetUrl, sourceHash, generatorVersion, layerTilesetUrls,
                layerDensityTilesetUrls, densityProfiles, progress == null ? null : progress.asStale());
    }

    /** 保留既有完整构造器，新增观测字段不迫使发布与动态窗口调用方同步改写。 */
    public VegetationTaskResponse(String taskId, VegetationTaskStatus status, int totalTileCount,
                                  int completedTileCount, int failedTileCount, long candidateCount,
                                  long instanceCount, String manifestUrl, String errorMessage,
                                  String publicationVersion, String tilesetUrl, String sourceHash,
                                  String generatorVersion, Map<String, String> layerTilesetUrls,
                                  Map<String, Map<String, String>> layerDensityTilesetUrls,
                                  Map<String, VegetationGenerationRequest.DensityProfile> densityProfiles) {
        this(taskId, status, totalTileCount, completedTileCount, failedTileCount, candidateCount,
                instanceCount, manifestUrl, errorMessage, publicationVersion, tilesetUrl,
                sourceHash, generatorVersion, layerTilesetUrls, layerDensityTilesetUrls,
                densityProfiles, null);
    }

    /** 兼容旧版客户端的九参数响应；新增发布信息缺省为空。 */
    public VegetationTaskResponse(String taskId, VegetationTaskStatus status, int totalTileCount,
                                  int completedTileCount, int failedTileCount, long candidateCount,
                                  long instanceCount, String manifestUrl, String errorMessage) {
        this(taskId, status, totalTileCount, completedTileCount, failedTileCount, candidateCount,
                instanceCount, manifestUrl, errorMessage, null, null, null, null, Map.of(), Map.of(), Map.of());
    }

    /** 兼容旧调用方；未提供图层映射时返回空映射而不是null，减少前端分支。 */
    public VegetationTaskResponse(String taskId, VegetationTaskStatus status, int totalTileCount,
                                  int completedTileCount, int failedTileCount, long candidateCount,
                                  long instanceCount, String manifestUrl, String errorMessage,
                                  String publicationVersion, String tilesetUrl, String sourceHash,
                                  String generatorVersion) {
        this(taskId, status, totalTileCount, completedTileCount, failedTileCount, candidateCount,
                instanceCount, manifestUrl, errorMessage, publicationVersion, tilesetUrl,
                sourceHash, generatorVersion, Map.of(), Map.of(), Map.of());
    }
}
