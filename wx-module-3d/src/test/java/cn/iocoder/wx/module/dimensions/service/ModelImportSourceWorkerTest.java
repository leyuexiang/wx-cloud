package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实数据库锁和事务覆盖丢租约/迟到结果；存储边界模拟耗时及改写。 */
class ModelImportSourceWorkerTest {
    JdbcTemplate db;@InjectMocks TestWorker worker;ArtResourceObjectStore store;ArtResourceAccess access;
    @Mock PermissionApi permissions;
    @Mock ModelImportService importService;
    AutoCloseable mocks;
    String op=UUID.randomUUID().toString();
    static class TestWorker extends ModelImportSourceWorker {
        LocalDateTime now=LocalDateTime.of(2026,11,4,1,0);
        @Override protected LocalDateTime databaseNow(){return now;}
    }
    @BeforeEach void setup() {
        mocks=MockitoAnnotations.openMocks(this);
        when(permissions.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(CommonResult.success(true));
        var data=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");db=new JdbcTemplate(data);
        db.execute("CREATE TABLE wx_3d_resource(id BIGINT PRIMARY KEY,resource_scope VARCHAR(20),tenant_id VARCHAR(20),upload_source VARCHAR(20),del_flag CHAR,current_revision BIGINT,resource_type VARCHAR(16) DEFAULT 'MODEL',asset_name VARCHAR(150) DEFAULT 'sample',content_state VARCHAR(16) DEFAULT 'PENDING',current_operation_id VARCHAR(36),storage_prefix VARCHAR(512),manifest_path VARCHAR(512),manifest_sha256 VARCHAR(64),content_hash VARCHAR(64),content_format VARCHAR(32),platform VARCHAR(20),app_compatibility VARCHAR(128),update_by BIGINT)");
        db.execute("ALTER TABLE wx_3d_resource ADD data_user_id BIGINT");
        db.execute("CREATE TABLE wx_3d_resource_operation(id VARCHAR(36) PRIMARY KEY,resource_id BIGINT,resource_scope VARCHAR(20),tenant_id VARCHAR(20),operator_id BIGINT,status VARCHAR(16),expires_at TIMESTAMP,candidate_descriptor CLOB,operation_type VARCHAR(16) DEFAULT 'CREATE',expected_revision BIGINT DEFAULT 0,storage_prefix VARCHAR(512) DEFAULT 'resources/test/operations/',committed_revision BIGINT,before_snapshot CLOB,after_snapshot CLOB,error_code VARCHAR(64))");
        db.execute("CREATE TABLE wx_3d_resource_import(operation_id VARCHAR(36) PRIMARY KEY,resource_id BIGINT,status VARCHAR(24),source_size BIGINT,source_sha256 VARCHAR(64),frozen_source_key VARCHAR(512),attempt_id VARCHAR(36),lease_until TIMESTAMP,available_at TIMESTAMP,cancel_requested BOOLEAN DEFAULT FALSE,error_code VARCHAR(64),row_version BIGINT DEFAULT 0,target_format VARCHAR(4),artifact_descriptor CLOB,publish_descriptor CLOB)");
        db.update("INSERT INTO wx_3d_resource(id,resource_scope,tenant_id,upload_source,del_flag,current_revision) VALUES(101,'TENANT_SHARED','2','WEB_UPLOAD','0',0)");
        db.update("INSERT INTO wx_3d_resource_operation(id,resource_id,resource_scope,tenant_id,operator_id,status,expires_at,candidate_descriptor) VALUES(?,101,'TENANT_SHARED','2',10,'UPLOADING','2026-11-04 02:00:00','{\"modelImport\":true}')",op);
        db.update("INSERT INTO wx_3d_resource_import(operation_id,resource_id,status,source_size,source_sha256,available_at,target_format) VALUES(?,101,'QUEUED',4,?,'2026-11-04 00:00:00','glb')",op,"a".repeat(64));
        db.execute("CREATE TABLE wx_3d_model(id BIGINT AUTO_INCREMENT PRIMARY KEY,resource_id BIGINT,model_file_id BIGINT,tenant_id VARCHAR(20),del_flag CHAR DEFAULT '0',status VARCHAR(16) DEFAULT 'ONLINE',model_name VARCHAR(50),category VARCHAR(32),model_desc VARCHAR(500),resource_belong VARCHAR(16),is_paid VARCHAR(16),data_user_id BIGINT,create_by BIGINT,update_by BIGINT)");
        db.execute("ALTER TABLE wx_3d_model ADD price INT");
        db.execute("ALTER TABLE wx_3d_model ADD cover_image_id BIGINT");
        db.execute("ALTER TABLE wx_3d_model ADD update_time TIMESTAMP");
        db.execute("CREATE TABLE wx_infra_file(id BIGINT PRIMARY KEY,tenant_id BIGINT,uploader_id BIGINT,visibility_scope VARCHAR(20),type VARCHAR(40),size INT,deleted BOOLEAN DEFAULT FALSE,update_time TIMESTAMP)");
        db.update("INSERT INTO wx_infra_file VALUES(5,2,10,'PRIVATE_UPLOAD','image/png',50,FALSE,NULL)");
        for(String table:List.of("wx_3d_icon","wx_3d_template"))
            db.execute("CREATE TABLE "+table+"(id BIGINT PRIMARY KEY,resource_id BIGINT,tenant_id VARCHAR(20),del_flag CHAR,status VARCHAR(16))");
        db.execute("ALTER TABLE wx_3d_icon ADD icon_file_id BIGINT");
        db.execute("ALTER TABLE wx_3d_template ADD cover_image_id BIGINT");
        store=mock(ArtResourceObjectStore.class);access=mock(ArtResourceAccess.class);
        ReflectionTestUtils.setField(worker,"jdbc",db);ReflectionTestUtils.setField(worker,"transactions",new DataSourceTransactionManager(data));
        ReflectionTestUtils.setField(worker,"store",store);ReflectionTestUtils.setField(worker,"access",access);
    }
    /** 定向保存沿用真实发布执行器，只发布指定操作并提交目录和内容。 */
    @Test void publishesOnlyRequestedOperation() throws Exception {
        preparePublication();
        assertTrue(worker.publishOperation(op));
        assertEquals("COMMITTED", status());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wx_3d_model", Integer.class));
        // 正式内容仅保留资源下的一层版本目录，路径与模型及主清单的实际写入共同验证。
        String attempt = db.queryForObject("SELECT attempt_id FROM wx_3d_resource_import", String.class);
        String prefix = "resources/TENANT_SHARED/2/101/" + attempt + "/";
        assertEquals(prefix, db.queryForObject("SELECT storage_prefix FROM wx_3d_resource", String.class));
        verify(store).write(eq(prefix + "scene.glb"), any(), anyLong());
        verify(store).write(eq(prefix + "manifest.json"), any(), anyLong());
        var snapshot = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                db.queryForObject("SELECT after_snapshot FROM wx_3d_resource_operation", String.class));
        assertEquals(prefix, snapshot.path("resource").path("storage_prefix").asText());
    }

