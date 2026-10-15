package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.domain.dto.ModelSceneCountDTO;
import cn.iocoder.wx.module.dimensions.domain.vo.ProjectResourceFindVO;
import cn.iocoder.wx.module.dimensions.domain.vo.ProjectResourcePageVO;
import cn.iocoder.wx.module.dimensions.entity.Resource;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ResourceService extends IService<Resource> {

    ProjectResourcePageVO getByParam(ProjectResourceFindVO projectResourceFindVo);

    Boolean judgeResourceName(Integer id, String resourceName);

    String createprojectResource(Resource resource);

    ModelSceneCountDTO getModelSceneCount();

}

