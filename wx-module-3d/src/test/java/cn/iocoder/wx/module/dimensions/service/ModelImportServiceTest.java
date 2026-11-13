package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.system.api.dict.DictDataApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实事务及既有ART服务检查身份、幂等、入队和旧入口隔离。 */
class ModelImportServiceTest {
    JdbcTemplate db;@InjectMocks ModelImportService service;ArtResourceService art;ArtResourceAccess access;
    @Mock SecurityFrameworkService permissions;
    @Mock DictDataApi dictionaries;
    AutoCloseable mocks;
    ArtResourceUploadSigner signer;ObjectMapper json=new ObjectMapper();ObjectNode request;
    @BeforeEach void setup() throws Exception {
        mocks=MockitoAnnotations.openMocks(this);
        when(permissions.hasPermission(anyString())).thenReturn(true);
        when(dictionaries.validateDictDataList(eq("dim3d_model_category"), anyCollection())).thenReturn(CommonResult.success(true));
        var data=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        db=new JdbcTemplate(data);var tx=new DataSourceTransactionManager(data);
        db.execute("CREATE TABLE wx_3d_resource(id BIGINT PRIMARY KEY,resource_name VARCHAR(150),resource_type VARCHAR(20),asset_name VARCHAR(150),resource_relative_path VARCHAR(255),resource_scope VARCHAR(20),tenant_id VARCHAR(20),content_state VARCHAR(16),current_revision BIGINT DEFAULT 0,current_operation_id VARCHAR(36),content_hash VARCHAR(64),upload_source VARCHAR(20),create_by BIGINT,del_flag CHAR DEFAULT '0')");
        db.execute("CREATE TABLE wx_3d_resource_operation(id VARCHAR(36) PRIMARY KEY,resource_id BIGINT,operation_type VARCHAR(16),resource_scope VARCHAR(20),tenant_id VARCHAR(20),operator_id BIGINT,request_hash VARCHAR(64),expected_revision BIGINT,committed_revision BIGINT,status VARCHAR(16) DEFAULT 'UPLOADING',storage_prefix VARCHAR(512),candidate_descriptor CLOB,expires_at TIMESTAMP,error_code VARCHAR(64))");
        db.execute("CREATE TABLE wx_3d_resource_import(operation_id VARCHAR(36) PRIMARY KEY,resource_id BIGINT,target_format VARCHAR(4),source_size BIGINT,source_sha256 VARCHAR(64),status VARCHAR(24) DEFAULT 'RECEIVING',attempt_id VARCHAR(36),error_code VARCHAR(64),row_version BIGINT DEFAULT 0,publish_descriptor CLOB,cancel_requested BOOLEAN DEFAULT FALSE)");
        db.execute("CREATE TABLE wx_3d_model(resource_id BIGINT,cover_image_id BIGINT,del_flag CHAR DEFAULT '0')");
        db.execute("CREATE TABLE wx_infra_file(id BIGINT PRIMARY KEY,tenant_id BIGINT,uploader_id BIGINT,visibility_scope VARCHAR(20),type VARCHAR(40),size INT,deleted BOOLEAN DEFAULT FALSE,update_time TIMESTAMP)");
        db.update("INSERT INTO wx_infra_file VALUES(5,2,10,'PRIVATE_UPLOAD','image/png',50,FALSE,NULL)");
        access=mock(ArtResourceAccess.class);when(access.actor()).thenReturn(new ArtResourceAccess.Actor(10,2));
        art=new ArtResourceService();signer=mock(ArtResourceUploadSigner.class);
        for(Object target:List.of(art,service)) {
            ReflectionTestUtils.setField(target,"jdbc",db);ReflectionTestUtils.setField(target,"transactions",tx);
            ReflectionTestUtils.setField(target,"access",access);ReflectionTestUtils.setField(target,"json",json);
        }
        ReflectionTestUtils.setField(service,"resources",art);ReflectionTestUtils.setField(service,"signer",signer);
        var images = new Dim3dImageResourceService();
        ReflectionTestUtils.setField(images, "jdbc", db);
        ReflectionTestUtils.setField(service, "images", images);
        request=(ObjectNode)json.readTree("{\"operationId\":\""+UUID.randomUUID()+"\",\"operationType\":\"CREATE\",\"resourceScope\":\"TENANT_SHARED\",\"metadata\":{\"resourceName\":\"模型\",\"assetName\":\"model\"},\"targetFormat\":\"glb\",\"source\":{\"size\":\"100\",\"sha256\":\""+"a".repeat(64)+"\"}}");
    }
    String op(){return request.get("operationId").asText();}
    @AfterEach void cleanup() throws Exception {
        // 独立内存库和模拟对象只属于当前用例，结束时释放，不积累数据库实例。
        if(db!=null)db.execute("SHUTDOWN");
        if(mocks!=null)mocks.close();
    }
    @Test void editPermissionCannotCreateAnAddOperation() {
        when(permissions.hasPermission("dim3d:model:add")).thenReturn(false);
        when(permissions.hasPermission("dim3d:model:edit")).thenReturn(true);
        assertEquals(403,assertThrows(ServiceException.class,()->service.create(request)).getCode());
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM wx_3d_resource",Long.class));
    }
    @Test void revokedAddPermissionCannotSignOrQueueAnExistingOperation() {
        service.create(request);
        when(permissions.hasPermission("dim3d:model:add")).thenReturn(false);
        assertEquals(403,assertThrows(ServiceException.class,()->service.sourceTarget(op())).getCode());
        assertEquals(403,assertThrows(ServiceException.class,()->service.submit(op())).getCode());
        assertEquals("RECEIVING",db.queryForObject("SELECT status FROM wx_3d_resource_import",String.class));
        verifyNoInteractions(signer);
    }
    @Test void publishConfirmationIsAuthorizedAndFixedOnce() {
        service.create(request);
        var directory=json.createObjectNode().put("modelName","样本模型").put("category","sample");
        assertThrows(ServiceException.class,()->service.requestPublish(op(),directory));
        db.update("UPDATE wx_3d_resource_import SET status='PUBLISHING'");
        assertEquals("PUBLISHING",service.requestPublish(op(),directory).importStatus());
        assertEquals("PUBLISHING",service.requestPublish(op(),directory).importStatus());
        assertEquals(1L,db.queryForObject("SELECT row_version FROM wx_3d_resource_import",Long.class));
        assertThrows(ServiceException.class,()->service.requestPublish(op(),directory.deepCopy().put("category","other")));
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(11,3));
        assertThrows(ServiceException.class,()->service.requestPublish(op(),directory));
    }

    @Test void publishAcceptsAndFixesCoverAndIntegerCentPrice() throws Exception {
        // 系统模型沿用原权限与范围；新增字段必须在确认时持久化，重放不能丢失。
        request.put("resourceScope", "GLOBAL_SYSTEM");
        service.create(request);
        db.update("UPDATE wx_3d_resource_import SET status='PUBLISHING'");
        var directory = json.createObjectNode().put("modelName", "完整模型").put("category", "sample")
                .put("modelDesc", "").put("coverImageId", "5").put("isPaid", "PAID").put("price", 1299);
        assertEquals("PUBLISHING", service.requestPublish(op(), directory).importStatus());
        var saved = json.readTree(db.queryForObject("SELECT publish_descriptor FROM wx_3d_resource_import", String.class));
        assertEquals(directory, saved);
        assertEquals("PUBLISHING", service.requestPublish(op(), directory).importStatus());
        assertEquals(1L, db.queryForObject("SELECT row_version FROM wx_3d_resource_import", Long.class));
        assertEquals("PRIVATE_UPLOAD", db.queryForObject("SELECT visibility_scope FROM wx_infra_file", String.class));
    }

    @Test void categoryValidationRunsOutsideLocksAndConfirmedReplaySurvivesDictionaryChanges() {
        service.create(request);
        db.update("UPDATE wx_3d_resource_import SET status='PUBLISHING'");
        var directory = json.createObjectNode().put("modelName", "样本模型").put("category", "sample");
        when(dictionaries.validateDictDataList(anyString(), anyCollection())).thenAnswer(invocation -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return CommonResult.success(false);
        });
        assertEquals(400, assertThrows(ServiceException.class, () -> service.requestPublish(op(), directory)).getCode());
        assertNull(db.queryForObject("SELECT publish_descriptor FROM wx_3d_resource_import", String.class));
        when(dictionaries.validateDictDataList(anyString(), anyCollection())).thenReturn(CommonResult.success(true));
        service.requestPublish(op(), directory);
        when(dictionaries.validateDictDataList(anyString(), anyCollection())).thenThrow(new IllegalStateException("字典暂不可用"));
        assertEquals("PUBLISHING", service.requestPublish(op(), directory).importStatus());
        verify(dictionaries, times(2)).validateDictDataList("dim3d_model_category", List.of("sample"));
    }

    @Test void cancellationDuringDictionaryPrecheckCannotFixDirectory() {
        service.create(request);
        db.update("UPDATE wx_3d_resource_import SET status='PUBLISHING'");
        when(dictionaries.validateDictDataList(anyString(), anyCollection())).thenAnswer(invocation -> {
            db.update("UPDATE wx_3d_resource_import SET cancel_requested=TRUE");
            return CommonResult.success(true);
        });
        var directory = json.createObjectNode().put("modelName", "样本模型").put("category", "sample");
        assertThrows(ServiceException.class, () -> service.requestPublish(op(), directory));
        assertNull(db.queryForObject("SELECT publish_descriptor FROM wx_3d_resource_import", String.class));
    }

    @Test void invalidCoverCanBeCorrectedBeforeDirectoryIsFixed() {
        service.create(request);
        db.update("UPDATE wx_3d_resource_import SET status='PUBLISHING'");
        var directory = json.createObjectNode().put("modelName", "样本模型").put("category", "sample").put("coverImageId", "5");
        db.update("UPDATE wx_infra_file SET tenant_id=3");
        assertEquals(403, assertThrows(ServiceException.class, () -> service.requestPublish(op(), directory)).getCode());
        assertNull(db.queryForObject("SELECT publish_descriptor FROM wx_3d_resource_import", String.class));
        db.update("UPDATE wx_infra_file SET tenant_id=2,size=5242881");
        assertEquals(400, assertThrows(ServiceException.class, () -> service.requestPublish(op(), directory)).getCode());
        db.update("UPDATE wx_infra_file SET size=50,type='image/gif'");
        assertEquals(400, assertThrows(ServiceException.class, () -> service.requestPublish(op(), directory)).getCode());
        db.update("UPDATE wx_infra_file SET type='image/png'");
        assertEquals("PUBLISHING", service.requestPublish(op(), directory).importStatus());
    }

    @Test void tenantPaidDirectoryIsRejectedAndIntegerPriceCannotOverflowOrRound() {
        service.create(request);
        db.update("UPDATE wx_3d_resource_import SET status='PUBLISHING'");
        var directory = json.createObjectNode().put("modelName", "样本模型").put("category", "sample").put("isPaid", "PAID").put("price", 1);
        assertEquals(400, assertThrows(ServiceException.class, () -> service.requestPublish(op(), directory)).getCode());
        db.update("UPDATE wx_3d_resource_operation SET resource_scope='GLOBAL_SYSTEM',tenant_id='0'");
        for (String price : List.of("0", "-1", "1.01", "2147483648", "null", "\"1\"")) {
            try { directory.set("price", json.readTree(price)); } catch (Exception invalid) { throw new AssertionError(invalid); }
            assertEquals(400, assertThrows(ServiceException.class, () -> service.requestPublish(op(), directory)).getCode());
        }
        assertNull(db.queryForObject("SELECT publish_descriptor FROM wx_3d_resource_import", String.class));
    }

    @Test void descriptionSupportsParagraphsAndRejectsHiddenControls() {
        service.create(request);
        db.update("UPDATE wx_3d_resource_import SET status='PUBLISHING'");
        var directory = json.createObjectNode().put("modelName", "样本模型").put("category", "sample");
        directory.put("modelDesc", "描述\u0000内容");
        assertThrows(ServiceException.class, () -> service.requestPublish(op(), directory));
        directory.put("modelDesc", "第一段\n第二段");
        assertEquals("PUBLISHING", service.requestPublish(op(), directory).importStatus());
    }

    @Test void committedReplayReturnsOriginalRevisionAndRejectsChangedInput() {
        service.create(request);
        var directory=json.createObjectNode().put("modelName","样本模型").put("category","sample");
        db.update("UPDATE wx_3d_resource_import SET status='PUBLISHING'");
        service.requestPublish(op(),directory);
        db.update("UPDATE wx_3d_resource_operation SET status='COMMITTED',committed_revision=1");
        assertEquals("1",service.requestPublish(op(),directory).committedRevision());
        assertThrows(ServiceException.class,()->service.requestPublish(op(),directory.deepCopy().put("category","other")));
    }
    @Test void creationAndReplayShareIdentityAndTrustedSource() {
        var first=service.create(request);var second=service.create(request);
        assertEquals(first.resourceId(),second.resourceId());assertEquals("RECEIVING",first.importStatus());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM wx_3d_resource",Integer.class));
        assertEquals("WEB_UPLOAD",db.queryForObject("SELECT upload_source FROM wx_3d_resource",String.class));
    }
    @Test void repeatedSubmitQueuesOnceAndDoesNotAdvanceResourceRevision() {
        service.create(request);assertEquals("QUEUED",service.submit(op()).importStatus());
        assertEquals("QUEUED",service.submit(op()).importStatus());
        assertEquals(1L,db.queryForObject("SELECT row_version FROM wx_3d_resource_import",Long.class));
        assertEquals(0L,db.queryForObject("SELECT current_revision FROM wx_3d_resource",Long.class));
    }
    @Test void sourceTargetIsIsolatedAndUnavailableAfterSubmit() {
        service.create(request);when(signer.sign(anyString(),eq("source.zip"))).thenReturn("test-target");
        assertEquals("test-target",service.sourceTarget(op()).get("uploadUrl"));
        verify(signer).sign(eq("resources/staging/model-imports/"+op()+"/"),eq("source.zip"));
        service.submit(op());assertThrows(ServiceException.class,()->service.sourceTarget(op()));
    }
    @Test void changedSourceOrTargetCannotReuseOperation() {
        service.create(request);request.put("targetFormat","gltf");assertThrows(ServiceException.class,()->service.create(request));
        request.put("targetFormat","glb");((ObjectNode)request.get("source")).put("sha256","b".repeat(64));
        assertThrows(ServiceException.class,()->service.create(request));
    }
    @Test void untrustedFieldsAndOversizeRejectedWithoutWriting() {
        request.put("tenantId","3");assertThrows(ServiceException.class,()->service.create(request));request.remove("tenantId");
        ((ObjectNode)request.get("source")).put("size","524288001");assertThrows(ServiceException.class,()->service.create(request));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM wx_3d_resource",Integer.class));
    }
    @Test void foreignActorAndRevokedAdminCannotReadOrQueue() {
        service.create(request);when(access.actor()).thenReturn(new ArtResourceAccess.Actor(11,2));
        assertThrows(ServiceException.class,()->service.submit(op()));when(access.actor()).thenReturn(new ArtResourceAccess.Actor(10,3));
        assertThrows(ServiceException.class,()->service.get(op()));when(access.actor()).thenReturn(new ArtResourceAccess.Actor(10,2));
        doThrow(new ServiceException(403,"denied")).when(access).maintain(any(),anyString(),anyString());
        assertThrows(ServiceException.class,()->service.submit(op()));
        assertEquals("RECEIVING",db.queryForObject("SELECT status FROM wx_3d_resource_import",String.class));
    }
    @Test void expiredOrCancelledOperationDoesNotQueue() {
        service.create(request);db.update("UPDATE wx_3d_resource_operation SET expires_at='2000-01-01'");
        assertThrows(ServiceException.class,()->service.submit(op()));
    }
    @Test void cancelledOperationCannotGetTargetOrSubmit() {
        service.create(request);db.update("UPDATE wx_3d_resource_operation SET status='CANCELLED'");
        assertThrows(ServiceException.class,()->service.submit(op()));
        assertThrows(ServiceException.class,()->service.sourceTarget(op()));
        assertEquals("CANCELLED",service.get(op()).importStatus());
    }
    @Test void legacyOperationCannotBeAdoptedAsImport() {
        var old=request.deepCopy();old.remove(List.of("targetFormat","source"));((ObjectNode)old.get("metadata")).put("resourceType","MODEL");
        art.create(old);assertThrows(ServiceException.class,()->service.create(request));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM wx_3d_resource_import",Integer.class));
    }
    @Test void legacyReplacementCannotChangeWebResourceToHotUpdate() throws Exception {
        var created=service.create(request);db.update("UPDATE wx_3d_resource SET content_state='READY',current_revision=1");
        var replace=json.createObjectNode().put("operationId",UUID.randomUUID().toString()).put("operationType","REPLACE")
            .put("resourceId",created.resourceId()).put("expectedRevision","1");
        assertThrows(ServiceException.class,()->art.create(replace));
    }
    @Test void concurrentSubmitsDoNotQueueTwice() throws Exception {
        service.create(request);String id=op();var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<ModelImportStatusView> action=()->{start.await();return service.submit(id);};
            var a=pool.submit(action);var b=pool.submit(action);start.countDown();
            assertEquals("QUEUED",a.get(10,java.util.concurrent.TimeUnit.SECONDS).importStatus());
            assertEquals("QUEUED",b.get(10,java.util.concurrent.TimeUnit.SECONDS).importStatus());
            assertEquals(1L,db.queryForObject("SELECT row_version FROM wx_3d_resource_import",Long.class));
        } finally {pool.shutdownNow();}
    }
    @Test void failedTaskInsertRollsBackResourceAndOperation() {
        db.execute("DROP TABLE wx_3d_resource_import");assertThrows(RuntimeException.class,()->service.create(request));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM wx_3d_resource",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM wx_3d_resource_operation",Integer.class));
    }
    @Test void legacyCreateCannotReplayImportAndLegacyCommitCannotBypassIt() {
        service.create(request);ObjectNode old=request.deepCopy();old.remove(List.of("targetFormat","source"));
        ((ObjectNode)old.get("metadata")).put("resourceType","MODEL");assertThrows(ServiceException.class,()->art.create(old));
        var transaction=new ArtResourceTransactionService();
        ReflectionTestUtils.setField(transaction,"jdbc",db);ReflectionTestUtils.setField(transaction,"access",access);
        ReflectionTestUtils.setField(transaction,"transactions",new DataSourceTransactionManager(db.getDataSource()));
        ReflectionTestUtils.setField(transaction,"json",json);
        var plan=json.createObjectNode();plan.putArray("files").addObject().put("path","model.ab").put("size",1).put("sha256","a".repeat(64));
        assertThrows(ServiceException.class,()->transaction.uploadTargets(op(),plan));
        assertThrows(ServiceException.class,()->transaction.commit(op(),json.createObjectNode()));
        assertEquals("UPLOADING",db.queryForObject("SELECT status FROM wx_3d_resource_operation",String.class));
    }
}
