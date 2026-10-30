package cn.iocoder.wx.gateway.filter.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 无令牌请求也必须先去掉伪造身份，不能绕过后端的资源管理权限。 */
class TokenAuthenticationFilterTest {

    @Test
    void shouldStripForgedIdentityBeforeForwardingAnonymousRequest() {
        var request = MockServerHttpRequest.get("/admin-api/dim3d/model/page")
                .header("login-user", "{\"id\":1}")
                .header("x-bot-user", "3")
                .header("tenant-id", "2").build();
        var exchange = MockServerWebExchange.from(request);
        var forwarded = new AtomicReference<ServerWebExchange>();
        var filter = new TokenAuthenticationFilter(new ReactorLoadBalancerExchangeFilterFunction(null, List.of()));

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        var headers = forwarded.get().getRequest().getHeaders();
        assertNull(headers.getFirst("login-user"));
        assertNull(headers.getFirst("x-bot-user"));
        assertEquals("2", headers.getFirst("tenant-id"));
    }

    @Test
    void shouldStripRobotHeaderWhenLoginHeaderIsAbsent() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/admin-api/dim3d/model/page")
                .header("x-bot-user", "3"));
        var filter = new TokenAuthenticationFilter(new ReactorLoadBalancerExchangeFilterFunction(null, List.of()));
        filter.filter(exchange, next -> {
            assertNull(next.getRequest().getHeaders().getFirst("x-bot-user"));
            return Mono.empty();
        }).block();
    }
}
