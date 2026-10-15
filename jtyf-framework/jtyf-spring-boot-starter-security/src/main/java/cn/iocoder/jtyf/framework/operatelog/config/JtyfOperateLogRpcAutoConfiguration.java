package cn.iocoder.jtyf.framework.operatelog.config;

import cn.iocoder.jtyf.module.system.api.logger.OperateLogApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * OperateLog 使用到 Feign 的配置项
 *
 * @author 九天芸菲
 */
@AutoConfiguration
@EnableFeignClients(clients = {OperateLogApi.class}) // 主要是引入相关的 API 服务
public class JtyfOperateLogRpcAutoConfiguration {
}
