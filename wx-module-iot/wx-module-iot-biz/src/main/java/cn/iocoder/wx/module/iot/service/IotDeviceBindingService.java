package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.module.iot.controller.admin.devicebinding.vo.IotDeviceBindingSaveReqVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotDeviceBindingDO;

import java.util.List;

/** 外部设备与数字孪生对象绑定服务。 */
public interface IotDeviceBindingService {

    Long createBinding(IotDeviceBindingSaveReqVO reqVO);

    void updateBinding(IotDeviceBindingSaveReqVO reqVO);

    void deleteBinding(Long id);

    IotDeviceBindingDO getBinding(Long id);

    List<IotDeviceBindingDO> getBindingList(String connectorId, Boolean enabled);

    /** 根据外部设备编号解析启用中的绑定。 */
    IotDeviceBindingDO getEnabledBinding(String connectorId, String externalDeviceId);
}
