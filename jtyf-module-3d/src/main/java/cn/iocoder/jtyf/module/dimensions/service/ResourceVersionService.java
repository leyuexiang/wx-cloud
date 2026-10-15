package cn.iocoder.jtyf.module.dimensions.service;

import cn.iocoder.jtyf.module.dimensions.entity.ResourceVersion;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ResourceVersionService extends IService<ResourceVersion> {

    String deleteVersion(Integer id);
}
