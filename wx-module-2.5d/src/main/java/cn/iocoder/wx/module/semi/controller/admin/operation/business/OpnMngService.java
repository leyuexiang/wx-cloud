package cn.iocoder.wx.module.semi.controller.admin.operation.business;

import java.util.ArrayList;
import java.util.List;

import cn.iocoder.wx.module.semi.controller.admin.account.business.AccountMngService;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.DepartmentInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.util.AccountMessageConst;
import cn.iocoder.wx.module.semi.common.business.CmnService;
import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.common.log.CmnLogger;
import cn.iocoder.wx.module.semi.common.util.ListUtil;
import cn.iocoder.wx.module.semi.common.util.StrUtil;
import cn.iocoder.wx.module.semi.controller.admin.operation.businessinfo.RoleInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.dao.OpnMngDao;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.form.AccountReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.form.RoleReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.model.AccountListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.model.RoleListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.model.StatisticsResInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.util.OperationMessageConst;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpnMngService {

    private CmnLogger logger = CmnLogger.getLogger(this.getClass());

    @Autowired
    private OpnMngDao opnMngDao;

    @Autowired
    private CmnService cmnService;

    @Autowired
    private AccountMngService accountMngService;

    /**
     * 获取统计情报
     * 
     * @return 统计情报
     */
    public StatisticsResInfo getStatisticsInfo() {
        return opnMngDao.getStatisticsInfo();
    }

    /**
     * 获取账号总件数
     * 
     * @param reqInfo 账号API请求内容情报
     * @param deleted 数据删除状态(0 - 未删除;1 - 删除;Other - 全部)
     * @return 账号总件数
     */
    public int getAccountInfoTotal(AccountReqInfo reqInfo, int deleted) {
        return opnMngDao.getAccountInfoTotal(reqInfo, deleted);
    }

    /**
     * 获取账号列表
     * 
     * @param reqInfo 账号API请求内容情报
     * @param deleted 数据删除状态(0 - 未删除;1 - 删除;Other - 全部)
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 账号列表情报
     */
    public AccountListResInfo getAccountInfoList(AccountReqInfo reqInfo, int deleted, Integer current, Integer pageSize) {
        if (reqInfo == null) {
            reqInfo = new AccountReqInfo();
        }

        // 当前页
        if (current == null || current.intValue() <= 0) {
            current = 1;
        }

        // 每页记录数
        if (pageSize == null || pageSize.intValue() <= 0) {
            pageSize = 10;
        }

        // 获取账号总件数
        int total = getAccountInfoTotal(reqInfo, deleted);

        // 获取账号列表
        List<AccountInfo> accountInfoList = opnMngDao.getAccountInfoList(reqInfo, deleted, pageSize * (current - 1), pageSize);

        if (accountInfoList == null) {
            accountInfoList = new ArrayList<AccountInfo>();
        } else {
            for (AccountInfo accountInfo : accountInfoList) {
                accountInfo.buildTeams();
            }
        }

        // 账号列表情报
        AccountListResInfo result = new AccountListResInfo();
        result.setTotal(total);
        result.setList(accountInfoList);
        return result;
    }

    /**
     * 添加账号
     * 
     * @param accountInfo 账号情报
     * @return 账号情报
     * @throws CmnBizException 异常
     */
    public AccountInfo addAccountInfo(AccountInfo accountInfo) throws CmnBizException {
        return accountMngService.registerAccount(accountInfo);
    }

    /**
     * 更新账号
     * 
     * @param accountInfo 账号情报
     * @return 账号情报
     * @throws CmnBizException 异常
     */
    public AccountInfo updateAccountInfo(AccountInfo accountInfo) throws CmnBizException {
        String id = accountInfo.getId();
        String account = accountInfo.getAccount();

        // 检证账号是否存在
        if (!accountMngService.checkExistAccount(id, account, null)) {
            Object[] args = new Object[] { StrUtil.isNotEmpty(id) ? id : account };
            logger.log(AccountMessageConst.MSG_11040002, args);
            throw new CmnBizException(AccountMessageConst.MSG_11040002, args);
        }

        if (ListUtil.isNotEmptyList(accountInfo.getRoleList())) {
            // 保存角色
            accountMngService.saveRole(accountInfo.getRoleList());

            accountInfo.setRoleNmsStr(StrUtil.join(accountInfo.getRoleList(), ",", "'"));
        }

        if (ListUtil.isNotEmptyList(accountInfo.getTeams())) {
            accountInfo.setTeamsStr(StrUtil.join(accountInfo.getTeams(), ",", "'"));
        }

        // 更新账号
        opnMngDao.updateAccountInfo(accountInfo);

        // 取得用户详细情报
        return accountMngService.getAccountDetail(id, account, false);
    }

    /**
     * 禁用账号
     * 
     * @param reqInfo 账号API请求内容情报
     * @throws CmnBizException 异常
     */
    public void disableAccountInfo(AccountReqInfo reqInfo) throws CmnBizException {
        String id = reqInfo.getId();
        String account = reqInfo.getAccount();

        // 检证账号是否存在
        if (!accountMngService.checkExistAccount(id, account, null)) {
            Object[] args = new Object[] { StrUtil.isNotEmpty(id) ? id : account };
            logger.log(AccountMessageConst.MSG_11040002, args);
            throw new CmnBizException(AccountMessageConst.MSG_11040002, args);
        }

        // 禁用账号
        opnMngDao.disableAccountInfo(reqInfo, true);
    }

    /**
     * 获取角色总件数
     * 
     * @param reqInfo 角色API请求内容情报
     * @return 角色总件数
     */
    public int getRoleInfoTotal(RoleReqInfo reqInfo) {
        return opnMngDao.getRoleInfoTotal(reqInfo);
    }

    /**
     * 获取角色列表
     * 
     * @param reqInfo 角色API请求内容情报
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 角色列表
     */
    public RoleListResInfo getRoleInfoList(RoleReqInfo reqInfo, Integer current, Integer pageSize) {
        if (reqInfo == null) {
            reqInfo = new RoleReqInfo();
        }

        // 当前页
        if (current == null || current.intValue() <= 0) {
            current = 1;
        }

        // 每页记录数
        if (pageSize == null || pageSize.intValue() <= 0) {
            pageSize = 100;
        }

        // 获取角色总件数
        int total = getRoleInfoTotal(reqInfo);

        // 获取角色列表
        List<RoleInfo> roleInfoList = opnMngDao.getRoleInfoList(reqInfo, pageSize * (current - 1), pageSize);

        if (roleInfoList == null) {
            roleInfoList = new ArrayList<RoleInfo>();
        }

        // 账号列表情报
        RoleListResInfo result = new RoleListResInfo();
        result.setTotal(total);
        result.setList(roleInfoList);
        return result;
    }

    /**
     * 创建角色
     * 
     * @param roleInfo 角色情报
     * @return 角色情报
     * @throws CmnBizException 异常
     */
    public RoleInfo addRoleInfo(RoleInfo roleInfo) throws CmnBizException {
        // 创建角色
        int number = opnMngDao.insertRoleInfo(roleInfo);
        if (number == 0) {
            Object[] args = new Object[] { roleInfo.getName() };
            logger.log(OperationMessageConst.MSG_99010002, args);
            throw new CmnBizException(OperationMessageConst.MSG_99010002, args);
        }
        return getRoleInfo(null, roleInfo.getName());
    }

    /**
     *  更新角色
     * 
     * @param roleInfo 角色情报
     * @return 角色情报
     * @throws CmnBizException 异常
     */
    public RoleInfo updateRoleInfo(RoleInfo roleInfo) throws CmnBizException {
        // 更新角色
        opnMngDao.updateRoleInfo(roleInfo);
        return getRoleInfo(roleInfo.getId(), null);
    }

    /**
     * 删除角色
     * 
     * @param roleInfo 角色情报
     * @throws CmnBizException 异常
     */
    public void deleteRoleInfo(RoleInfo roleInfo) throws CmnBizException {
        String id = roleInfo.getId();

        if (id.equals("00000000-0000-0000-0000-000000000001")
                || id.equals("00000000-0000-0000-0000-000000000010")) {
            logger.log(OperationMessageConst.MSG_99010003);
            throw new CmnBizException(OperationMessageConst.MSG_99010003);
        }

        // 删除角色
        opnMngDao.deleteRoleInfo(id);
    }

    /**
     * 创建部门
     * 
     * @param departmentInfo 部门情报
     * @return 部门情报
     * @throws CmnBizException 异常
     */
    public DepartmentInfo addDepartmentInfo(DepartmentInfo departmentInfo) throws CmnBizException {
        String id = cmnService.getUuid();
        departmentInfo.setId(id);
        departmentInfo.buildFullpathInfo();
        // 创建部门
        opnMngDao.insertDepartmentInfo(departmentInfo);
        return getDepartmentInfo(id, false);
    }

    /**
     * 更新部门
     * 
     * @param departmentInfo 部门情报
     * @return 部门情报
     * @throws CmnBizException 异常
     */
    public DepartmentInfo updateDepartmentInfo(DepartmentInfo departmentInfo) throws CmnBizException {
        departmentInfo.buildFullpaths();
        // 更新部门
        opnMngDao.updateDepartmentInfo(departmentInfo);
        return getDepartmentInfo(departmentInfo.getId(), false);
    }

    /**
     * 删除部门
     * 
     * @param departmentInfo 部门情报
     */
    public void deleteDepartmentInfo(DepartmentInfo departmentInfo) {
        // 删除部门
        opnMngDao.deleteDepartmentInfo(departmentInfo.getId());
    }

    private RoleInfo getRoleInfo(String id, String name) throws CmnBizException {
        RoleInfo roleInfo = opnMngDao.getRoleInfo(id, name);
        if (roleInfo == null) {
            Object[] args = new Object[] { name };
            logger.log(OperationMessageConst.MSG_99010001, args);
            throw new CmnBizException(OperationMessageConst.MSG_99010001, args);
        }
        return roleInfo;
    }

    private DepartmentInfo getDepartmentInfo(String id, Boolean deleted) throws CmnBizException {
        DepartmentInfo departmentInfo = opnMngDao.getDepartmentInfo(id, deleted);
        if (departmentInfo == null) {
            Object[] args = new Object[] { id };
            logger.log(OperationMessageConst.MSG_99010004, args);
            throw new CmnBizException(OperationMessageConst.MSG_99010004, args);
        }
        return departmentInfo;
    }
}
