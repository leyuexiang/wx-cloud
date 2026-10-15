package cn.iocoder.wx.module.goview.controller.admin;


import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.iocoder.wx.module.goview.domain.AjaxResult;
import cn.iocoder.wx.module.goview.model.SysUser;
import cn.iocoder.wx.module.goview.service.ISysUserService;
import cn.iocoder.wx.module.goview.util.SaTokenUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api2d/zy/sys")
public class ApiController extends BaseController {
    @Autowired
    private ISysUserService iSysUserService;

    @Operation(summary = "登陆", description = "登陆")
    @PostMapping("/login")
    @ResponseBody
    public AjaxResult APIlogin(@RequestBody SysUser user, HttpServletRequest request) {

        // 判断是否登陆
        if (StpUtil.isLogin()) {

            Map<String, Object> map = new HashMap<String, Object>();
            map.put("userinfo", SaTokenUtil.getUser());
            map.put("token", StpUtil.getTokenInfo());
            return success().put("data", map);
        } else {
            if (StrUtil.isNotBlank(user.getUsername()) && StrUtil.isNotBlank(user.getPassword())) {
                SysUser sysUser = iSysUserService.getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()).eq(SysUser::getPassword, SecureUtil.md5(user.getPassword())).last("LIMIT 1"));
                if (sysUser != null) {
                    StpUtil.login(sysUser.getId());
                    SaTokenUtil.setUser(sysUser);
                    Map<String, Object> map = new HashMap<String, Object>();
                    map.put("userinfo", sysUser);
                    map.put("token", StpUtil.getTokenInfo());

                    return success().put("data", map);
                } else {
                    return error(500, "账户或者密码错误");
                }
            } else {
                return error(500, "账户密码不能为空");
            }
        }

    }

    @Operation(summary = "登出", description = "登出")
    @GetMapping("/logout")
    @ResponseBody
    public AjaxResult logout() {

        // 判断是否登陆
        StpUtil.logout();

        return success();

    }


    @Operation(summary = "获取oss地址", description = "获取oss地址")
    @GetMapping("/getOssInfo")
    @ResponseBody
    public AjaxResult getOssInfo() {

        return success();

    }

}
