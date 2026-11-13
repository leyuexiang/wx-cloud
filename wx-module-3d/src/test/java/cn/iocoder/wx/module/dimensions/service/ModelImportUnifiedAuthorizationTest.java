package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.module.dimensions.controller.admin.ModelImportController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 通过真实方法权限代理验证模型入口复用场景列表的 ss 服务，拒绝发生在业务调用之前。 */
class ModelImportUnifiedAuthorizationTest {
    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withPropertyValues("dim3d.model-import.enabled=true")
            .withUserConfiguration(Permissions.class, ModelImportController.class);
    private final byte[] empty = "{}".getBytes(StandardCharsets.UTF_8);

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Permissions {
        @Bean("ss") SecurityFrameworkService permissions() { return mock(SecurityFrameworkService.class); }
        @Bean ModelImportService imports() { return mock(ModelImportService.class); }
        @Bean ArtResourceManifestVerifier parser() { return new ArtResourceManifestVerifier(); }
    }

    @BeforeEach void login() {
        // 认证过滤器已单独实测；此处提供认证上下文，只聚焦统一方法权限的调用边界。
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", "", List.of()));
    }
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void sharedPermissionDenialPreventsAllImportActions() {
        contexts.run(context -> {
            assertNull(context.getStartupFailure());
            var controller = context.getBean(ModelImportController.class);
            var service = context.getBean(ModelImportService.class);
            assertThrows(AccessDeniedException.class, () -> controller.create(empty));
            assertThrows(AccessDeniedException.class, () -> controller.get("op"));
            assertThrows(AccessDeniedException.class, () -> controller.sourceTarget("op", empty));
            assertThrows(AccessDeniedException.class, () -> controller.submit("op", empty));
            assertThrows(AccessDeniedException.class, () -> controller.publish("op", empty));
            verifyNoInteractions(service);
        });
    }

    @Test void existingModelPermissionAllowsTheSameController() {
        contexts.run(context -> {
            var permissions = context.getBean(SecurityFrameworkService.class);
            var service = context.getBean(ModelImportService.class);
            var controller = context.getBean(ModelImportController.class);
            when(permissions.hasAnyPermissions("dim3d:model:add", "dim3d:model:edit")).thenReturn(true);
            when(permissions.hasPermission("dim3d:model:query")).thenReturn(true);
            assertDoesNotThrow(() -> controller.create(empty));
            assertDoesNotThrow(() -> controller.get("op"));
            assertDoesNotThrow(() -> controller.sourceTarget("op", empty));
            verify(service).create(any());
            verify(service).get("op");
            verify(service).sourceTarget("op");
        });
    }
}
