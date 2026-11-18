package cn.iocoder.wx.module.system.service.auth;

import cn.iocoder.wx.module.system.controller.admin.auth.vo.AuthLoginReqVO;
import cn.iocoder.wx.module.system.controller.admin.auth.vo.AuthRegisterReqVO;
import cn.iocoder.wx.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import cn.iocoder.wx.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 通过真实约束校验锁定创建与登录合同，不仅检查注解文字。 */
class AuthAccountValidationTest {
    @Test
    void accountBoundariesMatchEveryCreationEntry() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            for (int length : new int[]{4, 16, 17, 30}) {
                String username = "A".repeat(length);
                for (Object request : requests(username)) {
                    assertTrue(factory.getValidator().validateProperty(request, "username").isEmpty(),
                            request.getClass().getSimpleName() + " 应接受 " + length + " 位账号");
                }
            }
        }
    }

    @Test
    void invalidLengthAndCharactersStayRejected() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            for (String username : List.of("abc", "a".repeat(31), "user_name", "user name", "中文账号")) {
                for (Object request : requests(username)) {
                    assertFalse(factory.getValidator().validateProperty(request, "username").isEmpty());
                }
            }
        }
    }

    @Test
    void passwordContractIsUnchanged() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            AuthLoginReqVO request = new AuthLoginReqVO().setPassword("p".repeat(16));
            assertTrue(factory.getValidator().validateProperty(request, "password").isEmpty());
            request.setPassword("p".repeat(17));
            assertFalse(factory.getValidator().validateProperty(request, "password").isEmpty());
        }
    }

    private List<Object> requests(String username) {
        return List.of(new AuthLoginReqVO().setUsername(username), new AuthRegisterReqVO().setUsername(username),
                new UserSaveReqVO().setUsername(username), new TenantSaveReqVO().setUsername(username));
    }
}
