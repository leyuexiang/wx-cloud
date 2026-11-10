package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.config.MinioConfigInfo;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/** 使用虚构单元测试密钥离线验签名有效期，不连接对象存储、不输出签名地址。 */
class ArtResourceDownloadSignerTest {
    private ArtResourceDownloadSigner signer() {
        var config = new MinioConfigInfo();
        config.setAccessKey("unit-access-key"); config.setSecretKey("unit-test-secret-key"); config.setBucketName("unit-bucket");
        var signer = new ArtResourceDownloadSigner();
        ReflectionTestUtils.setField(signer, "storage", config);
        ReflectionTestUtils.setField(signer, "endpoint", "https://example.invalid");
        ReflectionTestUtils.setField(signer, "region", "us-east-1");
        return signer;
    }

    @Test void actualSignedExpiryCannotExceedTicketDeadline() {
        Instant deadline = Instant.now().plusSeconds(60);
        var result = signer().signUntil("resources/frozen/", "manifest.json", deadline);
        Instant expires = Instant.parse(result.get("downloadExpiresAt"));
        assertTrue(expires.isAfter(Instant.now()));
        assertFalse(expires.isAfter(deadline));
        assertTrue(result.get("manifestUrl").startsWith("https://example.invalid/"));
    }

    @Test void expiredTicketAndUnsafePathCannotProduceUrl() {
        var signer = signer();
        assertEquals(401, assertThrows(ServiceException.class, () -> signer.signUntil("resources/frozen/", "manifest.json", Instant.now())).getCode());
        assertThrows(ServiceException.class, () -> signer.signUntil("resources/frozen/", "../secret", Instant.now().plusSeconds(60)));
        assertThrows(ServiceException.class, () -> signer.signUntil("resources/frozen/", "manifest.json", null));
    }
}
