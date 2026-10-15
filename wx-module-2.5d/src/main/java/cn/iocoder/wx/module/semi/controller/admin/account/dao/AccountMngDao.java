package cn.iocoder.wx.module.semi.controller.admin.account.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.DepartmentInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.TeamInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.TeamMemberInfo;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;


@Repository
public class AccountMngDao {

    @Autowired
    private SqlSessionTemplate sqlSession;

    private static final String NAMESPACE = "cn.iocoder.wx.module.semi.controller.admin.account.dao.AccountMngDao.";

    public boolean checkExistAccount(String id, String account, Boolean deleted) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("account", account);
        params.put("deleted", deleted);
        Integer result = sqlSession.selectOne(NAMESPACE + "checkExistAccount", params);
        return result > 0 ? true : false;
    }

    public boolean checkEmailUsedByOther(String id, String email) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("email", email);
        Integer result = sqlSession.selectOne(NAMESPACE + "checkEmailUsedByOther", params);
        return result > 0 ? true : false;
    }

    public boolean checkExistRole(String roleNm) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("roleNm", roleNm);
        Integer result = sqlSession.selectOne(NAMESPACE + "checkExistRole", params);
        return result > 0 ? true : false;
    }

    public void insertRole(String roleNm) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("roleNm", roleNm);
        sqlSession.insert(NAMESPACE + "insertRole", params);
    }

    public boolean checkExistLockedAccount(String account, boolean locked, boolean reset) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("account", account);
        params.put("locked", locked);
        params.put("reset", reset);
        Integer result = sqlSession.selectOne(NAMESPACE + "checkExistLockedAccount", params);
        return result > 0 ? true : false;
    }

    public void insertLockedAccount(String account) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("account", account);
        sqlSession.insert(NAMESPACE + "insertLockedAccount", params);
    }

    public void updateLockedAccount(String account, boolean reset) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("account", account);
        params.put("reset", reset);
        sqlSession.update(NAMESPACE + "updateLockedAccount", params);
    }

    public void deleteLockedAccount(String account) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("account", account);
        sqlSession.delete(NAMESPACE + "deleteLockedAccount", params);
    }
    
    public void registerAccount(AccountInfo accountInfo) {
        sqlSession.insert(NAMESPACE + "registerAccount", accountInfo);
    }

    public AccountInfo getAccountDetail(String id, String account, boolean pswdFlg) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("account", account);
        params.put("pswdFlg", pswdFlg);
        return sqlSession.selectOne(NAMESPACE + "getAccountDetail", params);
    }

    public AccountInfo getAccountSimple(String account) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("account", account);
        return sqlSession.selectOne(NAMESPACE + "getAccountSimple", params);
    }

    public List<AccountInfo> getAccountSimpleList(List<String> idList) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("idList", idList);
        return sqlSession.selectList(NAMESPACE + "getAccountSimpleList", params);
    }

    public boolean checkLogin(String account, String password) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("account", account);
        params.put("password", password);
        Integer result = sqlSession.selectOne(NAMESPACE + "checkLogin", params);
        return result > 0 ? true : false;
    }

    public void updateAccount(String id, String username, String avatarUrl) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("username", username);
        params.put("avatarUrl", avatarUrl);
        sqlSession.update(NAMESPACE + "updateAccount", params);
    }

    public void updatePassword(String account, String password) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("account", account);
        params.put("password", password);
        sqlSession.update(NAMESPACE + "updatePassword", params);
    }

    public void updateEmail(AccountInfo accountInfo) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", accountInfo.getId());
        params.put("account", accountInfo.getAccount());
        params.put("username", accountInfo.getUsername());
        params.put("email", accountInfo.getEmail());
        sqlSession.update(NAMESPACE + "updateEmail", params);
    }

    public int getDepartmentInfoTotal(String name, String parentId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("name", name);
        params.put("parentId", parentId);
        return sqlSession.selectOne(NAMESPACE + "getDepartmentInfoTotal", params);
    }

    public List<DepartmentInfo> getDepartmentInfoList(String name, String parentId,
                                                      int current, int pageSize) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("name", name);
        params.put("parentId", parentId);
        params.put("current", current);
        params.put("pageSize", pageSize);
        return sqlSession.selectList(NAMESPACE + "getDepartmentInfoList", params);
    }

    public List<Long> getRoleAuthCdList(List<String> roleNmList) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("roleNmList", roleNmList);
        return sqlSession.selectList(NAMESPACE + "getRoleAuthCdList", params);
    }

    public int updateAccountInfo4AddTeam(String id, String teamId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("teamId", teamId);
        return sqlSession.update(NAMESPACE + "updateAccountInfo4AddTeam", params);
    }

    public int updateAccountInfo4RemoveTeam(String id, String teamId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("teamId", teamId);
        return sqlSession.update(NAMESPACE + "updateAccountInfo4RemoveTeam", params);
    }

    public TeamInfo getTeamDetailInfo(String id, Boolean inviteLink, Boolean inviteExpiration,
                                      Boolean memberPending, String memberUserId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("inviteLink", inviteLink);
        params.put("inviteExpiration", inviteExpiration);
        params.put("memberPending", memberPending);
        params.put("memberUserId", memberUserId);
        return sqlSession.selectOne(NAMESPACE + "getTeamDetailInfo", params);
    }

    public int getTeamInfoTotal(String userId, String teamName) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("userId", userId);
        params.put("teamName", teamName);
        return sqlSession.selectOne(NAMESPACE + "getTeamInfoTotal", params);
    }

    public List<TeamInfo> getTeamInfoList(String userId, String teamName,
            int current, int pageSize) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("userId", userId);
        params.put("teamName", teamName);
        params.put("current", current);
        params.put("pageSize", pageSize);
        return sqlSession.selectList(NAMESPACE + "getTeamInfoList", params);
    }

    public int getTeamMemberInfoTotal(String teamId, String username) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("teamId", teamId);
        params.put("username", username);
        return sqlSession.selectOne(NAMESPACE + "getTeamMemberInfoTotal", params);
    }

    public List<TeamMemberInfo> getTeamMemberInfoList(String teamId, String username, Boolean isAdmin,
                                                      Integer current, Integer pageSize) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("teamId", teamId);
        params.put("username", username);
        params.put("isAdmin", isAdmin);
        params.put("current", current);
        params.put("pageSize", pageSize);
        return sqlSession.selectList(NAMESPACE + "getTeamMemberInfoList", params);
    }

    public String insertTeamInfo(TeamInfo teamInfo) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("teamInfo", teamInfo);
        sqlSession.insert(NAMESPACE + "insertTeamInfo", params);
        return teamInfo.getId();
    }

    public void updateTeamInfo(String userId, TeamInfo teamInfo) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("userId", userId);
        params.put("teamInfo", teamInfo);
        sqlSession.update(NAMESPACE + "updateTeamInfo", params);
    }

    public void updateTeamInfo4Owner(String id, String oldOwnerId, String newOwnerId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("oldOwnerId", oldOwnerId);
        params.put("newOwnerId", newOwnerId);
        sqlSession.update(NAMESPACE + "updateTeamInfo4Owner", params);
    }

    public void deleteTeamInfo(String id, String ownerId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("ownerId", ownerId);
        sqlSession.delete(NAMESPACE + "deleteTeamInfo", params);
    }

    public void insertTeamMemberInfo(String teamId, String userId, String username,
            int isAdmin, Boolean pending) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("teamId", teamId);
        params.put("userId", userId);
        params.put("username", username);
        params.put("isAdmin", isAdmin);
        params.put("pending", pending);
        sqlSession.insert(NAMESPACE + "insertTeamMemberInfo", params);
    }

    public int insertTeamMemberInfo4JoinTeam(String teamId, String userId, String username,
            int isAdmin, Boolean pending) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("teamId", teamId);
        params.put("userId", userId);
        params.put("username", username);
        params.put("isAdmin", isAdmin);
        params.put("pending", pending);
        return sqlSession.insert(NAMESPACE + "insertTeamMemberInfo4JoinTeam", params);
    }

    public void updateTeamMemberInfo4Admin(String loginUserId,
            String teamId, String userId, String username, int isAdmin) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("loginUserId", loginUserId);
        params.put("teamId", teamId);
        params.put("userId", userId);
        params.put("username", username);
        params.put("isAdmin", isAdmin);
        sqlSession.update(NAMESPACE + "updateTeamMemberInfo4Admin", params);
    }

    public void updateTeamMemberInfo4AddAdmin(String teamId, String userId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("teamId", teamId);
        params.put("userId", userId);
        sqlSession.update(NAMESPACE + "updateTeamMemberInfo4AddAdmin", params);
    }

    public void updateTeamMemberInfo4RemoveAdmin(String teamId, String userId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("teamId", teamId);
        params.put("userId", userId);
        sqlSession.update(NAMESPACE + "updateTeamMemberInfo4RemoveAdmin", params);
    }

    public void updateTeamMemberInfo4Approved(String approverUserId, String teamId, String approvedUserId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("approverUserId", approverUserId);
        params.put("teamId", teamId);
        params.put("approvedUserId", approvedUserId);
        sqlSession.update(NAMESPACE + "updateTeamMemberInfo4Approved", params);
    }

    public void deleteTeamMemberInfo(String teamId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("teamId", teamId);
        sqlSession.delete(NAMESPACE + "deleteTeamMemberInfo", params);
    }

    public void deleteTeamMemberInfo4NotApprovedOrRemove(String approverUserId, String teamId, String notApprovedOrRemoveUserId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("approverUserId", approverUserId);
        params.put("teamId", teamId);
        params.put("notApprovedOrRemoveUserId", notApprovedOrRemoveUserId);
        sqlSession.delete(NAMESPACE + "deleteTeamMemberInfo4NotApprovedOrRemove", params);
    }

    public void deleteTeamMemberInfo4Quit(String teamId, String userId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("teamId", teamId);
        params.put("userId", userId);
        sqlSession.delete(NAMESPACE + "deleteTeamMemberInfo4Quit", params);
    }
}
