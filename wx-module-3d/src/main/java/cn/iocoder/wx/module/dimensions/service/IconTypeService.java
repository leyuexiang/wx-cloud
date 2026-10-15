package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.entity.IconType;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IconTypeService extends IService<IconType> {

    String deleteType(Integer id);

    String createType(IconType iconType);

    Integer updateType(IconType iconType);

    boolean checkTypeNameExists(String typeName);

    boolean checkTypeCodeExists(String typeCode);
}
