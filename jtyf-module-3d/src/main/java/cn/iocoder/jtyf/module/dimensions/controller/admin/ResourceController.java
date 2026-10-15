package cn.iocoder.jtyf.module.dimensions.controller.admin;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.framework.common.pojo.ListResult;
import cn.iocoder.jtyf.module.dimensions.domain.dto.ModelSceneCountDTO;
import cn.iocoder.jtyf.module.dimensions.domain.vo.ProjectResourceFindVO;
import cn.iocoder.jtyf.module.dimensions.domain.vo.ProjectResourcePageVO;
import cn.iocoder.jtyf.module.dimensions.entity.Resource;
import cn.iocoder.jtyf.module.dimensions.service.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dimensions/project-resource")
@Slf4j
@Tag(name = "项目资源信息接口")
public class ResourceController extends BaseCrudController<Resource, Long> {

    @jakarta.annotation.Resource
    private ResourceService resourceService;

    @GetMapping("/get-by-project-id")
    @Operation(summary = "根据项目id获取资源列表")
    @PreAuthorize("@ss.hasPermission('dimensions:resource:getAll')")
    public ListResult<Resource> getAll(String projectId) {
        return ListResult.success(resourceService.lambdaQuery().eq(Resource::getProjectId, projectId).list());
    }

    @GetMapping("/get-by-uniqueId")
    @Operation(summary = "根据uniqueId获取资源列表")
    @PreAuthorize("@ss.hasPermission('dimensions:resource:getAllByUniqueId')")
    public ListResult<Resource> getAllByUniqueId(String uniqueId) {
        return ListResult.success(resourceService.lambdaQuery().eq(Resource::getUniqueId, uniqueId).list());
    }

    @GetMapping("/get-by-param")
    @Operation(summary = "条件查询项目资源信息")
    @PreAuthorize("@ss.hasPermission('dimensions:resource:getByParam')")
    public CommonResult<ProjectResourcePageVO> getByParam(ProjectResourceFindVO projectResourceFindVo) {
        return CommonResult.success(resourceService.getByParam(projectResourceFindVo));
    }

    @GetMapping("/get-model-scene-count")
    @Operation(summary = "查询模型和场景数量")
    @PreAuthorize("@ss.hasPermission('dimensions:resource:count')")
    public CommonResult<ModelSceneCountDTO> getModelSceneCount() {
        return CommonResult.success(resourceService.getModelSceneCount());
    }

    @PostMapping("/create-project-resource")
    @Operation(summary = "通过UniqueId是否存在判断是插入还是修改项目资源信息")
    @PreAuthorize("@ss.hasPermission('dimensions:resource:createProject')")
    public CommonResult<String> createprojectResource(@RequestBody Resource resource) {
        return CommonResult.success(resourceService.createprojectResource(resource));

    }

    @PostMapping("/update-project-resource")
    @Operation(summary = "通过id修改项目资源信息")
    @PreAuthorize("@ss.hasPermission('dimensions:resource:update')")
    public CommonResult<Boolean> updateprojectResource(@RequestBody Resource resource) {
        resourceService.updateById(resource);
        return CommonResult.success(Boolean.TRUE);
    }

    @GetMapping("/judge-resource-name")
    @Operation(summary = "判断资源名称是否存在")
    @PreAuthorize("@ss.hasPermission('dimensions:resource:judgeResourceName')")
    public CommonResult<Boolean> judgeResourceName(Integer id, String resourceName) {
        return CommonResult.success(resourceService.judgeResourceName(id, resourceName));
    }
}

