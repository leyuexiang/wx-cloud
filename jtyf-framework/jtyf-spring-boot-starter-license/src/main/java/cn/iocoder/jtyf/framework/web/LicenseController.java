package cn.iocoder.jtyf.framework.web;

import cn.iocoder.jtyf.framework.core.LicenseCreator;
import cn.iocoder.jtyf.framework.core.param.LicenseCreatorParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

import java.io.IOException;

/**
 * @author xwd
 * @version 1.0  参考地址：<a href="https://juejin.cn/post/7125349018783186958">...</a>
 * @date 2025-04-03
 */
@RestController
@Tag(name = "License管理", description = "包含证书生成、查看等操作")
@RequestMapping("/license")
public class LicenseController {

    @Resource
    private LicenseCreator licenseCreator;

    @PostMapping
    @Operation(summary = "生成license")
    public String create(@RequestBody LicenseCreatorParam creatorParam) throws IOException {
       boolean result = licenseCreator.generateLicense(creatorParam);
       return result ? "success" : "fail";
    }

    @GetMapping
    @Operation(summary = "查看license")
    public String query() throws IOException {
        return licenseCreator.queryLicense();
    }
}
