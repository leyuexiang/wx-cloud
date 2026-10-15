package cn.iocoder.wx.module.dimensions.controller.admin;


import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.ListResult;
import cn.iocoder.wx.module.dimensions.domain.vo.LabelVo;
import cn.iocoder.wx.module.dimensions.entity.AssociationObject;
import cn.iocoder.wx.module.dimensions.service.AssociationObjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


 @RestController
 @RequestMapping("/dimensions/association-object")
 @Slf4j
 @Tag(name = "关联对象数据获取接口")
public class AssociationObjectController {

    @Resource
    AssociationObjectService associationObjectService;


    @GetMapping("/get-label-by-name")
    @Operation(summary="根据表名获取对应表的标签树")
    @PreAuthorize("@ss.hasPermission('dimensions:association:getLabel')")
    public ListResult<AssociationObject> getLabel(String tableName) {
        return ListResult.success(associationObjectService.getLabel(tableName));
    }

    @GetMapping("/get-device-Info")
    @Operation(summary="获取对应设备表信息(CBDSS/PPFMTER)")
    @PreAuthorize("@ss.hasPermission('dimensions:association:getDeviceInfo')")
    public CommonResult<?> getDeviceInfos(String type) {
        return associationObjectService.getDeviceInfos(type);
    }

    @PostMapping("/get-device-id")
    @Operation(summary="根据标签获取所有相关设备id(CBDSS/PPFMTER)")
    @PreAuthorize("@ss.hasPermission('dimensions:association:getDeviceId')")
    public ListResult<String> getDeviceId(@RequestBody String[][] label) {
        return ListResult.success(associationObjectService.getDeviceId(label));
    }

    @GetMapping("/getLabel-by-typeOrSpace")
    @Operation(summary="根据类型或者空间获取标签树")
    @PreAuthorize("@ss.hasPermission('dimensions:association:getNewLabel')")
    public ListResult<AssociationObject> getNewLabel(String typeOrSpace) {
        return ListResult.success(associationObjectService.getNewLabel(typeOrSpace));
    }

    @PostMapping("/get-deviceId-by-value")
    @Operation(summary="根据value数组和typeOrSpace获取设备相关信息")
    @PreAuthorize("@ss.hasPermission('dimensions:association:getDeviceIdByValue')")
    public ListResult<String> getDeviceIdByValue(@RequestBody LabelVo labelVo) {
        return ListResult.success(associationObjectService.getDeviceIdByValue(labelVo));
    }
 }


