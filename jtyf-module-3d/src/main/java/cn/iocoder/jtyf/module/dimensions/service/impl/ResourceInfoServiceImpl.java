package cn.iocoder.jtyf.module.dimensions.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.jtyf.framework.common.exception.JtyfBusinessException;
import cn.iocoder.jtyf.module.dimensions.domain.dto.ModelSceneCountDTO;
import cn.iocoder.jtyf.module.dimensions.domain.vo.ProjectResourceInfoFindVO;
import cn.iocoder.jtyf.module.dimensions.domain.vo.ProjectResourceInfoPageVO;
import cn.iocoder.jtyf.module.dimensions.entity.ResourceInfo;
import cn.iocoder.jtyf.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.jtyf.module.dimensions.mapper.ResourceInfoMapper;
import cn.iocoder.jtyf.module.dimensions.service.ResourceInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ResourceInfoServiceImpl extends ServiceImpl<ResourceInfoMapper, ResourceInfo> implements ResourceInfoService {


    @Override
    public Boolean judgeUniqueId(Integer id, String uniqueId) {
        List<ResourceInfo> list = this.baseMapper.selectList(new LambdaQueryWrapper<ResourceInfo>()
                .eq(ResourceInfo::getUniqueId, uniqueId));
        return getBooleanCommonResult(id, list);
    }

    @Override
    public Boolean judgeArtResName(Integer id, String artResName) {
        List<ResourceInfo> list = this.baseMapper.selectList(new LambdaQueryWrapper<ResourceInfo>()
                .eq(ResourceInfo::getArtResName, artResName));
        return getBooleanCommonResult(id, list);
    }

    @Override
    public Boolean judgeResourceName(String resFromPlatform, Integer id, String resourceName) {
        LambdaQueryWrapper<ResourceInfo> queryWrapper = new LambdaQueryWrapper<ResourceInfo>()
                .eq(ResourceInfo::getResourceName, resourceName)
                .eq(ResourceInfo::getResFromPlatform, resFromPlatform);

        if ("U3D_ALL".equals(resFromPlatform)) {
            queryWrapper.in(ResourceInfo::getResFromPlatform, "U3D_WEB", "U3D");
        } else {
            queryWrapper.eq(ResourceInfo::getResFromPlatform, resFromPlatform);
        }

        List<ResourceInfo> list = this.baseMapper.selectList(queryWrapper);
        return getBooleanCommonResult(id, list);
    }

    private Boolean getBooleanCommonResult(Integer id, List<ResourceInfo> list) {
        //除了在数据库中找到重名的，但不包括自己以外，都不需要校验。
        if (id != null && !list.isEmpty()) {
            if (!Objects.equals(list.get(0).getId(), id)) {
                return Boolean.TRUE;
            } else {
                return Boolean.FALSE;
            }
        } else {
            if (id == null && !list.isEmpty()) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
    }

    @Override
    public ProjectResourceInfoPageVO getByParam(ProjectResourceInfoFindVO projectResourceInfoFindVO) {
        int page = projectResourceInfoFindVO.getPage() == null ? 1 : projectResourceInfoFindVO.getPage();
        int size = projectResourceInfoFindVO.getSize() == null ? 10 : projectResourceInfoFindVO.getSize();

        Page<ResourceInfo> objectPage = new Page<>(page, size);
        LambdaQueryWrapper<ResourceInfo> queryWrapper = new LambdaQueryWrapper<ResourceInfo>()
                .like(StrUtil.isNotBlank(projectResourceInfoFindVO.getResourceName()), ResourceInfo::getResourceName, projectResourceInfoFindVO.getResourceName())
                .eq(projectResourceInfoFindVO.getIsCollection() != null, ResourceInfo::getIsCollection, projectResourceInfoFindVO.getIsCollection())
                .eq(projectResourceInfoFindVO.getTypeId() != null, ResourceInfo::getTypeId, projectResourceInfoFindVO.getTypeId())
                .eq(projectResourceInfoFindVO.getArtResType() != null, ResourceInfo::getArtResType, projectResourceInfoFindVO.getArtResType());

        if (projectResourceInfoFindVO.getResFromPlatform() != null) {
            if ("U3D_ALL".equals(projectResourceInfoFindVO.getResFromPlatform())) {
                queryWrapper.in(ResourceInfo::getResFromPlatform, "U3D_WEB", "U3D");
            } else {
                queryWrapper.eq(ResourceInfo::getResFromPlatform, projectResourceInfoFindVO.getResFromPlatform());
            }
        }

        if (projectResourceInfoFindVO.getSort() != null) {
            switch (projectResourceInfoFindVO.getSort()) {
                case 1:
                    queryWrapper.orderBy(true, true, ResourceInfo::getUpdateTime);
                    break;
                case 2:
                    queryWrapper.orderBy(true, false, ResourceInfo::getUpdateTime);
                    break;
            }
        }

        Page<ResourceInfo> pageData = page(objectPage, queryWrapper);
        ProjectResourceInfoPageVO projectResourceInfoPageVO = new ProjectResourceInfoPageVO();
        projectResourceInfoPageVO.setProjectResourceInfoList(pageData.getRecords());
        projectResourceInfoPageVO.setPageMax((int) pageData.getPages());
        projectResourceInfoPageVO.setCountMax((int) pageData.getTotal());
        return projectResourceInfoPageVO;
    }

    @Override
    public String createProjectResource(ResourceInfo resourceInfo) {
        if (resourceInfo.getUniqueId() != null) {
            List<ResourceInfo> list = this.baseMapper.selectList(
                    new LambdaQueryWrapper<ResourceInfo>().eq(
                            ResourceInfo::getUniqueId, resourceInfo.getUniqueId()
                    ));
            if (list.isEmpty()) {
                this.save(resourceInfo);
                return "数据库中UniqueId不存在,执行插入操作,并更新当前时间";
            }
            resourceInfo.setUpdateTime(LocalDateTime.now());
            this.update(resourceInfo, new LambdaQueryWrapper<ResourceInfo>().eq(
                    ResourceInfo::getUniqueId, resourceInfo.getUniqueId()
            ));
            return "数据库中UniqueId存在,执行更新操作,并更新当前时间";
        }
        throw JtyfBusinessException.of(ErrorCodeConstants.RESOURCE_UNIQUE_ID_IS_NULL);
    }

    @Override
    public ModelSceneCountDTO getModelSceneCount(String resFromPlatform) {
        LambdaQueryWrapper<ResourceInfo> modelLambdaQueryWrapper = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<ResourceInfo> sceneLambdaQueryWrapper = new LambdaQueryWrapper<>();
        modelLambdaQueryWrapper.eq(ResourceInfo::getArtResType, "model");
        sceneLambdaQueryWrapper.eq(ResourceInfo::getArtResType, "scene");

        if ("U3D_ALL".equals(resFromPlatform)) {
            modelLambdaQueryWrapper.in(ResourceInfo::getResFromPlatform, "U3D_WEB", "U3D");
            sceneLambdaQueryWrapper.in(ResourceInfo::getResFromPlatform, "U3D_WEB", "U3D");
        } else {
            modelLambdaQueryWrapper.eq(ResourceInfo::getResFromPlatform, resFromPlatform);
            sceneLambdaQueryWrapper.eq(ResourceInfo::getResFromPlatform, resFromPlatform);
        }

        List<ResourceInfo> modelList = this.baseMapper.selectList(modelLambdaQueryWrapper);
        List<ResourceInfo> sceneList = this.baseMapper.selectList(sceneLambdaQueryWrapper);
        int modelSize = modelList.size();
        int sceneSize = sceneList.size();
        ModelSceneCountDTO modelSceneCount = new ModelSceneCountDTO();
        modelSceneCount.setModelSize(modelSize);
        modelSceneCount.setSceneSize(sceneSize);
        return modelSceneCount;
    }

}

