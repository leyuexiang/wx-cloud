package cn.iocoder.jtyf.module.dimensions.controller.admin;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.framework.common.pojo.ListResult;
import cn.iocoder.jtyf.module.dimensions.entity.ResourceType;
import cn.iocoder.jtyf.module.dimensions.service.ResourceTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@Tag(name = "项目资源类型表")
@RequestMapping("/dimensions/project-resource-type")
public class ResourceTypeController extends BaseCrudController<ResourceType, Long> {

    @Resource
    ResourceTypeService resourceTypeService;

    @GetMapping("/find-resource-type")
    @Operation(summary = "查询所有资源类型")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceType:getAll')")
    public ListResult<ResourceType> getAll(String typeName) {
        return ListResult.success(resourceTypeService.lambdaQuery()
                .like(StrUtil.isNotBlank(typeName), ResourceType::getTypeName, typeName).list());
    }

    @PostMapping("/create-resource-type")
    @Operation(summary = "新建项目资源类型_id不用传")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceType:createType')")
    public CommonResult<String> createType(@RequestBody @Validated ResourceType resourceType) {
        return CommonResult.success(resourceTypeService.createType(resourceType));
    }

    @PostMapping("/create-resource-update")
    @Operation(summary = "修改项目资源类型")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceType:updateType')")
    public CommonResult<Integer> updateType(@RequestBody @Validated ResourceType resourceType) {
        return CommonResult.success(resourceTypeService.updateType(resourceType));
    }

    @PostMapping("/create-resource-delete")
    @Operation(summary = "根据id删除项目资源类型")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceType:delete')")
    public CommonResult<String> deleteType(Integer id) {
        return CommonResult.success(resourceTypeService.deleteType(id));

    }
}

