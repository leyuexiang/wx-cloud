package cn.iocoder.wx.module.iot.controller.admin.devicebinding.vo;

import cn.iocoder.wx.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 管理端有界分页，筛选在数据库执行，避免全量下载设备绑定。 */
@Schema(description = "管理后台 - 设备绑定分页请求")
@Data
@EqualsAndHashCode(callSuper = true)
public class IotDeviceBindingPageReqVO extends PageParam {
    @Size(max = 64)
    private String connectorId;
    @Size(max = 128)
    private String externalDeviceId;
    /** 模型数据源仅查询当前场景实例的已绑定设备。 */
    @Size(max = 128)
    private String sceneId;
    @Size(max = 128)
    private String objectId;
    private Boolean enabled;
}
