package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.module.iot.controller.admin.devicebinding.IotDeviceBindingController;
import cn.iocoder.wx.module.iot.controller.admin.devicebinding.vo.IotDeviceBindingPageReqVO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 新分页入口通过真实方法权限代理，不能只依赖前端隐藏菜单。 */
class IotDeviceBindingPermissionTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean("ss")
        SecurityFrameworkService permissions() { return mock(SecurityFrameworkService.class); }
        @Bean
        IotDeviceBindingService service() { return mock(IotDeviceBindingService.class); }
        @Bean
        IotDeviceBindingController controller(IotDeviceBindingService service) { return new IotDeviceBindingController(service); }
    }

    @Test
    void shouldDenyWithoutQueryPermissionAndAllowReaderOnly() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("测试只读用户", "", List.of()));
            var controller = context.getBean(IotDeviceBindingController.class);
            var service = context.getBean(IotDeviceBindingService.class);
            var permissions = context.getBean(SecurityFrameworkService.class);
            assertThrows(AccessDeniedException.class, () -> controller.page(new IotDeviceBindingPageReqVO()));
            verifyNoInteractions(service);
            when(permissions.hasPermission("iot:device-binding:query")).thenReturn(true);
            when(service.getBindingPage(any())).thenReturn(PageResult.empty());
            assertEquals(0L, controller.page(new IotDeviceBindingPageReqVO()).getData().getTotal());
            assertThrows(AccessDeniedException.class, () -> controller.delete(1L));
            verify(service, never()).deleteBinding(any());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
