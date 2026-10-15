package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.ListResult;
import cn.iocoder.wx.module.dimensions.domain.dto.ModelSceneCountDTO;
import cn.iocoder.wx.module.dimensions.domain.vo.ProjectResourceInfoFindVO;
import cn.iocoder.wx.module.dimensions.domain.vo.ProjectResourceInfoPageVO;
import cn.iocoder.wx.module.dimensions.entity.ResourceInfo;
import cn.iocoder.wx.module.dimensions.service.ResourceInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/dimensions/project-resource-info")
@Slf4j
@Tag(name = "新增：项目资源信息接口")
public class ResourceInfoController extends BaseCrudController<ResourceInfo, Long> {

    @Resource
    private ResourceInfoService resourceInfoService;

    @GetMapping("/get-by-project-id")
    @Operation(summary = "根据项目id获取资源列表")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceInfo:getAll')")
    public ListResult<ResourceInfo> getAll(@NotBlank(message = "平台类型不能为空") @RequestParam String resFromPlatform, String projectId) {

        LambdaQueryWrapper<ResourceInfo> queryWrapper = new LambdaQueryWrapper<ResourceInfo>().eq(ResourceInfo::getProjectId, projectId);

        if ("U3D_ALL".equals(resFromPlatform)) {
            queryWrapper.in(ResourceInfo::getResFromPlatform, "U3D_WEB", "U3D");
        } else {
            queryWrapper.eq(ResourceInfo::getResFromPlatform, resFromPlatform);
        }
        return ListResult.success(resourceInfoService.list(queryWrapper));
    }

    @GetMapping("/get-by-uniqueId")
    @Operation(summary = "根据uniqueId获取资源信息")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceInfo:getAllByUniqueId')")
    public CommonResult<ResourceInfo> getAllByUniqueId(@NotBlank(message = "平台类型不能为空") @RequestParam String resFromPlatform, String uniqueId) {
        LambdaQueryWrapper<ResourceInfo> queryWrapper = new LambdaQueryWrapper<ResourceInfo>().eq(ResourceInfo::getUniqueId, uniqueId);

        if ("U3D_ALL".equals(resFromPlatform)) {
            queryWrapper.in(ResourceInfo::getResFromPlatform, "U3D_WEB", "U3D");
        } else {
            queryWrapper.eq(ResourceInfo::getResFromPlatform, resFromPlatform);
        }
        List<ResourceInfo> resourceInfos = resourceInfoService.list(queryWrapper);

        if (resourceInfos.isEmpty()) {
            return CommonResult.success(new ResourceInfo());
        }
        return CommonResult.success(resourceInfos.get(0));
    }

    @GetMapping("/get-by-param")
    @Operation(summary = "条件查询项目资源信息")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceInfo:getByParam')")
    public CommonResult<ProjectResourceInfoPageVO> getByParam(@Valid ProjectResourceInfoFindVO projectResourceInfoFindVO) {
        return CommonResult.success(resourceInfoService.getByParam(projectResourceInfoFindVO));
    }

    @GetMapping("/get-model-scene-count")
    @Operation(summary = "查询模型和场景数量")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceInfo:getModelSceneCount')")
    public CommonResult<ModelSceneCountDTO> getModelSceneCount(@NotBlank(message = "平台类型不能为空") @RequestParam String resFromPlatform) {
        return CommonResult.success(resourceInfoService.getModelSceneCount(resFromPlatform));
    }

    @PostMapping("/create-project-resource")
    @Operation(summary = "通过UniqueId是否存在判断是插入还是修改项目资源信息")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceInfo:createProjectResource')")
    public CommonResult<String> createProjectResource(@Valid @RequestBody ResourceInfo resourceInfo) {
        return CommonResult.success(resourceInfoService.createProjectResource(resourceInfo));

    }

    @PostMapping("/update-project-resource")
    @Operation(summary = "通过id修改项目资源信息")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceInfo:updateProjectResource')")
    public CommonResult<Boolean> updateProjectResource(@Valid @RequestBody ResourceInfo resourceInfo) {
        resourceInfoService.updateById(resourceInfo);
        return CommonResult.success(Boolean.TRUE);
    }

    @GetMapping("/judge-resource-name")
    @Operation(summary = "判断用户自定义资源名称是否存在")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceInfo:judgeResourceName')")
    public CommonResult<Boolean> judgeResourceName(@NotBlank(message = "平台类型不能为空") @RequestParam String resFromPlatform, @RequestParam(required = false) Integer id, String resourceName) {
        return CommonResult.success(resourceInfoService.judgeResourceName(resFromPlatform, id, resourceName));
    }

    @GetMapping("/judge-art-res-name")
    @Operation(summary = "判断资源名称是否存在")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceInfo:judgeArtResName')")
    public CommonResult<Boolean> judgeArtResName(Integer id, String artResName) {
        return CommonResult.success(resourceInfoService.judgeArtResName(id, artResName));
    }

    @GetMapping("/judge-unique-id")
    @Operation(summary = "判断资源唯一标识是否存在")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceInfo:judgeUniqueId')")
    public CommonResult<Boolean> judgeUniqueId(Integer id, String uniqueId) {
        return CommonResult.success(resourceInfoService.judgeUniqueId(id, uniqueId));
    }


    @DeleteMapping("/del-by-uniqueId")
    @Operation(summary = "根据uniqueId删除资源信息")
    @PreAuthorize("@ss.hasPermission('dimensions:resourceInfo:delAllByUniqueId')")
    public CommonResult<String> delAllByUniqueId(@NotBlank(message = "平台类型不能为空") @RequestParam String resFromPlatform, String uniqueId) {

        LambdaQueryWrapper<ResourceInfo> queryWrapper = new LambdaQueryWrapper<ResourceInfo>()
                .eq(ResourceInfo::getUniqueId, uniqueId)
                .eq(ResourceInfo::getResFromPlatform, resFromPlatform);

        if ("U3D_ALL".equals(resFromPlatform)) {
            queryWrapper.in(ResourceInfo::getResFromPlatform, "U3D_WEB", "U3D");
        } else {
            queryWrapper.eq(ResourceInfo::getResFromPlatform, resFromPlatform);
        }

        List<ResourceInfo> resourceInfos = resourceInfoService.list(queryWrapper);

        if (resourceInfos.isEmpty()) {
            return CommonResult.error(500, "资源信息不存在");
        }


        int result = resourceInfoService.getBaseMapper().delete(queryWrapper);

        if (result == 0) {
            return CommonResult.error(500, "资源信息删除失败");
        }
        return CommonResult.success("资源信息删除成功");
    }
}

