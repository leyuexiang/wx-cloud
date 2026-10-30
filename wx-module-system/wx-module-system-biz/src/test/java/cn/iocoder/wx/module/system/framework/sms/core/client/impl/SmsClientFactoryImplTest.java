package cn.iocoder.wx.module.system.framework.sms.core.client.impl;

import cn.iocoder.wx.module.system.framework.sms.core.enums.SmsChannelEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 验证未配置发送凭据时，回执解析客户端仍能初始化，不影响账号密码登录。 */
class SmsClientFactoryImplTest {

    @Test
    void shouldInitializeReceiptClientsWithoutSendingCredentials() {
        SmsClientFactoryImpl factory = new SmsClientFactoryImpl();
        for (SmsChannelEnum channel : SmsChannelEnum.values()) {
            assertNotNull(factory.getSmsClient(channel.getCode()), channel.getCode());
        }
    }
}
