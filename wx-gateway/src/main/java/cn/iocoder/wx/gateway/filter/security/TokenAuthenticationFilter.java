package cn.iocoder.wx.gateway.filter.security;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.util.date.LocalDateTimeUtils;
import cn.iocoder.wx.framework.common.util.json.JsonUtils;
import cn.iocoder.wx.gateway.util.SecurityFrameworkUtils;
import cn.iocoder.wx.gateway.util.WebFrameworkUtils;
import cn.iocoder.wx.module.system.api.oauth2.OAuth2TokenApi;
import cn.iocoder.wx.module.system.api.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Token 过滤器，验证 token 的有效性
 * 1. 验证通过时，将 userId、userType、tenantId 通过 Header 转发给服务
 * 2. 验证不通过，还是会转发给服务。因为，接口是否需要登录的校验，还是交给服务自身处理
 *
 * @author 九天芸菲
 */
@Component
public class TokenAuthenticationFilter implements GlobalFilter, Ordered {

    /**
     * CommonResult<OAuth2AccessTokenCheckRespDTO> 对应的 TypeReference 结果，用于解析 checkToken 的结果
     */
    private static final TypeReference<CommonResult<OAuth2AccessTokenCheckRespDTO>> CHECK_RESULT_TYPE_REFERENCE
            = new TypeReference<CommonResult<OAuth2AccessTokenCheckRespDTO>>() {};

    /**
     * 空的 LoginUser 的结果
     *
     * 用于解决如下问题：
     * 1. {@link #getLoginUser(ServerWebExchange, String)} 返回 Mono.empty() 时，会导致后续的 flatMap 无法进行处理的问题。
     * 2. {@link #buildUser(String)} 明确收到无效令牌时，不向业务服务透传旧身份。
     */
    private static final LoginUser LOGIN_USER_EMPTY = new LoginUser();

    /** 依赖故障与匿名请求分别处理，不能在认证失败时继续访问下游。 */
    private static final LoginUser LOGIN_USER_UNAVAILABLE = new LoginUser();

    private final WebClient webClient;

