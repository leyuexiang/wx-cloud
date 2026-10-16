package cn.iocoder.wx.module.iot.dal.dataobject;

import cn.iocoder.wx.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 外部设备到内部数字孪生对象的绑定及点位映射版本。 */
@TableName("wx_iot_device_binding")
@Data
@EqualsAndHashCode(callSuper = true)
public class IotDeviceBindingDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String connectorId;
    private String externalDeviceId;
    private String deviceId;
    private String sceneId;
    private String objectId;
    private String mappingRevision;
    private String pointMappingJson;
    private Boolean enabled;
}