    /** 真正的后台校验和发布复用同一固定 ZIP，中文原成员按安全入口名写入且模型字节保持一致。 */
    @Test void localizedArchiveEntryPublishesWithVerifiedBytes() throws Exception {
        byte[] binary = ModelImportArtifactVerifierTest.glb(
                ModelImportArtifactVerifierTest.json("\"buffers\":[{\"byteLength\":4}]"), 4);
        preparePublication(modelZip("叉车.glb", binary));
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var plan = json.readTree(db.queryForObject("SELECT artifact_descriptor FROM wx_3d_resource_import", String.class))
                .path("validated");
        assertEquals("model.glb", plan.path("modelPath").asText());
        assertEquals("叉车.glb", plan.path("archiveModelPath").asText());
        doAnswer(invocation -> {
            assertArrayEquals(binary, ((java.io.InputStream) invocation.getArgument(1)).readAllBytes());
            return null;
        }).when(store).write(endsWith("/model.glb"), any(), eq((long) binary.length));
        assertTrue(worker.publishOperation(op));
        assertEquals("COMMITTED", status());
        String prefix = db.queryForObject("SELECT storage_prefix FROM wx_3d_resource", String.class);
        verify(store).write(eq(prefix + "model.glb"), any(), eq((long) binary.length));
        verify(store).write(eq(prefix + "manifest.json"), any(), anyLong());
        verify(store, never()).write(endsWith("/叉车.glb"), any(), anyLong());
    }

    @Test void redirectsNewFileIntoExistingDirectoryAtomically() throws Exception {
        preparePublication();
        redirectToLegacyDirectory();
        assertTrue(worker.publishOperation(op));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wx_3d_model", Integer.class));
        assertEquals(101L, db.queryForObject("SELECT resource_id FROM wx_3d_model WHERE id=44", Long.class));
        assertNull(db.queryForObject("SELECT model_file_id FROM wx_3d_model WHERE id=44", Long.class));
        assertEquals("OFFLINE", db.queryForObject("SELECT status FROM wx_3d_model WHERE id=44", String.class));
        assertEquals("COMMITTED", status());
    }

    @Test void redirectedPublicationRejectsConcurrentFileChange() throws Exception {
        preparePublication();
        redirectToLegacyDirectory();
        db.update("UPDATE wx_3d_model SET model_file_id=302 WHERE id=44");
        assertFalse(worker.publishOperation(op));
        assertEquals(302L, db.queryForObject("SELECT model_file_id FROM wx_3d_model WHERE id=44", Long.class));
        assertNull(db.queryForObject("SELECT resource_id FROM wx_3d_model WHERE id=44", Long.class));
        assertEquals(0L, db.queryForObject("SELECT current_revision FROM wx_3d_resource WHERE id=101", Long.class));
        assertEquals("FAILED", status());
    }

    @Test void redirectedPublicationRechecksEditPermission() throws Exception {
        preparePublication();
        redirectToLegacyDirectory();
        when(permissions.hasAnyPermissions(10L, "dim3d:model:edit")).thenReturn(CommonResult.success(false));
        assertFalse(worker.publishOperation(op));
        assertEquals(301L, db.queryForObject("SELECT model_file_id FROM wx_3d_model WHERE id=44", Long.class));
        assertEquals("IMPORT_PERMISSION_REVOKED", db.queryForObject("SELECT error_code FROM wx_3d_resource_import", String.class));
    }

