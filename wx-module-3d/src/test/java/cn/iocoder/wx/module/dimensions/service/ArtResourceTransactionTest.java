package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实数据库事务验证失败回滚、修订冲突和操作幂等；对象IO通过可控边界模拟。 */
class ArtResourceTransactionTest {
    JdbcTemplate db;ArtResourceService resources;ArtResourceTransactionService tx;ArtResourceObjectStore store;
    ArtResourceAccess access;ArtResourceClosureVerifier closure;ObjectMapper json=new ObjectMapper();ArtResourceManifestVerifier verifier=new ArtResourceManifestVerifier();
    Map<String,Object> operation;JsonNode commit;byte[] manifestBytes;
    @BeforeEach void setup()throws Exception{
        var data=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");db=new JdbcTemplate(data);var manager=new DataSourceTransactionManager(data);
        db.execute("CREATE TABLE wx_dim3d_resource(id BIGINT PRIMARY KEY,resource_name VARCHAR(150),resource_type VARCHAR(20),asset_name VARCHAR(150),resource_relative_path VARCHAR(255),resource_scope VARCHAR(20),tenant_id VARCHAR(20),content_state VARCHAR(16),current_revision BIGINT DEFAULT 0,current_operation_id VARCHAR(36),content_hash VARCHAR(64),manifest_sha256 VARCHAR(64),storage_prefix VARCHAR(512),manifest_path VARCHAR(512),platform VARCHAR(20),content_format VARCHAR(32),app_compatibility VARCHAR(128),create_by BIGINT,update_by BIGINT,del_flag CHAR DEFAULT '0')");
        db.execute("CREATE TABLE wx_dim3d_resource_operation(id VARCHAR(36) PRIMARY KEY,resource_id BIGINT,operation_type VARCHAR(16),resource_scope VARCHAR(20),tenant_id VARCHAR(20),operator_id BIGINT,request_hash VARCHAR(64),expected_revision BIGINT,committed_revision BIGINT,status VARCHAR(16) DEFAULT 'UPLOADING',storage_prefix VARCHAR(512),candidate_descriptor CLOB,before_snapshot CLOB,after_snapshot CLOB,expires_at TIMESTAMP,error_code VARCHAR(64))");
        for(String table:List.of("wx_dim3d_model","wx_dim3d_icon","wx_dim3d_template"))db.execute("CREATE TABLE "+table+"(id BIGINT PRIMARY KEY,resource_id BIGINT,tenant_id VARCHAR(20),del_flag CHAR DEFAULT '0',status VARCHAR(16) DEFAULT 'ONLINE')");
        access=mock(ArtResourceAccess.class);when(access.actor()).thenReturn(new ArtResourceAccess.Actor(10,2));store=mock(ArtResourceObjectStore.class);closure=mock(ArtResourceClosureVerifier.class);
        resources=new ArtResourceService();tx=new ArtResourceTransactionService();
        for(Object service:List.of(resources,tx)){ReflectionTestUtils.setField(service,"jdbc",db);ReflectionTestUtils.setField(service,"access",access);ReflectionTestUtils.setField(service,"json",json);ReflectionTestUtils.setField(service,"transactions",manager);}
        ReflectionTestUtils.setField(tx,"resources",resources);ReflectionTestUtils.setField(tx,"store",store);ReflectionTestUtils.setField(tx,"closure",closure);ReflectionTestUtils.setField(tx,"verifier",verifier);
        var signer=mock(ArtResourceUploadSigner.class);when(signer.sign(anyString(),anyString())).thenReturn("https://example.invalid/put");ReflectionTestUtils.setField(tx,"signer",signer);
        operation=create();prepare(operation);
    }
    Map<String,Object> create()throws Exception{return resources.create(json.readTree("{\"operationId\":\""+UUID.randomUUID()+"\",\"operationType\":\"CREATE\",\"resourceScope\":\"TENANT_SHARED\",\"metadata\":{\"resourceName\":\"汽车\",\"resourceType\":\"MODEL\",\"assetName\":\"car\"}}"));}
    void prepare(Map<String,Object> op)throws Exception{
        ObjectNode manifest=json.createObjectNode();manifest.put("schemaVersion","wx.art-resource.v1");manifest.put("resourceId",op.get("resourceId").toString());manifest.put("contentFormat","unity.assetbundle.v1");manifest.put("platform","WebGL");manifest.put("appCompatibility","test");
        ObjectNode entry=manifest.putObject("entry");entry.put("kind","MODEL");entry.put("assetName","car");entry.put("packagePath","car.bundle");entry.put("nativeManifestPath","native");entry.put("catalogPath","catalog.json");entry.put("packManifestPath","packs.json");
        ArrayNode files=manifest.putArray("files"),plan=json.createArrayNode();
        for(String path:List.of("car.bundle","native","catalog.json","packs.json")){ObjectNode f=files.addObject();f.put("path",path);f.put("size","1");f.put("sha256","a".repeat(64));ObjectNode upload=plan.addObject();upload.put("path",path);upload.put("size",1);upload.put("sha256","a".repeat(64));}
        manifest.put("contentHash",verifier.canonicalHash(manifest));manifestBytes=json.writeValueAsBytes(manifest);String hash=ArtResourceManifestVerifier.sha256(manifestBytes);
        ObjectNode f=plan.addObject();f.put("path","manifest.json");f.put("size",manifestBytes.length);f.put("sha256",hash);
        tx.uploadTargets(op.get("operationId").toString(),json.createObjectNode().set("files",plan));
        commit=json.readTree("{\"manifestPath\":\"manifest.json\",\"manifestSha256\":\""+hash+"\",\"contentHash\":\""+manifest.get("contentHash").asText()+"\",\"contentFormat\":\"unity.assetbundle.v1\",\"platform\":\"WebGL\",\"appCompatibility\":\"test\"}");
        when(store.readManifest(anyString(),eq("manifest.json"))).thenReturn(manifestBytes);
    }
    String op(){return operation.get("operationId").toString();}
    String id(){return operation.get("resourceId").toString();}
    @Test void firstCommitUpdatesPointerSnapshotAndReceiptTogether(){var result=tx.commit(op(),commit);assertEquals("COMMITTED",result.get("status"));assertEquals("1",result.get("committedRevision"));assertEquals("READY",db.queryForObject("SELECT content_state FROM wx_dim3d_resource WHERE id=?",String.class,id()));assertNotNull(db.queryForObject("SELECT after_snapshot FROM wx_dim3d_resource_operation WHERE id=?",String.class,op()));}
    @Test void replayDoesNotFreezeOrIncrementAgain(){tx.commit(op(),commit);tx.commit(op(),commit);verify(store,times(1)).freeze(anyString(),anyString(),anyList());assertEquals(1L,db.queryForObject("SELECT current_revision FROM wx_dim3d_resource WHERE id=?",Long.class,id()));}
    @Test void differentCommitForSameOperationRejected(){tx.commit(op(),commit);ObjectNode changed=commit.deepCopy();changed.put("contentHash","b".repeat(64));assertThrows(ServiceException.class,()->tx.commit(op(),changed));}
    @Test void copyFailureKeepsOldPointer(){doThrow(new ServiceException(502,"合成存储错误")).when(store).freeze(anyString(),anyString(),anyList());assertThrows(ServiceException.class,()->tx.commit(op(),commit));assertEquals(0L,db.queryForObject("SELECT current_revision FROM wx_dim3d_resource WHERE id=?",Long.class,id()));assertEquals("FAILED",resources.operation(op()).get("status"));}
    @Test void retryFailedSamePayloadSucceeds(){doThrow(new ServiceException(502,"临时错误")).doNothing().when(store).freeze(anyString(),anyString(),anyList());assertThrows(ServiceException.class,()->tx.commit(op(),commit));assertEquals("COMMITTED",tx.commit(op(),commit).get("status"));}
    @Test void revisionConflictDoesNotOverwrite(){db.update("UPDATE wx_dim3d_resource SET current_revision=1 WHERE id=?",id());assertThrows(ServiceException.class,()->tx.commit(op(),commit));assertEquals("CONFLICT",resources.operation(op()).get("status"));assertEquals(1L,db.queryForObject("SELECT current_revision FROM wx_dim3d_resource WHERE id=?",Long.class,id()));}
    @Test void catalogueRejectionKeepsOldPointer(){doThrow(new ServiceException(400,"缺依赖")).when(closure).verify(anyString(),any());assertThrows(ServiceException.class,()->tx.commit(op(),commit));assertEquals(0L,db.queryForObject("SELECT current_revision FROM wx_dim3d_resource WHERE id=?",Long.class,id()));}
    @Test void cancelledOperationCannotCommit(){db.update("UPDATE wx_dim3d_resource_operation SET status='CANCELLED' WHERE id=?",op());assertThrows(ServiceException.class,()->tx.commit(op(),commit));verify(store,never()).freeze(anyString(),anyString(),anyList());}
    @Test void unauthorizedOperatorCannotCommit(){when(access.actor()).thenReturn(new ArtResourceAccess.Actor(20,3));assertThrows(ServiceException.class,()->tx.commit(op(),commit));verify(store,never()).freeze(anyString(),anyString(),anyList());}
    @Test void invalidTargetPlanCannotChangeBoundPlan()throws Exception{var body=json.readTree("{\"files\":[{\"path\":\"other.bundle\",\"size\":1,\"sha256\":\""+"a".repeat(64)+"\"}]}");assertThrows(ServiceException.class,()->tx.uploadTargets(op(),body));}
    @Test void receiptDatabaseFailureRollsBackPointer(){db.execute("ALTER TABLE wx_dim3d_resource_operation ADD CONSTRAINT fail_success CHECK(status<>'COMMITTED')");assertThrows(ServiceException.class,()->tx.commit(op(),commit));assertEquals(0L,db.queryForObject("SELECT current_revision FROM wx_dim3d_resource WHERE id=?",Long.class,id()));assertNull(db.queryForObject("SELECT current_operation_id FROM wx_dim3d_resource WHERE id=?",String.class,id()));}
}
