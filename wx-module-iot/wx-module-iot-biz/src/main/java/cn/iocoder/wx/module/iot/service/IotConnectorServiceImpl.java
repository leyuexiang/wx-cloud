package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.util.object.BeanUtils;
import cn.iocoder.wx.module.iot.api.IotConnectorStatus;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorSaveReqVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;

import static cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.CONNECTOR_ID_DUPLICATE;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.CONNECTOR_NOT_EXISTS;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.CONNECTOR_TYPE_INVALID;

/** 物联网连接器配置服务实现。 */
@Service
@Validated
@RequiredArgsConstructor
public class IotConnectorServiceImpl implements IotConnectorService {

    private final IotConnectorMapper connectorMapper;

    @Override
    public Long createConnector(IotConnectorSaveReqVO reqVO) {
        reqVO.setConnectorType(normalizeConnectorType(reqVO.getConnectorType()));
        validateConnectorIdUnique(null, reqVO.getConnectorId());
        IotConnectorDO connector = BeanUtils.toBean(reqVO, IotConnectorDO.class);
        connector.setStatus("DISABLED");
        // 连接器创建后统一走显式启用接口，避免启用标志与运行状态矛盾。
        connector.setEnabled(false);
        connector.setVersion(1);
        connectorMapper.insert(connector);
        return connector.getId();
    }

    @Override
    public void updateConnector(IotConnectorSaveReqVO reqVO) {
        reqVO.setConnectorType(normalizeConnectorType(reqVO.getConnectorType()));
        validateConnectorExists(reqVO.getId());
        validateConnectorIdUnique(reqVO.getId(), reqVO.getConnectorId());
        IotConnectorDO updateObj = BeanUtils.toBean(reqVO, IotConnectorDO.class);
        // 不更新启停字段，避免与并发执行的启用/停用接口发生状态覆盖。
        updateObj.setEnabled(null);
        connectorMapper.updateById(updateObj);
    }

    @Override
    public void deleteConnector(Long id) {
        validateConnectorExists(id);
        connectorMapper.deleteById(id);
    }

    @Override
    public void enableConnector(Long id) {
        IotConnectorDO connector = validateConnectorExists(id);
        connector.setEnabled(true);
        // 启用只表示允许适配器接入，实际连接建立后再由适配器更新为 CONNECTED。
        connector.setStatus(IotConnectorStatus.DISCONNECTED.name());
        connectorMapper.updateById(connector);
    }

    @Override
    public void disableConnector(Long id) {
        IotConnectorDO connector = validateConnectorExists(id);
        connector.setEnabled(false);
        connector.setStatus(IotConnectorStatus.DISABLED.name());
        connectorMapper.updateById(connector);
    }

    @Override
    public IotConnectorDO getConnector(Long id) {
        return connectorMapper.selectById(id);
    }

    @Override
    public IotConnectorDO getConnector(String connectorId) {
        return connectorMapper.selectByConnectorId(connectorId);
    }

    @Override
    public List<IotConnectorDO> getConnectorList(String connectorId, Boolean enabled) {
        return connectorMapper.selectList(connectorId, enabled);
    }

    private IotConnectorDO validateConnectorExists(Long id) {
        IotConnectorDO connector = id == null ? null : connectorMapper.selectById(id);
        if (connector == null) {
            throw exception(CONNECTOR_NOT_EXISTS);
        }
        return connector;
    }

    private void validateConnectorIdUnique(Long id, String connectorId) {
        IotConnectorDO connector = connectorMapper.selectByConnectorId(connectorId);
        if (connector != null && !connector.getId().equals(id)) {
            throw exception(CONNECTOR_ID_DUPLICATE);
        }
    }

    private String normalizeConnectorType(String connectorType) {
        if (connectorType == null) {
            throw exception(CONNECTOR_TYPE_INVALID);
        }
        try {
            return cn.iocoder.wx.module.iot.api.IotConnectorType.valueOf(
                    connectorType.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw exception(CONNECTOR_TYPE_INVALID);
        }
    }
}
