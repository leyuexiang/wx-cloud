package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorSaveReqVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;

import java.util.List;

/** 物联网外部连接器配置服务。 */
public interface IotConnectorService {

    Long createConnector(IotConnectorSaveReqVO reqVO);

    void updateConnector(IotConnectorSaveReqVO reqVO);

    void deleteConnector(Long id);

    void enableConnector(Long id);

    void disableConnector(Long id);

    IotConnectorDO getConnector(Long id);

    IotConnectorDO getConnector(String connectorId);

    List<IotConnectorDO> getConnectorList(String connectorId, Boolean enabled);
}
