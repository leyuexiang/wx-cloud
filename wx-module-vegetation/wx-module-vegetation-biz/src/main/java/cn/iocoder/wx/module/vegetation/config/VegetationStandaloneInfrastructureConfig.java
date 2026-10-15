package cn.iocoder.wx.module.vegetation.config;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.infra.api.logger.ApiErrorLogApi;
import cn.iocoder.wx.module.infra.api.logger.dto.ApiErrorLogCreateReqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 植被服务独立运行时的基础设施替代实现。
 *
 * <p>Web 框架的全局异常处理器固定依赖 {@link ApiErrorLogApi}。集群部署会把异常日志发送到
 * infra 服务；独立部署没有该服务时，使用本实现把必要信息保留在本地应用日志中，避免异常处理
 * 反向依赖 wx-cloud 的其他微服务，且不影响原始异常响应。</p>
 */
@Slf4j
@Profile("standalone")
@Configuration(proxyBeanMethods = false)
public class VegetationStandaloneInfrastructureConfig {

    /**
     * 提供本地异常日志接口。
     *
     * <p>仅记录异常名称、请求地址和追踪编号，不记录请求体或堆栈，避免将敏感字段重复写入日志；
     * 返回成功使全局异常处理器不产生二次错误。完整堆栈仍由异常处理器自身输出到服务日志。</p>
     */
    @Bean
    public ApiErrorLogApi vegetationLocalApiErrorLogApi() {
        return this::writeLocalErrorLog;
    }

    /**
     * 在当前服务日志中记录异常概要，替代远程 infra 异常日志写入。
     *
     * @param request 异常处理器构造的异常日志请求，可能在构造失败场景下为 {@code null}
     * @return 固定成功，保证原始接口的异常响应优先返回给调用方
     */
    private CommonResult<Boolean> writeLocalErrorLog(ApiErrorLogCreateReqDTO request) {
        if (request == null) {
            log.error("[独立异常日志][异常日志请求为空]");
        } else {
            log.error("[独立异常日志][traceId({}) url({}) exception({})]",
                    request.getTraceId(), request.getRequestUrl(), request.getExceptionName());
        }
        return CommonResult.success(true);
    }

}
