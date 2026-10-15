package cn.iocoder.wx.module.dimensions.service.impl;

import cn.iocoder.wx.framework.common.exception.WxBusinessException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.entity.VirtualCamera;
import cn.iocoder.wx.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.wx.module.dimensions.mapper.VirtualCameraMapper;
import cn.iocoder.wx.module.dimensions.service.VirtualCameraService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class VirtualCameraServiceImpl extends ServiceImpl<VirtualCameraMapper, VirtualCamera> implements VirtualCameraService {

    @Override
    public List<VirtualCamera> getVirtualCameraListBySceneId(Integer sceneId) {
        List<VirtualCamera> virtualCameraList = this.baseMapper.selectList(new LambdaQueryWrapper<VirtualCamera>()
                .eq(VirtualCamera::getSceneId, sceneId));
        if(virtualCameraList.isEmpty()){
            throw WxBusinessException.of(ErrorCodeConstants.SCENE_ID_DATABASE_NOT_EXIST, sceneId);
        }
        return virtualCameraList;
    }

    @Override
    public VirtualCamera getVirtualCamera(Integer virtualCameraId) {
        VirtualCamera virtualCamera = this.baseMapper.selectOne(new LambdaQueryWrapper<VirtualCamera>()
                .eq(VirtualCamera::getId, virtualCameraId));
        if(virtualCamera == null) {
            throw WxBusinessException.of(ErrorCodeConstants.VIRTUAL_CAMERA_ID_DATABASE_NOT_EXIST, virtualCameraId);
        }
        return virtualCamera;
    }

    @Override
    public String insertVirtualCamera(VirtualCamera virtualCamera) {
        if(virtualCamera.getSceneId() == null || virtualCamera.getSceneId() == 0) {
            throw WxBusinessException.of(ErrorCodeConstants.SCENE_ID_IS_NULL);
        }

        virtualCamera.setCreateTime(LocalDateTime.now());
        virtualCamera.setUpdateTime(LocalDateTime.now());
        if (virtualCamera.getId() != null && virtualCamera.getId() > 0) {
            VirtualCamera virtualCamera1 = this.baseMapper.selectOne(new LambdaQueryWrapper<VirtualCamera>()
                    .eq(VirtualCamera::getId, virtualCamera.getId()));
            if (virtualCamera1 != null) {
                virtualCamera.setCreateTime(null);
                this.baseMapper.updateById(virtualCamera);
                return "更新成功";
            }
            throw WxBusinessException.of(ErrorCodeConstants.ID_DATABASE_NOT_EXIST, virtualCamera.getId());
        }
        virtualCamera.setId(null);
        this.baseMapper.insert(virtualCamera);
        return "添加成功";
    }

    @Override
    public CommonResult<?> batchInsertVirtualCamera(Integer sceneId, List<VirtualCamera> virtualCameras) {
        // 参数校验
        if (virtualCameras == null || virtualCameras.isEmpty()) {
            throw WxBusinessException.of(ErrorCodeConstants.VIRTUAL_CAMERA_IS_NULL);
        }

        // 记录成功和失败的结果
        List<CommonResult<String>> successResults = new ArrayList<>();
        List<CommonResult<String>> failResults = new ArrayList<>();

        // 遍历插入每一个相机
        for (VirtualCamera virtualCamera : virtualCameras) {
            virtualCamera.setSceneId(sceneId);
            String result;
            try {
                // 根据结果分类
                result = this.insertVirtualCamera(virtualCamera);
                successResults.add(CommonResult.success(result));
            } catch (WxBusinessException e) {
                failResults.add(CommonResult.error(e.getCode(), e.getMessage()));
            }
        }

        // 构建返回结果
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("successCount", successResults.size());
        resultData.put("failCount", failResults.size());
        resultData.put("successResults", successResults);
        resultData.put("failResults", failResults);

        // 根据成功和失败情况返回不同的结果
        if (failResults.isEmpty()) {
            // 全部成功
            return CommonResult.success( "批量插入全部成功");
        } else if (successResults.isEmpty()) {
            // 全部失败
            throw WxBusinessException.of(ErrorCodeConstants.BATCH_INSERT_ALL_ERROR);
        } else {
            // 部分成功，部分失败
            return CommonResult.success(resultData);
        }
    }


    @Override
    public String deleteVirtualCamera(Integer sceneId, Integer virtualCameraId) {
        //如果没有传入virtualCameraId，那么删除该sceneId下的所有virtualCamera
        if (virtualCameraId == null || virtualCameraId == 0) {
            this.baseMapper.delete(new LambdaQueryWrapper<VirtualCamera>().eq(VirtualCamera::getSceneId, sceneId));
        }

        VirtualCamera virtualCamera1 = this.baseMapper.selectOne(new LambdaQueryWrapper<VirtualCamera>().eq(VirtualCamera::getId,virtualCameraId));
        if(virtualCamera1 != null){
            this.baseMapper.deleteById(virtualCamera1);
            return "删除完成！";
        }
        throw WxBusinessException.of(ErrorCodeConstants.VIRTUAL_CAMERA_ID_DATABASE_NOT_EXIST, virtualCameraId);
    }
}
