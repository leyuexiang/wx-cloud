package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.config.MinioConfigInfo;
import cn.iocoder.wx.module.dimensions.utils.MinioUtil;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 旧通用接口不能触碰 ART 专用对象；拒绝发生在任何远程副作用之前。 */
class MinioManagedWriteBoundaryTest {
    private MinioUtil util;
    private MinioClient client;
    @BeforeEach void setup() {
        util = new MinioUtil(); client = mock(MinioClient.class);
        var config = new MinioConfigInfo(); config.setBucketName("unit-resource-bucket");
        ReflectionTestUtils.setField(util, "minioClient", client);
        ReflectionTestUtils.setField(util, "minioConfigInfo", config);
    }
    @Test void legacyDeleteCannotRemoveManagedObject() {
        assertEquals(2030001021, assertThrows(ServiceException.class,
                () -> util.deleteObject("unit-resource-bucket", "resources/frozen/manifest.json")).getCode());
        verifyNoInteractions(client);
    }

    @Test void pathAliasesCannotBypassManagedNamespace() {
        for (String key : new String[]{"/resources/x", "./resources/x", "../resources/x", "folder/../resources/x", "folder/../../resources/x", "resources\\x", "//resources//x"})
            assertEquals(2030001021, assertThrows(ServiceException.class, () -> util.deleteObject("unit-resource-bucket", key)).getCode());
        verifyNoInteractions(client);
    }

    @Test void batchIsRejectedBeforeDeletingAnyOrdinaryObject() {
        assertThrows(ServiceException.class, () -> util.deleteObjects("unit-resource-bucket", java.util.List.of("uploads/old.txt", "resources/frozen/x")));
        verifyNoInteractions(client);
    }

    @Test void legacyUploadCopyAndMultipartCannotOverwriteManagedObjects() {
        assertThrows(ServiceException.class, () -> util.uploadFile("unit-resource-bucket", "resources/x", new java.io.ByteArrayInputStream(new byte[]{1})));
        assertThrows(ServiceException.class, () -> util.createDir("unit-resource-bucket", "resources/x"));
        assertThrows(ServiceException.class, () -> util.copyObject("other-bucket", "source", "unit-resource-bucket", "resources/x"));
        assertThrows(ServiceException.class, () -> util.getUploadObjectUrl("application/octet-stream", "resources/x"));
        assertThrows(ServiceException.class, () -> util.initMultiPartUpload(new cn.iocoder.wx.module.dimensions.domain.model.FileUploadInfo(), "resources/x"));
        assertThrows(ServiceException.class, () -> util.mergeMultipartUpload("resources/x", "unit-upload"));
        verifyNoInteractions(client);
    }

    @Test void ordinaryLegacyObjectsAndOtherBucketsKeepOriginalDeleteBehavior() throws Exception {
        util.deleteObject("unit-resource-bucket", "uploads/old.txt");
        util.deleteObject("other-bucket", "resources/old.txt");
        verify(client).removeObject(argThat(args -> args.bucket().equals("unit-resource-bucket") && args.object().equals("uploads/old.txt")));
        verify(client).removeObject(argThat(args -> args.bucket().equals("other-bucket") && args.object().equals("resources/old.txt")));
        verifyNoMoreInteractions(client);
    }
}
