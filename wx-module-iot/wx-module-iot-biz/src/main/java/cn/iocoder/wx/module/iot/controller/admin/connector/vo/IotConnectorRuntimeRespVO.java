package cn.iocoder.wx.module.iot.controller.admin.connector.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/** 将管理意图、历史状态和租约观测分开；连接状态不代表设备或下游业务健康。 */
@Schema(description = "管理后台 - 连接器运行观测")
@Data
public class IotConnectorRuntimeRespVO {
    private Long id;
    private String connectorId;
    private String connectorType;
    private Boolean enabled;
    @Schema(description = "当前配置版本，不能作为已加载的运行版本")
    private Integer version;
    @Schema(description = "数据库中最后保存的状态，可能已过期")
    private String persistedStatus;
    @Schema(description = "结合启停和数据库租约得出的状态")
    private RuntimeState effectiveStatus;
    @Schema(description = "查询时数据库租约是否有效，不代表已主动探测网络或 Redis")
    private boolean leaseActive;
    private LocalDateTime runtimeLeaseUntil;
    @Schema(description = "数据库观测时间，与租约期限使用相同时间来源")
    private LocalDateTime observedAt;

    /** 未观测与断开不能混为一谈，SINGLE 模式当前没有持久化心跳。 */
    public enum RuntimeState {
        DISABLED, NOT_APPLICABLE, UNOBSERVED, LEASE_EXPIRED,
        CONNECTED, DISCONNECTED, AUTH_FAILED, ERROR, UNKNOWN
    }
}
