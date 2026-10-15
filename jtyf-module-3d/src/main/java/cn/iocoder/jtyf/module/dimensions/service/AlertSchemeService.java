package cn.iocoder.jtyf.module.dimensions.service;

import cn.iocoder.jtyf.module.dimensions.domain.vo.AlertSchemePage;
import cn.iocoder.jtyf.module.dimensions.domain.vo.AlertSchemePageVO;
import cn.iocoder.jtyf.module.dimensions.entity.AlertScheme;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface AlertSchemeService extends IService<AlertScheme> {
    List<AlertScheme> getAlertSchemeList(String[] uniqueId);

    AlertSchemePageVO getAlertSchemePage(AlertSchemePage alertSchemePage);

    String insertAlertScheme(AlertScheme alertScheme);

    void deleteAlertSchema(String[] uniqueId);
}
