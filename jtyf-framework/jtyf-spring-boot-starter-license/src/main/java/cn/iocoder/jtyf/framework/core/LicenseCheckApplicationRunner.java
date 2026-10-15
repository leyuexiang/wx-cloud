package cn.iocoder.jtyf.framework.core;

import cn.iocoder.jtyf.framework.common.constant.OrderConstant;
import cn.iocoder.jtyf.framework.common.exception.ServerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;

import jakarta.annotation.Resource;

/**
 * @author xwd
 * @version 1.0
 * @date 2025-04-03
 */
@Slf4j
//@Component
@Order(OrderConstant.RUNNER_LICENSE)
public class LicenseCheckApplicationRunner implements ApplicationRunner {
    @Resource
    private LicenseVerify licenseVerify;
    @Resource
    private ApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        try {
            licenseVerify.install();
        } catch (ServerException e) {
            log.error("许可证验证失败，错误码：{}，错误信息：{}", e.getCode(), e.getMessage());
            System.exit(SpringApplication.exit(context));
        }
    }
}
