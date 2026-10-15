package cn.iocoder.jtyf.module.semi.common.business;

import cn.iocoder.jtyf.module.semi.common.dao.CmnDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class CmnService {

    @Autowired
    private CmnDao cmnDao;

    public String getUuid() {
        return cmnDao.getUuid();
    }
}
