package cn.iocoder.wx.module.dimensions.service.impl;


import cn.iocoder.wx.module.dimensions.domain.vo.ComponentInfoPageVO;
import cn.iocoder.wx.module.dimensions.domain.vo.ComponentInfoVO;
import cn.iocoder.wx.module.dimensions.entity.ComponentInfo;
import cn.iocoder.wx.module.dimensions.mapper.ComponentInfoMapper;
import cn.iocoder.wx.module.dimensions.service.ComponentInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ComponentInfoServiceImpl extends ServiceImpl<ComponentInfoMapper, ComponentInfo> implements ComponentInfoService {


    @Override
    public ComponentInfoPageVO getPageComponent(ComponentInfoVO componentInfoVo) {
        int page = componentInfoVo.getPage() == null ? 1: componentInfoVo.getPage();
        int size = componentInfoVo.getSize() == null ? 10:componentInfoVo.getSize();

        Page<ComponentInfo> objectPage = new Page<>(page, size);
        LambdaQueryWrapper<ComponentInfo> queryWrapper = new LambdaQueryWrapper<>();

        if((componentInfoVo.getComponentName() == null || componentInfoVo.getComponentName().equals("")) && componentInfoVo.getComponentType() == null){
            log.info("查询全部");
        }else {
            queryWrapper = queryWrapper
                    .like(componentInfoVo.getComponentName() != null, ComponentInfo::getComponentName,componentInfoVo.getComponentName())
                    .eq(componentInfoVo.getComponentType() != null, ComponentInfo::getComponentType, componentInfoVo.getComponentType());
        }
        Page<ComponentInfo> pageData = page(objectPage, queryWrapper);
        ComponentInfoPageVO componentInfoPageVo = new ComponentInfoPageVO();
        componentInfoPageVo.setPageMax(pageData.getPages());
        componentInfoPageVo.setCountMax(pageData.getTotal());
        componentInfoPageVo.setComponentInfoList(pageData.getRecords());
        return componentInfoPageVo;
    }
}