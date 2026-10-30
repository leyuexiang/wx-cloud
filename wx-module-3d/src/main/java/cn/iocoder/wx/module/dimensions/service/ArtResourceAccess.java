package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.security.core.LoginUser;
import cn.iocoder.wx.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.system.api.permission.PermissionApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import java.util.Objects;
import static cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode.*;

/** 只使用已认证身份；全局资源所有者0与平台登录租户严格分开。 */
@Component
public class ArtResourceAccess {
    @Resource private PermissionApi permissions;
    @Value("${dim3d.resource.platform-tenant-id:0}") private long platformTenant;
    public record Actor(long user, long tenant) {}
    public Actor actor() {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || user.getId() == null || user.getTenantId() == null || user.getTenantId() <= 0)
            throw new ServiceException(401, "请先登录");
        if (!Objects.equals(user.getTenantId(), TenantContextHolder.getTenantId()))
            throw ServiceException.of(INVALID_TENANT);
        return new Actor(user.getId(), user.getTenantId());
    }
    public void maintain(Actor actor, String scope, String owner) {
        boolean allowed = "GLOBAL_SYSTEM".equals(scope) && "0".equals(owner)
            && platformTenant > 0 && actor.tenant() == platformTenant
            && Boolean.TRUE.equals(permissions.hasAnyRoles(actor.user(), "super_admin").getCheckedData());
        if (!allowed && "TENANT_SHARED".equals(scope) && Long.toString(actor.tenant()).equals(owner))
            allowed = Boolean.TRUE.equals(permissions.hasAnyRoles(actor.user(), "tenant_admin").getCheckedData());
        if (!allowed) throw ServiceException.of(ADMIN_REQUIRED);
    }
}
