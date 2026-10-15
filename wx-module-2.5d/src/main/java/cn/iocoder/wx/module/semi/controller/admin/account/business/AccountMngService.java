package cn.iocoder.wx.module.semi.controller.admin.account.business;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.DepartmentInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.TeamInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.TeamMemberInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.conf.AccountConfParser;
import cn.iocoder.wx.module.semi.controller.admin.account.dao.AccountMngDao;
import cn.iocoder.wx.module.semi.controller.admin.account.presentation.form.*;
import cn.iocoder.wx.module.semi.controller.admin.account.util.AccountMessageConst;
import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.common.log.CmnLogger;
import cn.iocoder.wx.module.semi.common.util.DateUtil;
import cn.iocoder.wx.module.semi.common.util.JsonUtil;
import cn.iocoder.wx.module.semi.common.util.ListUtil;
import cn.iocoder.wx.module.semi.common.util.StrUtil;
import cn.iocoder.wx.module.semi.controller.admin.data.business.DataMngService;
import cn.iocoder.wx.module.semi.controller.admin.message.business.MessageMngService;
import cn.iocoder.wx.module.semi.controller.admin.message.businessinfo.MessageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class AccountMngService {

    private CmnLogger logger = CmnLogger.getLogger(this.getClass());

    @Autowired
    private AccountMngDao accountMngDao;

    @Autowired
    private DataMngService dataMngService;

    @Autowired
    private MessageMngService messageMngService;

    /**
     * 检证是否存在账号
     * 
     * @param id 账号ID
     * @param account 账号名
     * @param deleted 是否删除
     * @return 存在的场合返回true;反之返回false
     */
    public boolean checkExistAccount(String id, String account, Boolean deleted) {
        return accountMngDao.checkExistAccount(id, account, deleted);
    }

    /**
     * 获取部门总件数
     * 
     * @param name 部门名称
     * @param parentId 父部门ID
     * @return 部门总件数
     */
    private int getDepartmentInfoTotal(String name, String parentId) {
        return accountMngDao.getDepartmentInfoTotal(name, parentId);
    }

    /**
     * 获取团队总件数
     * 
     * @param userId 用户ID
     * @param teamName 团队名称
     * @return 团队总件数
     */
    private int getTeamInfoTotal(String userId, String teamName) {
        return accountMngDao.getTeamInfoTotal(userId, teamName);
    }

    /**
     * 获取团队成员总件数
     * 
     * @param teamId 团队ID
     * @param username 用户名
     * @return 团队成员总件数
     */
    private int getTeamMemberInfoTotal(String teamId, String username) {
        return accountMngDao.getTeamMemberInfoTotal(teamId, username);
    }

    /**
     * 注册账号
     * 
     * @param accountInfo 账号情报
     * @return 账号情报
     * @throws CmnBizException 异常
     */
    public AccountInfo registerAccount(AccountInfo accountInfo) throws CmnBizException {
        // 检证账号是否已经存在
        if (checkExistAccount(null, accountInfo.getAccount(), null)) {
            Object[] args = new Object[] { accountInfo.getAccount() };
            logger.log(AccountMessageConst.MSG_11040001, args);
            throw new CmnBizException(AccountMessageConst.MSG_11040001, args);
        }

        // 保存角色
        saveRole(accountInfo.getRoleList());

        // 注册账号
        accountMngDao.registerAccount(accountInfo);

        // 取得用户详细情报
        return getAccountDetail(null, accountInfo.getAccount(), false);
    }

    /**
     * 获取账号详细情报
     * 
     * @param id 账号ID
     * @param account 账号名
     * @param pswdFlg 是否获取密码标识
     * @return 账号情报
     * @throws CmnBizException 异常
     */
    public AccountInfo getAccountDetail(String id, String account, boolean pswdFlg) throws CmnBizException {
        AccountInfo result = accountMngDao.getAccountDetail(id, account, pswdFlg);
        if (result == null) {
            Object[] args = new Object[] { account };
            logger.log(AccountMessageConst.MSG_11040002, args);
            throw new CmnBizException(AccountMessageConst.MSG_11040002, args);
        }
        result.buildTeams();
        return result;
    }

    /**
     * 获取账号简要情报
     * 
     * @param account 账号/手机/邮箱
     * @return 账号情报
     * @throws CmnBizException 异常
     */
    public AccountInfo getAccountSimple(String account) throws CmnBizException {
        AccountInfo result = accountMngDao.getAccountSimple(account);
        if (result == null) {
            Object[] args = new Object[] { account };
            logger.log(AccountMessageConst.MSG_11040002, args);
            throw new CmnBizException(AccountMessageConst.MSG_11040002, args);
        }
        return result;
    }

    /**
     * 获取账号简要情报列表
     * 
     * @param idList ID列表
     * @return 账号情报列表
     */
    public List<AccountInfo> getAccountSimpleList(List<String> idList) {
        return accountMngDao.getAccountSimpleList(idList);
    }

    /**
     * 登录账号
     * 
     * @param account 账号
     * @param password 密码
     * @return 账号情报
     * @throws CmnBizException 异常
     */
    public AccountInfo login(String account, String password) throws CmnBizException {
        // 检证账号是否禁用
        if (accountMngDao.checkExistAccount(null, account, true)) {
            Object[] args = new Object[] { account };
            logger.log(AccountMessageConst.MSG_11040007, args);
            throw new CmnBizException(AccountMessageConst.MSG_11040007, args);
        }

        // 检证账号是否锁定
        if (accountMngDao.checkExistLockedAccount(account, true, false)) {
            logger.log(AccountMessageConst.MSG_11050001);
            throw new CmnBizException(AccountMessageConst.MSG_11050001);
        }

        // 用户名密码错误
        if (!accountMngDao.checkLogin(account, password)) {
            // 账号存在的场合、登录错误次数
            if (checkExistAccount(null, account, null)) {
                if (!accountMngDao.checkExistLockedAccount(account, false, false)) {
                    accountMngDao.insertLockedAccount(account);
                } else {
                    // 当前时间距离用户最后一次错误登录时间超过24小时的场合，重置错误次数为1
                    // 上记以外的场合，错误次数增加1
                    boolean reset = accountMngDao.checkExistLockedAccount(account, false, true);
                    accountMngDao.updateLockedAccount(account, reset);
                }
            }

            return null;
        }

        // 账号解锁
        accountMngDao.deleteLockedAccount(account);

        // 取得用户详细情报
        return getAccountDetail(null, account, false);
    }

    /**
     * 更新用户信息
     * 
     * @param loginUser 登录用户
     * @param accountDataMap 账号数据Map
     */
    public void updateAccount(AccountInfo loginUser, Map<String, String> accountDataMap) {
        String userId = loginUser.getId();
        String username = null;
        String avatarUrl = null;
        if (accountDataMap.containsKey("username")) {
            username = accountDataMap.get("username");
        }
        if (accountDataMap.containsKey("avatarUrl")) {
            avatarUrl = accountDataMap.get("avatarUrl");
        }

        // 更新用户信息
        accountMngDao.updateAccount(userId, username, avatarUrl);
    }

    /**
     * 获取统计情报
     * 
     * @param loginUser 登录用户
     * @return 统计情报Map
     */
    public Map<String, Integer> getStatisticInfo(AccountInfo loginUser) {
        String ownerId = loginUser.getId();
        String departmentId = loginUser.getDepartmentId();
        List<String> teamIdList = loginUser.getTeams();
        Map<String, Integer> result = new HashMap<String, Integer>();
        result.put("2d", dataMngService.getDataInfoTotal(
                ownerId, "\"2d\"", "\"2d.authorize\"", null, 0, ownerId, departmentId, teamIdList));
        result.put("3d", dataMngService.getDataInfoTotal(
                ownerId, "\"3d\"", "\"3d.authorize\"", null, 0, ownerId, departmentId, teamIdList));
        result.put("v", dataMngService.getDataInfoTotal(
                ownerId, "\"v\"", "\"v.authorize\"", null, 0, ownerId, departmentId, teamIdList));
        return result;
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
    public AccountInfo updatePassword(String account, String code, String password,
            boolean forget) throws CmnBizException {
        // 检证账号是否存在
        if (!checkExistAccount(null, account, null)) {
            Object[] args = new Object[] { account };
            logger.log(AccountMessageConst.MSG_11040002, args);
            throw new CmnBizException(AccountMessageConst.MSG_11040002, args);
        }

        // 检证账号是否禁用
        if (accountMngDao.checkExistAccount(null, account, true)) {
            Object[] args = new Object[] { account };
            logger.log(AccountMessageConst.MSG_11040007, args);
            throw new CmnBizException(AccountMessageConst.MSG_11040007, args);
        }

        // 找回密码的场合
        if (forget) {
            // nothing
        // 修改密码的场合
        } else {
            // 检证旧密码是否正确
            if (!accountMngDao.checkLogin(account, code)) {
                logger.log(AccountMessageConst.MSG_11040004);
                throw new CmnBizException(AccountMessageConst.MSG_11040004);
            }

            // 新旧密码相同的场合
            if (code.equals(password)) {
                logger.log(AccountMessageConst.MSG_11040005);
                throw new CmnBizException(AccountMessageConst.MSG_11040005);
            }
        }

        // 更新密码
        accountMngDao.updatePassword(account, password);

        // 取得用户详细情报
        return getAccountDetail(null, account, false);
    }

    /**
     * 检证新邮箱是否已经被使用
     * 
     * @param id 账号ID
     * @param email 新邮箱地址
     * @return 被使用的场合返回true;反之返回false
     */
    public boolean checkEmailUsedByOther(String id, String email) {
        return accountMngDao.checkEmailUsedByOther(id, email);
    }

    /**
     * 更新邮箱
     * 
     * @param accountInfo 账号情报
     * @throws CmnBizException 异常
     */
    public void updateEmail(AccountInfo accountInfo) throws CmnBizException {
        // 检证账号是否禁用
        if (accountMngDao.checkExistAccount(accountInfo.getId(), null, true)) {
            Object[] args = new Object[] { accountInfo.getId() };
            logger.log(AccountMessageConst.MSG_11040007, args);
            throw new CmnBizException(AccountMessageConst.MSG_11040007, args);
        }

        // 检证新邮箱是否已经被使用
        if (checkEmailUsedByOther(accountInfo.getId(), accountInfo.getEmail())) {
            Object[] args = new Object[] { accountInfo.getEmail() };
            logger.log(AccountMessageConst.MSG_11040006, args);
            throw new CmnBizException(AccountMessageConst.MSG_11040006, args);
        }

        // 更新邮箱
        accountMngDao.updateEmail(accountInfo);
    }

    /**
     * 保存角色
     * 
     * @param roleNmList 角色名List
     */
    public void saveRole(List<String> roleNmList) {
        if (ListUtil.isEmptyList(roleNmList)) {
            return;
        }
        for (String roleNm : roleNmList) {
            if (!accountMngDao.checkExistRole(roleNm)) {
                accountMngDao.insertRole(roleNm);
            }
        }
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
    public DepartmentListResInfo getDepartmentInfoList(String name, String parentId,
                                                       Integer current, Integer pageSize) {
        // 当前页
        if (current == null || current.intValue() <= 0) {
            current = 1;
        }

        // 每页记录数
        if (pageSize == null || pageSize.intValue() <= 0) {
            pageSize = 100;
        }

        // 获取部门总件数
        int total = getDepartmentInfoTotal(name != null ? name.trim() : null, parentId);

        // 获取部门列表
        List<DepartmentInfo> departmentInfoList = accountMngDao.getDepartmentInfoList(
                name != null ? name.trim() : null, parentId, pageSize * (current - 1), pageSize);
        if (departmentInfoList == null) {
            departmentInfoList = new ArrayList<DepartmentInfo>();
        }

        // 部门列表情报
        DepartmentListResInfo result = new DepartmentListResInfo();
        result.setTotal(total);
        result.setDepartmentInfoList(departmentInfoList);
        return result;
    }

    public List<Long> getRoleAuthCdList(List<String> roleNmList) {
        return accountMngDao.getRoleAuthCdList(roleNmList);
    }

    /**
     * 查找用户
     * 
     * @param reqInfo 账号API请求内容情报
     * @return 账号情报
     * @throws CmnBizException 异常
     */
    public AccountInfo findUser(AccountReqInfo reqInfo) throws CmnBizException {
        String account = reqInfo.getAccount();
        if (StrUtil.isEmpty(account)) {
            account = reqInfo.getPhone();
        }
        if (StrUtil.isEmpty(account)) {
            account = reqInfo.getEmail();
        }
        return getAccountSimple(account);
    }

    /**
     * 查找用户列表
     * 
     * @param reqInfo 账号API请求内容情报
     * @return 账号情报列表
     */
    public List<AccountInfo> findUserList(AccountReqInfo reqInfo) {
        return getAccountSimpleList(reqInfo.getIdList());
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
    public TeamListResInfo getTeamInfoList(AccountInfo loginUser, String teamName,
                                           Integer current, Integer pageSize) {
        // 当前页
        if (current == null || current.intValue() <= 0) {
            current = 1;
        }

        // 每页记录数
        if (pageSize == null || pageSize.intValue() <= 0) {
            pageSize = 100;
        }

        // 获取团队总件数
        int total = getTeamInfoTotal(loginUser.getId(), teamName);

        // 获取团队列表
        List<TeamInfo> teamInfoList = accountMngDao.getTeamInfoList(
                loginUser.getId(), teamName, pageSize * (current - 1), pageSize);
        if (teamInfoList == null) {
            teamInfoList = new ArrayList<TeamInfo>();
        }

        // 团队列表情报
        TeamListResInfo result = new TeamListResInfo();
        result.setTotal(total);
        result.setTeamInfoList(teamInfoList);
        return result;
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
    public TeamMemberListResInfo getTeamMemberInfoList(String teamId, String username,
                                                       Integer current, Integer pageSize) {
        // 当前页
        if (current == null || current.intValue() <= 0) {
            current = 1;
        }

        // 每页记录数
        if (pageSize == null || pageSize.intValue() <= 0) {
            pageSize = 100;
        }

        // 获取团队成员总件数
        int total = getTeamMemberInfoTotal(teamId, username);

        // 获取团队成员列表
        List<TeamMemberInfo> teamMemberInfoList = accountMngDao.getTeamMemberInfoList(
                teamId, username, null, pageSize * (current - 1), pageSize);
        if (teamMemberInfoList == null) {
            teamMemberInfoList = new ArrayList<TeamMemberInfo>();
        }

        // 团队成员列表情报
        TeamMemberListResInfo result = new TeamMemberListResInfo();
        result.setTotal(total);
        result.setTeamMemberInfoList(teamMemberInfoList);
        return result;
    }

    /**
     * 创建团队
     * 
     * @param loginUser 登录用户
     * @param teamInfo 团队情报
     * @return 团队情报
     * @throws CmnBizException 异常
     */
    public TeamInfo createTeam(AccountInfo loginUser, TeamInfo teamInfo) throws CmnBizException {
        String ownerId = loginUser.getId();
        String username = loginUser.getUsername();

        // 创建团队
        teamInfo.setOwnerId(ownerId);
        String id = accountMngDao.insertTeamInfo(teamInfo);

        // 创建团队成员
        accountMngDao.insertTeamMemberInfo(id, ownerId, username, 10, null);

        // 更新账号(添加团队)
        accountMngDao.updateAccountInfo4AddTeam(ownerId, id);

        // 获取团队详细情报
        return getTeamDetailInfo(id, null, null, false, null);
    }

    /**
     * 获取团队详细情报
     * 
     * @param id 团队ID
     * @param inviteLink 邀请链接是否开启
     * @param inviteExpiration 邀请链接是否过期
     * @param memberPending 团队成员是否等待审核
     * @param memberUserId 团队成员用户ID
     * @return 团队情报
     * @throws CmnBizException 异常
     */
    public TeamInfo getTeamDetailInfo(String id, Boolean inviteLink, Boolean inviteExpiration,
            Boolean memberPending, String memberUserId) throws CmnBizException {
        TeamInfo result = accountMngDao.getTeamDetailInfo(id, inviteLink, inviteExpiration,
                memberPending, memberUserId);
        if (result == null) {
            Object[] args = new Object[] { id };
            logger.log(AccountMessageConst.MSG_11060001, args);
            throw new CmnBizException(AccountMessageConst.MSG_11060001, args);
        }
        result.buildIsAdmin();
        return result;
    }

    /**
     * 更新团队
     * 
     * @param loginUser 登录用户
     * @param teamInfo 团队情报
     * @return 团队情报
     * @throws CmnBizException 异常
     */
    public TeamInfo updateTeam(AccountInfo loginUser, TeamInfo teamInfo) throws CmnBizException {
        String userId = loginUser.getId();

        // 时间格式转换
        String inviteExpiration = teamInfo.getInviteExpiration();
        if (StrUtil.isNotEmpty(inviteExpiration)) {
            inviteExpiration = inviteExpiration.replaceAll("T", " ");
            if (StrUtil.isNotEmpty(inviteExpiration) && inviteExpiration.contains("+")) {
                inviteExpiration = inviteExpiration.substring(0, inviteExpiration.indexOf("+"));
            }
            try {
                DateUtil.toTimestamp(inviteExpiration, DateUtil.DATE_FORMAT);
            } catch (Exception e) {
                inviteExpiration = null;
            }
            teamInfo.setInviteExpiration(inviteExpiration);
        }

        // 更新团队
        accountMngDao.updateTeamInfo(userId, teamInfo);

        // 获取团队详细情报
        return getTeamDetailInfo(teamInfo.getId(), null, null, false, null);
    }

    /**
     * 删除团队
     * 
     * @param loginUser 登录用户
     * @param id 团队ID
     */
    public void deleteTeam(AccountInfo loginUser, String id) {
        String ownerId = loginUser.getId();

        // 更新账号(移除团队)
        accountMngDao.updateAccountInfo4RemoveTeam(ownerId, id);

        // 删除团队成员
        accountMngDao.deleteTeamMemberInfo(id);

        // 删除团队
        accountMngDao.deleteTeamInfo(id, ownerId);
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
    public TeamInfo transferTeam(AccountInfo loginUser, String teamId, String userId) throws CmnBizException {
        String ownerId = loginUser.getId();

        // 更新团队成员(取消管理员或创建者身份)
        accountMngDao.updateTeamMemberInfo4RemoveAdmin(teamId, userId);

        // 更新团队成员(设置创建者身份)
        accountMngDao.updateTeamMemberInfo4AddAdmin(teamId, ownerId);

        // 更新团队(设置所有者)
        accountMngDao.updateTeamInfo4Owner(teamId, userId, ownerId);

        // 更新账号(移除团队)
        accountMngDao.updateAccountInfo4RemoveTeam(userId, teamId);

        // 更新账号(添加团队)
        accountMngDao.updateAccountInfo4AddTeam(ownerId, teamId);

        // 获取团队详细情报
        return getTeamDetailInfo(teamId, null, null, false, null);
    }

    /**
     * 更新团队管理员
     * 
     * @param loginUser 登录用户
     * @param teamMemberInfo 团队成员情报
     * @return 团队情报
     * @throws CmnBizException 异常
     */
    public TeamInfo updateTeamAdmin(AccountInfo loginUser, TeamMemberInfo teamMemberInfo) throws CmnBizException {
        String loginUserId = loginUser.getId();
        String teamId = teamMemberInfo.getTeamId();
        String userId = teamMemberInfo.getUserId();
        String username = teamMemberInfo.getUsername();
        Integer isAdmin = teamMemberInfo.getIsAdmin();

        // 更新团队成员(更新管理员身份)
        accountMngDao.updateTeamMemberInfo4Admin(loginUserId, teamId, userId, username, isAdmin);

        // 获取团队详细情报
        return getTeamDetailInfo(teamId, null, null, false, null);
    }

    /**
     * 邀请用户加入团队
     * 
     * @param loginUser 登录用户
     * @param reqInfo 团队API请求内容情报
     * @throws CmnBizException 异常
     */
    public void teamInvite(AccountInfo loginUser, TeamReqInfo reqInfo) throws CmnBizException {
        String userId = loginUser.getId();
        String teamId = reqInfo.getTeamId();
        String teamName = reqInfo.getTeamName();
        String memberId = reqInfo.getMemberId();
        String memberName = reqInfo.getMemberName();

        // 获取团队详细情报
        TeamInfo teamInfo = getTeamDetailInfo(teamId, null, null, null, userId);

        Boolean pending = null;
        if (teamInfo.getIsAdmin() == null || teamInfo.getIsAdmin().intValue() < 1) {
            pending = teamInfo.getInviteApprove();
        }

        // 添加团队成员
        accountMngDao.insertTeamMemberInfo(teamId, memberId, memberName, 0, pending);

        if (pending == null || !pending.booleanValue()) {
            // 更新账号(添加团队)
            accountMngDao.updateAccountInfo4AddTeam(memberId, teamId);
        }

        // 消息HTML
        String html = "团队[@teamname@]已邀请您加入！请在“<a href=\"@account_url@\" target=\"_blank\">个人中心</a>” - “<a href=\"@account_url@/account/teams?id=@team_id@\" target=\"_blank\">我的团队</a>”查看。";
        html = html.replaceAll("@teamname@", teamName);
        html = html.replaceAll("@account_url@", AccountConfParser.getInstance().getAccountUrl());
        html = html.replaceAll("@team_id@", teamId);

        // 消息Data
        Map<String, String> msgDataMap = new HashMap<String, String>();
        msgDataMap.put("teamId", teamId);

        // 创建消息情报
        MessageInfo msg = new MessageInfo();
        msg.setFrom(userId);
        msg.setTo(memberId);
        msg.setTitle("团队邀请");
        msg.setHtml(html);
        msg.setData(JsonUtil.toJsonStr(msgDataMap));

        // 推送消息
        messageMngService.pushMessageInfo(msg);
    }

    /**
     * 申请加入团队
     * 
     * @param loginUser 登录用户
     * @param teamId 团队ID
     * @return 是否等待处理
     * @throws CmnBizException 异常
     */
    public boolean joinTeam(AccountInfo loginUser, String teamId) throws CmnBizException {
        String userId = loginUser.getId();
        String username = loginUser.getUsername();

        // 获取团队详细情报
        TeamInfo teamInfo = getTeamDetailInfo(teamId, true, null, null, null);

        // 检证邀请链接是否已失效
        if (StrUtil.isNotEmpty(teamInfo.getInviteExpiration())) {
            Timestamp inviteExpiration = DateUtil.toTimestamp(teamInfo.getInviteExpiration(), DateUtil.DATE_FORMAT);
            Date now = new Date();
            if (inviteExpiration.before(now)) { 
                Object[] args = new Object[] { teamId };
                logger.log(AccountMessageConst.MSG_11060002, args);
                throw new CmnBizException(AccountMessageConst.MSG_11060002, args);
            }
        }

        // 获取团队管理成员列表
        List<TeamMemberInfo> adminList = accountMngDao.getTeamMemberInfoList(teamId, null, true, null, null);

        // 邀请需要审核通过的场合
        if (teamInfo.getInviteApprove() != null && teamInfo.getInviteApprove().booleanValue()) {
            // 添加团队成员
            int number = accountMngDao.insertTeamMemberInfo4JoinTeam(teamId, userId, username, 0, true);
            if (number == 0) {
                return true;
            }

            // 推送消息
            if (ListUtil.isNotEmptyList(adminList)) {
                // 消息HTML
                String html = "有新用户申请加入团队！请在“<a href=\"@account_url@\" target=\"_blank\">个人中心</a>” - “<a href=\"@account_url@/account/teams?id=@team_id@\" target=\"_blank\">我的团队</a>”查看。";
                html = html.replaceAll("@account_url@", AccountConfParser.getInstance().getAccountUrl());
                html = html.replaceAll("@team_id@", teamId);

                // 消息Data
                Map<String, String> msgDataMap = new HashMap<String, String>();
                msgDataMap.put("teamId", teamId);

                // 创建消息情报
                MessageInfo msg = new MessageInfo();
     
                msg.setTitle("有新用户申请加入团队");
                msg.setHtml(html);
                msg.setData(JsonUtil.toJsonStr(msgDataMap));

                for (TeamMemberInfo admin : adminList) {
                    msg.setTo(admin.getUserId());

                    // 推送消息
                    messageMngService.pushMessageInfo(msg);
                }
            }

            return true;
        } else {
            // 添加团队成员
            int number = accountMngDao.insertTeamMemberInfo4JoinTeam(teamId, userId, username, 0, null);
            if (number == 0) {
                return true;
            }

            // 更新账号(添加团队)
            number = accountMngDao.updateAccountInfo4AddTeam(userId, teamId);
            if (number == 0) {
                return true;
            }

            // 推送消息
            if (ListUtil.isNotEmptyList(adminList)) {
                // 消息HTML
                String html = "有新用户加入了团队！请在“<a href=\"@account_url@\" target=\"_blank\">个人中心</a>” - “<a href=\"@account_url@/account/teams?id=@team_id@\" target=\"_blank\">我的团队</a>”查看。";
                html = html.replaceAll("@account_url@", AccountConfParser.getInstance().getAccountUrl());
                html = html.replaceAll("@team_id@", teamId);

                // 消息Data
                Map<String, String> msgDataMap = new HashMap<String, String>();
                msgDataMap.put("teamId", teamId);

                // 创建消息情报
                MessageInfo msg = new MessageInfo();
     
                msg.setTitle("有新用户加入了团队");
                msg.setHtml(html);
                msg.setData(JsonUtil.toJsonStr(msgDataMap));

                for (TeamMemberInfo admin : adminList) {
                    msg.setTo(admin.getUserId());

                    // 推送消息
                    messageMngService.pushMessageInfo(msg);
                }
            }

            return false;
        }
    }

    /**
     * 审核加入团队
     * 
     * @param loginUser 登录用户
     * @param reqInfo 团队API请求内容情报
     */
    public void teamAccept(AccountInfo loginUser, TeamReqInfo reqInfo) {
        String loginUserId = loginUser.getId();
        String teamId = reqInfo.getTeamId();
        String userId = reqInfo.getUserId();
        Boolean accept = reqInfo.getAccept();

        // 审核通过的场合
        if (accept.booleanValue()) {
            // 更新团队成员(审核通过)
            accountMngDao.updateTeamMemberInfo4Approved(loginUserId, teamId, userId);

            // 更新账号(添加团队)
            accountMngDao.updateAccountInfo4AddTeam(userId, teamId);
        } else {
            // 删除团队成员
            accountMngDao.deleteTeamMemberInfo4NotApprovedOrRemove(loginUserId, teamId, userId);

            // 更新账号(移除团队)
            accountMngDao.updateAccountInfo4RemoveTeam(userId, teamId);
        }
    }

    /**
     * 移除团队成员或退出团队
     * 
     * @param loginUser 登录用户
     * @param teamMemberInfo 团队成员情报
     */
    public void removeTeamMember(AccountInfo loginUser, TeamMemberInfo teamMemberInfo) {
        String loginUserId = loginUser.getId();
        String teamId = teamMemberInfo.getTeamId();
        String userId = teamMemberInfo.getUserId();

        if (loginUserId.equals(userId)) {
            // 退出团队
            accountMngDao.deleteTeamMemberInfo4Quit(teamId, userId);
        } else {
            // 移除团队成员
            accountMngDao.deleteTeamMemberInfo4NotApprovedOrRemove(loginUserId, teamId, userId);
        }

        // 更新账号(移除团队)
        accountMngDao.updateAccountInfo4RemoveTeam(userId, teamId);
    }
}
