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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** 从实际配置绑定并执行网关过滤器，验证公共字典路由改写与外部身份头隔离。 */
class DictionaryRouteTest {

    @Test
    void shouldRewriteDictionaryReadAndKeepSessionHeaders() throws Exception {
        // 使用真实配置绑定器，覆盖替换组被误当环境变量解析的风险。
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("gateway", new ClassPathResource("application.yaml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        GatewayProperties properties = Binder.get(environment)
                .bind("spring.cloud.gateway", Bindable.of(GatewayProperties.class)).get();
        var route = properties.getRoutes().stream()
                .filter(item -> item.getId().equals("system-dictionary-api")).findFirst().orElseThrow();
        var predicateConfig = new PathRoutePredicateFactory.Config();
        predicateConfig.setPatterns(List.of(route.getPredicates().get(0).getArgs().get("_genkey_0")));
        var predicate = new PathRoutePredicateFactory().apply(predicateConfig);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/system/dict/data/type/dim3d_icon_category")
                .header("Authorization", "Bearer test-session")
                .header("ClientId", "test-client")
                .header("tenant-id", "1")
                .header("x-bot-user", "untrusted"));
        assertTrue(predicate.test(exchange));
        // 字典写入、带平台的业务路径及多级类型均不可获得此别名。
        for (String path : List.of("/api/system/dict/data/create",
                "/api/operation/system/dict/data/type/dim3d_icon_category",
                "/api/system/dict/data/type/a/b")) {
            assertFalse(predicate.test(MockServerWebExchange.from(MockServerHttpRequest.get(path))));
        }

        var rewriteDefinition = route.getFilters().stream()
                .filter(item -> item.getName().equals("RewritePath")).findFirst().orElseThrow();
        var rewriteConfig = new RewritePathGatewayFilterFactory.Config();
        rewriteConfig.setRegexp(rewriteDefinition.getArgs().get("_genkey_0"));
        rewriteConfig.setReplacement(rewriteDefinition.getArgs().get("_genkey_1"));
        var rewrite = new RewritePathGatewayFilterFactory().apply(rewriteConfig);
        var removeConfig = new RemoveRequestHeaderGatewayFilterFactory.NameConfig();
        removeConfig.setName("x-bot-user");
        var remove = new RemoveRequestHeaderGatewayFilterFactory().apply(removeConfig);
        AtomicBoolean forwarded = new AtomicBoolean();
        rewrite.filter(exchange, rewritten -> remove.filter(rewritten, actual -> Mono.fromRunnable(() -> {
            assertEquals("/admin-api/system/dict/data/type/dim3d_icon_category",
                    actual.getRequest().getURI().getPath());
            assertEquals("Bearer test-session", actual.getRequest().getHeaders().getFirst("Authorization"));
            assertEquals("test-client", actual.getRequest().getHeaders().getFirst("ClientId"));
            assertEquals("1", actual.getRequest().getHeaders().getFirst("tenant-id"));
            assertNull(actual.getRequest().getHeaders().getFirst("x-bot-user"));
            forwarded.set(true);
        }))).block(Duration.ofSeconds(2));
        assertTrue(forwarded.get());
    }
}
