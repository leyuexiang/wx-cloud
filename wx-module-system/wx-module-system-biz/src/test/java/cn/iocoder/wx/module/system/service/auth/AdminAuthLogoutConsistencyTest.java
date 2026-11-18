package cn.iocoder.wx.module.system.service.auth;

import cn.iocoder.wx.framework.common.enums.UserTypeEnum;
import cn.iocoder.wx.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.wx.module.system.api.logger.dto.LoginLogCreateReqDTO;
import cn.iocoder.wx.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.wx.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.wx.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.wx.module.system.service.logger.LoginLogService;
import cn.iocoder.wx.module.system.service.member.MemberService;
import cn.iocoder.wx.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.wx.module.system.service.user.AdminUserService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 锁定已删除账号仍可完成令牌撤销和有效退出审计，日志失败不能被静默吞掉。 */
class AdminAuthLogoutConsistencyTest extends BaseMockitoUnitTest {
    @InjectMocks private AdminAuthServiceImpl auth;
    @Mock private OAuth2TokenService tokens;
    @Mock private AdminUserService users;
    @Mock private MemberService members;
    @Mock private LoginLogService logs;
    private static final String TOKEN = "unit-test-token";
    private static final int TYPE = LoginLogTypeEnum.LOGOUT_SELF.getType();

    @Test
    void deletedAdminKeepsTrustedIdentityInValidAudit() {
        when(tokens.removeAccessToken(TOKEN)).thenReturn(token(Long.MAX_VALUE, UserTypeEnum.ADMIN.getValue()));
        auth.logout(TOKEN, TYPE);
        LoginLogCreateReqDTO audit = audit();
        assertEquals("user#" + Long.MAX_VALUE, audit.getUsername());
        assertEquals(Long.MAX_VALUE, audit.getUserId());
        assertEquals(UserTypeEnum.ADMIN.getValue(), audit.getUserType());
        assertValidUsername(audit);
        var order = inOrder(tokens, logs);
        order.verify(tokens).removeAccessToken(TOKEN);
        order.verify(logs).createLoginLog(any());
    }

    @Test
    void existingAdminStillUsesRealUsername() {
        when(tokens.removeAccessToken(TOKEN)).thenReturn(token(7L, UserTypeEnum.ADMIN.getValue()));
        when(users.getUser(7L)).thenReturn(new AdminUserDO().setUsername("TenantAdmin30"));
        auth.logout(TOKEN, TYPE);
        assertEquals("TenantAdmin30", audit().getUsername());
    }

    @Test
    void blankAdminAccountDoesNotBreakAuditValidation() {
        when(tokens.removeAccessToken(TOKEN)).thenReturn(token(8L, UserTypeEnum.ADMIN.getValue()));
        when(users.getUser(8L)).thenReturn(new AdminUserDO().setUsername(" "));
        auth.logout(TOKEN, TYPE);
        assertEquals("user#8", audit().getUsername());
        assertValidUsername(audit());
    }

    @Test
    void missingMemberMobileKeepsMemberType() {
        when(tokens.removeAccessToken(TOKEN)).thenReturn(token(9L, UserTypeEnum.MEMBER.getValue()));
        auth.logout(TOKEN, TYPE);
        LoginLogCreateReqDTO audit = audit();
        assertEquals("user#9", audit.getUsername());
        assertEquals(UserTypeEnum.MEMBER.getValue(), audit.getUserType());
        assertValidUsername(audit);
    }

    @Test
    void alreadyRevokedTokenDoesNotCreateAnotherAudit() {
        auth.logout(TOKEN, TYPE);
        verifyNoInteractions(users, members, logs);
    }

    @Test
    void persistenceFailureRemainsVisibleAfterTokenRevocation() {
        when(tokens.removeAccessToken(TOKEN)).thenReturn(token(10L, UserTypeEnum.ADMIN.getValue()));
        doThrow(new IllegalStateException("测试日志存储故障")).when(logs).createLoginLog(any());
        assertThrows(IllegalStateException.class, () -> auth.logout(TOKEN, TYPE));
        verify(tokens).removeAccessToken(TOKEN);
    }

    private OAuth2AccessTokenDO token(long user, int type) {
        return new OAuth2AccessTokenDO().setUserId(user).setUserType(type);
    }

    private LoginLogCreateReqDTO audit() {
        ArgumentCaptor<LoginLogCreateReqDTO> value = ArgumentCaptor.forClass(LoginLogCreateReqDTO.class);
        verify(logs).createLoginLog(value.capture());
        return value.getValue();
    }

    private void assertValidUsername(LoginLogCreateReqDTO audit) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validateProperty(audit, "username").isEmpty());
        }
    }
}
