package cn.iocoder.jtyf.module.dimensions.controller.admin;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.framework.common.pojo.ListResult;
import cn.iocoder.jtyf.module.dimensions.entity.ResourceDataType;
import cn.iocoder.jtyf.module.dimensions.service.ResourceDataTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@Tag(name = "项目资源数据类型表")
@RequestMapping("/dimensions/project-resource-datatype")
public class ResourceDataTypeController extends BaseCrudController<ResourceDataType, Long> {

    @Resource
    ResourceDataTypeService resourceDataTypeService;

    @GetMapping("/find-resource-type")
    @Operation(summary = "查询所有资源数据类型")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceData:getAll')")
    public ListResult<ResourceDataType> getAll(String typeName){
        return ListResult.success(resourceDataTypeService.lambdaQuery()
                .like(Objects.nonNull(typeName), ResourceDataType::getTypeName, typeName).list());
    }
    @PostMapping("/create-resource-type")
    @Operation(summary = "新建&修改项目资源数据类型")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceData:createType')")
    public CommonResult<Boolean> createType(@RequestBody ResourceDataType resourceDataType){
        resourceDataTypeService.save(resourceDataType);
        return CommonResult.success(Boolean.TRUE);
    }

    @PostMapping("/create-resource-update")
    @Operation(summary = "修改项目资源数据类型")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceData:update')")
    public CommonResult<Boolean> updateType(@RequestBody ResourceDataType resourceDataType){
        resourceDataTypeService.updateById(resourceDataType);
        return CommonResult.success(Boolean.TRUE);
    }
}
