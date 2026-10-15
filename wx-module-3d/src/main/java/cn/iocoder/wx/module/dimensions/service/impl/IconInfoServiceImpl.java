package cn.iocoder.wx.module.dimensions.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.wx.framework.common.exception.WxBusinessException;
import cn.iocoder.wx.module.dimensions.domain.vo.ProjectIconInfoFindVO;
import cn.iocoder.wx.module.dimensions.domain.vo.ProjectIconInfoPageVO;
import cn.iocoder.wx.module.dimensions.entity.IconInfo;
import cn.iocoder.wx.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.wx.module.dimensions.mapper.IconInfoMapper;
import cn.iocoder.wx.module.dimensions.service.IconInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class IconInfoServiceImpl extends ServiceImpl<IconInfoMapper, IconInfo> implements IconInfoService {

    @Override
    public Boolean judgeUniqueId(Integer id, String uniqueId) {
        List<IconInfo> list = lambdaQuery().eq(IconInfo::getUniqueId, uniqueId).list();
        return getBooleanCommonResult(id, list);
    }

    @Override
    public Boolean judgeIconName(String resFromPlatform, Integer id, String iconName) {
        List<IconInfo> list = lambdaQuery().eq(IconInfo::getIconName, iconName)
                .eq(IconInfo::getResFromPlatform, resFromPlatform).list();
        return getBooleanCommonResult(id, list);
    }

    private Boolean getBooleanCommonResult(Integer id, List<IconInfo> list) {
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
    public ProjectIconInfoPageVO getByParam(ProjectIconInfoFindVO projectIconInfoFindVO) {
        int page = projectIconInfoFindVO.getPage() == null ? 1 : projectIconInfoFindVO.getPage();
        int size = projectIconInfoFindVO.getSize() == null ? 10 : projectIconInfoFindVO.getSize();

        Page<IconInfo> objectPage = new Page<>(page, size);
        LambdaQueryWrapper<IconInfo> queryWrapper = new LambdaQueryWrapper<IconInfo>()
                .like(StrUtil.isNotBlank(projectIconInfoFindVO.getIconName()), IconInfo::getIconName, projectIconInfoFindVO.getIconName())
                .eq(projectIconInfoFindVO.getIsCollection() != null, IconInfo::getIsCollection, projectIconInfoFindVO.getIsCollection())
                .eq(projectIconInfoFindVO.getTypeId() != null, IconInfo::getTypeId, projectIconInfoFindVO.getTypeId());

        if (projectIconInfoFindVO.getResFromPlatform() != null) {
            if ("U3D_ALL".equals(projectIconInfoFindVO.getResFromPlatform())) {
                queryWrapper.in(IconInfo::getResFromPlatform, "U3D_WEB", "U3D");
            } else {
                queryWrapper.eq(IconInfo::getResFromPlatform, projectIconInfoFindVO.getResFromPlatform());
            }
        }

        if (projectIconInfoFindVO.getSort() != null) {
            switch (projectIconInfoFindVO.getSort()) {
                case 1:
                    queryWrapper.orderBy(true, true, IconInfo::getUpdateTime);
                    break;
                case 2:
                    queryWrapper.orderBy(true, false, IconInfo::getUpdateTime);
                    break;
            }
        }

        Page<IconInfo> pageData = page(objectPage, queryWrapper);
        ProjectIconInfoPageVO projectIconInfoPageVO = new ProjectIconInfoPageVO();
        projectIconInfoPageVO.setProjectIconInfoList(pageData.getRecords());
        projectIconInfoPageVO.setPageMax((int) pageData.getPages());
        projectIconInfoPageVO.setCountMax((int) pageData.getTotal());
        return projectIconInfoPageVO;
    }

    @Override
    public String createProjectIcon(IconInfo iconInfo) {
        if (iconInfo.getUniqueId() != null) {
            List<IconInfo> list = this.baseMapper.selectList(
                    new LambdaQueryWrapper<IconInfo>().eq(
                            IconInfo::getUniqueId, iconInfo.getUniqueId()
                    ));
            if (list.isEmpty()) {
                iconInfo.setCreateTime(LocalDateTime.now());
                this.save(iconInfo);
                return "数据库中UniqueId不存在,执行插入操作,并更新当前时间";
            }
            iconInfo.setUpdateTime(LocalDateTime.now());
            this.update(iconInfo, new LambdaQueryWrapper<IconInfo>().eq(
                    IconInfo::getUniqueId, iconInfo.getUniqueId()
            ));
            return "数据库中UniqueId存在,执行更新操作,并更新当前时间";
        }
        throw WxBusinessException.of(ErrorCodeConstants.ICON_INFO_UNIQUE_ID_IS_NULL);
    }
}