    /** 实际表单服务贯通原发布确认和后台执行器，重复保存不会新增目录或内容修订。 */
    @Test void canonicalCreatePublishesOriginalUploadAndReplaysSingleDirectory() throws Exception {
        var catalog = catalogWriter();
        var body = catalogForm();
        long first = catalog.create(body);
        assertEquals(first, catalog.create(catalogForm()));
        assertEquals("COMMITTED", status());
        assertEquals(1L, db.queryForObject("SELECT current_revision FROM wx_3d_resource WHERE id=101", Long.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wx_3d_model", Integer.class));
        assertEquals(101L, db.queryForObject("SELECT resource_id FROM wx_3d_model WHERE id=?", Long.class, first));
        assertNull(db.queryForObject("SELECT model_file_id FROM wx_3d_model WHERE id=?", Long.class, first));
    }

    @Test void canonicalEditPublishesNewUploadIntoOriginalDirectory() throws Exception {
        var catalog = catalogWriter();
        db.update("INSERT INTO wx_3d_model(id,model_file_id,tenant_id,resource_belong,data_user_id,create_by,status,model_name,model_desc,cover_image_id)"
                + " VALUES(44,301,'2','MINE',10,10,'OFFLINE','旧模型','保留描述',5)");
        db.update("UPDATE wx_infra_file SET visibility_scope='TENANT',uploader_id=11 WHERE id=5");
        var system = catalogForm();
        system.setResourceBelong("SYSTEM");
        catalog.update(44L, system);
        assertEquals("COMMITTED", status());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wx_3d_model", Integer.class));
        assertEquals(101L, db.queryForObject("SELECT resource_id FROM wx_3d_model WHERE id=44", Long.class));
        assertNull(db.queryForObject("SELECT model_file_id FROM wx_3d_model WHERE id=44", Long.class));
        assertEquals("OFFLINE", db.queryForObject("SELECT status FROM wx_3d_model WHERE id=44", String.class));
        assertEquals("保留描述", db.queryForObject("SELECT model_desc FROM wx_3d_model WHERE id=44", String.class));
        assertEquals(5L, db.queryForObject("SELECT cover_image_id FROM wx_3d_model WHERE id=44", Long.class));
        assertEquals("SYSTEM", db.queryForObject("SELECT resource_belong FROM wx_3d_model WHERE id=44", String.class));
        assertEquals("GLOBAL", db.queryForObject("SELECT visibility_scope FROM wx_infra_file WHERE id=5", String.class));
    }

    /** 暂存文件可在新增时选择系统归属，同一文件和表单重试只产生一条系统目录。 */
    @Test void canonicalCreateUsesScopeSelectedAtSave() throws Exception {
        var catalog = catalogWriter();
        assertEquals("TENANT_SHARED", db.queryForObject("SELECT resource_scope FROM wx_3d_resource", String.class));
        var system = catalogForm();
        system.setResourceBelong("SYSTEM");
        long id = catalog.create(system);
        assertEquals(id, catalog.create(system));
        assertEquals("GLOBAL_SYSTEM", db.queryForObject("SELECT resource_scope FROM wx_3d_resource_operation", String.class));
        assertEquals("0", db.queryForObject("SELECT tenant_id FROM wx_3d_resource", String.class));
        assertEquals("SYSTEM", db.queryForObject("SELECT resource_belong FROM wx_3d_model", String.class));
        String prefix = db.queryForObject("SELECT storage_prefix FROM wx_3d_resource", String.class);
        assertTrue(prefix.startsWith("resources/GLOBAL_SYSTEM/0/101/"));
    }

    /** 原位替换可随保存调整归属，并保留目录编号、文件编号与当前上下线状态。 */
    @Test void canonicalReplacementCanSaveAnotherScope() throws Exception {
        var catalog = catalogWriter();
        db.update("UPDATE wx_3d_resource SET current_revision=1,content_state='READY'");
        db.update("UPDATE wx_3d_resource_operation SET operation_type='REPLACE',expected_revision=1");
        db.update("INSERT INTO wx_3d_model(id,resource_id,tenant_id,resource_belong,data_user_id,status,model_name)"
                + " VALUES(44,101,'2','MINE',10,'OFFLINE','旧模型')");
        var system = catalogForm();
        system.setResourceBelong("SYSTEM");
        catalog.replaceManaged(44L, system, op);
        catalog.replaceManaged(44L, system, op);
        assertEquals("SYSTEM", db.queryForObject("SELECT resource_belong FROM wx_3d_model WHERE id=44 AND resource_id=101", String.class));
        assertEquals("OFFLINE", db.queryForObject("SELECT status FROM wx_3d_model WHERE id=44", String.class));
        assertEquals(2L, db.queryForObject("SELECT current_revision FROM wx_3d_resource", Long.class));
    }

    private cn.iocoder.wx.module.dimensions.entity.Dim3dModel catalogForm() {
        var body = new cn.iocoder.wx.module.dimensions.entity.Dim3dModel();
        body.setModelName("样本模型");
        body.setCategory("sample");
        body.setResourceBelong("MINE");
        body.setIsPaid("FREE");
        body.setModelFileId(101L);
        return body;
    }

    /** 复用现有真实发布夹具，替换其确认服务测试替身以覆盖目录保存的正常入口。 */
    private Dim3dModelWriteService catalogWriter() throws Exception {
        preparePublication();
        db.update("UPDATE wx_3d_resource_import SET publish_descriptor=NULL");
        db.update("UPDATE wx_3d_resource_operation SET expires_at=?", LocalDateTime.now().plusDays(1));
        db.execute("ALTER TABLE wx_3d_resource ADD create_by BIGINT DEFAULT 10");
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(10, 2));
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var security = mock(cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService.class);
        when(security.hasPermission(anyString())).thenReturn(true);
        var dictionary = mock(cn.iocoder.wx.module.system.api.dict.DictDataApi.class);
        when(dictionary.validateDictDataList(eq("dim3d_model_category"), anyCollection())).thenReturn(CommonResult.success(true));
        var actualImports = new ModelImportService();
        ReflectionTestUtils.setField(actualImports, "enabled", true);
        var catalog = new Dim3dModelWriteService();
        var transactions = ReflectionTestUtils.getField(worker, "transactions");
        var publicationTransaction = ReflectionTestUtils.getField(worker, "resourceTransactions");
        var images = ReflectionTestUtils.getField(publicationTransaction, "images");
        for (Object target : List.of(actualImports, catalog)) {
            ReflectionTestUtils.setField(target, "jdbc", db);
            ReflectionTestUtils.setField(target, "transactions", transactions);
            ReflectionTestUtils.setField(target, "access", access);
            ReflectionTestUtils.setField(target, "json", json);
            ReflectionTestUtils.setField(target, "dictionaries", dictionary);
            ReflectionTestUtils.setField(target, "images", images);
        }
        ReflectionTestUtils.setField(actualImports, "permissions", security);
        ReflectionTestUtils.setField(worker, "importService", actualImports);
        ReflectionTestUtils.setField(catalog, "security", security);
        ReflectionTestUtils.setField(catalog, "imports", actualImports);
        ReflectionTestUtils.setField(catalog, "worker", worker);
        return catalog;
    }

    /** 内部目标只能由服务端确认生成，夹具固定旧关联用于最终事务的并发检查。 */
    private void redirectToLegacyDirectory() throws Exception {
        db.update("INSERT INTO wx_3d_model(id,model_file_id,tenant_id,resource_belong,data_user_id,create_by,status,model_name)"
                + " VALUES(44,301,'2','MINE',10,10,'OFFLINE','旧模型')");
        var directory = (com.fasterxml.jackson.databind.node.ObjectNode) new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                db.queryForObject("SELECT publish_descriptor FROM wx_3d_resource_import WHERE operation_id=?", String.class, op));
        directory.putObject("catalogTarget").put("id", "44").put("resourceId", "").put("modelFileId", "301")
                .put("tenantId", "2").put("resourceBelong", "MINE");
        db.update("UPDATE wx_3d_resource_import SET publish_descriptor=? WHERE operation_id=?", directory.toString(), op);
    }

