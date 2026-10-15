package cn.iocoder.wx.module.vegetation.api;

import java.util.Map;

/** 生成器阶段观测；时间为带时区的ISO文本，避免Java数据库本地时区改变计时含义。 */
public record VegetationTaskProgress(
        String currentStage,
        String stageStartedAt,
        Map<String, Long> phaseDurationsMs,
        Map<String, Long> metrics,
        Long totalDurationMs,
        String createdAt,
        String startedAt,
        String finishedAt,
        String failedStage,
        boolean stale
) {
    /** 远端不可用只标记摘要陈旧，不修改数据库中最后确认的任务状态和时长。 */
    public VegetationTaskProgress asStale() {
        return new VegetationTaskProgress(currentStage, stageStartedAt, phaseDurationsMs, metrics,
                totalDurationMs, createdAt, startedAt, finishedAt, failedStage, true);
    }
}
