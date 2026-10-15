package cn.iocoder.wx.module.semi.controller.admin.operation.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.DepartmentInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.businessinfo.RoleInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.form.AccountReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.form.RoleReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.model.StatisticsResInfo;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;


@Repository
public class OpnMngDao {

    @Autowired
    private SqlSessionTemplate sqlSession;

    private static final String NAMESPACE = "cn.iocoder.wx.module.semi.controller.admin.operation.dao.opnMng.";

    public StatisticsResInfo getStatisticsInfo() {
        StatisticsResInfo result = new StatisticsResInfo();
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("result", result);
        sqlSession.selectOne(NAMESPACE + "getStatisticsInfo", params);
        return result;
    }

    public int getAccountInfoTotal(AccountReqInfo accountInfo, int deleted) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("accountInfo", accountInfo);
        params.put("deleted", deleted);
        return sqlSession.selectOne(NAMESPACE + "getAccountInfoTotal", params);
    }

    public List<AccountInfo> getAccountInfoList(AccountReqInfo accountInfo, int deleted, int current, int pageSize) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("accountInfo", accountInfo);
        params.put("deleted", deleted);
        params.put("current", current);
        params.put("pageSize", pageSize);
        return sqlSession.selectList(NAMESPACE + "getAccountInfoList", params);
    }

    public void updateAccountInfo(AccountInfo accountInfo) {
        sqlSession.update(NAMESPACE + "updateAccountInfo", accountInfo);
    }

    public void disableAccountInfo(AccountReqInfo accountInfo, boolean disable) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("accountInfo", accountInfo);
        params.put("disable", disable);
        sqlSession.update(NAMESPACE + "disableAccountInfo", params);
    }

    public int getRoleInfoTotal(RoleReqInfo roleInfo) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("roleInfo", roleInfo);
        return sqlSession.selectOne(NAMESPACE + "getRoleInfoTotal", params);
    }

    public List<RoleInfo> getRoleInfoList(RoleReqInfo roleInfo, int current, int pageSize) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("roleInfo", roleInfo);
        params.put("current", current);
        params.put("pageSize", pageSize);
        return sqlSession.selectList(NAMESPACE + "getRoleInfoList", params);
    }

    public RoleInfo getRoleInfo(String id, String name) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("name", name);
        return sqlSession.selectOne(NAMESPACE + "getRoleInfo", params);
    }

    public int insertRoleInfo(RoleInfo roleInfo) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("roleInfo", roleInfo);
        return sqlSession.insert(NAMESPACE + "insertRoleInfo", params);
    }

    public void updateRoleInfo(RoleInfo roleInfo) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("roleInfo", roleInfo);
        sqlSession.update(NAMESPACE + "updateRoleInfo", params);
    }

    public void deleteRoleInfo(String id) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        sqlSession.update(NAMESPACE + "deleteRoleInfo", params);
    }

    public DepartmentInfo getDepartmentInfo(String id, Boolean deleted) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("deleted", deleted);
        return sqlSession.selectOne(NAMESPACE + "getDepartmentInfo", params);
    }

    public void insertDepartmentInfo(DepartmentInfo departmentInfo) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("departmentInfo", departmentInfo);
        sqlSession.selectOne(NAMESPACE + "insertDepartmentInfo", params);
    }

    public void updateDepartmentInfo(DepartmentInfo departmentInfo) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("departmentInfo", departmentInfo);
        sqlSession.update(NAMESPACE + "updateDepartmentInfo", params);
    }

    public void deleteDepartmentInfo(String id) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        sqlSession.delete(NAMESPACE + "deleteDepartmentInfo", params);
    }
}
