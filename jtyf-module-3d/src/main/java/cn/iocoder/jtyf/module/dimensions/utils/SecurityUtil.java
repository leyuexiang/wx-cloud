package cn.iocoder.jtyf.module.dimensions.utils;


public class SecurityUtil {

    public static String getUsername() {
      /*  Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();*/
        return "test";
    }

    public static String getAppId() {
/*        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            return ((CustomUserDetails) principal).getAppId();
        }
        return null;*/
        return "1";
    }

    // CustomUserDetails 需要你根据实际情况来实现
    public static class CustomUserDetails {
        // 假设你的用户详情类中有getAppId方法
        public String getAppId() {
            // 返回应用的ID
            return "your-app-id";
        }
        // 其他用户详情相关的属性和方法
    }
}
