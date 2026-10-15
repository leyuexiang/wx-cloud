package cn.iocoder.jtyf.module.infra.api.config;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.module.infra.dal.dataobject.config.ConfigDO;
import cn.iocoder.jtyf.module.infra.service.config.ConfigService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.jtyf.framework.common.pojo.CommonResult.success;

@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class ConfigApiImpl implements ConfigApi {

    @Resource
    private ConfigService configService;

    @Override
    public CommonResult<String> getConfigValueByKey(String key) {
        ConfigDO config = configService.getConfigByKey(key);
        return success(config != null ? config.getValue() : null);
    }

}
