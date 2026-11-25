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
    /** 原转换回执只能提供文件元数据，租户和上传账号必须由当前认证身份覆盖。 */
    @Test
    void legacyConversionRecordsTrustedUploaderAndStringFileId() {
        var service = new Dim3dResourceCatalogServiceImpl();
        var transport = new RestTemplate();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(transport).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://example.invalid/convert"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"code\":200,\"data\":{\"id\":\"9007199254740993\",\"resourceName\":\"模型\",\"resourceType\":\"MODEL\","
                                + "\"assetName\":\"model\",\"resourceRelativePath\":\"model.glb\",\"tenantId\":\"99\","
                                + "\"createBy\":99,\"dataUserId\":99}}", org.springframework.http.MediaType.APPLICATION_JSON));
        var access = mock(ArtResourceAccess.class);
        org.mockito.Mockito.when(access.actor()).thenReturn(new ArtResourceAccess.Actor(10, 2));
        var mapper = mock(cn.iocoder.wx.module.dimensions.mapper.Dim3dConvertedResourceMapper.class);
        ReflectionTestUtils.setField(service, "restTemplate", transport);
        ReflectionTestUtils.setField(service, "convertUrl", "https://example.invalid/convert");
        ReflectionTestUtils.setField(service, "convertedResourceMapper", mapper);
        ReflectionTestUtils.setField(service, "access", access);
        ReflectionTestUtils.setField(service, "artResources", mock(ArtResourceService.class));
        var file = new MockMultipartFile("file", "model.zip", "application/zip", new byte[]{80,75,3,4});
        var result = service.convertAndSave(file, null, null);
        org.junit.jupiter.api.Assertions.assertEquals("2", result.getTenantId());
        org.junit.jupiter.api.Assertions.assertEquals(10L, result.getCreateBy());
        org.junit.jupiter.api.Assertions.assertEquals(10L, result.getDataUserId());
        org.junit.jupiter.api.Assertions.assertTrue(new com.fasterxml.jackson.databind.ObjectMapper()
                .valueToTree(result).path("id").isTextual());
        server.verify();
    }

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
