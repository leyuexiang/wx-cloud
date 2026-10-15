package cn.iocoder.jtyf.module.crm.framework.rpc.config;

import cn.iocoder.jtyf.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.jtyf.module.system.api.dept.DeptApi;
import cn.iocoder.jtyf.module.system.api.dept.PostApi;
import cn.iocoder.jtyf.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {AdminUserApi.class, DeptApi.class, PostApi.class,
        BpmProcessInstanceApi.class})
public class RpcConfiguration {
}
