package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.entity.VirtualCamera;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface VirtualCameraService extends IService<VirtualCamera> {

    List<VirtualCamera> getVirtualCameraListBySceneId(Integer sceneId);

    VirtualCamera getVirtualCamera(Integer virtualCameraId);

    String insertVirtualCamera(VirtualCamera virtualCamera);

    CommonResult<?> batchInsertVirtualCamera(Integer sceneId, List<VirtualCamera> virtualCameras);

    String deleteVirtualCamera(Integer sceneId, Integer virtualCameraId);
}
