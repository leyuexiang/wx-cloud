package cn.iocoder.wx.module.infra.framework.file.core.s3;

import cn.iocoder.wx.module.infra.framework.file.core.client.s3.S3FileClient;
import cn.iocoder.wx.module.infra.framework.file.core.client.s3.S3FileClientConfig;
import cn.iocoder.wx.module.infra.framework.file.core.client.s3.FilePresignedUrlRespDTO;
import io.minio.MinioClient;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/** GET 文件读取签名必须独立于历史 PUT 预签名，且有效期有界。 */
class S3FileClientPresignTest {

    @Test
    void createsGetSignatureWithoutChangingLegacyPutSignature() throws Exception {
        S3FileClientConfig config = new S3FileClientConfig();
        config.setEndpoint("http://127.0.0.1:9000");
        config.setBucket("private-images");
        config.setAccessKey("test-access");
        config.setAccessSecret("test-secret");
        S3FileClient client = new S3FileClient(9L, config);
        client.init();
        MinioClient minio = mock(MinioClient.class);
        ReflectionTestUtils.setField(client, "client", minio);
        when(minio.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenAnswer(invocation -> {
            GetPresignedObjectUrlArgs args = invocation.getArgument(0);
            return args.method() == Method.GET ? "https://signed.example.test/read" : "https://signed.example.test/put";
        });

        assertEquals("https://signed.example.test/read", client.getPresignedDownloadUrl("images/private/a.png", 600));
        FilePresignedUrlRespDTO legacyPut = client.getPresignedObjectUrl("images/private/a.png");
        assertEquals("https://signed.example.test/put", legacyPut.getUploadUrl());
        verify(minio).getPresignedObjectUrl(argThat(args -> args.method() == Method.GET));
        verify(minio).getPresignedObjectUrl(argThat(args -> args.method() == Method.PUT));
        assertThrows(IllegalArgumentException.class, () -> client.getPresignedDownloadUrl("key", 601));
        assertThrows(IllegalArgumentException.class, () -> client.getPresignedDownloadUrl("../outside", 60));
        verify(minio, times(2)).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }
}
