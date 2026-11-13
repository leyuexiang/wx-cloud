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
        db.execute("CREATE TABLE wx_3d_resource_operation(id VARCHAR(36) PRIMARY KEY,resource_id BIGINT,resource_scope VARCHAR(20),tenant_id VARCHAR(20),operator_id BIGINT,status VARCHAR(16),expires_at TIMESTAMP,candidate_descriptor CLOB,operation_type VARCHAR(16) DEFAULT 'CREATE',expected_revision BIGINT DEFAULT 0,storage_prefix VARCHAR(512) DEFAULT 'resources/test/operations/',committed_revision BIGINT,before_snapshot CLOB,after_snapshot CLOB,error_code VARCHAR(64))");
        db.execute("CREATE TABLE wx_3d_resource_import(operation_id VARCHAR(36) PRIMARY KEY,resource_id BIGINT,status VARCHAR(24),source_size BIGINT,source_sha256 VARCHAR(64),frozen_source_key VARCHAR(512),attempt_id VARCHAR(36),lease_until TIMESTAMP,available_at TIMESTAMP,cancel_requested BOOLEAN DEFAULT FALSE,error_code VARCHAR(64),row_version BIGINT DEFAULT 0,target_format VARCHAR(4),artifact_descriptor CLOB,publish_descriptor CLOB)");
        db.update("INSERT INTO wx_3d_resource(id,resource_scope,tenant_id,upload_source,del_flag,current_revision) VALUES(101,'TENANT_SHARED','2','WEB_UPLOAD','0',0)");
        db.update("INSERT INTO wx_3d_resource_operation(id,resource_id,resource_scope,tenant_id,operator_id,status,expires_at,candidate_descriptor) VALUES(?,101,'TENANT_SHARED','2',10,'UPLOADING','2026-11-04 02:00:00','{\"modelImport\":true}')",op);
        db.update("INSERT INTO wx_3d_resource_import(operation_id,resource_id,status,source_size,source_sha256,available_at,target_format) VALUES(?,101,'QUEUED',4,?,'2026-11-04 00:00:00','glb')",op,"a".repeat(64));
        db.execute("CREATE TABLE wx_3d_model(id BIGINT AUTO_INCREMENT PRIMARY KEY,resource_id BIGINT,model_file_id BIGINT,tenant_id VARCHAR(20),del_flag CHAR DEFAULT '0',status VARCHAR(16) DEFAULT 'ONLINE',model_name VARCHAR(50),category VARCHAR(32),model_desc VARCHAR(500),resource_belong VARCHAR(16),is_paid VARCHAR(16),data_user_id BIGINT,create_by BIGINT,update_by BIGINT)");
        for(String table:List.of("wx_3d_icon","wx_3d_template"))
            db.execute("CREATE TABLE "+table+"(id BIGINT PRIMARY KEY,resource_id BIGINT,tenant_id VARCHAR(20),del_flag CHAR,status VARCHAR(16))");
        store=mock(ArtResourceObjectStore.class);access=mock(ArtResourceAccess.class);
        ReflectionTestUtils.setField(worker,"jdbc",db);ReflectionTestUtils.setField(worker,"transactions",new DataSourceTransactionManager(data));
        ReflectionTestUtils.setField(worker,"store",store);ReflectionTestUtils.setField(worker,"access",access);
    }
    @Test void publishesDirectoryPointerAndReceiptTogether() throws Exception {
        preparePublication();
        assertTrue(worker.runNext());assertEquals("COMMITTED",status());
        assertEquals(1L,db.queryForObject("SELECT current_revision FROM wx_3d_resource",Long.class));
        assertEquals(101L,db.queryForObject("SELECT resource_id FROM wx_3d_model",Long.class));
        assertNull(db.queryForObject("SELECT model_file_id FROM wx_3d_model",Long.class));
        assertEquals("COMMITTED",db.queryForObject("SELECT status FROM wx_3d_resource_operation",String.class));
        assertFalse(worker.runNext());
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
        mockConversion();
        fixedArtifact(validZip());
        assertTrue(worker.runNext());assertEquals("PUBLISHING",status());
        db.update("UPDATE wx_3d_resource_import SET publish_descriptor=?","{\"modelName\":\"样本模型\",\"category\":\"sample\"}");
        var publication=new ModelImportPublication(store,new ModelImportArtifactValidation(store,new ModelImportArtifactVerifier()),
                new ModelResourceManifestVerifier(),new ArtResourceManifestVerifier(),"test-loader");
        ReflectionTestUtils.setField(worker,"publication",publication);
        var resourceTransactions=new ArtResourceTransactionService();
        ReflectionTestUtils.setField(resourceTransactions,"jdbc",db);
        ReflectionTestUtils.setField(resourceTransactions,"json",new com.fasterxml.jackson.databind.ObjectMapper());
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
        var bytes=new java.io.ByteArrayOutputStream();
        try(var output=new java.util.zip.ZipOutputStream(bytes)){
            output.putNextEntry(new java.util.zip.ZipEntry("scene.glb"));output.write(model);output.closeEntry();
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
