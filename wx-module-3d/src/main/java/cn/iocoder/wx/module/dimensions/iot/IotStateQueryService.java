package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.common.enums.UserTypeEnum;
import cn.iocoder.wx.framework.security.core.LoginUser;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.wx.framework.tenant.core.util.TenantUtils;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import org.springframework.security.access.AccessDeniedException;

import java.util.Objects;

/** 从认证身份确定租户并校验权限，适用于三维服务当前关闭全局租户拦截的环境。 */
public class IotStateQueryService {
    public static final String QUERY_PERMISSION = "dimensions:iot-state:query";
    private final IotStateStore store;
    private final SecurityFrameworkService security;

    public IotStateQueryService(IotStateStore store, SecurityFrameworkService security) {
        this.store = store;
        this.security = security;
    }

    public TwinTelemetryEvent get(String deviceId, String pointCode) {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || user.getId() == null || !Objects.equals(user.getUserType(), UserTypeEnum.ADMIN.getValue())
                || user.getTenantId() == null || user.getTenantId() < 0) {
            throw new AccessDeniedException("查询物联网状态需要有效的管理员租户身份");
        }
        // 权限远程调用使用认证租户，不能沿用调用方传入的 tenant-id 请求头上下文。
        boolean permitted = TenantUtils.execute(user.getTenantId(), () -> security.hasPermission(QUERY_PERMISSION));
        if (!permitted) {
            throw new AccessDeniedException("没有物联网状态查询权限");
        }
        return store.get(user.getTenantId(), deviceId, pointCode);
    }
}
