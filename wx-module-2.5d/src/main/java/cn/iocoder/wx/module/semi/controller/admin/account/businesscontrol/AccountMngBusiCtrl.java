package cn.iocoder.wx.module.semi.controller.admin.account.businesscontrol;

import java.util.List;
import java.util.Map;

import cn.iocoder.wx.module.semi.controller.admin.account.business.AccountMngService;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.TeamInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.TeamMemberInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.presentation.form.*;
import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.controller.admin.file.business.FileMngService;
import cn.iocoder.wx.module.semi.controller.admin.file.businessinfo.FileInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Transactional
@Component
public class AccountMngBusiCtrl {

    @Autowired
    private AccountMngService accountMngService;

    @Autowired
    private FileMngService fileMngService;

    /**
     * 注册账号
     * 
     * @param accountInfo 账号情报
     * @return 账号情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountInfo registerAccount(AccountInfo accountInfo) throws CmnBizException {
        return accountMngService.registerAccount(accountInfo);
    }

    /**
     * 获取账号详细情报
     * 
     * @param id 账号ID
     * @param pswdFlg 是否获取密码标识
     * @return 账号情报
     * @throws CmnBizException 异常
     */
    @Transactional(readOnly = true)
    public AccountInfo getAccountDetailById(String id, boolean pswdFlg) throws CmnBizException {
        return accountMngService.getAccountDetail(id, null, pswdFlg);
    }

    /**
     * 登录账号
     * 
     * @param account 账号
     * @param password 密码
     * @return 账号情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountInfo login(String account, String password) throws CmnBizException {
        return accountMngService.login(account, password);
    }

    /**
     * 更新用户信息
     * 
     * @param loginUser 登录用户
     * @param accountDataMap 账号数据Map
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateAccount(AccountInfo loginUser, Map<String, String> accountDataMap) {
        accountMngService.updateAccount(loginUser, accountDataMap);
    }

    /**
     * 获取统计情报
     * 
     * @param loginUser 登录用户
     * @return 统计情报Map
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> getStatisticInfo(AccountInfo loginUser) {
        return accountMngService.getStatisticInfo(loginUser);
    }

    /**
     * 找回密码/修改密码
     * 
     * @param account 账号
     * @param code 验证码(短信/邮件)/旧密码
     * @param password 密码
     * @param forget 是否忘记密码标识
     * @return 账号情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountInfo updatePassword(String account, String code, String password,
            boolean forget) throws CmnBizException {
        return accountMngService.updatePassword(account, code, password, forget);
    }

    /**
     * 更新邮箱
     * 
     * @param accountInfo 账号情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateEmail(AccountInfo accountInfo) throws CmnBizException {
        accountMngService.updateEmail(accountInfo);
    }

    /**
     * 获取公司部门列表
     * 
     * @param name 部门名称
     * @param parentId 父部门ID
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 部门列表
     */
    @Transactional(readOnly = true)
    public DepartmentListResInfo getDepartmentInfoList(String name, String parentId,
                                                       Integer current, Integer pageSize) {
        return accountMngService.getDepartmentInfoList(name, parentId, current, pageSize);
    }

    /**
     * 获取文件
     * 
     * @param loginUser 登录用户
     * @param fullname 文件全名
     * @return 文件情报
     * @throws CmnBizException 异常
     */
    @Transactional(readOnly = true)
    public FileInfo getFileInfo(AccountInfo loginUser, String fullname) throws CmnBizException {
        return fileMngService.getFileInfoByFullname(loginUser, fullname, true);
    }

    /**
     * 查找用户
     * 
     * @param reqInfo 账号API请求内容情报
     * @return 账号情报
     * @throws CmnBizException 异常
     */
    @Transactional(readOnly = true)
    public AccountInfo findUser(AccountReqInfo reqInfo) throws CmnBizException {
        return accountMngService.findUser(reqInfo);
    }

