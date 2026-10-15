package cn.iocoder.jtyf.module.dimensions.controller.admin;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.framework.common.pojo.ListResult;
import cn.iocoder.jtyf.module.dimensions.entity.IconType;
import cn.iocoder.jtyf.module.dimensions.service.IconTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@Tag(name = "项目图标类型表")
@RequestMapping("/dimensions/project-icon-type")
public class IconTypeController extends BaseCrudController<IconType, Long> {

    @Resource
    IconTypeService iconTypeService;

    @GetMapping("/find-icon-type")
    @Operation(summary = "查询所有图标类型")
    @PreAuthorize("@ss.hasPermission('dimensions:iconType:getAll')")
    public ListResult<IconType> getAll(String typeName){
        return ListResult.success(iconTypeService.lambdaQuery()
                .like(Objects.nonNull(typeName), IconType::getTypeName, typeName).list());
    }
    @PostMapping("/create-icon-type")
    @Operation(summary = "新建&修改项目图标类型_id不用传")
    @PreAuthorize("@ss.hasPermission('dimensions:iconType:createType')")
    public CommonResult<String> createType(@RequestBody @Validated IconType iconType){
        return CommonResult.success(iconTypeService.createType(iconType));
    }

    @PostMapping("/create-icon-update")
    @Operation(summary = "修改项目图标类型")
    @PreAuthorize("@ss.hasPermission('dimensions:iconType:update')")
    public CommonResult<Integer> updateType(@RequestBody @Validated IconType iconType){
        return CommonResult.success(iconTypeService.updateType(iconType));
    }

    @PostMapping("/create-icon-delete")
    @Operation(summary = "根据id删除项目图标类型")
    @PreAuthorize("@ss.hasPermission('dimensions:iconType:delete')")
    public CommonResult<String> deleteType(Integer id) {
        return CommonResult.success(iconTypeService.deleteType(id));

    }
}
