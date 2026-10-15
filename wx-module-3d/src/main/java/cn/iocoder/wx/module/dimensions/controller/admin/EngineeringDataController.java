package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.ListResult;
import cn.iocoder.wx.module.dimensions.entity.EngineeringData;
import cn.iocoder.wx.module.dimensions.service.EngineeringDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "3D场景信息")
@RequestMapping("/dimensions/engineering_data")
public class EngineeringDataController extends BaseCrudController<EngineeringData, Long> {

    @Resource
    private EngineeringDataService engineeringDataService;

    @GetMapping("/find-engineering-data-list")
    @Operation(summary = "查询所有3D场景信息")
    @PreAuthorize("@ss.hasPermission('dimensions:engineeringData:list')")
    public ListResult<EngineeringData> findEngineeringDataList(){
        return ListResult.success(engineeringDataService.list());
    }
    @PostMapping("/save-engineering-data")
    @Operation(summary = "新建&修改3D场景信息")
    @PreAuthorize("@ss.hasPermission('dimensions:engineeringData:save')")
    public CommonResult<Boolean> createType(@RequestBody EngineeringData engineeringData){
        return CommonResult.success(engineeringDataService.save(engineeringData));
    }

    @PostMapping("/update-engineering-data")
    @Operation(summary = "修改3D场景信息")
    @PreAuthorize("@ss.hasPermission('dimensions:engineeringData:update')")
    public CommonResult<Boolean> updateType(@RequestBody EngineeringData engineeringData){
        return CommonResult.success(engineeringDataService.updateById(engineeringData));
    }
}
