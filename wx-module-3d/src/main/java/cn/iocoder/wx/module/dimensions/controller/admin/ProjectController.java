/*
package com.iocoder.wx.module.dimensions.controller;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;
import java.util.List;


*/
/*

@RestController
@RequestMapping("/project")
@Slf4j
@Api(value = "项目相关信息获取controller", tags = {"项目相关信息获取接口"})
public class Szl3dProjectController  {

    @Resource
    ProjectFeignService projectFeignService;

    @GetMapping("/get-by-id")
    @ApiOperation("获取所有应用的appId")
    public Result<?> getAll(){
        Result<List<AppInfo>> resultAppInfoList = projectFeignService.userAppList();
        List<AppInfo> appInfoList = resultAppInfoList.getContent();
        List<Long> appIdList = new ArrayList<>();
        for (AppInfo appInfo : appInfoList) {
            appIdList.add(appInfo.getId());
        }
        return Result.ok(appIdList);
    }
}
*/
