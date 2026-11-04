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
import java.util.Objects;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import static cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.CONNECTOR_ID_DUPLICATE;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.CONNECTOR_NOT_EXISTS;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.CONNECTOR_TYPE_INVALID;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.CONNECTOR_VERSION_CONFLICT;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.CONNECTOR_ID_IMMUTABLE;

/** 物联网连接器配置服务实现。 */
@Service
@Validated
@RequiredArgsConstructor
public class IotConnectorServiceImpl implements IotConnectorService {

    private final IotConnectorMapper connectorMapper;
    private final IotConnectorConfigValidator configValidator;

    @Override
    public Long createConnector(IotConnectorSaveReqVO reqVO) {
        reqVO.setConnectorType(normalizeConnectorType(reqVO.getConnectorType()));
        validateConnectorIdUnique(null, reqVO.getConnectorId());
        IotConnectorDO connector = BeanUtils.toBean(reqVO, IotConnectorDO.class);
        connector.setStatus("DISABLED");
        // 连接器创建后统一走显式启用接口，避免启用标志与运行状态矛盾。
        connector.setEnabled(false);
        connector.setVersion(1);
        configValidator.validate(connector);
        connectorMapper.insert(connector);
        return connector.getId();
    }

    @Override
    public void updateConnector(IotConnectorSaveReqVO reqVO) {
        reqVO.setConnectorType(normalizeConnectorType(reqVO.getConnectorType()));
        IotConnectorDO current = validateConnectorExists(reqVO.getId());
        // 编号也是外部设备绑定和持久会话的引用，改名不能被当作普通展示名称编辑。
        if (!Objects.equals(current.getConnectorId(), reqVO.getConnectorId())) {
            throw exception(CONNECTOR_ID_IMMUTABLE);
        }
        if (reqVO.getVersion() != null && !Objects.equals(reqVO.getVersion(), current.getVersion())) {
            throw exception(CONNECTOR_VERSION_CONFLICT);
        }
        configValidator.validate(BeanUtils.toBean(reqVO, IotConnectorDO.class));
        requireUpdated(updateFields(versionCondition(current)
                .set(IotConnectorDO::getName, reqVO.getName())
                .set(IotConnectorDO::getConnectorType, reqVO.getConnectorType())
                .set(IotConnectorDO::getEndpoint, reqVO.getEndpoint())
                .set(IotConnectorDO::getCredentialRef, reqVO.getCredentialRef())
                .set(IotConnectorDO::getConfigJson, reqVO.getConfigJson())
                .set(IotConnectorDO::getStatus, Boolean.TRUE.equals(current.getEnabled()) ? "DISCONNECTED" : "DISABLED")
                .set(IotConnectorDO::getVersion, nextVersion(current))));
    }

    @Override
    public void deleteConnector(Long id) {
        IotConnectorDO current = validateConnectorExists(id);
        requireUpdated(connectorMapper.delete(versionCondition(current)));
    }

    @Override
    public void enableConnector(Long id) {
        IotConnectorDO connector = validateConnectorExists(id);
        if (Boolean.TRUE.equals(connector.getEnabled())) {
            return;
        }
        configValidator.validate(connector);
        // 启用只表示允许适配器接入，实际连接建立后再由适配器更新为 CONNECTED。
        requireUpdated(updateFields(versionCondition(connector)
                .set(IotConnectorDO::getEnabled, true)
                .set(IotConnectorDO::getStatus, IotConnectorStatus.DISCONNECTED.name())
                .set(IotConnectorDO::getVersion, nextVersion(connector))));
    }

    @Override
    public void disableConnector(Long id) {
        IotConnectorDO connector = validateConnectorExists(id);
        if (!Boolean.TRUE.equals(connector.getEnabled())) {
            return;
        }
        requireUpdated(updateFields(versionCondition(connector)
                .set(IotConnectorDO::getEnabled, false)
                .set(IotConnectorDO::getStatus, IotConnectorStatus.DISABLED.name())
                .set(IotConnectorDO::getVersion, nextVersion(connector))));
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

    private LambdaUpdateWrapper<IotConnectorDO> versionCondition(IotConnectorDO current) {
        return new LambdaUpdateWrapper<IotConnectorDO>().eq(IotConnectorDO::getId, current.getId())
                .eq(IotConnectorDO::getVersion, current.getVersion());
    }

    private int updateFields(LambdaUpdateWrapper<IotConnectorDO> fields) {
        // 空实体只负责触发框架的更新时间/更新人填充，业务字段由条件更新明确指定。
        return connectorMapper.update(new IotConnectorDO(), fields);
    }

    private int nextVersion(IotConnectorDO current) {
        if (current.getVersion() == null || current.getVersion() == Integer.MAX_VALUE) {
            throw exception(CONNECTOR_VERSION_CONFLICT);
        }
        return current.getVersion() + 1;
    }

    private void requireUpdated(int count) {
        if (count != 1) {
            throw exception(CONNECTOR_VERSION_CONFLICT);
        }
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
