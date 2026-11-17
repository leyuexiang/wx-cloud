package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.module.dimensions.entity.Dim3dModel;
import cn.iocoder.wx.module.system.api.dict.DictDataApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 实际内存关系库事务覆盖模型表单写入、可信归属、文件替换和引用删除保护。 */
class Dim3dModelWriteServiceTest {
    private JdbcTemplate jdbc;
    private Dim3dModelWriteService service;
    private ArtResourceAccess access;
    private SecurityFrameworkService security;
    private DictDataApi dictionaries;
    private ArtResourceObjectStore store;
    private ModelImportService imports;
    private ModelImportSourceWorker worker;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        var transactions = new DataSourceTransactionManager(source);
        jdbc.execute("""
                CREATE TABLE wx_3d_resource(id BIGINT PRIMARY KEY,resource_name VARCHAR(150),resource_type VARCHAR(20),
                    asset_name VARCHAR(150),resource_relative_path VARCHAR(255),resource_scope VARCHAR(20),tenant_id VARCHAR(20),
                    content_state VARCHAR(16),current_revision BIGINT DEFAULT 1,current_operation_id VARCHAR(36),
                    content_hash VARCHAR(64),manifest_sha256 VARCHAR(64),storage_prefix VARCHAR(512),manifest_path VARCHAR(512),
                    platform VARCHAR(20),content_format VARCHAR(32),app_compatibility VARCHAR(128),upload_source VARCHAR(20),
                    data_user_id BIGINT,create_by BIGINT,update_by BIGINT,del_flag CHAR DEFAULT '0')
                """);
        jdbc.execute("""
                CREATE TABLE wx_3d_model(id BIGINT PRIMARY KEY,resource_id BIGINT UNIQUE,model_file_id BIGINT,
                    model_name VARCHAR(50),model_desc VARCHAR(500),category VARCHAR(32),resource_belong VARCHAR(16),
                    is_paid VARCHAR(16),price INT,cover_image_id BIGINT,status VARCHAR(16),tenant_id VARCHAR(20),
                    data_user_id BIGINT,create_by BIGINT,update_by BIGINT,create_time TIMESTAMP,update_time TIMESTAMP,
                    del_flag CHAR DEFAULT '0')
                """);
        jdbc.execute("""
                CREATE TABLE wx_3d_resource_operation(id VARCHAR(36) PRIMARY KEY,resource_id BIGINT,operation_type VARCHAR(16),
                    resource_scope VARCHAR(20),tenant_id VARCHAR(20),operator_id BIGINT,request_hash VARCHAR(64),
                    expected_revision BIGINT,committed_revision BIGINT,status VARCHAR(16) DEFAULT 'UPLOADING',
                    storage_prefix VARCHAR(512),candidate_descriptor CLOB,before_snapshot CLOB,after_snapshot CLOB,
                    expires_at TIMESTAMP,error_code VARCHAR(64))
                """);
        jdbc.execute("CREATE TABLE wx_3d_resource_import(operation_id VARCHAR(36),status VARCHAR(24),cancel_requested BOOLEAN)");
        jdbc.execute("CREATE TABLE wx_3d_scene_publication_resource(id BIGINT PRIMARY KEY,resource_id BIGINT,resource_revision BIGINT)");
        for (String table : List.of("wx_3d_icon", "wx_3d_template")) {
            jdbc.execute("CREATE TABLE " + table + "(id BIGINT,resource_id BIGINT,tenant_id VARCHAR(20),del_flag CHAR,status VARCHAR(16))");
        }
        jdbc.execute("CREATE TABLE wx_infra_file(id BIGINT PRIMARY KEY,tenant_id BIGINT,uploader_id BIGINT,"
                + "visibility_scope VARCHAR(20),type VARCHAR(40),size INT,deleted INT DEFAULT 0,update_time TIMESTAMP)");
        jdbc.update("INSERT INTO wx_infra_file(id,tenant_id,uploader_id,visibility_scope,type,size) VALUES(5,2,10,'PRIVATE_UPLOAD','image/png',100)");
        jdbc.update("INSERT INTO wx_3d_resource(id,resource_type,resource_relative_path,tenant_id,create_by,content_state)"
                + " VALUES(101,'MODEL','tree.glb','2',10,'LEGACY'),(102,'MODEL','car.glb','2',10,'LEGACY'),"
                + "(103,'MODEL','other.glb','2',11,'LEGACY')");
        jdbc.update("INSERT INTO wx_3d_resource(id,resource_type,resource_scope,tenant_id,create_by,content_state,content_format)"
                + " VALUES(201,'MODEL','TENANT_SHARED','2',10,'READY','model.glb.v1')");
        access = mock(ArtResourceAccess.class);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(10, 2));
        security = mock(SecurityFrameworkService.class);
        when(security.hasPermission(anyString())).thenReturn(true);
        dictionaries = mock(DictDataApi.class);
        when(dictionaries.validateDictDataList(eq("dim3d_model_category"), anyCollection())).thenReturn(CommonResult.success(true));
        var images = new Dim3dImageResourceService();
        var resources = new ArtResourceService();
        var resourceTransactions = new ArtResourceTransactionService();
        service = new Dim3dModelWriteService();
        var json = new ObjectMapper();
        for (Object target : List.of(service, resources, resourceTransactions)) {
            ReflectionTestUtils.setField(target, "jdbc", jdbc);
            ReflectionTestUtils.setField(target, "transactions", transactions);
            ReflectionTestUtils.setField(target, "access", access);
            ReflectionTestUtils.setField(target, "json", json);
        }
        ReflectionTestUtils.setField(images, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "images", images);
        ReflectionTestUtils.setField(service, "security", security);
        ReflectionTestUtils.setField(service, "dictionaries", dictionaries);
        ReflectionTestUtils.setField(service, "resources", resources);
        ReflectionTestUtils.setField(service, "resourceTransactions", resourceTransactions);
        ReflectionTestUtils.setField(resourceTransactions, "resources", resources);
        store = mock(ArtResourceObjectStore.class);
        ReflectionTestUtils.setField(resourceTransactions, "store", store);
        imports = mock(ModelImportService.class);
        worker = mock(ModelImportSourceWorker.class);
        ReflectionTestUtils.setField(service, "imports", imports);
        ReflectionTestUtils.setField(service, "worker", worker);
    }

    @AfterEach
    void release() { jdbc.execute("SHUTDOWN"); }

    /** 从原目录服务入口验证受管模型可上下线，避免旧文件写保护阻断纯目录状态更新。 */
    @Test
    void managedModelStatusUsesUnifiedWriteService() {
        long id = service.create(form(201L));
        var catalog = new cn.iocoder.wx.module.dimensions.service.impl.Dim3dResourceCatalogServiceImpl();
        ReflectionTestUtils.setField(catalog, "jdbc", jdbc);
        ReflectionTestUtils.setField(catalog, "access", access);
        ReflectionTestUtils.setField(catalog, "models", service);
        ReflectionTestUtils.setField(catalog, "artResources", ReflectionTestUtils.getField(service, "resources"));
        catalog.changeModelStatus(id, "OFFLINE");
        assertEquals("OFFLINE", row(id).get("status"));
    }

    /** 两类目录均可往返切换，更新范围限定为状态与可信更新审计，资源及其他字段保持原值。 */
    @Test
    void statusRoundTripsPreserveMetadataAndResourceRevision() {
        for (long file : List.of(101L, 201L)) {
            long id = service.create(form(file));
            jdbc.update("UPDATE wx_3d_model SET update_by=99,update_time=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?", id);
            var before = row(id);
            var resource = jdbc.queryForMap("SELECT * FROM wx_3d_resource WHERE id=?", file);
            clearInvocations(dictionaries);
            service.changeStatus(id, "OFFLINE");
            var after = row(id);
            assertEquals("OFFLINE", after.get("status"));
            assertEquals(10L, after.get("update_by"));
            assertNotEquals(before.get("update_time"), after.get("update_time"));
            // 去除本次允许变更的三个字段后逐项比较，防止状态写入覆盖表单和文件关联。
            var fields = List.of("status", "update_by", "update_time");
            fields.forEach(before::remove);
            fields.forEach(after::remove);
            assertEquals(before, after);
            assertEquals(resource, jdbc.queryForMap("SELECT * FROM wx_3d_resource WHERE id=?", file));
            service.changeStatus(id, "ONLINE");
            assertEquals("ONLINE", row(id).get("status"));
            verifyNoInteractions(dictionaries, store, imports, worker);
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_resource_operation", Integer.class));
    }

    /** 重试相同状态应保全第一次更新审计，包括由其他平台维护账号写入的记录。 */
    @Test
    void repeatedStatusDoesNotRewriteAudit() {
        long id = service.create(form(201L));
        service.changeStatus(id, "OFFLINE");
        jdbc.update("UPDATE wx_3d_model SET update_by=99,update_time=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?", id);
        var before = row(id);
        service.changeStatus(id, "OFFLINE");
        assertEquals(before, row(id));
    }

    /** 非法状态和目录编号不得写库，逻辑删除目录也不能重新上线。 */
    @Test
    void statusRejectsInvalidInputAndDeletedDirectory() {
        long id = service.create(form(101L));
        var before = row(id);
        assertEquals(400, assertThrows(ServiceException.class, () -> service.changeStatus(id, null)).getCode());
        for (String state : List.of("online", "DELETED", "", " OFFLINE ")) {
            assertEquals(400, assertThrows(ServiceException.class, () -> service.changeStatus(id, state)).getCode());
        }
        assertEquals(400, assertThrows(ServiceException.class, () -> service.changeStatus(null, "ONLINE")).getCode());
        assertEquals(400, assertThrows(ServiceException.class, () -> service.changeStatus(0L, "ONLINE")).getCode());
        assertEquals(404, assertThrows(ServiceException.class, () -> service.changeStatus(999L, "ONLINE")).getCode());
        assertEquals(before, row(id));
        service.delete(id);
        var deleted = row(id);
        assertEquals(404, assertThrows(ServiceException.class, () -> service.changeStatus(id, "ONLINE")).getCode());
        assertEquals(deleted, row(id));
    }

    /** 服务层复核编辑权限及个人目录范围，避免内部调用绕开控制器或跨账号、跨租户写入。 */
    @Test
    void statusRequiresEditPermissionAndPersonalOwnership() {
        long id = service.create(form(201L));
        var before = row(id);
        when(security.hasPermission("dim3d:model:edit")).thenReturn(false);
        assertEquals(403, assertThrows(ServiceException.class, () -> service.changeStatus(id, "OFFLINE")).getCode());
        when(security.hasPermission("dim3d:model:edit")).thenReturn(true);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(11, 2));
        assertEquals(404, assertThrows(ServiceException.class, () -> service.changeStatus(id, "OFFLINE")).getCode());
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(10, 3));
        assertEquals(404, assertThrows(ServiceException.class, () -> service.changeStatus(id, "OFFLINE")).getCode());
        assertEquals(before, row(id));
    }

    /** 公共系统目录跨租户可读，状态维护仍须经过既有平台维护权限。 */
    @Test
    void systemStatusRequiresPlatformMaintenanceAndUsesTrustedActor() {
        var body = form(101L);
        body.setResourceBelong("SYSTEM");
        long id = service.create(body);
        var maintainer = new ArtResourceAccess.Actor(12, 3);
        when(access.actor()).thenReturn(maintainer);
        doThrow(new ServiceException(403, "无系统资源维护权限"))
                .when(access).maintain(maintainer, "GLOBAL_SYSTEM", "0");
        assertEquals(403, assertThrows(ServiceException.class, () -> service.changeStatus(id, "OFFLINE")).getCode());
        assertEquals("ONLINE", row(id).get("status"));
        doNothing().when(access).maintain(maintainer, "GLOBAL_SYSTEM", "0");
        service.changeStatus(id, "OFFLINE");
        assertEquals("OFFLINE", row(id).get("status"));
        assertEquals(12L, row(id).get("update_by"));
        assertEquals("0", row(id).get("tenant_id"));
    }

    /** 预检后发生归属迁移或删除时必须使用锁内现值拒绝，不覆盖新归属或复活目录。 */
    @Test
    void statusRechecksScopeAndDeletionAfterPreflight() {
        long id = service.create(form(101L));
        doAnswer(invocation -> {
            jdbc.update("UPDATE wx_3d_model SET resource_belong='SYSTEM',tenant_id='0' WHERE id=?", id);
            return null;
        }).when(access).maintain(any(), eq("TENANT_SHARED"), eq("2"));
        assertEquals(409, assertThrows(ServiceException.class, () -> service.changeStatus(id, "OFFLINE")).getCode());
        assertEquals("ONLINE", row(id).get("status"));
        doAnswer(invocation -> {
            jdbc.update("UPDATE wx_3d_model SET del_flag='1' WHERE id=?", id);
            return null;
        }).when(access).maintain(any(), eq("GLOBAL_SYSTEM"), eq("0"));
        assertEquals(404, assertThrows(ServiceException.class, () -> service.changeStatus(id, "OFFLINE")).getCode());
        assertEquals("ONLINE", row(id).get("status"));
        assertEquals("1", row(id).get("del_flag"));
    }

    /** 状态更新失败时事务回滚，状态及更新审计共同保持原值。 */
    @Test
    void statusUpdateFailureRollsBack() {
        long id = service.create(form(201L));
        var before = row(id);
        jdbc.execute("ALTER TABLE wx_3d_model ADD CONSTRAINT reject_offline CHECK(status<>'OFFLINE')");
        assertThrows(RuntimeException.class, () -> service.changeStatus(id, "OFFLINE"));
        assertEquals(before, row(id));
        verifyNoInteractions(store, imports, worker);
    }

    /** 下线后的普通编辑及文件替换沿用锁定目录状态，避免表单默认上线造成意外上架。 */
    @Test
    void offlineStatusSurvivesMetadataAndFileReplacement() {
        long id = service.create(form(101L));
        service.changeStatus(id, "OFFLINE");
        var body = form(101L);
        body.setModelName("下线后编辑");
        service.update(id, body);
        assertEquals("OFFLINE", row(id).get("status"));
        service.update(id, form(201L));
        assertEquals("OFFLINE", row(id).get("status"));
        assertEquals(201L, row(id).get("resource_id"));
    }

    @Test
    void createsPublicSystemAndIgnoresForgedIdentity() {
        var body = form(101L);
        body.setResourceBelong("SYSTEM");
        body.setTenantId("99");
        body.setDataUserId(99L);
        body.setCreateBy(99L);
        body.setCoverImageId(5L);
        long id = service.create(body);
        assertEquals("0", row(id).get("tenant_id"));
        assertEquals(10L, row(id).get("data_user_id"));
        assertEquals(10L, row(id).get("create_by"));
        assertEquals("0", jdbc.queryForObject("SELECT tenant_id FROM wx_3d_resource WHERE id=101", String.class));
        assertEquals("GLOBAL", coverScope());
    }

    @Test
    void supportsPaidPersonalModelAndManagedMetadataEditing() {
        var body = form(201L);
        body.setIsPaid("PAID");
        body.setPrice(1299);
        long id = service.create(body);
        var update = form(201L);
        update.setModelName("修改后的模型");
        update.setCoverImageId(5L);
        service.update(id, update);
        assertEquals("修改后的模型", row(id).get("model_name"));
        assertEquals(201L, row(id).get("resource_id"));
        assertNull(row(id).get("model_file_id"));
        assertEquals(0, row(id).get("price"));
        assertEquals("TENANT", coverScope());
        assertEquals(1L, jdbc.queryForObject("SELECT current_revision FROM wx_3d_resource WHERE id=201", Long.class));
        verifyNoInteractions(store);
    }

    @Test
    void omittedOptionalFieldsArePreservedAndExplicitNullClearsThem() throws Exception {
        var initial = form(201L);
        initial.setModelDesc("保留描述");
        initial.setCoverImageId(5L);
        long id = service.create(initial);
        var json = new ObjectMapper();
        String required = "{\"modelName\":\"修改模型\",\"category\":\"TREE\",\"resourceBelong\":\"MINE\","
                + "\"isPaid\":\"FREE\",\"modelFileId\":\"201\"}";
        service.update(id, json.readValue(required, Dim3dModel.class));
        assertEquals("保留描述", row(id).get("model_desc"));
        assertEquals(5L, row(id).get("cover_image_id"));
        service.update(id, json.readValue(required.replace("}", ",\"modelDesc\":null,\"coverImageId\":null}"), Dim3dModel.class));
        assertNull(row(id).get("model_desc"));
        assertNull(row(id).get("cover_image_id"));
    }

    @Test
    void replacesFileWithoutChangingDirectoryIdOrDeletingOldContent() {
        long id = service.create(form(101L));
        service.update(id, form(102L));
        assertEquals(102L, row(id).get("model_file_id"));
        service.update(id, form(201L));
        assertEquals(201L, row(id).get("resource_id"));
        assertNull(row(id).get("model_file_id"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_model", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_resource WHERE del_flag='0'", Integer.class));
    }

    @Test
    void restoresCoverWhenDirectoryUpdateFails() {
        long id = service.create(form(101L));
        jdbc.execute("ALTER TABLE wx_3d_model ADD CONSTRAINT reject_name CHECK(model_name<>'失败模型')");
        var body = form(102L);
        body.setModelName("失败模型");
        body.setCoverImageId(5L);
        assertThrows(RuntimeException.class, () -> service.update(id, body));
        assertEquals(101L, row(id).get("model_file_id"));
        assertNull(row(id).get("cover_image_id"));
        assertEquals("PRIVATE_UPLOAD", coverScope());
    }

    @Test
    void restoresPublicFileAndCoverWhenCreationFails() {
        jdbc.execute("ALTER TABLE wx_3d_model ADD CONSTRAINT reject_insert CHECK(model_name<>'测试模型')");
        var body = form(101L);
        body.setResourceBelong("SYSTEM");
        body.setCoverImageId(5L);
        assertThrows(RuntimeException.class, () -> service.create(body));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_model", Integer.class));
        assertEquals("2", jdbc.queryForObject("SELECT tenant_id FROM wx_3d_resource WHERE id=101", String.class));
        assertEquals("PRIVATE_UPLOAD", coverScope());
    }

    @Test
    void managedRemovalProtectsReferencesAndCommitsDirectoryWithResource() {
        long id = service.create(form(201L));
        jdbc.update("INSERT INTO wx_3d_scene_publication_resource VALUES(1,201,1)");
        assertEquals(2030001020, assertThrows(ServiceException.class, () -> service.delete(id)).getCode());
        assertEquals("0", row(id).get("del_flag"));
        assertEquals("0", jdbc.queryForObject("SELECT del_flag FROM wx_3d_resource WHERE id=201", String.class));
        jdbc.update("DELETE FROM wx_3d_scene_publication_resource WHERE id=1");
        service.delete(id);
        service.delete(id);
        assertEquals("1", row(id).get("del_flag"));
        assertEquals("1", jdbc.queryForObject("SELECT del_flag FROM wx_3d_resource WHERE id=201", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_resource_operation WHERE status='COMMITTED'", Integer.class));
        verifyNoInteractions(store);
    }

    @Test
    void legacyRemovalDoesNotRequirePhysicalPreviewFile() {
        long id = service.create(form(101L));
        jdbc.update("DELETE FROM wx_3d_resource WHERE id=101");
        service.delete(id);
        assertEquals("1", row(id).get("del_flag"));
        verifyNoInteractions(store);
    }

    @Test
    void rejectsAnotherAccountAndMissingMethodPermission() {
        long id = service.create(form(101L));
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(11, 2));
        assertEquals(404, assertThrows(ServiceException.class, () -> service.update(id, form(102L))).getCode());
        assertEquals(404, assertThrows(ServiceException.class, () -> service.delete(id)).getCode());
        when(security.hasPermission("dim3d:model:add")).thenReturn(false);
        assertEquals(403, assertThrows(ServiceException.class, () -> service.create(form(103L))).getCode());
        assertEquals("0", row(id).get("del_flag"));
    }

    @Test
    void rejectsOtherUploaderAndInvalidPaidAmount() {
        assertEquals(403, assertThrows(ServiceException.class, () -> service.create(form(103L))).getCode());
        var body = form(101L);
        body.setIsPaid("PAID");
        assertEquals(400, assertThrows(ServiceException.class, () -> service.create(body)).getCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_model", Integer.class));
    }

    @Test
    void duplicateManagedSaveKeepsSingleDirectoryUnderConcurrency() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> service.create(form(201L)));
            var second = pool.submit(() -> service.create(form(201L)));
            assertEquals(first.get(), second.get());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_model", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void cannotBindManagedFileAlreadyUsedByAnotherDirectory() {
        service.create(form(201L));
        long id = service.create(form(101L));
        assertEquals(409, assertThrows(ServiceException.class, () -> service.update(id, form(201L))).getCode());
        assertEquals(101L, row(id).get("model_file_id"));
    }

    @Test
    void changingSharedLegacyFileScopeCannotInvalidateOtherDirectories() {
        long first = service.create(form(101L));
        service.create(form(101L));
        var changed = form(101L);
        changed.setResourceBelong("SYSTEM");
        assertEquals(409, assertThrows(ServiceException.class, () -> service.update(first, changed)).getCode());
        assertEquals("2", jdbc.queryForObject("SELECT tenant_id FROM wx_3d_resource WHERE id=101", String.class));
        assertEquals("MINE", row(first).get("resource_belong"));
    }

    @Test
    void pendingImportCannotReturnSuccessBeforeCommit() {
        jdbc.update("UPDATE wx_3d_resource SET content_state='PENDING',upload_source='WEB_UPLOAD' WHERE id=201");
        jdbc.update("INSERT INTO wx_3d_resource_operation(id,resource_id,operation_type,operator_id,status) VALUES('op',201,'CREATE',10,'UPLOADING')");
        jdbc.update("INSERT INTO wx_3d_resource_import VALUES('op','PUBLISHING',FALSE)");
        var state = ModelImportStatusView.from("op", "201", "PUBLISHING", "UPLOADING", "glb", null, null, null, false);
        when(imports.requestPublish(eq("op"), any())).thenReturn(state);
        when(imports.get("op")).thenReturn(state);
        assertEquals(503, assertThrows(ServiceException.class, () -> service.create(form(201L))).getCode());
        verify(worker).publishOperation("op");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_model", Integer.class));
    }

    private java.util.Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT * FROM wx_3d_model WHERE id=?", id);
    }

    private String coverScope() { return jdbc.queryForObject("SELECT visibility_scope FROM wx_infra_file WHERE id=5", String.class); }

    private static Dim3dModel form(long file) {
        var body = new Dim3dModel();
        body.setModelName("测试模型");
        body.setCategory("TREE");
        body.setResourceBelong("MINE");
        body.setIsPaid("FREE");
        body.setModelFileId(file);
        return body;
    }
}
