package cn.iocoder.jtyf.module.dimensions.service;

import cn.iocoder.jtyf.module.dimensions.entity.ResourceType;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ResourceTypeService extends IService<ResourceType> {

    String deleteType(Integer id);

    String createType(ResourceType resourceType);

    Integer updateType(ResourceType resourceType);

    boolean checkTypeNameExists(String typeName);

    boolean checkTypeCodeExists(String typeCode);
}
