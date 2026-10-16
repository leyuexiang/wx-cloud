package cn.iocoder.wx.module.iot.dal.mysql;

import cn.iocoder.wx.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.wx.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** 物联网连接器配置数据访问层。 */
@Mapper
public interface IotConnectorMapper extends BaseMapperX<IotConnectorDO> {

    default IotConnectorDO selectByConnectorId(String connectorId) {
        return selectOne(IotConnectorDO::getConnectorId, connectorId);
    }

    default List<IotConnectorDO> selectList(String connectorId, Boolean enabled) {
        return selectList(new LambdaQueryWrapperX<IotConnectorDO>()
                .likeIfPresent(IotConnectorDO::getConnectorId, connectorId)
                .eqIfPresent(IotConnectorDO::getEnabled, enabled)
                .orderByDesc(IotConnectorDO::getId));
    }
}
