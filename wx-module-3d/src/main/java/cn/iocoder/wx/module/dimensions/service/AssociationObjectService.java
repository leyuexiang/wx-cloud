package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.domain.vo.LabelVo;
import cn.iocoder.wx.module.dimensions.entity.AssociationObject;
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
