package cn.iocoder.wx.framework.core.param;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;

/**
 * @author xwd
 * @version 1.0
 * @date 2025-04-03
 */
@Data
public class LicenseCreatorParam implements Serializable {
    /**
     * 证书生效时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date issuedTime = new Date();

    /**
     * 证书失效时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date expiryTime;

    /**
     * 用户类型
     */
    private String consumerType = "user";

    /**
     * 用户数量
     */
    private Integer consumerAmount = 1;

    /**
     * 公司名称
     */
    private String company = "子阳腾梦";

    /**
     * 描述信息
     */
    private String description = "子阳腾梦授权给易通星云的License证书";


    /**
     * 服务器系统信息
     */
    private SystemInfo systemInfo;


    /**
     * 综合态势证书失效时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date csExpiryTime;
}
