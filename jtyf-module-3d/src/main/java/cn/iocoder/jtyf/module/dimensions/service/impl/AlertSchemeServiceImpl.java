package cn.iocoder.jtyf.module.dimensions.service.impl;

import cn.iocoder.jtyf.framework.common.exception.JtyfBusinessException;
import cn.iocoder.jtyf.module.dimensions.domain.vo.AlertSchemePage;
import cn.iocoder.jtyf.module.dimensions.domain.vo.AlertSchemePageVO;
import cn.iocoder.jtyf.module.dimensions.entity.AlertScheme;
import cn.iocoder.jtyf.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.jtyf.module.dimensions.mapper.AlertSchemeMapper;
import cn.iocoder.jtyf.module.dimensions.service.AlertSchemeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AlertSchemeServiceImpl extends ServiceImpl<AlertSchemeMapper, AlertScheme> implements AlertSchemeService {
    @Override
    public List<AlertScheme> getAlertSchemeList(String[] uniqueIds) {
        if (uniqueIds.length == 0) {
            return Collections.emptyList();
        }
        List<AlertScheme> alertSchemeList = lambdaQuery().in(AlertScheme::getUniqueId, List.of(uniqueIds)).list();
        if (uniqueIds.length != alertSchemeList.size()) {
            throw JtyfBusinessException.of(ErrorCodeConstants.UUID_NOT_EXIST_PLEASE_UPLOAD, List.of(uniqueIds));
        }
        return alertSchemeList;
    }

    @Override
    public AlertSchemePageVO getAlertSchemePage(AlertSchemePage alertSchemePage) {
        int page = alertSchemePage.getPage()==null ? 1:alertSchemePage.getPage();
        int size = alertSchemePage.getSize()==null ? 10:alertSchemePage.getSize();
        Page<AlertScheme> objectPage = new Page<>(page, size);

        LambdaQueryWrapper<AlertScheme> queryWrapper = new LambdaQueryWrapper<>();
        if (alertSchemePage.getSchemeName()!=null) {
            queryWrapper.like(AlertScheme::getSchemeName, alertSchemePage.getSchemeName());
        }

        Page<AlertScheme> pageData = page(objectPage, queryWrapper);
        AlertSchemePageVO alertSchemePageVo = new AlertSchemePageVO();
        alertSchemePageVo.setPageMax((int) pageData.getPages());
        alertSchemePageVo.setCountMax((int) pageData.getTotal());
        alertSchemePageVo.setAlertSchemeList(pageData.getRecords());
        return alertSchemePageVo;
    }

    @Override
    public String insertAlertScheme(AlertScheme alertScheme) {
        if (alertScheme.getUniqueId() != null && !alertScheme.getUniqueId().equals("") && !alertScheme.getId().equals(0)) {
            AlertScheme alertScheme1 = this.baseMapper.selectOne(new LambdaQueryWrapper<AlertScheme>()
                    .eq(AlertScheme::getUniqueId, alertScheme.getUniqueId()));
            if (alertScheme1 != null) {
                this.baseMapper.updateById(alertScheme);
                return alertScheme.getUniqueId();
            } else {
                throw JtyfBusinessException.of(ErrorCodeConstants.UUID_NOT_EXIST);
            }
        }
        if (!Objects.equals(alertScheme.getSchemeName(), "") && !Objects.equals(alertScheme.getAlertText(), "")) {
            alertScheme.setId(null);
            String uuid = UUID.randomUUID().toString();
            alertScheme.setUniqueId(uuid);
            this.baseMapper.insert(alertScheme);
            return uuid;
        } else {
            throw JtyfBusinessException.of(ErrorCodeConstants.PARAM_IS_NOT_EMPTY);
        }
    }

    @Override
    public void deleteAlertSchema(String[] uniqueIds) {
        for (String uniqueId : uniqueIds) {
            AlertScheme alertScheme1 = lambdaQuery().eq(AlertScheme::getUniqueId, uniqueId).one();
            if(alertScheme1 == null){
                throw JtyfBusinessException.of(ErrorCodeConstants.UUID_NOT_EXIST_AND_DELETED, uniqueId);
            }
            this.baseMapper.deleteById(alertScheme1);
        }
    }
}
