package cn.iocoder.jtyf.module.vegetation.api;

/** 植被生成任务状态；状态只能按服务层定义的状态机流转。 */
public enum VegetationTaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    /** 部分地块失败但任务仍可重试，数据库脚本已预留此状态。 */
    PARTIAL_FAILED,
    FAILED,
    CANCELED
}
