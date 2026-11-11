package cn.iocoder.wx.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** 从真实网关配置执行图片文件别名与平台目录别名，并验证身份头隔离。 */
class ImageResourceRouteTest {

    @Test
    void directInfraControllerPathAlsoRemovesUntrustedIdentityHeader() throws Exception {
        var route = route("infra-admin-api");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/admin-api/infra/file/resource/oss/upload")
                .header("x-bot-user", "forged-user").header("Authorization", "Bearer trusted-session"));
        var forwarded = apply(route, exchange);
        assertEquals("/admin-api/infra/file/resource/oss/upload", forwarded.getRequest().getURI().getPath());
        assertNull(forwarded.getRequest().getHeaders().getFirst("x-bot-user"));
        assertEquals("Bearer trusted-session", forwarded.getRequest().getHeaders().getFirst("Authorization"));
    }

    /** 当前运行前端启用 admin 模式，原问题路径也必须进入相同的受鉴权图片适配。 */
    @Test
    void adminModeFileAliasesReachOwnedImageController() throws Exception {
        for (var pair : List.of(
                new RouteExchange(route("admin-image-oss-upload-api"), "/admin-api/resource/oss/upload"),
                new RouteExchange(route("admin-image-oss-list-by-id-api"), "/admin-api/resource/oss/listByIds/17"))) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(pair.path())
                    .header("x-bot-user", "forged-user"));
            assertTrue(predicate(pair.route()).test(exchange));
            var forwarded = apply(pair.route(), exchange);
            assertEquals(pair.path().replace("/admin-api/resource/oss/", "/admin-api/infra/file/resource/oss/"),
                    forwarded.getRequest().getURI().getPath());
            assertNull(forwarded.getRequest().getHeaders().getFirst("x-bot-user"));
        }
    }

    @Test
    void imageUploadRouteKeepsTrustedHeadersAndRemovesInternalIdentityHeader() throws Exception {
        var route = route("image-oss-upload-api");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/resource/oss/upload")
                .header("Authorization", "Bearer trusted-session")
                .header("ClientId", "trusted-client")
                .header("tenant-id", "7")
                .header("x-bot-user", "forged-user"));
        var forwarded = apply(route, exchange);

        assertEquals("/admin-api/infra/file/resource/oss/upload", forwarded.getRequest().getURI().getPath());
        assertEquals("Bearer trusted-session", forwarded.getRequest().getHeaders().getFirst("Authorization"));
        assertEquals("trusted-client", forwarded.getRequest().getHeaders().getFirst("ClientId"));
        assertEquals("7", forwarded.getRequest().getHeaders().getFirst("tenant-id"));
        assertNull(forwarded.getRequest().getHeaders().getFirst("x-bot-user"));

        Predicate<org.springframework.web.server.ServerWebExchange> predicate = predicate(route);
        for (String path : List.of("/api/resource/oss/delete", "/api/operation/resource/oss/upload",
                "/api/resource/oss/listByIds/12/extra")) {
            assertFalse(predicate.test(MockServerWebExchange.from(MockServerHttpRequest.post(path))));
        }
    }

    @Test
    void fileLookupRoutePreservesOnePathSegmentAndStringId() throws Exception {
        var route = route("image-oss-list-by-id-api");
        var request = MockServerHttpRequest.get("/api/resource/oss/listByIds/9223372036854775807");
        var exchange = MockServerWebExchange.from(request);
        assertTrue(predicate(route).test(exchange));
        var forwarded = apply(route, exchange);
        assertEquals("/admin-api/infra/file/resource/oss/listByIds/9223372036854775807",
                forwarded.getRequest().getURI().getPath());
        // 具体编号格式/有界 Long 校验属于文件控制器；网关只负责单路径段路由。
        assertTrue(predicate(route).test(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/resource/oss/listByIds/1,2"))));
        assertFalse(predicate(route).test(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/resource/oss/listByIds/1/2"))));
    }

    @Test
    void platformCatalogAliasesKeepIdentityHeadersAndMapToDimensions() throws Exception {
        var operation = route("operation-dim3d-api");
        var tenant = route("tenant-dim3d-api");
        for (var pair : List.of(
                new RouteExchange(operation, "/api/operation/dim3d/icon/add"),
                new RouteExchange(tenant, "/api/tenant/web/dim3d/icon/add"))) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(pair.path())
                    .header("Authorization", "Bearer trusted-session")
                    .header("ClientId", "trusted-client")
                    .header("x-bot-user", "forged-user"));
            var forwarded = apply(pair.route(), exchange);
            assertEquals("/admin-api/dim3d/icon/add", forwarded.getRequest().getURI().getPath());
            assertEquals("Bearer trusted-session", forwarded.getRequest().getHeaders().getFirst("Authorization"));
            assertEquals("trusted-client", forwarded.getRequest().getHeaders().getFirst("ClientId"));
            assertNull(forwarded.getRequest().getHeaders().getFirst("x-bot-user"));
        }
        assertFalse(predicate(operation).test(MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/tenant/web/dim3d/icon/add"))));
        assertFalse(predicate(tenant).test(MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/operation/dim3d/icon/add"))));
    }

    private static org.springframework.cloud.gateway.route.RouteDefinition route(String id) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("gateway", new ClassPathResource("application.yaml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        GatewayProperties properties = Binder.get(environment)
                .bind("spring.cloud.gateway", Bindable.of(GatewayProperties.class)).get();
        return properties.getRoutes().stream().filter(item -> item.getId().equals(id)).findFirst().orElseThrow();
    }

    private static Predicate<org.springframework.web.server.ServerWebExchange> predicate(
            org.springframework.cloud.gateway.route.RouteDefinition route) {
        var config = new PathRoutePredicateFactory.Config();
        config.setPatterns(List.of(route.getPredicates().get(0).getArgs().get("_genkey_0")));
        return new PathRoutePredicateFactory().apply(config);
    }

    private static org.springframework.web.server.ServerWebExchange apply(
            org.springframework.cloud.gateway.route.RouteDefinition route,
            org.springframework.web.server.ServerWebExchange exchange) {
        var rewriteDefinition = route.getFilters().stream()
                .filter(item -> item.getName().equals("RewritePath")).findFirst().orElseThrow();
        var rewriteConfig = new RewritePathGatewayFilterFactory.Config();
        rewriteConfig.setRegexp(rewriteDefinition.getArgs().get("_genkey_0"));
        rewriteConfig.setReplacement(rewriteDefinition.getArgs().get("_genkey_1"));
        var rewrite = new RewritePathGatewayFilterFactory().apply(rewriteConfig);
        var removeDefinition = route.getFilters().stream()
                .filter(item -> item.getName().equals("RemoveRequestHeader")).findFirst().orElseThrow();
        var removeConfig = new RemoveRequestHeaderGatewayFilterFactory.NameConfig();
        removeConfig.setName(removeDefinition.getArgs().get("_genkey_0"));
        var remove = new RemoveRequestHeaderGatewayFilterFactory().apply(removeConfig);
        var forwarded = new AtomicReference<org.springframework.web.server.ServerWebExchange>();
        rewrite.filter(exchange, rewritten -> remove.filter(rewritten,
                actual -> Mono.fromRunnable(() -> forwarded.set(actual))))
                .block(Duration.ofSeconds(2));
        return forwarded.get();
    }

    private record RouteExchange(org.springframework.cloud.gateway.route.RouteDefinition route, String path) {}
}
