package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorSaveReqVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证配置和启停仅修改授权字段，并发版本冲突不能静默覆盖其他管理员的修改。 */
@SuppressWarnings("unchecked")
class IotConnectorVersionTest {
    private final IotConnectorMapper mapper = mock(IotConnectorMapper.class);
    private final IotConnectorConfigValidator validator = mock(IotConnectorConfigValidator.class);
    private final IotConnectorServiceImpl service = new IotConnectorServiceImpl(mapper, validator);
    private IotConnectorDO current;

    @BeforeEach
    void prepare() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "connector-test"), IotConnectorDO.class);
        current = new IotConnectorDO();
        current.setId(1L);
        current.setConnectorId("pilot");
        current.setConnectorType("MQTT");
        current.setEnabled(true);
        current.setVersion(3);
        when(mapper.selectById(1L)).thenReturn(current);
        when(mapper.update(any(IotConnectorDO.class), any())).thenReturn(1);
    }

    private IotConnectorSaveReqVO request() {
        IotConnectorSaveReqVO request = new IotConnectorSaveReqVO();
        request.setId(1L);
        request.setConnectorId("pilot");
        request.setName("新的展示名称");
        request.setConnectorType("MQTT");
        request.setVersion(3);
        request.setEndpoint("ssl://broker.example.com:8883");
        return request;
    }

    private LambdaUpdateWrapper<IotConnectorDO> updated() {
        ArgumentCaptor<LambdaUpdateWrapper<IotConnectorDO>> wrapper = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        ArgumentCaptor<IotConnectorDO> audit = ArgumentCaptor.forClass(IotConnectorDO.class);
        verify(mapper).update(audit.capture(), wrapper.capture());
        assertNull(audit.getValue().getEnabled());
        assertNull(audit.getValue().getEndpoint());
        return wrapper.getValue();
    }

    @Test
    void shouldIncrementVersionWithoutUpdatingEnabledOrOwner() {
        var request = request();
        request.setEnabled(false);
        service.updateConnector(request);
        var update = updated();
        assertTrue(update.getSqlSet().contains("version="));
        assertFalse(update.getSqlSet().contains("enabled="));
        assertFalse(update.getSqlSet().contains("runtime_owner="));
        assertTrue(update.getSqlSegment().contains("version"));
        assertTrue(update.getParamNameValuePairs().containsValue(3));
        assertTrue(update.getParamNameValuePairs().containsValue(4));
        assertTrue(current.getEnabled());
        verify(validator).validate(any());
    }

    @Test
    void shouldRejectStaleClientVersionBeforeUpdating() {
        var request = request();
        request.setVersion(2);
        ServiceException error = assertThrows(ServiceException.class, () -> service.updateConnector(request));
        assertEquals(CONNECTOR_VERSION_CONFLICT.getCode(), error.getCode());
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void shouldRejectConcurrentModificationRatherThanReturnSuccess() {
        when(mapper.update(any(IotConnectorDO.class), any())).thenReturn(0);
        ServiceException error = assertThrows(ServiceException.class, () -> service.updateConnector(request()));
        assertEquals(CONNECTOR_VERSION_CONFLICT.getCode(), error.getCode());
    }

    @Test
    void shouldRejectBusinessIdentifierRename() {
        var request = request();
        request.setConnectorId("renamed");
        ServiceException error = assertThrows(ServiceException.class, () -> service.updateConnector(request));
        assertEquals(CONNECTOR_ID_IMMUTABLE.getCode(), error.getCode());
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void shouldEnableOnlyLifecycleFieldsAndValidateConfiguration() {
        current.setEnabled(false);
        service.enableConnector(1L);
        var update = updated();
        assertTrue(update.getSqlSet().contains("enabled="));
        assertFalse(update.getSqlSet().contains("endpoint="));
        assertFalse(update.getSqlSet().contains("config_json="));
        assertFalse(current.getEnabled());
        verify(validator).validate(current);
    }

    @Test
    void shouldDisableOnlyLifecycleFieldsAndIncrementVersion() {
        service.disableConnector(1L);
        var update = updated();
        assertTrue(update.getSqlSet().contains("enabled="));
        assertTrue(update.getParamNameValuePairs().containsValue(4));
        assertTrue(current.getEnabled());
    }

    @Test
    void shouldTreatRepeatedEnableAsNoOp() {
        service.enableConnector(1L);
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void shouldValidateBeforeCreatingAndIgnoreSuppliedLifecycleVersion() {
        var request = request();
        request.setId(null);
        request.setVersion(99);
        request.setEnabled(true);
        when(mapper.insert(any(IotConnectorDO.class))).thenAnswer(invocation -> {
            IotConnectorDO inserted = invocation.getArgument(0);
            assertFalse(inserted.getEnabled());
            assertEquals(1, inserted.getVersion());
            inserted.setId(2L);
            return 1;
        });
        assertEquals(2L, service.createConnector(request));
        var order = inOrder(validator, mapper);
        order.verify(validator).validate(any());
        order.verify(mapper).insert(any(IotConnectorDO.class));
    }
}
