package cn.iocoder.jtyf.module.dimensions.service;

import cn.iocoder.jtyf.module.dimensions.domain.vo.ProjectIconInfoFindVO;
import cn.iocoder.jtyf.module.dimensions.domain.vo.ProjectIconInfoPageVO;
import cn.iocoder.jtyf.module.dimensions.entity.IconInfo;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IconInfoService extends IService<IconInfo> {

    Boolean judgeUniqueId(Integer id, String uniqueId);

    Boolean judgeIconName(String resFromPlatform, Integer id, String iconName);

    ProjectIconInfoPageVO getByParam(ProjectIconInfoFindVO projectIconInfoFindVO);

    String createProjectIcon(IconInfo icon);

}
