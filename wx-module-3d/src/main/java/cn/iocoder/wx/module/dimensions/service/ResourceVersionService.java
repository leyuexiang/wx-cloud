package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.entity.ResourceVersion;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ResourceVersionService extends IService<ResourceVersion> {

    String deleteVersion(Integer id);
}
