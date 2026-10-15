package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.domain.dto.ModelSceneCountDTO;
import cn.iocoder.wx.module.dimensions.domain.vo.ProjectResourceInfoFindVO;
import cn.iocoder.wx.module.dimensions.domain.vo.ProjectResourceInfoPageVO;
import cn.iocoder.wx.module.dimensions.entity.ResourceInfo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ResourceInfoService extends IService<ResourceInfo> {

    Boolean judgeUniqueId(Integer id, String uniqueId);

    Boolean judgeArtResName(Integer id, String artResName);

    Boolean judgeResourceName(String resFromPlatform, Integer id, String resourceName);

    ProjectResourceInfoPageVO getByParam(ProjectResourceInfoFindVO projectResourceInfoFindVO);

    String createProjectResource(ResourceInfo resource);

    ModelSceneCountDTO getModelSceneCount(String resFromPlatform);

}

