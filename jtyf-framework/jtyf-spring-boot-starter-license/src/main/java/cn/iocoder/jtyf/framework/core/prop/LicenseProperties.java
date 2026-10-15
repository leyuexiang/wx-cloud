package cn.iocoder.jtyf.framework.core.prop;

import cn.iocoder.jtyf.framework.core.enums.VerifySystemType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author xwd
 * @version 1.0
 * @date 2025-04-03
 */
@ConfigurationProperties(prefix = "ptc.license")
@Data
public class LicenseProperties {
    /**
     * 证书生成路径
     */
    private String licensePath = "/home/service/license/license.lic";

    /**
     * 是否开启license验证服务器系统信息, 不配置，默认开启
     */
    private Boolean verifySystemSwitch = true;

    /**
     * 验证系统信息方式 {@link VerifySystemType}
     */
    private VerifySystemType verifySystemType = VerifySystemType.CPU_ID;

    /**
     * 是否项目是验证license
     */
    private Boolean startCheck = true;
}