    public TokenAuthenticationFilter(ReactorLoadBalancerExchangeFilterFunction lbFunction) {
        // Q：为什么不使用 OAuth2TokenApi 进行调用？
        // A1：Spring Cloud OpenFeign 官方未内置 Reactive 的支持 https://docs.spring.io/spring-cloud-openfeign/docs/current/reference/html/#reactive-support
        // A2：校验 Token 的 API 需要使用到 header[tenant-id] 传递租户编号，暂时不想编写 RequestInterceptor 实现
        // 因此，这里采用 WebClient，通过 lbFunction 实现负载均衡
        this.webClient = WebClient.builder().filter(lbFunction).build();
    }

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, GatewayFilterChain chain) {
        // 请求对象不可变，必须沿后续整条链使用清理后的副本，防止外部伪造内部身份。
        final ServerWebExchange safeExchange = SecurityFrameworkUtils.removeLoginUser(exchange);

        // 情况一，如果没有 Token 令牌，则直接继续 filter
        String token = SecurityFrameworkUtils.obtainAuthorization(safeExchange);
        if (StrUtil.isEmpty(token)) {
            return chain.filter(safeExchange);
        }

        // 情况二，如果有 Token 令牌，则解析对应 userId、userType、tenantId 等字段，并通过 通过 Header 转发给服务
        // 重要说明：defaultIfEmpty 作用，保证 Mono.empty() 情况，可以继续执行 `flatMap 的 chain.filter(exchange)` 逻辑，避免返回给前端空的 Response！！
        return getLoginUser(safeExchange, token).defaultIfEmpty(LOGIN_USER_EMPTY)
                // 只处理认证阶段的故障，不吞下游业务异常，也不输出含令牌的原始异常地址。
                .onErrorReturn(LOGIN_USER_UNAVAILABLE).flatMap(user -> {
            if (user == LOGIN_USER_UNAVAILABLE) {
                safeExchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                return safeExchange.getResponse().setComplete();
            }
            // 1. 无用户，直接 filter 继续请求
            if (user == LOGIN_USER_EMPTY || // 下面 expiresTime 的判断，为了解决 token 实际已经过期的情况
                    user.getExpiresTime() == null || LocalDateTimeUtils.beforeNow(user.getExpiresTime())) {
                return chain.filter(safeExchange);
            }
            // 浏览器提交的租户编号只是查验提示，不能覆盖令牌绑定的租户。
            if (SecurityFrameworkUtils.isDimensionsWebSocket(safeExchange)) {
                Long requestedTenant = WebFrameworkUtils.getTenantId(safeExchange);
                if (requestedTenant != null && !Objects.equals(requestedTenant, user.getTenantId())) {
                    safeExchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return safeExchange.getResponse().setComplete();
                }
            }

            // 2.1 有用户，则设置登录用户
            SecurityFrameworkUtils.setLoginUser(safeExchange, user);
            // 2.2 将 user 并设置到 login-user 的请求头，使用 json 存储值
            ServerWebExchange newExchange = safeExchange.mutate()
                    .request(builder -> SecurityFrameworkUtils.setLoginUserHeader(builder, user)).build();
            return chain.filter(newExchange);
        });
    }

    private Mono<LoginUser> getLoginUser(ServerWebExchange exchange, String token) {
        // 每次查询现有权威接口，复用身份服务自身的 Redis 缓存，避免各网关持有已撤销身份。
        Long tenantId = WebFrameworkUtils.getTenantId(exchange);
        return checkAccessToken(tenantId, token).map(this::buildUser);
    }

    private Mono<String> checkAccessToken(Long tenantId, String token) {
        return webClient.get()
                .uri(OAuth2TokenApi.URL_CHECK, uriBuilder -> uriBuilder.queryParam("accessToken", token).build())
                .headers(httpHeaders -> WebFrameworkUtils.setTenantIdHeader(tenantId, httpHeaders)) // 设置租户的 Header
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                        return response.releaseBody().thenReturn("{\"code\":401}");
                    }
                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody().then(Mono.error(new IllegalStateException("身份服务暂不可用")));
                    }
                    return response.bodyToMono(String.class)
                            .switchIfEmpty(Mono.error(new IllegalStateException("身份服务响应为空")));
                })
                // 非阻塞限定完整请求时间，避免依赖挂起导致网关无限积累等待请求。
                .timeout(Duration.ofSeconds(5));
    }

    private LoginUser buildUser(String body) {
        // 处理结果，结果不正确
        // 使用现有静默解析，认证响应格式异常也不得将原文和凭据写入日志。
        CommonResult<OAuth2AccessTokenCheckRespDTO> result = JsonUtils.parseObjectQuietly(body, CHECK_RESULT_TYPE_REFERENCE);
        if (result == null) {
            throw new IllegalStateException("身份服务响应格式错误");
        }
        if (result.isError()) {
            // 明确的无效令牌按原有匿名语义交业务权限层处理，其余认证故障拒绝转发。
            if (Objects.equals(result.getCode(), HttpStatus.UNAUTHORIZED.value())) {
                return LOGIN_USER_EMPTY;
            }
            throw new IllegalStateException("身份服务校验失败");
        }

        // 创建登录用户
        OAuth2AccessTokenCheckRespDTO tokenInfo = result.getData();
        if (tokenInfo == null || tokenInfo.getUserId() == null || tokenInfo.getUserType() == null
                || tokenInfo.getTenantId() == null || tokenInfo.getExpiresTime() == null) {
            throw new IllegalStateException("身份服务响应缺少可信身份字段");
        }
        return new LoginUser().setId(tokenInfo.getUserId()).setUserType(tokenInfo.getUserType())
                .setInfo(tokenInfo.getUserInfo()) // 额外的用户信息
                .setTenantId(tokenInfo.getTenantId()).setScopes(tokenInfo.getScopes())
                .setExpiresTime(tokenInfo.getExpiresTime());
    }

    @Override
    public int getOrder() {
        return -100; // 和 Spring Security Filter 的顺序对齐
    }

}
