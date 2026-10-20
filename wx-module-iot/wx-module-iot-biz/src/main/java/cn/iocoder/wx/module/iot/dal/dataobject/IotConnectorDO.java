package cn.iocoder.wx.module.iot.dal.dataobject;

import cn.iocoder.wx.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 外部物联网服务连接器配置，凭据只保存引用，不在本表保存明文。 */
@TableName("wx_iot_connector")
@Data
@EqualsAndHashCode(callSuper = true)
public class IotConnectorDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String connectorId;
    private String name;
    private String connectorType;
    private String status;
    private String endpoint;
    private String credentialRef;
    private String configJson;
    private Boolean enabled;
    private Integer version;
    /** 运行所有者令牌及数据库期限，阻止迟到状态回写覆盖新实例。 */
    private String runtimeOwner;
    private java.time.LocalDateTime runtimeLeaseUntil;
}
