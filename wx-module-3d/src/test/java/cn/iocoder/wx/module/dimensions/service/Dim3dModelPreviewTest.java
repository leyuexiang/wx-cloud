package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.entity.Dim3dModel;
import cn.iocoder.wx.module.dimensions.service.impl.Dim3dResourceCatalogServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 通过实际目录查询验证预览来源和账号隔离，对象存储签发边界由资源服务回归另行覆盖。 */
class Dim3dModelPreviewTest {
    private JdbcTemplate jdbc;
    private Dim3dResourceCatalogServiceImpl service;
    private ArtResourceService resources;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE wx_3d_model(id BIGINT PRIMARY KEY, model_file_id BIGINT, resource_id BIGINT,"
                + "model_name VARCHAR(50),tenant_id VARCHAR(20),resource_belong VARCHAR(16),data_user_id BIGINT,"
                + "create_by BIGINT,price INT,del_flag CHAR DEFAULT '0')");
        jdbc.execute("CREATE TABLE wx_3d_resource(id BIGINT PRIMARY KEY,resource_name VARCHAR(150),asset_name VARCHAR(150),tenant_id VARCHAR(20),resource_type VARCHAR(20),"
                + "resource_relative_path VARCHAR(255),resource_scope VARCHAR(20),del_flag CHAR DEFAULT '0')");
        jdbc.update("INSERT INTO wx_3d_model(id,model_file_id,model_name,tenant_id,resource_belong,data_user_id,price)"
                + " VALUES(1,101,'历史模型','41','MINE',7001,12345)");
        jdbc.update("INSERT INTO wx_3d_resource(id,tenant_id,resource_type,resource_relative_path)"
                + " VALUES(101,'41','MODEL','models/tree.glb')");
        var access = mock(ArtResourceAccess.class);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(7001, 41));
        resources = mock(ArtResourceService.class);
        service = new Dim3dResourceCatalogServiceImpl();
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "access", access);
        ReflectionTestUtils.setField(service, "artResources", resources);
    }

    @AfterEach
    void release() { jdbc.execute("SHUTDOWN"); }

    @Test
    void detailIncludesActualLegacyResourcePath() {
        Dim3dModel result = service.getModel(1L);
        assertEquals("models/tree.glb", result.getResourceRelativePath());
        assertEquals(new BigDecimal("123.45"), result.getPriceYuan());
        verifyNoInteractions(resources);
    }

    @Test
    void managedDetailUsesAuthorizedPreviewAndPreservesSeparateIds() {
        jdbc.update("UPDATE wx_3d_model SET resource_id=201,model_file_id=NULL WHERE id=1");
        when(resources.modelPreview("201")).thenReturn(Map.of("modelFileUrl", "https://example.invalid/tree.glb",
                "downloadExpiresAt", "2030-01-01T00:00:00Z"));
        var result = service.getModel(1L);
        assertEquals("https://example.invalid/tree.glb", result.getModelFileUrl());
        assertEquals("2030-01-01T00:00:00Z", result.getDownloadExpiresAt());
        assertEquals(201L, result.getModelFileId());
        assertNull(result.getResourceRelativePath());
        assertEquals(201L, result.getResourceId());
        assertNull(jdbc.queryForObject("SELECT model_file_id FROM wx_3d_model WHERE id=1", Long.class));
    }

    @Test
    void hiddenDirectoryCannotTriggerSigning() {
        jdbc.update("UPDATE wx_3d_model SET resource_id=201,data_user_id=7002 WHERE id=1");
        assertThrows(ServiceException.class, () -> service.getModel(1L));
        verifyNoInteractions(resources);
    }

    @Test
    void missingWrongTypeAndOtherTenantFilesCannotSupplyPreview() {
        jdbc.update("UPDATE wx_3d_resource SET resource_type='SPRITE' WHERE id=101");
        assertThrows(ServiceException.class, () -> service.getModel(1L));
        jdbc.update("UPDATE wx_3d_resource SET resource_type='MODEL',tenant_id='42' WHERE id=101");
        assertThrows(ServiceException.class, () -> service.getModel(1L));
        jdbc.update("DELETE FROM wx_3d_resource WHERE id=101");
        assertThrows(ServiceException.class, () -> service.getModel(1L));
    }

    /** 分页只读取目录，不能因为预览服务故障阻断列表或产生逐条存储查询。 */
    @Test
    void pageDoesNotReadOrSignResourceFiles() {
        jdbc.update("UPDATE wx_3d_model SET resource_id=201 WHERE id=1");
        assertEquals(1, service.getModelPage(null, null, null, null, null, 1, 10).getTotal());
        verifyNoInteractions(resources);
    }

    /** 删除沿用原目录及资源保护检查，模型预览不可用时不应触发签名或清单读取。 */
    @Test
    void legacyRemovalDoesNotResolvePreview() {
        var models = mock(Dim3dModelWriteService.class);
        ReflectionTestUtils.setField(service, "models", models);
        jdbc.update("DELETE FROM wx_3d_resource WHERE id=101");
        service.deleteModel(1L);
        verifyNoInteractions(resources);
        verify(models).delete(1L);
    }

    @Test
    void managedRemovalUsesTransactionalModelWriter() {
        var models = mock(Dim3dModelWriteService.class);
        ReflectionTestUtils.setField(service, "models", models);
        jdbc.update("UPDATE wx_3d_model SET resource_id=201,model_file_id=NULL WHERE id=1");
        doThrow(new ServiceException(2030001020, "模型仍被发布引用"))
                .when(models).delete(1L);
        assertThrows(ServiceException.class, () -> service.deleteModel(1L));
        verify(resources, never()).modelPreview(anyString());
        verify(models).delete(1L);
    }

    @Test
    void resourceInfoUsesActualFileAndDirectoryVisibility() {
        var result = service.getModelResource(101L);
        assertEquals("101", result.get("id"));
        assertEquals("MODEL", result.get("resourceType"));
        assertEquals("models/tree.glb", result.get("resourceRelativePath"));
        jdbc.update("UPDATE wx_3d_model SET data_user_id=7002 WHERE id=1");
        assertThrows(ServiceException.class, () -> service.getModelResource(101L));
    }

    @Test
    void responseIdsRemainStringsAndPreviewFieldsCannotBeWritten() throws Exception {
        var mapper = new ObjectMapper();
        var model = new Dim3dModel();
        model.setId(9007199254740993L);
        model.setResourceId(9007199254740994L);
        model.setModelFileId(9007199254740995L);
        model.setCoverImageId(9007199254740996L);
        var result = mapper.valueToTree(model);
        for (String field : new String[]{"id", "resourceId", "modelFileId", "coverImageId"}) {
            assertTrue(result.path(field).isTextual(), field);
        }
        var request = mapper.readValue("{\"resourceId\":\"999\",\"modelFileUrl\":\"https://example.invalid/forged\","
                + "\"resourceRelativePath\":\"forged.glb\",\"downloadExpiresAt\":\"forged\",\"priceYuan\":1}", Dim3dModel.class);
        assertNull(request.getResourceId());
        assertNull(request.getModelFileUrl());
        assertNull(request.getResourceRelativePath());
        assertNull(request.getDownloadExpiresAt());
        assertNull(request.getPriceYuan());
    }
}
