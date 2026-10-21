package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorPageReqVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorRuntimeRespVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorRuntimeRespVO.RuntimeState;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotMqttConfigValidateReqVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotMqttConfigValidateRespVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorRuntimeDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;

import static cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.CONNECTOR_NOT_EXISTS;

/** 管理端只读与预校验能力，独立于运行管理器，关闭 MQTT 仍可使用。 */
@Service
@Validated
@RequiredArgsConstructor
public class IotConnectorManagementService {
    private final IotConnectorMapper connectors;
    private final IotConnectorConfigValidator configValidator;

    public PageResult<IotConnectorDO> getPage(@Valid IotConnectorPageReqVO request) {
        return connectors.selectManagementPage(TenantContextHolder.getRequiredTenantId(), request);
    }

    public IotMqttConfigValidateRespVO validateMqtt(@Valid IotMqttConfigValidateReqVO request) {
        IotConnectorDO draft = new IotConnectorDO();
        draft.setEndpoint(request.getEndpoint());
        draft.setCredentialRef(request.getCredentialRef());
        draft.setConfigJson(request.getConfigJson());
        var settings = configValidator.parseMqtt(draft);
        // 不回传生成的客户端号：草稿没有正式身份，其指纹不能供实际会话使用。
        return new IotMqttConfigValidateRespVO("MQTT_STRUCTURE", settings.topics().size(), 1);
    }

    public IotConnectorRuntimeRespVO getRuntime(Long id) {
        IotConnectorRuntimeDO row = connectors.selectRuntime(TenantContextHolder.getRequiredTenantId(), id);
        if (row == null) {
            throw exception(CONNECTOR_NOT_EXISTS);
        }
        IotConnectorRuntimeRespVO result = new IotConnectorRuntimeRespVO();
        result.setId(row.getId());
        result.setConnectorId(row.getConnectorId());
        result.setConnectorType(row.getConnectorType());
        result.setEnabled(row.getEnabled());
        result.setVersion(row.getVersion());
        result.setPersistedStatus(row.getStatus());
        result.setLeaseActive(Boolean.TRUE.equals(row.getLeaseActive()));
        result.setRuntimeLeaseUntil(row.getRuntimeLeaseUntil());
        result.setObservedAt(row.getObservedAt());
        result.setEffectiveStatus(effectiveStatus(row));
        return result;
    }

    private RuntimeState effectiveStatus(IotConnectorRuntimeDO row) {
        if (!Boolean.TRUE.equals(row.getEnabled())) {
            return RuntimeState.DISABLED;
        }
        if (!"MQTT".equals(row.getConnectorType())) {
            return RuntimeState.NOT_APPLICABLE;
        }
        if (!Boolean.TRUE.equals(row.getOwnerPresent())) {
            return RuntimeState.UNOBSERVED;
        }
        if (!Boolean.TRUE.equals(row.getLeaseActive())) {
            return RuntimeState.LEASE_EXPIRED;
        }
        // 仅识别运行器已定义的状态，未知历史值不能让整个管理查询失败。
        return switch (row.getStatus() == null ? "" : row.getStatus()) {
            case "CONNECTED" -> RuntimeState.CONNECTED;
            case "DISCONNECTED" -> RuntimeState.DISCONNECTED;
            case "AUTH_FAILED" -> RuntimeState.AUTH_FAILED;
            case "ERROR" -> RuntimeState.ERROR;
            default -> RuntimeState.UNKNOWN;
        };
    }
}
