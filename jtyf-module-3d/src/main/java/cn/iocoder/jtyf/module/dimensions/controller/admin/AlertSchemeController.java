package cn.iocoder.jtyf.module.dimensions.controller.admin;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.framework.common.pojo.ListResult;
import cn.iocoder.jtyf.module.dimensions.domain.vo.AlertSchemePage;
import cn.iocoder.jtyf.module.dimensions.domain.vo.AlertSchemePageVO;
import cn.iocoder.jtyf.module.dimensions.entity.AlertScheme;
import cn.iocoder.jtyf.module.dimensions.entity.DeviceInfoCbds;
import cn.iocoder.jtyf.module.dimensions.entity.DeviceStatusCbds;
import cn.iocoder.jtyf.module.dimensions.service.AlertComponentInfoCbdsService;
import cn.iocoder.jtyf.module.dimensions.service.AlertComponentStatusCbdsService;
import cn.iocoder.jtyf.module.dimensions.service.AlertSchemeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/dimensions/alert-scheme")
@Slf4j
//@Api(tags = "预警方案接口")
@Tag(name = "预警方案接口",description = "预警方案接口")
public class AlertSchemeController {

    @Autowired
    AlertSchemeService alertSchemeService;

    @Autowired
    AlertComponentInfoCbdsService alertComponentInfoCbdsService;

    @Autowired
    AlertComponentStatusCbdsService alertComponentStatusCbdsService;

    @PostMapping("/insert-update-alert-scheme")
    @PreAuthorize("@ss.hasPermission('dimensions:alert:insertOrUpdate')")
    @Operation(summary = "插入或者更改预警方案",description = "插入或者更改预警方案列表")
    public CommonResult<String> insertAlertScheme(@RequestBody AlertScheme alertScheme) {
        return CommonResult.success(alertSchemeService.insertAlertScheme(alertScheme));
    }

    @GetMapping("/delete-alert-scheme")
    @Operation(summary = "删除预警方案列表")
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("@ss.hasPermission('dimensions:alert:delete')")
    public CommonResult<String> deleteAlertScheme(@RequestBody String[] uniqueId) {
        alertSchemeService.deleteAlertSchema(uniqueId);
        return CommonResult.success("删除完成！");
    }

    @PostMapping("/get-alert-scheme-uniqueId")
    @Operation(summary = "根据uniqueId获取预警方案列表")
    @PreAuthorize("@ss.hasPermission('dimensions:alert:listByUniqued')")
    public ListResult<AlertScheme> getAlertScheme(@RequestBody String[] uniqueId) {
        return ListResult.success(alertSchemeService.getAlertSchemeList(uniqueId));
    }

    @PostMapping("/get-page-alert-scheme")
    @Operation(summary = "根据预警名称获取分页预警方案列表")
    @PreAuthorize("@ss.hasPermission('dimensions:alert:listByName')")
    public CommonResult<AlertSchemePageVO> getAlertScheme(@RequestBody AlertSchemePage alertSchemePage) {
        return CommonResult.success(alertSchemeService.getAlertSchemePage(alertSchemePage));
    }

    @GetMapping("/get-all-alert-scheme")
    @Operation(summary = "获取所有预警方案列表")
    @PreAuthorize("@ss.hasPermission('dimensions:alert:allList')")
    public ListResult<AlertScheme> getAllAlertScheme() {
        return ListResult.success(alertSchemeService.list());
    }

    @GetMapping("/test-device-Info")
    @Operation(summary = "获取传感器设备信息表信息-测试使用")
    //@PreAuthorize("@ss.hasPermission('dimensions:alert:infoByDevice')")
    public ListResult<DeviceInfoCbds> testDeviceInfos() {
        return ListResult.success(alertComponentInfoCbdsService.list());
    }

    @GetMapping("/test-device-status")
    @Operation(summary = "根据传感器设备状态表信息-测试使用")
    //@PreAuthorize("@ss.hasPermission('dimensions:alert:deviceStatus')")
    public ListResult<DeviceStatusCbds> testDeviceStatus() {
        return ListResult.success(alertComponentStatusCbdsService.list());
    }
}
