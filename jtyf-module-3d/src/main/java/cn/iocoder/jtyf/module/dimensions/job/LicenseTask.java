package cn.iocoder.jtyf.module.dimensions.job;

import cn.iocoder.jtyf.framework.core.LicenseVerify;
import cn.iocoder.jtyf.framework.core.utils.DmcUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 检查许可证是否过期
 */
@Slf4j
@Component
public class LicenseTask {

    @Autowired
    private LicenseVerify licenseVerify;

    @Scheduled(fixedRate = 60000*30)//30分钟执行一次
    public void checkLicence() {
        if (licenseVerify.verify()) {
            log.info("许可证已过期，准备杀掉以 jtyf-module-3d 开头的 Java 服务");
            DmcUtils.killJavaProcess();
        }
    }
}