    @Test void publishesDirectoryPointerAndReceiptTogether() throws Exception {
        preparePublication();
        assertTrue(worker.runNext());assertEquals("COMMITTED",status());
        assertEquals(1L,db.queryForObject("SELECT current_revision FROM wx_3d_resource",Long.class));
        assertEquals(101L,db.queryForObject("SELECT resource_id FROM wx_3d_model",Long.class));
        // 新增目录继承原上传操作的账号和归属，客户端不能在保存时改写可信所有者。
        assertEquals(10L, db.queryForObject("SELECT data_user_id FROM wx_3d_model", Long.class));
        assertEquals(10L, db.queryForObject("SELECT create_by FROM wx_3d_model", Long.class));
        assertEquals("MINE", db.queryForObject("SELECT resource_belong FROM wx_3d_model", String.class));
        assertNull(db.queryForObject("SELECT model_file_id FROM wx_3d_model",Long.class));
        assertEquals("COMMITTED",db.queryForObject("SELECT status FROM wx_3d_resource_operation",String.class));
        assertFalse(worker.runNext());
    }
    @Test void publishesCoverAndPaidDirectoryTogetherWithReceipt() throws Exception {
        // 覆盖真实后台发布入口，封面私有归属和收费字段必须随同一提交改变。
        prepareCoveredSystemPublication();
        assertTrue(worker.runNext());
        assertEquals("COMMITTED", status());
        assertEquals("PAID", db.queryForObject("SELECT is_paid FROM wx_3d_model", String.class));
        assertEquals(1299, db.queryForObject("SELECT price FROM wx_3d_model", Integer.class));
        assertEquals(5L, db.queryForObject("SELECT cover_image_id FROM wx_3d_model", Long.class));
        assertEquals("GLOBAL", db.queryForObject("SELECT visibility_scope FROM wx_infra_file", String.class));
        assertEquals(0L, db.queryForObject("SELECT tenant_id FROM wx_infra_file", Long.class));
        assertEquals(1L, db.queryForObject("SELECT committed_revision FROM wx_3d_resource_operation", Long.class));
        // 系统模型使用公共所有者零，正式前缀不继承上传操作目录的旧布局。
        String attempt = db.queryForObject("SELECT attempt_id FROM wx_3d_resource_import", String.class);
        assertEquals("resources/GLOBAL_SYSTEM/0/101/" + attempt + "/",
                db.queryForObject("SELECT storage_prefix FROM wx_3d_resource", String.class));
    }
    @Test void receiptFailureAlsoRollsBackCoverVisibilityAndPaidDirectory() throws Exception {
        prepareCoveredSystemPublication();
        db.execute("ALTER TABLE wx_3d_resource_operation ADD CHECK(status<>'COMMITTED')");
        assertThrows(org.springframework.dao.DataAccessException.class, () -> worker.runNext());
        assertEquals("PUBLISHING", status());
        assertEquals("PRIVATE_UPLOAD", db.queryForObject("SELECT visibility_scope FROM wx_infra_file", String.class));
        assertEquals(2L, db.queryForObject("SELECT tenant_id FROM wx_infra_file", Long.class));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM wx_3d_model", Long.class));
        assertEquals(0L, db.queryForObject("SELECT current_revision FROM wx_3d_resource", Long.class));
    }

    @Test void removedCoverFailsWithoutPublishingModelOrReceipt() throws Exception {
        prepareCoveredSystemPublication();
        db.update("UPDATE wx_infra_file SET deleted=TRUE");
        assertFalse(worker.runNext());
        assertEquals("FAILED", status());
        assertEquals("DIRECTORY_INVALID", db.queryForObject("SELECT error_code FROM wx_3d_resource_import", String.class));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM wx_3d_model", Long.class));
        assertEquals(0L, db.queryForObject("SELECT current_revision FROM wx_3d_resource", Long.class));
    }

    @Test void disabledCategoryAfterConfirmationFailsBeforeWritingPublication() throws Exception {
        preparePublication();
        clearInvocations(store);
        doThrow(new ServiceException(400, "分类已停用")).when(importService).validateCategory("sample");
        assertFalse(worker.runNext());
        assertEquals("DIRECTORY_INVALID", db.queryForObject("SELECT error_code FROM wx_3d_resource_import", String.class));
        assertEquals(0L, db.queryForObject("SELECT current_revision FROM wx_3d_resource", Long.class));
        verify(store, never()).write(anyString(), any(), anyLong());
    }

    @Test void backgroundDictionaryCallUsesOriginalTenantAndRestoresThreadContext() throws Exception {
        prepareCoveredSystemPublication();
        TenantContextHolder.setTenantId(88L);
        TenantContextHolder.setIgnore(true);
        doAnswer(invocation -> {
            assertEquals(2L, TenantContextHolder.getTenantId());
            assertFalse(TenantContextHolder.isIgnore());
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            throw new ServiceException(400, "分类已停用");
        }).when(importService).validateCategory("sample");
        assertFalse(worker.runNext());
        assertEquals(88L, TenantContextHolder.getTenantId());
        assertTrue(TenantContextHolder.isIgnore());
        assertEquals("DIRECTORY_INVALID", db.queryForObject("SELECT error_code FROM wx_3d_resource_import", String.class));
    }

    @Test void replacementOmittedFieldsPreserveExistingCoverPriceDescriptionAndStatus() throws Exception {
        prepareCoveredSystemPublication();
        db.update("UPDATE wx_3d_resource SET current_revision=1,content_state='READY'");
        db.update("UPDATE wx_3d_resource_operation SET operation_type='REPLACE',expected_revision=1");
        db.update("INSERT INTO wx_3d_model(id,resource_id,tenant_id,status,model_name,model_desc,is_paid,price,cover_image_id) VALUES(44,101,'0','OFFLINE','旧模型','旧描述','PAID',999,5)");
        db.update("UPDATE wx_infra_file SET visibility_scope='GLOBAL',tenant_id=0,uploader_id=42");
        db.update("UPDATE wx_3d_resource_import SET publish_descriptor=?", "{\"modelName\":\"替换模型\",\"category\":\"sample\"}");
        assertTrue(worker.runNext());
        var directory = db.queryForMap("SELECT * FROM wx_3d_model");
        assertEquals(44L, ((Number) directory.get("id")).longValue());
        assertEquals("OFFLINE", directory.get("status"));
        assertEquals("旧描述", directory.get("model_desc"));
        assertEquals("PAID", directory.get("is_paid"));
        assertEquals(999, directory.get("price"));
        assertEquals(5L, ((Number) directory.get("cover_image_id")).longValue());
        assertEquals(2L, db.queryForObject("SELECT current_revision FROM wx_3d_resource", Long.class));
    }

