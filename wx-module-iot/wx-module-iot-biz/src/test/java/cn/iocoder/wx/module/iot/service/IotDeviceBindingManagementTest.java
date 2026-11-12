package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.iot.controller.admin.devicebinding.vo.IotDeviceBindingPageReqVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotDeviceBindingDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotDeviceBindingMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实分页包装器与租户条件检查，不代替真实数据库及网关验收。 */
class IotDeviceBindingManagementTest {
    private final IotDeviceBindingMapper mapper = mock(IotDeviceBindingMapper.class);
    private final IotConnectorService connectors = mock(IotConnectorService.class);
    private final IotDeviceBindingServiceImpl service = new IotDeviceBindingServiceImpl(mapper,
            connectors, new IotPointMappingService(new ObjectMapper()));

    @AfterEach
    void clearTenant() { TenantContextHolder.clear(); }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldPageWithExplicitTenantAndAllFilters() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "binding-test"), IotDeviceBindingDO.class);
        TenantContextHolder.setTenantId(7L);
        var request = new IotDeviceBindingPageReqVO();
        request.setPageNo(2);
        request.setPageSize(10);
        request.setConnectorId("pilot");
        request.setExternalDeviceId("device");
        request.setEnabled(false);
        var page = new PageResult<IotDeviceBindingDO>(List.of(), 12L);
        doCallRealMethod().when(mapper).selectManagementPage(7L, request);
        doReturn(page).when(mapper).selectPage(eq(request), any(Wrapper.class));
        assertSame(page, service.getBindingPage(request));
        ArgumentCaptor<Wrapper<IotDeviceBindingDO>> filter = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectPage(eq(request), filter.capture());
        var sql = filter.getValue().getSqlSegment();
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("connector_id LIKE"));
        assertTrue(sql.contains("external_device_id LIKE"));
        assertTrue(sql.contains("enabled"));
        assertTrue(sql.contains("ORDER BY id DESC"));
        verify(mapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void shouldRejectMissingTenantBeforeDatabaseAccess() {
        assertThrows(NullPointerException.class, () -> service.getBindingPage(new IotDeviceBindingPageReqVO()));
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldNormalizeEmptyMappingToValidJsonBeforeInsertAndUpdate() {
        var request = new cn.iocoder.wx.module.iot.controller.admin.devicebinding.vo.IotDeviceBindingSaveReqVO();
        request.setConnectorId("pilot");
        request.setExternalDeviceId("device");
        request.setMappingRevision("v1");
        request.setPointMappingJson("");
        when(connectors.getConnector("pilot")).thenReturn(new cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO());
        service.createBinding(request);
        var inserted = ArgumentCaptor.forClass(IotDeviceBindingDO.class);
        verify(mapper).insert(inserted.capture());
        assertEquals("{}", inserted.getValue().getPointMappingJson());
        request.setId(1L);
        when(mapper.selectById(1L)).thenReturn(new IotDeviceBindingDO());
        service.updateBinding(request);
        verify(mapper).updateById(inserted.capture());
        assertEquals("{}", inserted.getValue().getPointMappingJson());
    }

    @Test
    void shouldRejectInvalidMappingBeforeWriting() {
        var request = new cn.iocoder.wx.module.iot.controller.admin.devicebinding.vo.IotDeviceBindingSaveReqVO();
        request.setPointMappingJson("{\"points\":{\"temp\":{\"scale\":\"1\"}}}");
        assertThrows(cn.iocoder.wx.framework.common.exception.ServiceException.class, () -> service.createBinding(request));
        assertThrows(cn.iocoder.wx.framework.common.exception.ServiceException.class, () -> service.updateBinding(request));
        verifyNoInteractions(mapper, connectors);
    }

    @Test
    void shouldRejectUnboundedPageAndOversizedFilters() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new IotDeviceBindingPageReqVO();
            for (int size : new int[]{-1, 0, 101}) {
                request.setPageSize(size);
                assertFalse(factory.getValidator().validate(request).isEmpty());
            }
            request.setPageSize(10);
            request.setExternalDeviceId("x".repeat(129));
            assertFalse(factory.getValidator().validate(request).isEmpty());
        }
    }
}
