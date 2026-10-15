package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.exception.WxBusinessException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.ListResult;
import cn.iocoder.wx.module.dimensions.domain.vo.ComponentInfoPageVO;
import cn.iocoder.wx.module.dimensions.domain.vo.ComponentInfoVO;
import cn.iocoder.wx.module.dimensions.entity.ComponentInfo;
import cn.iocoder.wx.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.wx.module.dimensions.service.ComponentInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dimensions/component-info")
@Slf4j
@Tag(name = "组件信息增删改查接口")
public class ComponentInfoController {

    @Resource
    ComponentInfoService componentInfoService;

    @GetMapping("/get-UniqueId-Component-Info")
    @Operation(summary = "根据componentUniqueId获取组件信息")
    @PreAuthorize("@ss.hasPermission('dimensions:component:infoByUniqueId')")
    public ListResult<ComponentInfo> getUniqueIdComponentInfo(String componentUniqueId) {
        if (componentUniqueId != null) {
            return ListResult.success(componentInfoService.lambdaQuery()
                    .eq(ComponentInfo::getComponentUniqueId,componentUniqueId).list());
        }
        throw WxBusinessException.of(ErrorCodeConstants.COMPONENT_UUID_IS_NULL);
    }

    @GetMapping("/get-projectId-Component-Info")
    @Operation(summary = "根据projectId获取组件信息")
    @PreAuthorize("@ss.hasPermission('dimensions:component:infoByProjectId')")
    public ListResult<ComponentInfo> getProjectIdComponentInfo(String projectId) {
        if (projectId != null) {
            return ListResult.success(componentInfoService.lambdaQuery()
                    .eq(ComponentInfo::getProjectId, projectId).list());
        }
        throw WxBusinessException.of(ErrorCodeConstants.PROJECT_ID_IS_NULL);
    }

    @PostMapping("/get-page-Component-Info")
    @Operation(summary = "根据名称和类型获取分页组件信息")
    @PreAuthorize("@ss.hasPermission('dimensions:component:page')")
    public CommonResult<ComponentInfoPageVO> getPageComponentInfo(@RequestBody ComponentInfoVO componentInfoVo){
     return CommonResult.success(componentInfoService.getPageComponent(componentInfoVo));
    }


    @PostMapping("/update-Or-Insert-Component-Info")
    @Operation(summary = "根据ComponentUniqueId是否存在判断是插入还是修改项目资源信息")
    @PreAuthorize("@ss.hasPermission('dimensions:component:updateOrInsert')")
    public CommonResult<Boolean> updateOrInsertComponentInfo(@RequestBody ComponentInfo componentInfo) {
        componentInfo.setAppId(Long.valueOf(componentInfo.getProjectId()));
        if(componentInfo.getComponentUniqueId() != null){
            return CommonResult.success(componentInfoService.saveOrUpdate(componentInfo,new LambdaQueryWrapper<ComponentInfo>()
                    .eq(ComponentInfo::getComponentUniqueId, componentInfo.getComponentUniqueId())));
        }
        throw WxBusinessException.of(ErrorCodeConstants.COMPONENT_UUID_IS_NULL);
    }

    @GetMapping("/delete-Component-Info")
    @Operation(summary = "根据componentUniqueId和项目Id删除组件信息")
    @PreAuthorize("@ss.hasPermission('dimensions:component:delete')")
    public CommonResult<Integer> deleteComponentInfo(String componentUniqueId,String appId) {
        if (componentUniqueId != null ) {
            return CommonResult.success(componentInfoService.getBaseMapper().delete(new LambdaQueryWrapper<ComponentInfo>()
                    .eq(ComponentInfo::getComponentUniqueId, componentUniqueId)
                    .eq(ComponentInfo::getProjectId,Integer.parseInt(appId))));
        }
        throw WxBusinessException.of(ErrorCodeConstants.COMPONENT_UUID_IS_NULL);
    }
}
