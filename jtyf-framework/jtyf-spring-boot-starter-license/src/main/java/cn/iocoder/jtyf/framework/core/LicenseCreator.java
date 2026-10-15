package cn.iocoder.jtyf.framework.core;

import cn.iocoder.jtyf.framework.common.exception.ServerException;
import cn.iocoder.jtyf.framework.core.param.LicenseCreatorParam;
import cn.iocoder.jtyf.framework.core.prop.LicenseProperties;
import jakarta.annotation.Resource;
import javax0.license3j.Feature;
import javax0.license3j.License;
import javax0.license3j.io.LicenseReader;
import javax0.license3j.io.LicenseWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

/**
 * @author xwd
 * @version 1.0
 * @date 2025-04-03
 * @desc License证书生成器
 * 负责创建和管理软件许可证文件
 */
@Slf4j
@Component
public class LicenseCreator {

    @Resource
    private LicenseProperties licenseProperties;

    // 日期格式化器，使用常量避免重复创建
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 生成或更新License证书
     *
     * @param param 证书参数
     * @return 操作结果
     */
    public boolean generateLicense(LicenseCreatorParam param) {
        File licenseFile = new File(licenseProperties.getLicensePath());

        try {
            if (!licenseFile.exists()) {
                // 创建新证书
                License license = createNewLicense(param);
                writeLicenseToFile(license, licenseFile);
                log.info("新License证书生成成功，路径：{}", licenseFile.getAbsolutePath());
                return true;
            } else {
                // 更新现有证书
                License license = updateExistingLicense(param, licenseFile);
                writeLicenseToFile(license, licenseFile);
                log.info("License证书更新成功，路径：{}", licenseFile.getAbsolutePath());
                return true;
            }
        } catch (Exception e) {
            log.error("证书生成失败", e);
            throw new ServerException(HttpStatus.FORBIDDEN.value(), "生成License证书失败");
        }
    }

    /**
     * 创建新的License证书
     */
    private License createNewLicense(LicenseCreatorParam param) {
        License license = new License();

        // 设置基本特征
        license.setLicenseId(UUID.fromString(param.getSystemInfo().getUuid()));
        license.add(Feature.Create.dateFeature("issuedtime", param.getIssuedTime()));
        license.add(Feature.Create.stringFeature("company", param.getCompany()));
        license.add(Feature.Create.intFeature("version", 1));
        license.add(Feature.Create.stringFeature("user", param.getConsumerType()));

        Date expiryTime = getExpiryTime(param);
        license.add(Feature.Create.dateFeature("endtime", expiryTime));
        license.setExpiry(expiryTime);


        license.add(Feature.Create.dateFeature("csExpiryTime", param.getCsExpiryTime()));

        log.debug("新证书内容：\n{}", printLicenseInfo(license));
        return license;
    }

    /**
     * 更新现有License证书
     */
    private License updateExistingLicense(LicenseCreatorParam param, File licenseFile) throws IOException {
        // 使用try-with-resources确保资源关闭
        try (LicenseReader reader = new LicenseReader(licenseFile.getAbsolutePath())) {
            License license = reader.read();

            Date expiryTime = getExpiryTime(param);
            license.setExpiry(expiryTime);

            // 更新特征
            license.add(Feature.Create.intFeature("version", 2));
            license.add(Feature.Create.dateFeature("endtime", expiryTime));
            license.add(Feature.Create.stringFeature("company", param.getCompany()));
            license.add(Feature.Create.stringFeature("user", param.getConsumerType()));
            license.add(Feature.Create.stringFeature("description", param.getDescription()));


            license.add(Feature.Create.dateFeature("csExpiryTime", param.getCsExpiryTime()));

            log.debug("更新后证书内容：\n{}", printLicenseInfo(license));
            return license;
        }
    }

    /**
     * 将License写入文件
     */
    private void writeLicenseToFile(License license, File licenseFile) throws IOException {
        try (LicenseWriter writer = new LicenseWriter(licenseFile.getAbsolutePath())) {
            writer.write(license);
        }
    }

    /**
     * 计算过期时间
     */
    private Date getExpiryTime(LicenseCreatorParam param) {
        // 默认30天后过期
        return param.getExpiryTime() != null ? param.getExpiryTime() : new Date(param.getIssuedTime().getTime() + 30L * 24 * 60 * 60 * 1000);
    }

    /**
     * 查询License信息
     */
    public String queryLicense() {
        File licenseFile = new File(licenseProperties.getLicensePath());

        try {
            if (!licenseFile.exists()) {
                throw new ServerException(HttpStatus.FORBIDDEN.value(), "License证书不存在，请联系管理员");
            }

            try (LicenseReader reader = new LicenseReader(licenseFile.getAbsolutePath())) {
                License license = reader.read();
                return printLicenseInfo(license);
            }
        } catch (IOException e) {
            log.error("读取License证书失败", e);
            throw new ServerException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "查询License证书失败");
        }
    }

    /**
     * 格式化License信息为可读字符串
     */
    public String printLicenseInfo(License license) {
        StringBuilder result = new StringBuilder("证书信息:\n");

        // 输出基本信息
        result.append("公司: ").append(license.get("company")).append("\n");
        result.append("描述: ").append(license.get("description")).append("\n");
        result.append("证书ID: ").append(license.getLicenseId()).append("\n");
        result.append("用户类型: ").append(license.get("user")).append("\n");
        result.append("版本: ").append(license.get("version")).append("\n");

        // 格式化日期字段
        formatDateField(result, license, "issuedtime", "颁发时间");
        formatDateField(result, license, "endtime", "到期时间");
        formatDateField(result, license, "expiryDate", "过期日期");

        formatDateField(result, license, "csExpiryTime", "综合态势过期日期");


        return result.toString();
    }

    /**
     * 格式化日期字段
     */
    private void formatDateField(StringBuilder result, License license, String fieldName, String displayName) {
        Feature feature = license.get(fieldName);
        if (feature != null && feature.getDate() != null) {
            Instant instant = feature.getDate().toInstant();
            ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
            result.append(displayName).append(": ")
                    .append(zonedDateTime.format(DATE_FORMATTER))
                    .append("\n");
        }
    }
}