    /**
     * 查找用户列表
     * 
     * @param reqInfo 账号API请求内容情报
     * @return 账号情报列表
     */
    public List<AccountInfo> findUserList(AccountReqInfo reqInfo) {
        return accountMngService.findUserList(reqInfo);
    }

    /**
     * 获取团队列表
     * 
     * @param loginUser 登录用户
     * @param teamName 团队名称
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 团队列表情报
     */
    @Transactional(readOnly = true)
    public TeamListResInfo getTeamInfoList(AccountInfo loginUser, String teamName,
                                           Integer current, Integer pageSize) {
        return accountMngService.getTeamInfoList(loginUser, teamName, current, pageSize);
    }

    /**
     * 获取团队成员列表
     * 
     * @param teamId 团队ID
     * @param username 用户名
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 团队成员列表情报
     */
    @Transactional(readOnly = true)
    public TeamMemberListResInfo getTeamMemberInfoList(String teamId, String username,
                                                       Integer current, Integer pageSize) {
        return accountMngService.getTeamMemberInfoList(teamId, username, current, pageSize);
    }

    /**
     * 创建团队
     * 
     * @param loginUser 登录用户
     * @param teamInfo 团队情报
     * @return 团队情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public TeamInfo createTeam(AccountInfo loginUser, TeamInfo teamInfo) throws CmnBizException {
        return accountMngService.createTeam(loginUser, teamInfo);
    }

    /**
     * 更新团队
     * 
     * @param loginUser 登录用户
     * @param teamInfo 团队情报
     * @return 团队情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public TeamInfo updateTeam(AccountInfo loginUser, TeamInfo teamInfo) throws CmnBizException {
        return accountMngService.updateTeam(loginUser, teamInfo);
    }

    /**
     * 删除团队
     * 
     * @param loginUser 登录用户
     * @param id 团队ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTeam(AccountInfo loginUser, String id) {
        accountMngService.deleteTeam(loginUser, id);
    }

    /**
     * 转让团队
     * 
     * @param loginUser 登录用户
     * @param teamId 团队ID
     * @param userId 用户ID
     * @return 团队情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public TeamInfo transferTeam(AccountInfo loginUser, String teamId, String userId) throws CmnBizException {
        return accountMngService.transferTeam(loginUser, teamId, userId);
    }

    /**
     * 更新团队管理员
     * 
     * @param loginUser 登录用户
     * @param teamMemberInfo 团队成员情报
     * @return 团队情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public TeamInfo updateTeamAdmin(AccountInfo loginUser, TeamMemberInfo teamMemberInfo) throws CmnBizException {
        return accountMngService.updateTeamAdmin(loginUser, teamMemberInfo);
    }

    /**
     * 邀请用户加入团队
     * 
     * @param loginUser 登录用户
     * @param reqInfo 团队API请求内容情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void teamInvite(AccountInfo loginUser, TeamReqInfo reqInfo) throws CmnBizException {
        accountMngService.teamInvite(loginUser, reqInfo);
    }

    /**
     * 申请加入团队
     * 
     * @param loginUser 登录用户
     * @param teamId 团队ID
     * @return 是否等待处理
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean joinTeam(AccountInfo loginUser, String teamId) throws CmnBizException {
        return accountMngService.joinTeam(loginUser, teamId);
    }

    /**
     * 审核加入团队
     * 
     * @param loginUser 登录用户
     * @param reqInfo 团队API请求内容情报
     */
    @Transactional(rollbackFor = Exception.class)
    public void teamAccept(AccountInfo loginUser, TeamReqInfo reqInfo) {
        accountMngService.teamAccept(loginUser, reqInfo);
    }

    /**
     * 移除团队成员或退出团队
     * 
     * @param loginUser 登录用户
     * @param teamMemberInfo 团队成员情报
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeTeamMember(AccountInfo loginUser, TeamMemberInfo teamMemberInfo) {
        accountMngService.removeTeamMember(loginUser, teamMemberInfo);
    }
}
