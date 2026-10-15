package cn.iocoder.wx.module.dimensions.controller.admin;


import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.ListResult;
import cn.iocoder.wx.module.dimensions.domain.vo.*;
import cn.iocoder.wx.module.dimensions.entity.Engineering;
import cn.iocoder.wx.module.dimensions.service.EngineeringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;


@RestController
@RequestMapping("/dimensions/project-engineering")
@Slf4j
@Tag(name = "项目场景信息相关接口")
public class EngineeringController {

    @Resource
    private EngineeringService engineeringService;

    @PostMapping("/new-scene")
    @Operation(summary = "新建场景")
    @PreAuthorize("@ss.hasPermission('dimensions:engineering:create')")
    public CommonResult<?> createScene(@RequestParam String platformType, @RequestParam String name, @RequestParam Integer isPublish) {

        Engineering engineering = new Engineering();
        engineering.setPlatformType(platformType);
        engineering.setName(name);
        engineering.setIsPublish(isPublish);
        Integer state = engineeringService.create(engineering);
        if(state==2){
            return CommonResult.success(true);
        }else if(state==0){
            return CommonResult.error(500,"未传项目名");
        }else if(state==1){
            return CommonResult.error(500,"程序报错，请联系后端");
        }else if(state==3){
            return CommonResult.error(500,"文件夹创建失败");
        }else{
            return CommonResult.error(500,"已存在同名文件夹或者同名项目，请核实");
        }

    }

    @GetMapping("/get-isPublish-list")
    @PreAuthorize("@ss.hasPermission('dimensions:engineering:queryPublish')")
    @Operation(summary = "查询已发布场景")
    public ListResult<IsPublishVO> getIsPublishList() {
        return ListResult.success(engineeringService.getIsPublishList());
    }

    // 无需权限
    @GetMapping("/get-scene-info")
    @Operation(summary = "查询场景信息")
    public ListResult<ProjectEngineeringVO> getSceneInfo(@RequestParam String platformType, Integer id) {
        return ListResult.success(engineeringService.getSceneList(platformType, null, id, null, null, null, null, null, null, null));
    }

    @GetMapping("/get-scene-list")
    @PreAuthorize("@ss.hasPermission('dimensions:engineering:query')")
    @Operation(summary = "查询场景列表")
    public ListResult<ProjectEngineeringVO> getSceneList(@RequestParam String platformType,
                                                          String sort,
                                                          Integer id,

                                                          String project,
                                                          String name,
                                                          String nodes,
                                                          String comsInstance,
                                                          String type,
                                                          Integer isPublish,
                                                          String findType
    ) {
       return ListResult.success(engineeringService.getSceneList(platformType,sort,id,project,name,nodes, comsInstance,type,isPublish,findType));
    }

    @PostMapping("/delete-scene")
    @Operation(summary = "删除场景")
    @PreAuthorize("@ss.hasPermission('dimensions:engineering:delete')")
    public CommonResult<Boolean> delete(@RequestBody ProjectEngineeringDeleteVO projectEngineeringDeleteVo) {
        engineeringService.removeById(projectEngineeringDeleteVo.getId());
        return CommonResult.success(Boolean.TRUE);
    }

//    @PostMapping("/set-collection")
//    @Operation(summary = "收藏设置")
//    public CommonResult<?> setCollection(@RequestBody ProjectEngineeringCollectionVO projectEngineeringCollectionVo) {
//        engineeringService.setCollection(projectEngineeringCollectionVo);
//        return CommonResult.success(true);
//    }

    @PostMapping("/set-publish")
    @Operation(summary = "发布设置")
    @PreAuthorize("@ss.hasPermission('dimensions:engineering:publish')")
    public CommonResult<Boolean> setPublish(@RequestBody ProjectEngineeringPublishVO projectEngineeringPublishVo) {
        engineeringService.setPublish(projectEngineeringPublishVo);
        return CommonResult.success(Boolean.TRUE);
    }

    @PostMapping("/update-scene-info")
    @Operation(summary = "修改场景")
    @PreAuthorize("@ss.hasPermission('dimensions:engineering:change')")
    public CommonResult<Boolean> update(@RequestBody ProjectEngineeringCreateVO projectEngineeringCreateVo) {
        engineeringService.change(projectEngineeringCreateVo);
        return CommonResult.success(Boolean.TRUE);
    }


    @PostMapping("/update-web-scene-info")
    @Operation(summary = "前端修改场景")
    @PreAuthorize("@ss.hasPermission('dimensions:engineering:update')")
    public CommonResult<Boolean> updateWebSceneInfo(@RequestBody ProjectEngineeringUpdateVO projectEngineeringUpdateVO) {
        return CommonResult.success(engineeringService.updateWebSceneInfo(projectEngineeringUpdateVO));
    }


    @PostMapping("/update-scene-name")
    @Operation(summary = "修改场景名称")
    @PreAuthorize("@ss.hasPermission('dimensions:engineering:updateSceneName')")
    public CommonResult<String> updateSceneName(Integer id, String name) {
        if (id == null || name == null) {
            return CommonResult.error(500,"id和name都需传");
        }
        Integer status =  engineeringService.setName(id,name);
        if(status == 1){
            return CommonResult.success("此id未找到projectEngineering信息");
        }
        return CommonResult.success("已修改场景名称");
    }

    @GetMapping("/copy-scene/{id}")
    @Operation(summary = "复制场景并修改名称")
    @PreAuthorize("@ss.hasPermission('dimensions:engineering:copy')")
    public CommonResult<Engineering> copyScene(@PathVariable("id") String id) {
        return CommonResult.success(engineeringService.copyScene(id));
    }


    @PostMapping("/upload-update")
    @PreAuthorize("@ss.hasPermission('dimensions:engineering:updateScene')")
    @Operation(summary = "上传场景或者编辑场景")
    public CommonResult<Integer> uploadOrUpdateScene(@RequestParam String platformType, @RequestPart(required = false,value = "jsonFile") MultipartFile jsonFile, Integer id, String name, String photoAddress) throws IOException {
        return CommonResult.success(engineeringService.uploadOrUpdateScene(platformType,jsonFile,id,name,photoAddress));
    }

    @PostMapping("/down-load")
    @Operation(summary = "单个或者批量下载场景")
    @PreAuthorize("@ss.hasPermission('dimensions:engineering:download')")
    public void downLoadScene(@RequestBody int[] ids, HttpServletResponse response){
        engineeringService.downLoadScene(ids,response);
    }

    @GetMapping("/judge-scene-name")
    @PreAuthorize("@ss.hasPermission('dimensions:engineering:judge')")
    @Operation(summary = "判断场景名称是否存在")
    public CommonResult<Boolean> judgeSceneName(@RequestParam String platformType, Integer id, String sceneName) {
        return CommonResult.success(engineeringService.judgeSceneName(platformType, id, sceneName));
    }
}




