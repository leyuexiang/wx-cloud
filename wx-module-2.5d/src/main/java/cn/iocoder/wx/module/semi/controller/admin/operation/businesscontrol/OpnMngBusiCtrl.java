package cn.iocoder.wx.module.semi.controller.admin.operation.businesscontrol;

import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.DepartmentInfo;
import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.controller.admin.data.business.DataMngService;
import cn.iocoder.wx.module.semi.controller.admin.data.businessinfo.DataInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.form.DataReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.model.DataListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.business.OpnMngService;
import cn.iocoder.wx.module.semi.controller.admin.operation.businessinfo.RoleInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.form.AccountReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.form.RoleReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.model.AccountListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.model.RoleListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.model.StatisticsResInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Transactional
@Component
public class OpnMngBusiCtrl {

    @Autowired
    private OpnMngService opnMngService;

    @Autowired
    private DataMngService dataMngService;

    /**
     * 获取统计情报
     * 
     * @return 统计情报
     */
    @Transactional(readOnly = false)
    public StatisticsResInfo getStatisticsInfo() {
        return opnMngService.getStatisticsInfo();
    }

    /**
     * 获取账号列表
     * 
     * @param reqInfo 账号API请求内容情报
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 账号列表情报
     */
    @Transactional(readOnly = true)
    public AccountListResInfo getAccountInfoList(AccountReqInfo reqInfo, Integer current, Integer pageSize) {
        return opnMngService.getAccountInfoList(reqInfo, -1, current, pageSize);
    }

    /**
     * 添加账号
     * 
     * @param accountInfo 账号情报
     * @return 账号情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountInfo addAccountInfo(AccountInfo accountInfo) throws CmnBizException {
        return opnMngService.addAccountInfo(accountInfo);
    }

    /**
     * 更新账号
     * 
     * @param accountInfo 账号情报
     * @return 账号情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountInfo updateAccountInfo(AccountInfo accountInfo) throws CmnBizException {
        return opnMngService.updateAccountInfo(accountInfo);
    }

    /**
     * 禁用账号
     * 
     * @param reqInfo 账号API请求内容情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableAccountInfo(AccountReqInfo reqInfo) throws CmnBizException {
        opnMngService.disableAccountInfo(reqInfo);
    }

    /**
     * 获取角色列表
     * 
     * @param reqInfo 角色API请求内容情报
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 角色列表
     */
    @Transactional(readOnly = true)
    public RoleListResInfo getRoleInfoList(RoleReqInfo reqInfo, Integer current, Integer pageSize) {
        return opnMngService.getRoleInfoList(reqInfo, current, pageSize);
    }

    /**
     * 创建角色
     * 
     * @param roleInfo 角色情报
     * @return 角色情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleInfo addRoleInfo(RoleInfo roleInfo) throws CmnBizException {
        return opnMngService.addRoleInfo(roleInfo);
    }

    /**
     *  更新角色
     * 
     * @param roleInfo 角色情报
     * @return 角色情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleInfo updateRoleInfo(RoleInfo roleInfo) throws CmnBizException {
        return opnMngService.updateRoleInfo(roleInfo);
    }

    /**
     * 删除角色
     * 
     * @param roleInfo 角色情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoleInfo(RoleInfo roleInfo) throws CmnBizException {
        opnMngService.deleteRoleInfo(roleInfo);
    }

    /**
     * 创建部门
     * 
     * @param departmentInfo 部门情报
     * @return 部门情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public DepartmentInfo addDepartmentInfo(DepartmentInfo departmentInfo) throws CmnBizException {
        return opnMngService.addDepartmentInfo(departmentInfo);
    }

    /**
     * 更新部门
     * 
     * @param departmentInfo 部门情报
     * @return 部门情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public DepartmentInfo updateDepartmentInfo(DepartmentInfo departmentInfo) throws CmnBizException {
        return opnMngService.updateDepartmentInfo(departmentInfo);
    }

    /**
     * 删除部门
     * 
     * @param departmentInfo 部门情报
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDepartmentInfo(DepartmentInfo departmentInfo) {
        opnMngService.deleteDepartmentInfo(departmentInfo);
    }

    /**
     * 获取数据列表
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param reqInfo 数据API请求内容情报
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 数据列表情报
     * @throws CmnBizException 异常
     */
    @Transactional(readOnly = true)
    public DataListResInfo getDataInfoList(AccountInfo loginUser, String collection,
                                           DataReqInfo reqInfo, Integer current, Integer pageSize) throws CmnBizException {
        return dataMngService.getDataInfoList(loginUser, collection, reqInfo, 0, true, current, pageSize);
    }

    /**
     * 更新数据
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param dataInfo 数据情报
     * @return 数据情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public DataInfo updateDataInfo(AccountInfo loginUser, String collection, DataInfo dataInfo) throws CmnBizException {
        return dataMngService.updateDataInfo(loginUser, collection, dataInfo, true);
    }
}
