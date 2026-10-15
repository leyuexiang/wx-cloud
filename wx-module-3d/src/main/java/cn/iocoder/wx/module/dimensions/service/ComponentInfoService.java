package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.domain.vo.ComponentInfoPageVO;
import cn.iocoder.wx.module.dimensions.domain.vo.ComponentInfoVO;
import cn.iocoder.wx.module.dimensions.entity.ComponentInfo;
import com.baomidou.mybatisplus.extension.service.IService;


public interface ComponentInfoService extends IService<ComponentInfo> {

    ComponentInfoPageVO getPageComponent(ComponentInfoVO componentInfoVo);
}
