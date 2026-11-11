package cn.iocoder.wx.module.infra.framework.file.core.s3;

import cn.iocoder.wx.module.infra.framework.file.core.client.s3.S3FileClient;
import cn.iocoder.wx.module.infra.framework.file.core.client.s3.S3FileClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 显式配置真实 MinIO 时执行，凭据从进程环境读取，所有测试对象在 finally 中精确回收。 */
@EnabledIfEnvironmentVariable(named = "WX_IMAGE_MINIO_ENDPOINT", matches = ".+")
class S3FileClientMinioIntegrationTest {
    @Test
    void uploadsPrivateImageAndDownloadsIdenticalBytesThroughGetSignature() throws Exception {
        var config = new S3FileClientConfig();
        config.setEndpoint(System.getenv("WX_IMAGE_MINIO_ENDPOINT"));
        config.setBucket(System.getenv("WX_IMAGE_MINIO_BUCKET"));
        config.setAccessKey(System.getenv("WX_MINIO_ACCESS_KEY"));
        config.setAccessSecret(System.getenv("WX_MINIO_SECRET_KEY"));
        var client = new S3FileClient(1L, config);
        client.init();
        String key = "images/verification/" + UUID.randomUUID() + ".png";
        byte[] bytes = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==");
        boolean uploaded = false;
        try {
            String plainUrl = client.upload(bytes, key, "image/png");
            uploaded = true;
            HttpURLConnection anonymous = (HttpURLConnection) URI.create(plainUrl).toURL().openConnection();
            anonymous.setConnectTimeout(5000);
            anonymous.setReadTimeout(5000);
            try {
                assertEquals(403, anonymous.getResponseCode(), "图片桶应维持私有访问");
            } finally {
                anonymous.disconnect();
            }
            String signedUrl = client.getPresignedDownloadUrl(key, 60);
            HttpURLConnection signed = (HttpURLConnection) URI.create(signedUrl).toURL().openConnection();
            signed.setConnectTimeout(5000);
            signed.setReadTimeout(5000);
            try (var input = signed.getInputStream()) {
                assertArrayEquals(bytes, input.readNBytes(bytes.length + 1));
            } finally {
                signed.disconnect();
            }
            assertArrayEquals(bytes, client.getContent(key));
        } finally {
            if (uploaded) client.delete(key);
        }
    }
}
