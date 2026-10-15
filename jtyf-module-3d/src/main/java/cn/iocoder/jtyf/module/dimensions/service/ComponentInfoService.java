package cn.iocoder.jtyf.module.dimensions.service;

import cn.iocoder.jtyf.module.dimensions.domain.vo.ComponentInfoPageVO;
import cn.iocoder.jtyf.module.dimensions.domain.vo.ComponentInfoVO;
import cn.iocoder.jtyf.module.dimensions.entity.ComponentInfo;
import com.baomidou.mybatisplus.extension.service.IService;


public interface ComponentInfoService extends IService<ComponentInfo> {

    ComponentInfoPageVO getPageComponent(ComponentInfoVO componentInfoVo);
}
