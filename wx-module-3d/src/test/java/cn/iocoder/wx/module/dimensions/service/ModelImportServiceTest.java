package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实事务及既有ART服务检查身份、幂等、入队和旧入口隔离。 */
class ModelImportServiceTest {
    JdbcTemplate db;ModelImportService service;ArtResourceService art;ArtResourceAccess access;
    ArtResourceUploadSigner signer;ObjectMapper json=new ObjectMapper();ObjectNode request;
    @BeforeEach void setup() throws Exception {
        var data=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        db=new JdbcTemplate(data);var tx=new DataSourceTransactionManager(data);
        db.execute("CREATE TABLE wx_3d_resource(id BIGINT PRIMARY KEY,resource_name VARCHAR(150),resource_type VARCHAR(20),asset_name VARCHAR(150),resource_relative_path VARCHAR(255),resource_scope VARCHAR(20),tenant_id VARCHAR(20),content_state VARCHAR(16),current_revision BIGINT DEFAULT 0,current_operation_id VARCHAR(36),content_hash VARCHAR(64),upload_source VARCHAR(20),create_by BIGINT,del_flag CHAR DEFAULT '0')");
        db.execute("CREATE TABLE wx_3d_resource_operation(id VARCHAR(36) PRIMARY KEY,resource_id BIGINT,operation_type VARCHAR(16),resource_scope VARCHAR(20),tenant_id VARCHAR(20),operator_id BIGINT,request_hash VARCHAR(64),expected_revision BIGINT,committed_revision BIGINT,status VARCHAR(16) DEFAULT 'UPLOADING',storage_prefix VARCHAR(512),candidate_descriptor CLOB,expires_at TIMESTAMP,error_code VARCHAR(64))");
        db.execute("CREATE TABLE wx_3d_resource_import(operation_id VARCHAR(36) PRIMARY KEY,resource_id BIGINT,target_format VARCHAR(4),source_size BIGINT,source_sha256 VARCHAR(64),status VARCHAR(24) DEFAULT 'RECEIVING',attempt_id VARCHAR(36),error_code VARCHAR(64),row_version BIGINT DEFAULT 0,publish_descriptor CLOB,cancel_requested BOOLEAN DEFAULT FALSE)");
        access=mock(ArtResourceAccess.class);when(access.actor()).thenReturn(new ArtResourceAccess.Actor(10,2));
        art=new ArtResourceService();service=new ModelImportService();signer=mock(ArtResourceUploadSigner.class);
        for(Object target:List.of(art,service)) {
            ReflectionTestUtils.setField(target,"jdbc",db);ReflectionTestUtils.setField(target,"transactions",tx);
            ReflectionTestUtils.setField(target,"access",access);ReflectionTestUtils.setField(target,"json",json);
        }
        ReflectionTestUtils.setField(service,"resources",art);ReflectionTestUtils.setField(service,"signer",signer);
        request=(ObjectNode)json.readTree("{\"operationId\":\""+UUID.randomUUID()+"\",\"operationType\":\"CREATE\",\"resourceScope\":\"TENANT_SHARED\",\"metadata\":{\"resourceName\":\"模型\",\"assetName\":\"model\"},\"targetFormat\":\"glb\",\"source\":{\"size\":\"100\",\"sha256\":\""+"a".repeat(64)+"\"}}");
    }
    String op(){return request.get("operationId").asText();}
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
