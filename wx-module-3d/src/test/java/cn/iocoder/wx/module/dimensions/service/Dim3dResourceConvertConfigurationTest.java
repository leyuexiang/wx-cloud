package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.service.impl.Dim3dResourceCatalogServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** 未配置旧转换入口时必须在转发令牌和文件前拒绝，不能回退到内网测试主机。 */
class Dim3dResourceConvertConfigurationTest {
    @Test
    void shouldRejectMissingEndpointBeforeSendingRequest() {
        var service = new Dim3dResourceCatalogServiceImpl();
        var transport = mock(RestTemplate.class);
        ReflectionTestUtils.setField(service, "restTemplate", transport);
        ReflectionTestUtils.setField(service, "convertUrl", " ");
        var file = new MockMultipartFile("file", "model.zip", "application/zip", new byte[] {1});
        assertThatThrownBy(() -> service.convertAndSave(file, "Bearer test-only", "test-app"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("未配置");
        verifyNoInteractions(transport);
    }
}
