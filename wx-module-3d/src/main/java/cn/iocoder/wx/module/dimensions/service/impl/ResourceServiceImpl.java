package cn.iocoder.wx.module.dimensions.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.wx.framework.common.exception.WxBusinessException;
import cn.iocoder.wx.module.dimensions.domain.dto.ModelSceneCountDTO;
import cn.iocoder.wx.module.dimensions.domain.vo.ProjectResourceFindVO;
import cn.iocoder.wx.module.dimensions.domain.vo.ProjectResourcePageVO;
import cn.iocoder.wx.module.dimensions.entity.Resource;
import cn.iocoder.wx.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.wx.module.dimensions.mapper.ResourceMapper;
import cn.iocoder.wx.module.dimensions.service.ResourceService;
import cn.iocoder.wx.module.dimensions.utils.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ResourceServiceImpl extends ServiceImpl<ResourceMapper, Resource> implements ResourceService {


    @Value("${server-path}")
    private String serverPath;

    @Override
    public ProjectResourcePageVO getByParam(ProjectResourceFindVO projectResourceFindVo) {
        int page = projectResourceFindVo.getPage()==null ? 1  : projectResourceFindVo.getPage();
        int size = projectResourceFindVo.getSize()==null ? 10 : projectResourceFindVo.getSize();

        Page<Resource> objectPage = new Page<>(page, size);
        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<Resource>()
                .like(StrUtil.isNotBlank(projectResourceFindVo.getResourceName()), Resource::getResourceName, projectResourceFindVo.getResourceName())
                .like(StrUtil.isNotBlank(projectResourceFindVo.getRootDirName()), Resource::getRootDirName, projectResourceFindVo.getRootDirName())
                .eq(projectResourceFindVo.getIsCollection() != null, Resource::getIsCollection, projectResourceFindVo.getIsCollection())
                .eq(projectResourceFindVo.getResourceDataTypeId() != null, Resource::getResourceDataTypeId, projectResourceFindVo.getResourceDataTypeId())
                .eq(projectResourceFindVo.getTypeId() != null, Resource::getTypeId, projectResourceFindVo.getTypeId())
                .eq(projectResourceFindVo.getArtResType() != null, Resource::getArtResType,projectResourceFindVo.getArtResType());

        if(projectResourceFindVo.getSort()!=null){
            switch (projectResourceFindVo.getSort()){
                case 1: queryWrapper.orderBy(true, true, Resource::getUpdateTime);
                break;
                case 2: queryWrapper.orderBy(true, false, Resource::getUpdateTime);
                break;
            }
        }

        Page<Resource> pageData = page(objectPage, queryWrapper);
        ProjectResourcePageVO projectResourcePageVo = new ProjectResourcePageVO();
        projectResourcePageVo.setProjectResourceList(pageData.getRecords());
        projectResourcePageVo.setPageMax((int) pageData.getPages());
        projectResourcePageVo.setCountMax((int) pageData.getTotal());
        return projectResourcePageVo;
    }

    @Override
    public Boolean judgeResourceName(Integer id, String resourceName) {
        List<Resource> list = this.baseMapper.selectList(new LambdaQueryWrapper<Resource>()
                .eq(Resource::getResourceName,resourceName));
        //除了在数据库中找到重名的，但不包括自己以外，都不需要校验。
        if(id != null && list.size() != 0){
                if(!Objects.equals(list.get(0).getId(), id)){
                    return Boolean.TRUE;
                }else {
                    return Boolean.FALSE;
                }
        }else{
            if(id == null && list.size()>0){
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
    }

    @Override
    public String createprojectResource(Resource resource) {
        String newPreviewOutPath =  serverPath + "/file-info/download-resource?uploadPath=/home/app/flask-file-server-master/tmp/SZL-3D-1/"+ resource.getPreviewOutPath();
        if (resource.getUniqueId() != null) {
            List<Resource> list = this.baseMapper.selectList(
                    new LambdaQueryWrapper<Resource>().eq(
                            Resource::getUniqueId, resource.getUniqueId()
                    ));
            if (list.isEmpty()) {
                resource.setIsCollection(0);
                resource.setUploadTime(LocalDateTime.now());
                if (resource.getAssetName() != null) {
                    resource.setResourceName(resource.getAssetName());
                }
                resource.setUploadUserName(SecurityUtil.getUsername());
                resource.setPreviewOutPath(newPreviewOutPath);
                resource.setUploadUserId(123);
                this.save(resource);
                return "数据库中UniqueId不存在,执行插入操作,并更新当前时间";
            }
            resource.setUpdateTime(LocalDateTime.now());
            resource.setPreviewOutPath(newPreviewOutPath);
            this.update(resource, new LambdaQueryWrapper<Resource>().eq(
                    Resource::getUniqueId, resource.getUniqueId()
            ));
            return "数据库中UniqueId存在,执行更新操作,并更新当前时间";
        }
        throw WxBusinessException.of(ErrorCodeConstants.RESOURCE_UNIQUE_ID_IS_NULL);
    }

    @Override
    public ModelSceneCountDTO getModelSceneCount() {
        List<Resource> modelList = this.baseMapper.selectList(new LambdaQueryWrapper<Resource>()
                .eq(Resource::getArtResType, "model"));
        List<Resource> sceneList = this.baseMapper.selectList(new LambdaQueryWrapper<Resource>()
                .eq(Resource::getArtResType, "scene"));
        int modelSize = modelList.size();
        int sceneSize = sceneList.size();
        ModelSceneCountDTO modelSceneCount = new ModelSceneCountDTO();
        modelSceneCount.setModelSize(modelSize);
        modelSceneCount.setSceneSize(sceneSize);
        return modelSceneCount;
    }

}

