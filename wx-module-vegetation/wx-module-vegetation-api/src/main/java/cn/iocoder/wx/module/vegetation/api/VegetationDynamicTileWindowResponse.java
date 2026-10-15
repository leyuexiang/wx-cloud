package cn.iocoder.wx.module.vegetation.api;

import java.util.List;

/** 动态植被九宫格响应；每块地块独立返回，单块失败不影响其余地块。 */
public record VegetationDynamicTileWindowResponse(
        int protocolVersion,
        List<Tile> tiles
) {
    public VegetationDynamicTileWindowResponse {
        tiles = tiles == null ? List.of() : List.copyOf(tiles);
    }

    /** 单块动态任务状态和正式瓦片入口。 */
    public record Tile(
            String tileId,
            Status status,
            String taskId,
            String tilesetUrl,
            String sourceHash,
            String errorMessage
    ) {
    }

    /**
     * 动态块状态。
     *
     * <p>命中已完成的幂等任务返回 HIT；新建任务按已有生成器状态返回 PENDING、RUNNING、READY 或
     * FAILED。READY 与 HIT 都是客户端可安全挂载的正式瓦片。</p>
     */
    public enum Status {
        HIT, PENDING, RUNNING, READY, FAILED
    }
}
