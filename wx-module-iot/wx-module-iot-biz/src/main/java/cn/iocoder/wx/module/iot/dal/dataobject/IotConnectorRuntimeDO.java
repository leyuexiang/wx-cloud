package cn.iocoder.wx.module.iot.dal.dataobject;

import lombok.Data;
import java.time.LocalDateTime;

/** 只读运行快照；查询时由数据库判断期限，不加载配置、秘密引用或所有权令牌。 */
@Data
public class IotConnectorRuntimeDO {
    private Long id;
    private String connectorId;
    private String connectorType;
    private Boolean enabled;
    private Integer version;
    private String status;
    private Boolean ownerPresent;
    private Boolean leaseActive;
    private LocalDateTime runtimeLeaseUntil;
    private LocalDateTime observedAt;
}
