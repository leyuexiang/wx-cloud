package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.ListResult;
import cn.iocoder.wx.module.dimensions.entity.VirtualCamera;
import cn.iocoder.wx.module.dimensions.service.VirtualCameraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/dimensions/virtual-camera")
@Slf4j
@Tag(name = "虚拟相机接口",description = "虚拟相机接口")
public class VirtualCameraController {

    @Autowired
    VirtualCameraService virtualCameraService;


    @PostMapping("/insert-update-virtual-camera")
    @Operation(summary = "插入或者更改虚拟相机",description = "插入或者更改虚拟相机")
    @PreAuthorize("@ss.hasPermission('dimensions:virtualCamera:insertTaskEvent')")
    public CommonResult<String> insertTaskEvent(@RequestBody VirtualCamera virtualCamera) {
        return CommonResult.success(virtualCameraService.insertVirtualCamera(virtualCamera));
    }

    @PostMapping("/batch-insert-update-virtual-camera")
    @Operation(summary = "批量插入或者更改虚拟相机")
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("@ss.hasPermission('dimensions:virtualCamera:batchInsertVirtualCamera')")
    public CommonResult<?> batchInsertVirtualCamera(@RequestParam Integer sceneId, @RequestBody List<VirtualCamera> virtualCameras) {
        return virtualCameraService.batchInsertVirtualCamera(sceneId, virtualCameras);
    }

    @DeleteMapping("/delete-virtual-camera")
    @Operation(summary = "删除虚拟相机")
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("@ss.hasPermission('dimensions:virtualCamera:deleteVirtualCamera')")
    public CommonResult<String> deleteVirtualCamera(@RequestParam Integer sceneId, @RequestParam Integer virtualCameraId) {
        return CommonResult.success(virtualCameraService.deleteVirtualCamera(sceneId, virtualCameraId));
    }

    @GetMapping("/get-virtual-camera-sceneId")
    @Operation(summary = "根据sceneId获取虚拟相机列表")
    @PreAuthorize("@ss.hasPermission('dimensions:virtualCamera:getVirtualCameraListBySceneId')")
    public ListResult<VirtualCamera> getVirtualCameraListBySceneId(@RequestParam Integer sceneId) {
        return ListResult.success(virtualCameraService.getVirtualCameraListBySceneId(sceneId));
    }


    @GetMapping("/get-virtual-camera")
    @Operation(summary = "根据id获取虚拟相机详情")
    @PreAuthorize("@ss.hasPermission('dimensions:virtualCamera:getVirtualCamera')")
    public CommonResult<VirtualCamera> getVirtualCamera(@RequestParam Integer virtualCameraId) {
        return CommonResult.success(virtualCameraService.getVirtualCamera(virtualCameraId));
    }

    @GetMapping("/get-all-virtual-camera")
    @Operation(summary = "获取所有虚拟相机列表")
    @PreAuthorize("@ss.hasPermission('dimensions:virtualCamera:getAllVirtualCamera')")
    public CommonResult<List<VirtualCamera>> getAllVirtualCamera() {
        return CommonResult.success(virtualCameraService.list());
    }


}
