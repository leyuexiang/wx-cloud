package cn.iocoder.wx.framework.security.core.service;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.wx.framework.common.core.KeyValue;
import cn.iocoder.wx.framework.common.enums.UserTypeEnum;
import cn.iocoder.wx.framework.security.core.LoginUser;
import cn.iocoder.wx.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.wx.module.system.api.permission.PermissionApi;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import static cn.iocoder.wx.framework.common.util.cache.CacheUtils.buildCache;

/**
 * 默认的 {@link SecurityFrameworkService} 实现类
 *
 * @author 九天芸菲
 */
@Slf4j
@AllArgsConstructor
public class SecurityFrameworkServiceImpl implements SecurityFrameworkService {

    private final PermissionApi permissionApi;

    /**
     * 针对 {@link #hasAnyRoles(String...)} 的缓存
     */
    private final LoadingCache<KeyValue<Long, List<String>>, Boolean> hasAnyRolesCache = buildCache(
            Duration.ofMinutes(1L), // 过期时间 1 分钟
            new CacheLoader<>() {
                @Override
                public Boolean load(KeyValue<Long, List<String>> key) {
                    return permissionApi.hasAnyRoles(key.getKey(), key.getValue().toArray(new String[0])).getCheckedData();
                }
            });

    /**
     * 针对 {@link #hasAnyPermissions(String...)} 的缓存
     */
    private final LoadingCache<KeyValue<Long, List<String>>, Boolean> hasAnyPermissionsCache = buildCache(
            Duration.ofMinutes(1L), // 过期时间 1 分钟
            new CacheLoader<>() {
                @Override
                public Boolean load(KeyValue<Long, List<String>> key) {
                    return permissionApi.hasAnyPermissions(key.getKey(), key.getValue().toArray(new String[0])).getCheckedData();
                }
            });

    @Override
    public boolean hasPermission(String permission) {
        return hasAnyPermissions(permission);
    }

    @Override
    public boolean hasAnyPermissions(String... permissions) {
        return isAuthenticated(key -> {
            try {
                return hasAnyPermissionsCache.get(key);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
            }, permissions);

    }

    @Override
    public boolean hasRole(String role) {
        return hasAnyRoles(role);
    }

    @Override
    public boolean hasAnyRoles(String... roles) {
        return isAuthenticated(key -> {
            try {
                return hasAnyRolesCache.get(key);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, roles);
    }

    @Override
    public boolean hasScope(String scope) {
        return hasAnyScopes(scope);
    }

    @Override
    public boolean hasAnyScopes(String... scopes) {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null) {
            return false;
        }
        return CollUtil.containsAny(user.getScopes(), Arrays.asList(scopes));
    }

    private boolean isAuthenticated(Predicate<KeyValue<Long, List<String>>> cachePredicate, String... permissions) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            return false;
        }
        // 系统无需登录、校验权限的机器人账号
        if (Objects.equals(loginUser.getUserType(), UserTypeEnum.BOT.getValue())) {
            return true;
        }
        try {
            return cachePredicate.test(new KeyValue<>(loginUser.getId(), Arrays.asList(permissions)));
        } catch (RuntimeException e) {
            log.error("[isAuthenticated]权限校验异常", e);
            return false; // 异常时默认拒绝访问
        }
    }

}
