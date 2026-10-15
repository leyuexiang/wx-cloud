package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.service.SignalLingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/dimensions/signaling")
@Slf4j
@Tag(name = "信令服务器相关信息获取")
public class SignalLingController {

    @Resource
    SignalLingService signalLingService;

    /**
     * 获取配置接口不需要登录
     */
    @GetMapping("/get-address")
    @Operation(tags = "信令服务器相关信息获取")
    public CommonResult<Map<String,String>> getAddress(){
        return CommonResult.success(signalLingService.getAddress());
    }

}
