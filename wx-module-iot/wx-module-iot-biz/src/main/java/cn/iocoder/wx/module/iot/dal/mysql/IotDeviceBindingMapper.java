package cn.iocoder.wx.module.iot.dal.mysql;

import cn.iocoder.wx.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.wx.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.wx.module.iot.dal.dataobject.IotDeviceBindingDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** 外部设备绑定数据访问层。 */
@Mapper
public interface IotDeviceBindingMapper extends BaseMapperX<IotDeviceBindingDO> {

    default IotDeviceBindingDO selectByExternalDevice(String connectorId, String externalDeviceId) {
        return selectOne(new LambdaQueryWrapperX<IotDeviceBindingDO>()
                .eq(IotDeviceBindingDO::getConnectorId, connectorId)
                .eq(IotDeviceBindingDO::getExternalDeviceId, externalDeviceId));
    }

    default IotDeviceBindingDO selectEnabledByExternalDevice(String connectorId, String externalDeviceId) {
        return selectOne(new LambdaQueryWrapperX<IotDeviceBindingDO>()
                .eq(IotDeviceBindingDO::getConnectorId, connectorId)
                .eq(IotDeviceBindingDO::getExternalDeviceId, externalDeviceId)
                .eq(IotDeviceBindingDO::getEnabled, true));
    }

    default List<IotDeviceBindingDO> selectList(String connectorId, Boolean enabled) {
        return selectList(new LambdaQueryWrapperX<IotDeviceBindingDO>()
                .likeIfPresent(IotDeviceBindingDO::getConnectorId, connectorId)
                .eqIfPresent(IotDeviceBindingDO::getEnabled, enabled)
                .orderByDesc(IotDeviceBindingDO::getId));
    }
}
