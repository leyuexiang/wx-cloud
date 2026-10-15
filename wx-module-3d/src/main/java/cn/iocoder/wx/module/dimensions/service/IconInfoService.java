package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.domain.vo.ProjectIconInfoFindVO;
import cn.iocoder.wx.module.dimensions.domain.vo.ProjectIconInfoPageVO;
import cn.iocoder.wx.module.dimensions.entity.IconInfo;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IconInfoService extends IService<IconInfo> {

    Boolean judgeUniqueId(Integer id, String uniqueId);

    Boolean judgeIconName(String resFromPlatform, Integer id, String iconName);

    ProjectIconInfoPageVO getByParam(ProjectIconInfoFindVO projectIconInfoFindVO);

    String createProjectIcon(IconInfo icon);

}
