package cn.iocoder.jtyf.module.dimensions.service;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.module.dimensions.domain.vo.LabelVo;
import cn.iocoder.jtyf.module.dimensions.entity.AssociationObject;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AssociationObjectService extends IService<AssociationObject> {

    List<AssociationObject> getLabel(String tableName);

    List<String> getDeviceId(String[][] label);

    CommonResult<?> getDeviceInfos(String type);

    CommonResult<?> getDeviceStatus(String type);

    List<AssociationObject> getNewLabel(String typeOrSpace);

    List<String> getDeviceIdByValue(LabelVo labelVo);

}
