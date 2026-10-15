package cn.iocoder.jtyf.framework.core;

import cn.iocoder.jtyf.framework.common.exception.ServerException;
import cn.iocoder.jtyf.framework.core.prop.LicenseProperties;
import cn.iocoder.jtyf.framework.core.utils.DmcUtils;
import jakarta.annotation.Resource;
import javax0.license3j.Feature;
import javax0.license3j.License;
import javax0.license3j.io.LicenseReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * @author xwd
 * @version 1.0
 * @date 2025-04-03
 * @desc 许可证验证器
 * 负责验证许可证的有效性、过期时间及设备绑定
 */
@Component
@Slf4j
public class LicenseVerify {

    @Resource
    private LicenseProperties licenseProperties;

    // 缓存当前读取的License对象，减少重复IO
    private License cachedLicense;

    /**
     * 应用启动时安装并验证许可证（全量校验：文件存在+设备绑定+有效期）
     */
    public void install() {
        // 1. 检查许可证文件是否存在
        File licenseFile = new File(licenseProperties.getLicensePath());
        if (!licenseFile.exists()) {
            throw new ServerException(HttpStatus.FORBIDDEN.value(), "许可证文件不存在，请联系授权");
        }

        // 2. 读取许可证内容（缓存到内存，避免重复IO）
        License license = readLicense();
        if (license == null) {
            throw new ServerException(HttpStatus.FORBIDDEN.value(), "许可证文件损坏，无法读取");
        }

        // 3. 验证设备绑定（本机UUID与许可证UUID是否一致）
        if (!isMachineMatch(license)) {
            throw new ServerException(HttpStatus.FORBIDDEN.value(), "许可证与当前设备不匹配，请联系授权");
        }

        // 4. 验证许可证是否过期
        if (isExpired(license)) {
            throw new ServerException(HttpStatus.FORBIDDEN.value(), "许可证已过期，请联系授权");
        }

        // 5. 计算剩余天数并日志提示
        int remainingDays = getRemainingDays(license);
        log.info("许可证验证通过，距离过期还有 {} 天", remainingDays);
    }

    /**
     * 接口调用时的轻量校验（仅验证有效期，不重复验证设备绑定）
     */
    public boolean verify() {
        return verifyDate("endtime", "过期时间");
    }

    /**
     * 综合态势功能单独校验（检查特定过期字段）
     */
    public boolean verifyCs() {
        return verifyDate("csExpiryTime", "综合态势过期时间");
    }

    private boolean verifyDate(String dateTime, String dateTimeDesc) {
        License license = readLicense();
        if (license == null) {
            throw new ServerException(HttpStatus.FORBIDDEN.value(), "未获取到授权文件，请联系授权");
        }

        Feature expiryFeature = license.get(dateTime);
        if (expiryFeature == null) {
            log.warn("许可证中未配置[{}]，默认视为未授权", dateTimeDesc);
            throw new ServerException(HttpStatus.FORBIDDEN.value(), "许可证中未获取到[{}]，请检查授权文件是否正确", dateTimeDesc);
        }
        return isTimeExpired(expiryFeature.getDate());
    }


    // ------------------------------ 核心工具方法 ------------------------------

    /**
     * 读取License文件（使用try-with-resources确保资源关闭，缓存结果避免重复IO）
     */
    private License readLicense() {
        // 缓存已读取的License，减少重复文件操作
        if (cachedLicense != null) {
            return cachedLicense;
        }

        File licenseFile = new File(licenseProperties.getLicensePath());
        if (!licenseFile.exists()) {
            log.error("License文件不存在：{}", licenseProperties.getLicensePath());
            return null;
        }

        try (LicenseReader reader = new LicenseReader(licenseFile.getAbsolutePath())) {
            cachedLicense = reader.read();
            return cachedLicense;
        } catch (IOException e) {
            log.error("读取License文件失败", e);
            return null;
        }
    }

    /**
     * 验证设备是否匹配（本机UUID vs 许可证UUID）
     */
    private boolean isMachineMatch(License license) {
        String systemUuid = DmcUtils.getSystemUuid();
        String licenseUuid = license.getLicenseId().toString();
        boolean isMatch = systemUuid.equals(licenseUuid);
        if (!isMatch) {
            log.error("设备绑定校验失败：本机UUID={}, 许可证UUID={}", systemUuid, licenseUuid);
        }
        return isMatch;
    }

    /**
     * 检查许可证是否过期（使用UTC时间避免时区问题）
     */
    private boolean isExpired(License license) {
        Feature endTimeFeature = license.get("endtime");
        if (endTimeFeature == null) {
            log.warn("许可证未配置过期时间，默认视为已过期");
            return true;
        }
        return isTimeExpired(endTimeFeature.getDate());
    }

    /**
     * 通用时间过期判断（核心：使用UTC时间比较，消除时区差异）
     */
    private boolean isTimeExpired(Date expireDate) {
        if (expireDate == null) {
            return true;
        }
        // 转换为本地时区的日期对象（保持与原有Instant比较逻辑一致）
        LocalDate expireLocalDate = expireDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        return LocalDate.now().isAfter(expireLocalDate); // 当前时间晚于过期时间 → 已过期
    }

    /**
     * 计算剩余有效天数
     */
    private int getRemainingDays(License license) {
        Feature endTimeFeature = license.get("endtime");
        if (endTimeFeature == null) {
            return 0;
        }
        Date endDate = endTimeFeature.getDate();
        Date now = new Date();
        long diffMillis = endDate.getTime() - now.getTime();
        if (diffMillis <= 0) {
            return 0; // 已过期则返回0
        }
        return (int) (diffMillis / (24 * 60 * 60 * 1000));
    }
}