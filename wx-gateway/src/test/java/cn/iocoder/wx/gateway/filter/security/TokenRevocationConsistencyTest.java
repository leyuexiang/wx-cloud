package cn.iocoder.wx.gateway.filter.security;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.util.json.JsonUtils;
import cn.iocoder.wx.module.system.api.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 撤销只依赖权威身份查询，不允许已预热网关或另一个节点复用旧认证。 */
class TokenRevocationConsistencyTest {
    private static final String TOKEN = "revocation-unit-token";

    @Test
    void warmedGatewayMustRecheckAfterLogout() {
        var valid = new AtomicBoolean(true);
        var calls = new AtomicInteger();
        var filter = filter(request -> { calls.incrementAndGet(); return response(valid.get()); });
        assertNotNull(forwardedIdentity(filter));
        valid.set(false);
        assertNull(forwardedIdentity(filter));
        assertEquals(2, calls.get());
    }

    @Test
    void revocationMustReachAllPreviouslyWarmedNodes() {
        var valid = new AtomicBoolean(true);
        ExchangeFunction authority = request -> response(valid.get());
        var first = filter(authority);
        var second = filter(authority);
        assertNotNull(forwardedIdentity(first));
        assertNotNull(forwardedIdentity(second));
        valid.set(false);
        assertNull(forwardedIdentity(first));
        assertNull(forwardedIdentity(second));
    }

    @Test
    void authorityFailureMustNotFallBackToWarmedIdentity() {
        var available = new AtomicBoolean(true);
        var filter = filter(request -> available.get() ? response(true)
                : Mono.error(new IllegalStateException("不应向外回显的原始认证地址")));
        assertNotNull(forwardedIdentity(filter));
        available.set(false);
        assertUnavailable(filter);
    }

    @Test
    void malformedAuthorityBodyMustFailClosed() {
        assertUnavailable(filter(request -> Mono.just(ClientResponse.create(HttpStatus.OK).body("invalid-json").build())));
    }

    @Test
    void unexpectedBusinessFailureMustFailClosed() {
        assertUnavailable(filter(request -> json(CommonResult.error(500, "内部错误"))));
    }

    @Test
    void missingAuthorityDataMustFailClosed() {
        assertUnavailable(filter(request -> json(CommonResult.success(null))));
    }

    @Test
    void authorityHttpUnauthorizedMustRemainAnonymous() {
        var filter = filter(request -> Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build()));
        assertNull(forwardedIdentity(filter));
    }

    @Test
    void authorityHttpFailureMustNotForward() {
        assertUnavailable(filter(request -> Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build())));
    }

    @Test
    void emptySuccessfulResponseMustNotForward() {
        assertUnavailable(filter(request -> Mono.just(ClientResponse.create(HttpStatus.OK).build())));
    }

    @Test
    void expiredIdentityMustNotBeForwarded() {
        var filter = filter(request -> json(CommonResult.success(new OAuth2AccessTokenCheckRespDTO()
                .setUserId(7L).setUserType(2).setTenantId(2L).setExpiresTime(LocalDateTime.now().minusMinutes(1)))));
        assertNull(forwardedIdentity(filter));
    }

    @Test
    void hangingAuthorityMustHaveBoundedTotalTime() {
        var exchange = request();
        var forwarded = new AtomicBoolean();
        var filter = filter(ignored -> Mono.never());
        filter.filter(exchange, next -> { forwarded.set(true); return Mono.empty(); }).block(Duration.ofSeconds(7));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertFalse(forwarded.get());
    }

    @Test
    void concurrentRequestsMustEachQueryAuthority() {
        var calls = new AtomicInteger();
        var filter = filter(request -> { calls.incrementAndGet(); return response(true).delayElement(Duration.ofMillis(5)); });
        Flux.range(0, 24).flatMap(index -> filter.filter(request(), next -> {
            assertNotNull(next.getRequest().getHeaders().getFirst("login-user"));
            return Mono.empty();
        }), 8).blockLast();
        assertEquals(24, calls.get());
    }

    @Test
    void tenantHintAndForgedHeadersMustNotBypassAuthority() {
        var calls = new AtomicInteger();
        var filter = filter(request -> {
            assertEquals("2", request.headers().getFirst("tenant-id"));
            calls.incrementAndGet();
            return response(false);
        });
        assertNull(forwardedIdentity(filter));
        assertEquals(1, calls.get());
    }

    @Test
    void alreadyStartedCheckUsesItsResultButNextCheckSeesRevocation() {
        var valid = new AtomicBoolean(true);
        var filter = filter(request -> {
            // 模拟校验先读到有效状态，撤销发生在响应送达前；不承诺追溯取消已开始请求。
            boolean checked = valid.getAndSet(false);
            return response(checked).delayElement(Duration.ofMillis(5));
        });
        assertNotNull(forwardedIdentity(filter));
        assertNull(forwardedIdentity(filter));
    }

    private TokenAuthenticationFilter filter(ExchangeFunction authority) {
        var filter = new TokenAuthenticationFilter(new ReactorLoadBalancerExchangeFilterFunction(null, List.of()));
        ReflectionTestUtils.setField(filter, "webClient", WebClient.builder().exchangeFunction(authority).build());
        return filter;
    }

    private MockServerWebExchange request() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/admin-api/system/auth/get-permission-info")
                .header("Authorization", "Bearer " + TOKEN).header("tenant-id", "2")
                .header("login-user", "伪造身份").header("x-bot-user", "3"));
    }

    private String forwardedIdentity(TokenAuthenticationFilter filter) {
        var identity = new AtomicReference<String>();
        var forwarded = new AtomicBoolean();
        filter.filter(request(), next -> {
            forwarded.set(true);
            assertNull(next.getRequest().getHeaders().getFirst("x-bot-user"));
            identity.set(next.getRequest().getHeaders().getFirst("login-user"));
            return Mono.empty();
        }).block();
        assertTrue(forwarded.get());
        return identity.get();
    }

    private void assertUnavailable(TokenAuthenticationFilter filter) {
        var exchange = request();
        var forwarded = new AtomicBoolean();
        filter.filter(exchange, next -> { forwarded.set(true); return Mono.empty(); }).block();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertFalse(forwarded.get());
        assertEquals("", exchange.getResponse().getBodyAsString().block());
    }

    private Mono<ClientResponse> response(boolean valid) {
        return valid ? json(CommonResult.success(new OAuth2AccessTokenCheckRespDTO().setUserId(7L).setUserType(2)
                .setTenantId(2L).setExpiresTime(LocalDateTime.now().plusMinutes(10))))
                : json(CommonResult.error(401, "令牌已撤销"));
    }

    private Mono<ClientResponse> json(Object body) {
        return Mono.just(ClientResponse.create(HttpStatus.OK).body(JsonUtils.toJsonString(body)).build());
    }
}