    @Test void replacementCanExplicitlyClearCoverDescriptionAndChangeToFree() throws Exception {
        prepareCoveredSystemPublication();
        db.update("UPDATE wx_3d_resource SET current_revision=1,content_state='READY'");
        db.update("UPDATE wx_3d_resource_operation SET operation_type='REPLACE',expected_revision=1");
        db.update("INSERT INTO wx_3d_model(id,resource_id,tenant_id,status,model_name,model_desc,is_paid,price,cover_image_id) VALUES(44,101,'0','OFFLINE','旧模型','旧描述','PAID',999,5)");
        db.update("UPDATE wx_3d_resource_import SET publish_descriptor=?",
                "{\"modelName\":\"免费模型\",\"category\":\"sample\",\"modelDesc\":\"\",\"coverImageId\":null,\"isPaid\":\"FREE\",\"price\":0}");
        assertTrue(worker.runNext());
        var directory = db.queryForMap("SELECT * FROM wx_3d_model");
        assertEquals("OFFLINE", directory.get("status"));
        assertEquals("", directory.get("model_desc"));
        assertEquals("FREE", directory.get("is_paid"));
        assertEquals(0, directory.get("price"));
        assertNull(directory.get("cover_image_id"));
    }

    private void prepareCoveredSystemPublication() throws Exception {
        preparePublication();
        ReflectionTestUtils.setField(worker, "platformTenant", 2L);
        db.update("UPDATE wx_3d_resource SET resource_scope='GLOBAL_SYSTEM',tenant_id='0'");
        db.update("UPDATE wx_3d_resource_operation SET resource_scope='GLOBAL_SYSTEM',tenant_id='0'");
        db.update("UPDATE wx_3d_resource_import SET publish_descriptor=?",
                "{\"modelName\":\"完整模型\",\"category\":\"sample\",\"coverImageId\":\"5\",\"isPaid\":\"PAID\",\"price\":1299}");
    }
    @Test void receiptFailureRollsBackDirectoryAndPointer() throws Exception {
        preparePublication();
        db.execute("ALTER TABLE wx_3d_resource_operation ADD CHECK(status<>'COMMITTED')");
        assertThrows(org.springframework.dao.DataAccessException.class,()->worker.runNext());
        assertEquals("PUBLISHING",status());
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM wx_3d_model",Long.class));
        assertEquals(0L,db.queryForObject("SELECT current_revision FROM wx_3d_resource",Long.class));
    }
    @Test void publicationConflictKeepsExistingRevisionAndDirectory() throws Exception {
        preparePublication();
        db.update("UPDATE wx_3d_resource SET current_revision=2");
        assertFalse(worker.runNext());assertEquals("CONFLICT",status());
        assertEquals(2L,db.queryForObject("SELECT current_revision FROM wx_3d_resource",Long.class));
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM wx_3d_model",Long.class));
    }
    @Test void replacementKeepsDirectoryIdAndStatus() throws Exception {
        preparePublication();
        db.update("UPDATE wx_3d_resource SET current_revision=1,content_state='READY'");
        db.update("UPDATE wx_3d_resource_operation SET operation_type='REPLACE',expected_revision=1");
        db.update("INSERT INTO wx_3d_model(id,resource_id,tenant_id,status,model_name) VALUES(44,101,'2','OFFLINE','旧模型')");
        assertTrue(worker.runNext());
        assertEquals(2L,db.queryForObject("SELECT current_revision FROM wx_3d_resource",Long.class));
        assertEquals(44L,db.queryForObject("SELECT id FROM wx_3d_model",Long.class));
        assertEquals("OFFLINE",db.queryForObject("SELECT status FROM wx_3d_model",String.class));
        assertEquals("样本模型",db.queryForObject("SELECT model_name FROM wx_3d_model",String.class));
    }
    /** 写入模型后主清单失败的恢复使用全新版本目录，已写入的原尝试文件不会被覆盖。 */
    @Test void publicationRetryWritesDistinctContentVersion() throws Exception {
        preparePublication();
        clearInvocations(store);
        var writeCount = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            if (writeCount.incrementAndGet() == 2) throw new ServiceException(503, "临时存储故障");
            return null;
        }).when(store).write(anyString(), any(), anyLong());
        assertFalse(worker.runNext());
        String previousAttempt = db.queryForObject("SELECT attempt_id FROM wx_3d_resource_import", String.class);
        assertEquals("PUBLISHING", status());
        assertEquals(0L, db.queryForObject("SELECT current_revision FROM wx_3d_resource", Long.class));
        assertNull(db.queryForObject("SELECT storage_prefix FROM wx_3d_resource", String.class));
        worker.now = worker.now.plusSeconds(121);
        assertTrue(worker.runNext());
        String currentAttempt = db.queryForObject("SELECT attempt_id FROM wx_3d_resource_import", String.class);
        assertNotEquals(previousAttempt, currentAttempt);
        String previousPrefix = "resources/TENANT_SHARED/2/101/" + previousAttempt + "/";
        String currentPrefix = "resources/TENANT_SHARED/2/101/" + currentAttempt + "/";
        var keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store, times(4)).write(keys.capture(), any(), anyLong());
        assertEquals(List.of(previousPrefix + "scene.glb", previousPrefix + "manifest.json",
                currentPrefix + "scene.glb", currentPrefix + "manifest.json"), keys.getAllValues());
        assertEquals(currentPrefix, db.queryForObject("SELECT storage_prefix FROM wx_3d_resource", String.class));
        assertEquals(1L, db.queryForObject("SELECT current_revision FROM wx_3d_resource", Long.class));
        assertEquals("COMMITTED", status());
    }

    @Test void cancelledDuringObjectWriteCannotPublishDirectoryOrRevision() throws Exception {
        preparePublication();
        doAnswer(inv->{db.update("UPDATE wx_3d_resource_import SET cancel_requested=TRUE");return null;})
                .when(store).write(anyString(),any(),anyLong());
        assertFalse(worker.runNext());
        assertEquals("PUBLISHING",status());
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM wx_3d_model",Long.class));
        assertEquals(0L,db.queryForObject("SELECT current_revision FROM wx_3d_resource",Long.class));
    }
    private void preparePublication() throws Exception {
        preparePublication(validZip());
    }

    private void preparePublication(byte[] convertedZip) throws Exception {
        mockConversion();
        fixedArtifact(convertedZip);
        assertTrue(worker.runNext());assertEquals("PUBLISHING",status());
        db.update("UPDATE wx_3d_resource_import SET publish_descriptor=?","{\"modelName\":\"样本模型\",\"category\":\"sample\"}");
        var publication=new ModelImportPublication(store,new ModelImportArtifactValidation(store,new ModelImportArtifactVerifier()),
                new ModelResourceManifestVerifier(),new ArtResourceManifestVerifier(),"test-loader");
        ReflectionTestUtils.setField(worker,"publication",publication);
        var resourceTransactions=new ArtResourceTransactionService();
        ReflectionTestUtils.setField(resourceTransactions,"jdbc",db);
        ReflectionTestUtils.setField(resourceTransactions,"access",access);
        ReflectionTestUtils.setField(resourceTransactions,"json",new com.fasterxml.jackson.databind.ObjectMapper());
        var images = new Dim3dImageResourceService();
        ReflectionTestUtils.setField(images, "jdbc", db);
        ReflectionTestUtils.setField(resourceTransactions, "images", images);
        ReflectionTestUtils.setField(worker,"resourceTransactions",resourceTransactions);
    }

    @AfterEach void cleanup() throws Exception {
        worker.close();TenantContextHolder.clear();
        if(db!=null)db.execute("SHUTDOWN");
        if(mocks!=null)mocks.close();
    }
    @Test void queueCannotBypassTheUnifiedModelPermission() {
        when(permissions.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(CommonResult.success(false));
        assertTrue(worker.prepareNext().isEmpty());
        assertEquals("FAILED",status());
        assertEquals("IMPORT_PERMISSION_REVOKED",db.queryForObject("SELECT error_code FROM wx_3d_resource_import",String.class));
        verifyNoInteractions(store);
    }
    @Test void publicationRechecksUnifiedPermissionWithoutThreadLogin() throws Exception {
        preparePublication();
        when(permissions.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(CommonResult.success(false));
        assertFalse(worker.runNext());
        assertEquals("FAILED",status());
        assertEquals(0L,db.queryForObject("SELECT current_revision FROM wx_3d_resource",Long.class));
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM wx_3d_model",Long.class));
    }
    // 转换边界与真实任务事务组合，验证固定描述前后的恢复和权限门禁。
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;

    @Test void runNextStoresVerifiedZipWithoutPublishing() throws Exception {
        var converter=mockConversion();
        var artifact=convertedFile();
        when(converter.convert(any(),eq(4L),eq("glb"),any())).thenReturn(artifact);
        doAnswer(inv->{assertArrayEquals(new byte[]{1,2,3,4},((java.io.InputStream)inv.getArgument(1)).readAllBytes());return null;})
                .when(store).write(anyString(),any(),eq(4L));
        assertTrue(worker.runNext());assertEquals("VALIDATING",status());
        String attempt=db.queryForObject("SELECT attempt_id FROM wx_3d_resource_import",String.class);
        String key="resources/model-imports/"+op+"/"+attempt+"/converted.zip";
        verify(store).verifyObject(eq(key),eq(new ArtResourceManifestVerifier.FileSpec("converted.zip",4,"b".repeat(64))));
        var descriptor=new com.fasterxml.jackson.databind.ObjectMapper().readTree(db.queryForObject("SELECT artifact_descriptor FROM wx_3d_resource_import",String.class));
        assertEquals(key,descriptor.path("key").asText());assertEquals("4",descriptor.path("size").asText());
        assertEquals("glb",descriptor.path("format").asText());assertEquals("b".repeat(64),descriptor.path("sha256").asText());
        assertFalse(java.nio.file.Files.exists(artifact.file()));
        assertEquals("UPLOADING",db.queryForObject("SELECT status FROM wx_3d_resource_operation",String.class));
        assertEquals(0L,db.queryForObject("SELECT current_revision FROM wx_3d_resource",Long.class));
        verify(converter,times(1)).convert(any(),anyLong(),anyString(),any());
    }

    @Test void restartReclaimsExpiredConversionAndReusesOnlyFrozenSource() throws Exception {
        var original=worker.prepareNext().orElseThrow();clearInvocations(store);
        var converter=mockConversion();
        when(converter.convert(any(),anyLong(),anyString(),any())).thenReturn(convertedFile());
        assertFalse(worker.runNext());verify(converter,never()).convert(any(),anyLong(),anyString(),any());
        worker.now=worker.now.plusSeconds(121);assertTrue(worker.runNext());
        String fresh=db.queryForObject("SELECT attempt_id FROM wx_3d_resource_import",String.class);
        assertNotEquals(original.attemptId(),fresh);
        verify(store).open(original.frozenSourceKey());verify(store,never()).freeze(anyString(),anyString(),anyList());
        verify(store).write(contains("/"+fresh+"/converted.zip"),any(),eq(4L));
    }

    @Test void lateConversionCannotWriteStorageOrDescriptor() throws Exception {
        var converter=mockConversion();var artifact=convertedFile();
        when(converter.convert(any(),anyLong(),anyString(),any())).thenAnswer(inv->{worker.now=worker.now.plusSeconds(121);return artifact;});
        assertFalse(worker.runNext());assertEquals("CONVERTING",status());
        verify(store,never()).write(anyString(),any(),anyLong());
        assertNull(db.queryForObject("SELECT artifact_descriptor FROM wx_3d_resource_import",String.class));
        assertFalse(java.nio.file.Files.exists(artifact.file()));
    }

    @Test void cancellationAfterStorageCannotFixDescriptor() throws Exception {
        var converter=mockConversion();
        when(converter.convert(any(),anyLong(),anyString(),any())).thenReturn(convertedFile());
        doAnswer(inv->{db.update("UPDATE wx_3d_resource_import SET cancel_requested=TRUE");return null;})
                .when(store).write(anyString(),any(),anyLong());
        assertFalse(worker.runNext());assertEquals("CONVERTING",status());
        assertNull(db.queryForObject("SELECT artifact_descriptor FROM wx_3d_resource_import",String.class));
    }

    @Test void storageFailureDoesNotFixDescriptor() throws Exception {
        var converter=mockConversion();
        when(converter.convert(any(),anyLong(),anyString(),any())).thenReturn(convertedFile());
        doThrow(new java.io.IOException("private storage diagnostic")).when(store).write(anyString(),any(),anyLong());
        assertFalse(worker.runNext());assertEquals("FAILED",status());
        assertEquals("ARTIFACT_STORAGE_UNAVAILABLE",db.queryForObject("SELECT error_code FROM wx_3d_resource_import",String.class));
        assertNull(db.queryForObject("SELECT artifact_descriptor FROM wx_3d_resource_import",String.class));
    }

    @Test void permissionCheckCrossingLeaseDeadlineCannotFixDescriptor() throws Exception {
        var converter=mockConversion();
        when(converter.convert(any(),anyLong(),anyString(),any())).thenReturn(convertedFile());
        var stored=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(inv->{stored.set(true);return null;}).when(store).write(anyString(),any(),anyLong());
        doAnswer(inv->{if(stored.get())worker.now=worker.now.plusSeconds(121);return null;})
                .when(access).maintain(any(),anyString(),anyString());
        assertFalse(worker.runNext());assertTrue(stored.get());assertEquals("CONVERTING",status());
        assertNull(db.queryForObject("SELECT artifact_descriptor FROM wx_3d_resource_import",String.class));
    }

    @Test void absentConverterLeavesQueuedSourceUntouched() {
        var converter=mock(ModelImportConversionClient.class);
        ReflectionTestUtils.setField(worker,"converter",converter);
        assertFalse(worker.runNext());assertEquals("QUEUED",status());
        verifyNoInteractions(store);verify(converter,never()).convert(any(),anyLong(),anyString(),any());
    }
    @Test void validatesFixedZipAndHandsOffWithoutPublishingOrReconversion() throws Exception {
        var converter=mockConversion();
        byte[] zip=validZip();
        String key=fixedArtifact(zip);
        assertTrue(worker.runNext());assertEquals("PUBLISHING",status());
        var descriptor=new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                db.queryForObject("SELECT artifact_descriptor FROM wx_3d_resource_import",String.class));
        assertEquals(key,descriptor.path("key").asText());
        assertEquals("scene.glb",descriptor.path("validated").path("modelPath").asText());
        assertEquals("scene.glb",descriptor.path("validated").path("files").get(0).path("path").asText());
        assertEquals(0L,db.queryForObject("SELECT current_revision FROM wx_3d_resource",Long.class));
        assertEquals("UPLOADING",db.queryForObject("SELECT status FROM wx_3d_resource_operation",String.class));
        assertFalse(worker.runNext());verify(converter,never()).convert(any(),anyLong(),anyString(),any());
    }

    @Test void rejectsFixedZipWhoseBytesNoLongerMatchDescriptor() throws Exception {
        mockConversion();
        byte[] zip=validZip();
        String key=fixedArtifact(zip);
        zip[zip.length-1]^=1;
        when(store.open(key)).thenReturn(new java.io.ByteArrayInputStream(zip));
        assertFalse(worker.runNext());assertEquals("FAILED",status());
        assertEquals("ARTIFACT_UNAVAILABLE",db.queryForObject("SELECT error_code FROM wx_3d_resource_import",String.class));
    }

    @Test void cancellationDuringValidationCannotFixPublishingPlan() throws Exception {
        mockConversion();
        byte[] zip=validZip();
        String key=fixedArtifact(zip);
        when(store.open(key)).thenAnswer(inv->{
            db.update("UPDATE wx_3d_resource_import SET cancel_requested=TRUE");
            return new java.io.ByteArrayInputStream(zip);
        });
        assertFalse(worker.runNext());assertEquals("VALIDATING",status());
        assertFalse(db.queryForObject("SELECT artifact_descriptor FROM wx_3d_resource_import",String.class).contains("validated"));
    }

    private String fixedArtifact(byte[] bytes) throws Exception {
        String old=UUID.randomUUID().toString();
        String key="resources/model-imports/"+op+"/"+old+"/converted.zip";
        String descriptor=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .put("key",key).put("format","glb").put("size",Integer.toString(bytes.length))
                .put("sha256",ArtResourceManifestVerifier.sha256(bytes)).toString();
        db.update("UPDATE wx_3d_resource_import SET status='VALIDATING',frozen_source_key=?,attempt_id=?,lease_until=?,artifact_descriptor=?",
                "resources/model-imports/"+op+"/"+old+"/source.zip",old,worker.now,descriptor);
        when(store.open(key)).thenAnswer(inv->new java.io.ByteArrayInputStream(bytes));
        ReflectionTestUtils.setField(worker,"artifactValidation",new ModelImportArtifactValidation(store,new ModelImportArtifactVerifier()));
        return key;
    }

    private static byte[] validZip() throws Exception {
        byte[] model=ModelImportArtifactVerifierTest.glb(ModelImportArtifactVerifierTest.json("\"buffers\":[{\"byteLength\":4}]"),4);
        return modelZip("scene.glb", model);
    }

    /** 按给定原成员名组装真实 ZIP，以同一格式走后台固定产物和发布流程。 */
    private static byte[] modelZip(String archiveName, byte[] model) throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        try(var output=new java.util.zip.ZipOutputStream(bytes)){
            output.putNextEntry(new java.util.zip.ZipEntry(archiveName));output.write(model);output.closeEntry();
        }
        return bytes.toByteArray();
    }
    private ModelImportConversionClient mockConversion() throws Exception {
        var converter=mock(ModelImportConversionClient.class);
        when(converter.configured()).thenReturn(true);
        ReflectionTestUtils.setField(worker,"converter",converter);
        when(store.open(anyString())).thenAnswer(inv->new java.io.ByteArrayInputStream(new byte[]{1,2,3,4}));
        return converter;
    }
    private ModelImportConversionClient.Archive convertedFile() throws Exception {
        var path=java.nio.file.Files.write(temporary.resolve("converted.zip"),new byte[]{1,2,3,4});
        return new ModelImportConversionClient.Archive(path,4,"b".repeat(64));
    }
    @Test void freezeProducesOnlyConvertingTaskAndNeverResourceCommit() {
        var result=worker.prepareNext().orElseThrow();assertEquals(op,result.operationId());
        assertNotNull(result.frozenSourceKey());
        assertEquals("CONVERTING",status());assertEquals(0L,db.queryForObject("SELECT current_revision FROM wx_3d_resource",Long.class));
        verify(store).freeze(eq("resources/staging/model-imports/"+op+"/"),contains(result.attemptId()),anyList());
        verify(store).verifyZipHeader(anyString(),eq("source.zip"));
    }
    @Test void singleLeaseAndExpiredAttemptCannotRenewOrComplete() {
        var old=worker.claimNext().orElseThrow();assertTrue(worker.claimNext().isEmpty());
        worker.now=worker.now.plusSeconds(121);var fresh=worker.claimNext().orElseThrow();
        assertNotEquals(old.attemptId(),fresh.attemptId());assertFalse(worker.renew(old));assertTrue(worker.renew(fresh));
        assertFalse(worker.freezeSource(old));verifyNoInteractions(store);
    }
    @Test void restartReusesVerifiedImmutableSourceInsteadOfWritableStaging() {
        String key="resources/model-imports/"+op+"/old/source.zip";
        db.update("UPDATE wx_3d_resource_import SET frozen_source_key=?",key);
        assertTrue(worker.prepareNext().isPresent());verify(store,never()).freeze(anyString(),anyString(),anyList());
        verify(store).verifyObject(eq(key),any());
    }
    @Test void lateSuccessAfterLeaseLossCannotAdvanceOrWriteFailure() {
        doAnswer(inv->{worker.now=worker.now.plusSeconds(121);return null;}).when(store).freeze(anyString(),anyString(),anyList());
        assertTrue(worker.prepareNext().isEmpty());assertEquals("VERIFYING_SOURCE",status());
    }
    @Test void cancelDuringIoStopsFinalTransition() {
        doAnswer(inv->{db.update("UPDATE wx_3d_resource_import SET cancel_requested=TRUE");return null;}).when(store).freeze(anyString(),anyString(),anyList());
        assertTrue(worker.prepareNext().isEmpty());assertNotEquals("CONVERTING",status());
    }
    @Test void checksumFailureRecordedWithoutRawDiagnosticOrPointerChange() {
        doThrow(ServiceException.of(cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode.CHECKSUM_MISMATCH)).when(store).freeze(anyString(),anyString(),anyList());
        assertTrue(worker.prepareNext().isEmpty());assertEquals("FAILED",status());
        assertEquals("SOURCE_CHECKSUM_MISMATCH",db.queryForObject("SELECT error_code FROM wx_3d_resource_import",String.class));
    }
    @Test void expiredCancelledAndRevokedActorsNeverReachStorage() {
        db.update("UPDATE wx_3d_resource_operation SET expires_at='2026-11-04 00:00:00'");assertTrue(worker.prepareNext().isEmpty());verifyNoInteractions(store);
    }
    @Test void tenantContextRestoredAfterPermissionFailure() {
        TenantContextHolder.setTenantId(7L);
        doAnswer(inv->{assertEquals(2L,TenantContextHolder.getTenantId());throw new ServiceException(403,"revoked");}).when(access).maintain(any(),anyString(),anyString());
        assertTrue(worker.prepareNext().isEmpty());assertEquals(7L,TenantContextHolder.getTenantId());verifyNoInteractions(store);
    }
    @Test void backgroundChecksDoNotInheritTenantIgnoreAndRestoreOriginalFlag() {
        TenantContextHolder.clear();TenantContextHolder.setIgnore(true);
        doAnswer(inv->{assertFalse(TenantContextHolder.isIgnore());assertEquals(2L,TenantContextHolder.getTenantId());return null;})
                .when(access).maintain(any(),anyString(),anyString());
        assertTrue(worker.prepareNext().isPresent());assertNull(TenantContextHolder.getTenantId());assertTrue(TenantContextHolder.isIgnore());
    }
    @Test void unmarkedOperationsAndMovedResourcesAreNotClaimed() {
        db.update("UPDATE wx_3d_resource_operation SET candidate_descriptor='{}'");assertTrue(worker.prepareNext().isEmpty());
        db.update("UPDATE wx_3d_resource_operation SET candidate_descriptor='{\"modelImport\":true}'");
        db.update("UPDATE wx_3d_resource_import SET status='QUEUED'");
        db.update("UPDATE wx_3d_resource SET tenant_id='3'");assertTrue(worker.prepareNext().isEmpty());verifyNoInteractions(store);
    }
    @Test void concurrentClaimHasOnlyOneOwner() throws Exception {
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> action=()->{start.await();return worker.claimNext().isPresent();};
            var a=pool.submit(action);var b=pool.submit(action);start.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
    }
    @Test void permissionRevokedAfterIoPreventsTransitionAndRecordsStableFailure() {
        doAnswer(inv->{doThrow(new ServiceException(403,"private diagnostic")).when(access).maintain(any(),anyString(),anyString());return null;})
                .when(store).freeze(anyString(),anyString(),anyList());
        assertTrue(worker.prepareNext().isEmpty());assertEquals("FAILED",status());
        assertEquals("IMPORT_PERMISSION_REVOKED",db.queryForObject("SELECT error_code FROM wx_3d_resource_import",String.class));
    }
    @Test void foreignImmutableKeyNeverReadsAnotherJob() {
        for(String key:List.of("resources/model-imports/other/source.zip","resources/model-imports/"+op+"/../source.zip")) {
            db.update("UPDATE wx_3d_resource_import SET status='QUEUED',frozen_source_key=?",key);
            assertTrue(worker.prepareNext().isEmpty());verifyNoInteractions(store);
        }
    }
    @Test void completedReceiptRemovedFromQueue() {
        db.update("UPDATE wx_3d_resource_operation SET status='COMMITTED'");assertTrue(worker.claimNext().isEmpty());assertEquals("COMMITTED",status());
    }
    String status(){return db.queryForObject("SELECT status FROM wx_3d_resource_import",String.class);}
}
