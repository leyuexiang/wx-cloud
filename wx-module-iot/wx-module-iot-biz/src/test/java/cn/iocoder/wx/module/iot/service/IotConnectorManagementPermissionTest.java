package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.module.iot.controller.admin.connector.IotConnectorController;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorPageReqVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorRuntimeRespVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotMqttConfigValidateReqVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotMqttConfigValidateRespVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实方法权限代理核对新增入口，不将代理验证解释为网关和令牌服务联调。 */
class IotConnectorManagementPermissionTest {
    private AnnotationConfigApplicationContext context;
    private IotConnectorController controller;
    private IotConnectorManagementService service;
    private SecurityFrameworkService permissions;

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean("ss")
        SecurityFrameworkService permissions() { return mock(SecurityFrameworkService.class); }
        @Bean
        IotConnectorService connectors() { return mock(IotConnectorService.class); }
        @Bean
        IotConnectorManagementService management() { return mock(IotConnectorManagementService.class); }
        @Bean
        IotConnectorConnectivityService connectivity() { return mock(IotConnectorConnectivityService.class); }
        @Bean
        IotConnectorController controller(IotConnectorService connectors, IotConnectorManagementService management,
                IotConnectorConnectivityService connectivity) {
            return new IotConnectorController(connectors, management, connectivity);
        }
    }

    @BeforeEach
    void prepare() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(IotConnectorController.class);
        service = context.getBean(IotConnectorManagementService.class);
        permissions = context.getBean(SecurityFrameworkService.class);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("测试管理员", "", List.of()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void shouldDenyAllManagementEntrypointsWithoutPermissions() {
        assertThrows(AccessDeniedException.class, () -> controller.page(new IotConnectorPageReqVO()));
        assertThrows(AccessDeniedException.class, () -> controller.runtime(1L));
        assertThrows(AccessDeniedException.class, () -> controller.validateMqttConfig(new IotMqttConfigValidateReqVO()));
        verifyNoInteractions(service);
    }

    @Test
    void shouldAllowReaderToQueryButNotValidateDrafts() {
        when(permissions.hasPermission("iot:connector:query")).thenReturn(true);
        when(service.getPage(any())).thenReturn(PageResult.empty());
        when(service.getRuntime(1L)).thenReturn(new IotConnectorRuntimeRespVO());
        assertEquals(0, controller.page(new IotConnectorPageReqVO()).getData().getTotal());
        assertNotNull(controller.runtime(1L).getData());
        assertThrows(AccessDeniedException.class, () -> controller.validateMqttConfig(new IotMqttConfigValidateReqVO()));
        verify(service, never()).validateMqtt(any());
    }

    @Test
    void shouldUseExistingWritePermissionsForDraftValidation() {
        when(permissions.hasAnyPermissions("iot:connector:create", "iot:connector:update")).thenReturn(true);
        when(service.validateMqtt(any())).thenReturn(new IotMqttConfigValidateRespVO("MQTT_STRUCTURE", 1, 1));
        assertEquals("MQTT_STRUCTURE", controller.validateMqttConfig(new IotMqttConfigValidateReqVO()).getData().scope());
        assertThrows(AccessDeniedException.class, () -> controller.runtime(1L));
        verify(service, never()).getRuntime(anyLong());
    }
}
