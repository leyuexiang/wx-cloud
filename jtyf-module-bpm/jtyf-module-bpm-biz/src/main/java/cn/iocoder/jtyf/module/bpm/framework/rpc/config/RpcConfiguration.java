package cn.iocoder.jtyf.module.bpm.framework.rpc.config;

import cn.iocoder.jtyf.module.system.api.dept.DeptApi;
import cn.iocoder.jtyf.module.system.api.dept.PostApi;
import cn.iocoder.jtyf.module.system.api.dict.DictDataApi;
import cn.iocoder.jtyf.module.system.api.permission.RoleApi;
import cn.iocoder.jtyf.module.system.api.sms.SmsSendApi;
import cn.iocoder.jtyf.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {RoleApi.class, DeptApi.class, PostApi.class, AdminUserApi.class, SmsSendApi.class, DictDataApi.class})
public class RpcConfiguration {
}
