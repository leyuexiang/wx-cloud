package cn.iocoder.jtyf.module.dimensions.controller.admin;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.framework.common.pojo.ListResult;
import cn.iocoder.jtyf.module.dimensions.entity.ResourceVersion;
import cn.iocoder.jtyf.module.dimensions.service.ResourceVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@Slf4j
@Tag(name = "新增：项目资源版本表")
@RequestMapping("/dimensions/project-resource-version")
public class ResourceVersionController extends BaseCrudController<ResourceVersion, Long> {

    @Resource
    ResourceVersionService resourceVersionService;

    @GetMapping("/find-resource-version")
    @Operation(summary = "查询所有资源版本")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceVersion:getAll')")
    public ListResult<ResourceVersion> getAll(String versionName) {
        return ListResult.success(resourceVersionService.lambdaQuery()
                .like(StrUtil.isNotBlank(versionName), ResourceVersion::getVersionName, versionName).list());
    }

    @PostMapping("/create-resource-version")
    @Operation(summary = "新建项目资版本_id不用传")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceVersion:create')")
    public CommonResult<String> createVersion(@RequestBody ResourceVersion resourceVersion) {
        if (Objects.isNull(resourceVersion.getId())) {
            resourceVersionService.save(resourceVersion);
            return CommonResult.success(String.valueOf(resourceVersion.getId()));
        }
        return CommonResult.success("id不用传");
    }

    @PostMapping("/create-resource-update")
    @Operation(summary = "修改项目资源版本")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceVersion:update')")
    public CommonResult<String> updateVersion(@RequestBody ResourceVersion resourceVersion) {
        resourceVersionService.updateById(resourceVersion);
        return CommonResult.success("修改项目资源版本成功");
    }

    @PostMapping("/create-resource-delete")
    @Operation(summary = "根据id删除项目资源版本")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceVersion:delete')")
    public CommonResult<String> deleteType(Integer id) {
        return CommonResult.success(resourceVersionService.deleteVersion(id));

    }
}

