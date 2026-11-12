package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.util.object.BeanUtils;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.iot.controller.admin.devicebinding.vo.IotDeviceBindingPageReqVO;
import cn.iocoder.wx.module.iot.controller.admin.devicebinding.vo.IotDeviceBindingSaveReqVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotDeviceBindingDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotDeviceBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.DEVICE_BINDING_DUPLICATE;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.DEVICE_BINDING_NOT_EXISTS;

/** 外部设备绑定服务实现。 */
@Service
@Validated
@RequiredArgsConstructor
public class IotDeviceBindingServiceImpl implements IotDeviceBindingService {

    private final IotDeviceBindingMapper bindingMapper;
    private final IotConnectorService connectorService;
    private final IotPointMappingService pointMappingService;

    @Override
    public Long createBinding(IotDeviceBindingSaveReqVO reqVO) {
        // 保存前验证整份映射，不能等设备上报时才发现无法执行的转换配置。
        pointMappingService.validateConfiguration(reqVO.getPointMappingJson());
        validateConnector(reqVO.getConnectorId());
        validateBindingUnique(null, reqVO.getConnectorId(), reqVO.getExternalDeviceId());
        IotDeviceBindingDO binding = BeanUtils.toBean(reqVO, IotDeviceBindingDO.class);
        binding.setPointMappingJson(normalizeMapping(reqVO.getPointMappingJson()));
        binding.setEnabled(reqVO.getEnabled() == null || reqVO.getEnabled());
        bindingMapper.insert(binding);
        return binding.getId();
    }

    @Override
    public void updateBinding(IotDeviceBindingSaveReqVO reqVO) {
        pointMappingService.validateConfiguration(reqVO.getPointMappingJson());
        validateBindingExists(reqVO.getId());
        validateConnector(reqVO.getConnectorId());
        validateBindingUnique(reqVO.getId(), reqVO.getConnectorId(), reqVO.getExternalDeviceId());
        IotDeviceBindingDO binding = BeanUtils.toBean(reqVO, IotDeviceBindingDO.class);
        binding.setPointMappingJson(normalizeMapping(reqVO.getPointMappingJson()));
        bindingMapper.updateById(binding);
    }

    @Override
    public void deleteBinding(Long id) {
        validateBindingExists(id);
        bindingMapper.deleteById(id);
    }

    @Override
    public IotDeviceBindingDO getBinding(Long id) {
        return bindingMapper.selectById(id);
    }

    @Override
    public List<IotDeviceBindingDO> getBindingList(String connectorId, Boolean enabled) {
        return bindingMapper.selectList(connectorId, enabled);
    }

    @Override
    public PageResult<IotDeviceBindingDO> getBindingPage(IotDeviceBindingPageReqVO request) {
        return bindingMapper.selectManagementPage(TenantContextHolder.getRequiredTenantId(), request);
    }

    @Override
    public IotDeviceBindingDO getEnabledBinding(String connectorId, String externalDeviceId) {
        return bindingMapper.selectEnabledByExternalDevice(connectorId, externalDeviceId);
    }

    private void validateConnector(String connectorId) {
        if (connectorService.getConnector(connectorId) == null) {
            throw exception(cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.CONNECTOR_NOT_EXISTS);
        }
    }

    /** JSON列不能保存空文本；空映射用空对象明确清除旧规则，保持原样透传。 */
    private String normalizeMapping(String json) {
        return json == null || json.isBlank() ? "{}" : json;
    }

    private void validateBindingExists(Long id) {
        if (id == null || bindingMapper.selectById(id) == null) {
            throw exception(DEVICE_BINDING_NOT_EXISTS);
        }
    }

    private void validateBindingUnique(Long id, String connectorId, String externalDeviceId) {
        IotDeviceBindingDO binding = bindingMapper.selectByExternalDevice(connectorId, externalDeviceId);
        if (binding != null && !binding.getId().equals(id)) {
            throw exception(DEVICE_BINDING_DUPLICATE);
        }
    }
}
