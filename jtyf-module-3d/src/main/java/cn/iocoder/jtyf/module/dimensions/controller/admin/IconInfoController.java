package cn.iocoder.jtyf.module.dimensions.controller.admin;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.framework.common.pojo.ListResult;
import cn.iocoder.jtyf.module.dimensions.domain.vo.ProjectIconInfoFindVO;
import cn.iocoder.jtyf.module.dimensions.domain.vo.ProjectIconInfoPageVO;
import cn.iocoder.jtyf.module.dimensions.entity.IconInfo;
import cn.iocoder.jtyf.module.dimensions.service.IconInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@Tag(name = "项目图标信息维护")
@RequestMapping("/dimensions/project-icon-info")
public class IconInfoController extends BaseCrudController<IconInfo, Long> {

    @jakarta.annotation.Resource
    private IconInfoService iconInfoService;

    @GetMapping("/get-by-project-id")
    @Operation(summary = "根据项目id获取图标列表")
    @PreAuthorize("@ss.hasPermission('dimensions:iconInfo:getAll')")
    public ListResult<IconInfo> getAll(@NotBlank(message = "平台类型不能为空") @RequestParam String resFromPlatform, String projectId) {

        LambdaQueryWrapper<IconInfo> queryWrapper = new LambdaQueryWrapper<IconInfo>().eq(IconInfo::getProjectId, projectId);

        if ("U3D_ALL".equals(resFromPlatform)) {
            queryWrapper.in(IconInfo::getResFromPlatform, "U3D_WEB", "U3D");
        } else {
            queryWrapper.eq(IconInfo::getResFromPlatform, resFromPlatform);
        }
        return ListResult.success(iconInfoService.list(queryWrapper));
    }

    @GetMapping("/get-by-id")
    @Operation(summary = "根据主键ID获取图标信息")
    @PreAuthorize("@ss.hasPermission('dimensions:iconInfo:getIconInfoById')")
    public CommonResult<IconInfo> getIconInfoById(Long id) {
        IconInfo iconInfo = iconInfoService.getById(id);

        if (iconInfo == null) {
            return CommonResult.error(404,"图标不存在");
        }
        return CommonResult.success(iconInfo);
    }

    @GetMapping("/get-by-uniqueId")
    @Operation(summary = "根据uniqueId获取图标信息")
    @PreAuthorize("@ss.hasPermission('dimensions:iconInfo:getIconInfoByUniqueId')")
    public CommonResult<IconInfo> getIconInfoByUniqueId(@NotBlank(message = "平台类型不能为空") @RequestParam String resFromPlatform, String uniqueId) {
        List<IconInfo> iconInfos = iconInfoService.lambdaQuery().eq(IconInfo::getUniqueId, uniqueId)
                .eq(IconInfo::getResFromPlatform, resFromPlatform).list();
        if (iconInfos.isEmpty()) {
            return CommonResult.success(new IconInfo());
        }
        return CommonResult.success(iconInfos.get(0));
    }

    @GetMapping("/get-by-param")
    @Operation(summary = "条件查询项目图标信息")
    @PreAuthorize("@ss.hasPermission('dimensions:iconInfo:getByParam')")
    public CommonResult<ProjectIconInfoPageVO> getByParam(@Valid ProjectIconInfoFindVO projectIconInfoFindVO) {
        return CommonResult.success(iconInfoService.getByParam(projectIconInfoFindVO));
    }

    @PostMapping("/create-project-icon")
    @Operation(summary = "通过UniqueId是否存在判断是插入还是修改项目图标信息")
    @PreAuthorize("@ss.hasPermission('dimensions:iconInfo:createProjectIcon')")
    public CommonResult<String> createProjectIcon(@Valid @RequestBody IconInfo iconInfo) {
        return CommonResult.success(iconInfoService.createProjectIcon(iconInfo));
    }

    @PostMapping("/update-project-icon")
    @Operation(summary = "通过id修改项目图标信息")
    @PreAuthorize("@ss.hasPermission('dimensions:iconInfo:updateProjectIcon')")
    public CommonResult<Boolean> updateProjectIcon(@Valid @RequestBody IconInfo iconInfo) {
        iconInfoService.updateById(iconInfo);
        return CommonResult.success(Boolean.TRUE);
    }

    @GetMapping("/judge-icon-name")
    @Operation(summary = "判断用户自定义图标名称是否存在")
    @PreAuthorize("@ss.hasPermission('dimensions:iconInfo:judgeIconName')")
    public CommonResult<Boolean> judgeIconName(@NotBlank(message = "平台类型不能为空") @RequestParam String resFromPlatform, @RequestParam(required = false) Integer id, String iconName) {
        return CommonResult.success(iconInfoService.judgeIconName(resFromPlatform, id, iconName));
    }

    @GetMapping("/judge-unique-id")
    @Operation(summary = "判断图标唯一标识是否存在")
    @PreAuthorize("@ss.hasPermission('dimensions:iconInfo:judgeUniqueId')")
    public CommonResult<Boolean> judgeUniqueId(Integer id, String uniqueId) {
        return CommonResult.success(iconInfoService.judgeUniqueId(id, uniqueId));
    }


    @DeleteMapping("/del-by-uniqueId")
    @Operation(summary = "根据uniqueId删除图标信息")
    @PreAuthorize("@ss.hasPermission('dimensions:iconInfo:delAllByUniqueId')")
    public CommonResult<String> delAllByUniqueId(@NotBlank(message = "平台类型不能为空") @RequestParam String resFromPlatform, String uniqueId) {
        List<IconInfo> iconInfos = iconInfoService.lambdaQuery()
                .eq(IconInfo::getUniqueId, uniqueId)
                .eq(IconInfo::getResFromPlatform, resFromPlatform).list();

        if (iconInfos.isEmpty()) {
            return CommonResult.error(500, "图标信息不存在");
        }
        int result = iconInfoService.getBaseMapper().delete(new LambdaQueryWrapper<IconInfo>()
                .eq(IconInfo::getUniqueId, uniqueId)
                .eq(IconInfo::getResFromPlatform, resFromPlatform)
        );

        if (result == 0) {
            return CommonResult.error(500, "图标信息删除失败");
        }
        return CommonResult.success("图标信息删除成功");
    }
}

