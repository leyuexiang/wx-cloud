package cn.iocoder.wx.module.semi.common.dao;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class CmnDao {

    @Autowired
    private SqlSessionTemplate sqlSession;

    private static final String NAMESPACE = "cn.iocoder.wx.module.semi.controller.admin.common.dao.cmn.";

    public String getUuid() {
        return sqlSession.selectOne(NAMESPACE + "getUuid");
    }
}
