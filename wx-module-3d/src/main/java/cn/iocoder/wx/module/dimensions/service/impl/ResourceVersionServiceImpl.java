package cn.iocoder.wx.module.dimensions.service.impl;

import cn.iocoder.wx.framework.common.exception.WxBusinessException;
import cn.iocoder.wx.module.dimensions.entity.ResourceInfo;
import cn.iocoder.wx.module.dimensions.entity.ResourceVersion;
import cn.iocoder.wx.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.wx.module.dimensions.mapper.ResourceVersionMapper;
import cn.iocoder.wx.module.dimensions.service.ResourceInfoService;
import cn.iocoder.wx.module.dimensions.service.ResourceVersionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class ResourceVersionServiceImpl extends ServiceImpl<ResourceVersionMapper, ResourceVersion> implements ResourceVersionService {

    @jakarta.annotation.Resource
    ResourceInfoService resourceInfoService;


    @Override
    public String deleteVersion(Integer id) {
        if (id != null) {
            //先刪除这个分类
            removeById(id);
            //将这个分类下的资源的typeId置null
            List<ResourceInfo> resourceInfoList = resourceInfoService.list(new LambdaQueryWrapper<ResourceInfo>()
                    .eq(ResourceInfo::getArtResVersionId, id));
            for (ResourceInfo resourceInfo : resourceInfoList) {
                resourceInfo.setArtResVersionId(null);
                resourceInfoService.updateById(resourceInfo);
            }
            return "已删除对应资源且已重置versionId";
        }
        throw WxBusinessException.of(ErrorCodeConstants.TABLE_ID_IS_NULL);
    }
}

