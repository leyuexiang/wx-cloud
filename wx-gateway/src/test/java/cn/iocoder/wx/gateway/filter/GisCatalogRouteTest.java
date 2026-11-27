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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** 执行真实路由配置，确保旧网页 GIS 地址进入当前双端认证边界。 */
class GisCatalogRouteTest {
    @Test void mapsOnlyTheTwoCatalogPathsAndPreservesTrustedCredentials() throws Exception {
        var environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("gateway", new ClassPathResource("application.yaml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        var properties = Binder.get(environment).bind("spring.cloud.gateway", Bindable.of(GatewayProperties.class)).get();
        var route = properties.getRoutes().stream().filter(r -> r.getId().equals("gis-catalog-platform-api")).findFirst().orElseThrow();
        var predicateConfig = new PathRoutePredicateFactory.Config();
        predicateConfig.setPatterns(List.copyOf(route.getPredicates().get(0).getArgs().values()));
        var predicate = new PathRoutePredicateFactory().apply(predicateConfig);
        for (String platform : List.of("operation", "tenant")) {
            String external = platform.equals("tenant") ? "tenant/web" : platform;
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/" + external + "/webgis/info/list?dataType=SATELLITE")
                    .header("Authorization", "Bearer test").header("tenant-id", "2").header("x-bot-user", "forged"));
            assertTrue(predicate.test(exchange));
            var rewriteDefinition = route.getFilters().stream().filter(f -> f.getName().equals("RewritePath")).findFirst().orElseThrow();
            var rewriteConfig = new RewritePathGatewayFilterFactory.Config();
            rewriteConfig.setRegexp(rewriteDefinition.getArgs().get("_genkey_0"));
            rewriteConfig.setReplacement(rewriteDefinition.getArgs().get("_genkey_1"));
            var removeDefinition = route.getFilters().stream().filter(f -> f.getName().equals("RemoveRequestHeader")).findFirst().orElseThrow();
            var removeConfig = new RemoveRequestHeaderGatewayFilterFactory.NameConfig();
            removeConfig.setName(removeDefinition.getArgs().get("_genkey_0"));
            var captured = new AtomicReference<org.springframework.web.server.ServerWebExchange>();
            new RewritePathGatewayFilterFactory().apply(rewriteConfig).filter(exchange, next ->
                    new RemoveRequestHeaderGatewayFilterFactory().apply(removeConfig).filter(next,
                            value -> Mono.fromRunnable(() -> captured.set(value)))).block();
            var request = captured.get().getRequest();
            assertEquals("/admin-api/dim3d/" + platform + "/webgis/info/list", request.getURI().getPath());
            assertEquals("dataType=SATELLITE", request.getURI().getQuery());
            assertEquals("Bearer test", request.getHeaders().getFirst("Authorization"));
            assertEquals("2", request.getHeaders().getFirst("tenant-id"));
            assertNull(request.getHeaders().getFirst("x-bot-user"));
        }
        for (String path : List.of("/api/tenant/webgis/info/list", "/api/operation/webgis/info/delete", "/api/tenant/web/webgis/info/list/extra"))
            assertFalse(predicate.test(MockServerWebExchange.from(MockServerHttpRequest.get(path))));
    }
}
