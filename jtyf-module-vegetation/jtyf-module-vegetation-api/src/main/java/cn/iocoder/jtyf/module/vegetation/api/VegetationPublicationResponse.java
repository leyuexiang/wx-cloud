package cn.iocoder.jtyf.module.vegetation.api;

import java.util.Map;

/** 植被正式发布版本响应；稳定版本切换只改变数据库状态，不复制或删除对象存储瓦片。 */
public record VegetationPublicationResponse(
        String sceneId,
        String publicationVersion,
        Long taskId,
        String ruleVersion,
        String generatorVersion,
        String manifestUrl,
        String tilesetUrl,
        Map<String, String> layerTilesetUrls,
        /** 每图层三档严格密度入口；同一发布版本内只切换不同入口。 */
        Map<String, Map<String, String>> layerDensityTilesetUrls,
        String sourceHash,
        String status,
        Long instanceCount,
        Integer tileCount,
        /** 发布清单内的稳定密度子集比例。 */
        Map<String, VegetationGenerationRequest.DensityProfile> densityProfiles
) {
    /** 兼容旧发布服务调用方；旧版本没有密度层时返回空映射。 */
    public VegetationPublicationResponse(String sceneId, String publicationVersion, Long taskId,
                                         String ruleVersion, String generatorVersion, String manifestUrl,
                                         String tilesetUrl, Map<String, String> layerTilesetUrls,
                                         String sourceHash, String status, Long instanceCount, Integer tileCount) {
        this(sceneId, publicationVersion, taskId, ruleVersion, generatorVersion, manifestUrl, tilesetUrl,
                layerTilesetUrls, Map.of(), sourceHash, status, instanceCount, tileCount, Map.of());
    }
}
