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
                + "create_by BIGINT,price INT,status VARCHAR(16),del_flag CHAR DEFAULT '0')");
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
    void metadataDetailDoesNotReadOrSignPreviewFiles() {
        jdbc.execute("DROP TABLE wx_3d_resource");
        Dim3dModel result = service.getModel(1L);
        assertNull(result.getResourceRelativePath());
        assertEquals(101L, result.getModelFileId());
        assertEquals(new BigDecimal("123.45"), result.getPriceYuan());
        verifyNoInteractions(resources);
    }

    @Test
    void resourceInfoUsesRelativePathOnlyAndPreservesSeparateIds() {
        jdbc.update("UPDATE wx_3d_model SET resource_id=201,model_file_id=NULL WHERE id=1");
        jdbc.update("INSERT INTO wx_3d_resource(id,tenant_id,resource_type,resource_scope) VALUES(201,'41','MODEL','TENANT_SHARED')");
        when(resources.modelRelativePath("201")).thenReturn("resources/tree.glb");
        var result = service.getModelResource(201L);
        // 文件详情仅暴露既有业务元信息和相对路径，下载签名及有效期不能混入响应。
        assertEquals(java.util.Set.of("id", "resourceName", "resourceType", "assetName", "resourceRelativePath"), result.keySet());
        assertEquals("201", result.get("id"));
        assertEquals("resources/tree.glb", result.get("resourceRelativePath"));
        assertNull(jdbc.queryForObject("SELECT model_file_id FROM wx_3d_model WHERE id=1", Long.class));
        verify(resources).modelRelativePath("201");
        verifyNoMoreInteractions(resources);
    }

    /** 区分访问方式的轻量接口复用目录授权，受管响应不读取清单或生成签名。 */
    @Test
    void accessTypeUsesTrustedDirectoryBindingWithoutInspectingObjectStorage() {
        assertEquals(java.util.Map.of("id", "101", "accessMode", "LEGACY", "resourceRelativePath", "models/tree.glb"),
                service.getModelFileAccess(101L));
        jdbc.update("UPDATE wx_3d_model SET resource_id=201,model_file_id=NULL WHERE id=1");
        jdbc.update("INSERT INTO wx_3d_resource(id,tenant_id,resource_type,resource_scope) VALUES(201,'41','MODEL','TENANT_SHARED')");
        assertEquals(java.util.Map.of("id", "201", "accessMode", "MANAGED"), service.getModelFileAccess(201L));
        jdbc.update("UPDATE wx_3d_model SET data_user_id=7002 WHERE id=1");
        assertEquals(404, assertThrows(ServiceException.class, () -> service.getModelFileAccess(201L)).getCode());
        verifyNoInteractions(resources);
    }

    @Test
    void hiddenDirectoryCannotTriggerSigning() {
        jdbc.update("UPDATE wx_3d_model SET resource_id=201,data_user_id=7002 WHERE id=1");
        assertThrows(ServiceException.class, () -> service.getModel(1L));
        assertThrows(ServiceException.class, () -> service.getModelResource(201L));
        verifyNoInteractions(resources);
    }

    @Test
    void missingWrongTypeAndOtherTenantFilesCannotSupplyPreview() {
        jdbc.update("UPDATE wx_3d_resource SET resource_type='SPRITE' WHERE id=101");
        assertThrows(ServiceException.class, () -> service.getModelResource(101L));
        jdbc.update("UPDATE wx_3d_resource SET resource_type='MODEL',tenant_id='42' WHERE id=101");
        assertThrows(ServiceException.class, () -> service.getModelResource(101L));
        jdbc.update("DELETE FROM wx_3d_resource WHERE id=101");
        assertThrows(ServiceException.class, () -> service.getModelResource(101L));
    }

    /** 历史路径支持已有中文名称，空路径及错误关联的受管文件均不能作为历史资源返回。 */
    @Test
    void legacyResourceInfoPreservesChinesePathAndRejectsInvalidBinding() {
        String path = "dim3d/model/41/101/药材展示台/model.glb";
        jdbc.update("UPDATE wx_3d_resource SET resource_relative_path=? WHERE id=101", path);
        assertEquals(path, service.getModelResource(101L).get("resourceRelativePath"));
        jdbc.update("UPDATE wx_3d_resource SET resource_relative_path='' WHERE id=101");
        assertThrows(ServiceException.class, () -> service.getModelResource(101L));
        jdbc.update("UPDATE wx_3d_resource SET resource_relative_path=?,resource_scope='TENANT_SHARED' WHERE id=101", path);
        assertThrows(ServiceException.class, () -> service.getModelResource(101L));
        verifyNoInteractions(resources);
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
        // 历史资源与受管资源使用相同的对外字段集合。
        assertEquals(java.util.Set.of("id", "resourceName", "resourceType", "assetName", "resourceRelativePath"), result.keySet());
        assertEquals("101", result.get("id"));
        assertEquals("MODEL", result.get("resourceType"));
        assertEquals("models/tree.glb", result.get("resourceRelativePath"));
        jdbc.update("UPDATE wx_3d_model SET data_user_id=7002 WHERE id=1");
        assertThrows(ServiceException.class, () -> service.getModelResource(101L));
    }

    /** 下线后旧编号也不能解析到系统模型文件或访问方式。 */
    @Test
    void tenantReadsSystemFileOnlyWhileOnline() {
        jdbc.update("UPDATE wx_3d_model SET tenant_id='0',resource_belong='SYSTEM',status='ONLINE' WHERE id=1");
        jdbc.update("UPDATE wx_3d_resource SET tenant_id='0' WHERE id=101");
        assertEquals("101", service.getModelFileAccess(101L).get("id"));
        jdbc.update("UPDATE wx_3d_model SET status='OFFLINE' WHERE id=1");
        assertThrows(ServiceException.class, () -> service.getModelResource(101L));
        assertThrows(ServiceException.class, () -> service.getModelFileAccess(101L));
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
