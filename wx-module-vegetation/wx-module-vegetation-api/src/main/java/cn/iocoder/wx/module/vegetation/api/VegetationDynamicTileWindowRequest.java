package cn.iocoder.wx.module.vegetation.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 动态植被九宫格请求。
 *
 * <p>{@code generation} 复用既有三类植被规则快照，{@code tiles} 只表达本次相机窗口内的九个
 * 独立地块。服务端会为每块地块建立独立任务和幂等键，因此动态请求不会改变圈选、行道树原有任务的
 * 发布、取消或回滚语义。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VegetationDynamicTileWindowRequest(
        @Positive int protocolVersion,
        @NotNull @Valid VegetationGenerationRequest generation,
        @NotEmpty @Size(min = 9, max = 9) @Valid List<Tile> tiles
) {

    /** 单块动态地块的稳定身份、闭合边界和局部投影原点。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tile(
            @NotBlank String tileId,
            int latitudeRow,
            int longitudeColumn,
            @Positive int longitudeColumnCount,
            @NotEmpty @Valid List<VegetationGenerationRequest.GeoPoint> polygon,
            @DecimalMin("-180") @DecimalMax("180") double originLongitude,
            @DecimalMin("-90") @DecimalMax("90") double originLatitude,
            @NotNull @Valid CacheIdentity cacheIdentity
    ) {
    }

    /**
     * 后端缓存身份。
     *
     * <p>客户端提交结构化身份便于审计；真正的幂等键由 Java 根据规范化快照和该身份计算，客户端不能
     * 通过伪造短键把不同规则或不同高程版本写入同一缓存。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CacheIdentity(
            @Positive int protocolVersion,
            @NotBlank String sceneId,
            @NotBlank String ruleVersion,
            long seed,
            @NotBlank String tileId,
            @Valid VegetationGenerationRequest.ElevationSource elevationSource
    ) {
    }
}
