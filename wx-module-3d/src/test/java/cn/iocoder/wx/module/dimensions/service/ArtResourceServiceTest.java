package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实SQL执行验证范围过滤和幂等回滚；生产DDL另有MySQL演练证据。 */
class ArtResourceServiceTest {
    private ArtResourceService service;private ArtResourceAccess access;private JdbcTemplate db;private ObjectMapper json=new ObjectMapper();
    private ArtResourceObjectStore store;private ArtResourceDownloadSigner signer;
    private final ArtResourceManifestVerifier verifier=new ArtResourceManifestVerifier();
    @BeforeEach void setup(){
        var data=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        db=new JdbcTemplate(data);service=new ArtResourceService();access=mock(ArtResourceAccess.class);when(access.actor()).thenReturn(new ArtResourceAccess.Actor(10,2));
        signer=mock(ArtResourceDownloadSigner.class);when(signer.sign(anyString(),anyString())).thenReturn(Map.of("manifestUrl","https://example.invalid/manifest.json","downloadExpiresAt","2030-01-01T00:00:00Z"));
        store=mock(ArtResourceObjectStore.class);
        ReflectionTestUtils.setField(service,"store",store);ReflectionTestUtils.setField(service,"verifier",verifier);
        ReflectionTestUtils.setField(service,"jdbc",db);ReflectionTestUtils.setField(service,"access",access);ReflectionTestUtils.setField(service,"signer",signer);ReflectionTestUtils.setField(service,"json",json);ReflectionTestUtils.setField(service,"transactions",new DataSourceTransactionManager(data));
        db.execute("CREATE TABLE wx_dim3d_resource(id BIGINT PRIMARY KEY,resource_name VARCHAR(150),resource_type VARCHAR(20),asset_name VARCHAR(150),resource_relative_path VARCHAR(255),resource_scope VARCHAR(20),tenant_id VARCHAR(20),content_state VARCHAR(16),current_revision BIGINT DEFAULT 0,content_hash VARCHAR(64),manifest_sha256 VARCHAR(64),storage_prefix VARCHAR(512),manifest_path VARCHAR(512),platform VARCHAR(20),content_format VARCHAR(32),app_compatibility VARCHAR(128),create_by BIGINT,del_flag CHAR DEFAULT '0')");
        db.execute("CREATE TABLE wx_dim3d_resource_operation(id VARCHAR(36) PRIMARY KEY,resource_id BIGINT,operation_type VARCHAR(16),resource_scope VARCHAR(20),tenant_id VARCHAR(20),operator_id BIGINT,request_hash VARCHAR(64),expected_revision BIGINT,committed_revision BIGINT,status VARCHAR(16) DEFAULT 'UPLOADING',storage_prefix VARCHAR(512),candidate_descriptor VARCHAR(4096),after_snapshot CLOB,expires_at TIMESTAMP,error_code VARCHAR(64))");
        for(long id:List.of(11L,21L,31L,41L,51L))db.update("INSERT INTO wx_dim3d_resource(id,resource_name,resource_type,resource_scope,tenant_id,content_state,current_revision,content_hash,manifest_sha256,storage_prefix,manifest_path,content_format,platform,app_compatibility,del_flag) VALUES(?,'汽车','MODEL',?,?,?,1,?,?,'resources/a/','manifest.json','unity.assetbundle.v1','WebGL','test',?)",id,id==11?"GLOBAL_SYSTEM":id==41?null:"TENANT_SHARED",id==11?"0":id==31?"3":"2",id==41?"LEGACY":"READY","a".repeat(64),"b".repeat(64),id==51?"1":"0");
    }
    @Test void visibleListIncludesGlobalAndOwnOnly(){var page=service.page(1,20);assertEquals(2L,page.get("total"));assertEquals(2,((List<?>)page.get("list")).size());}
    @Test void ownAndGlobalCurrentReadable(){assertEquals("11",service.current("11").get("resourceId"));assertEquals("21",service.current("21").get("resourceId"));}
    @Test void otherTenantAndLegacyAndDeletedHidden(){for(String id:List.of("31","41","51"))assertThrows(ServiceException.class,()->service.current(id));}
    @Test void numericOverflowAndPagingRejected(){assertThrows(ServiceException.class,()->service.current("9223372036854775808"));assertThrows(ServiceException.class,()->service.page(0,20));assertThrows(ServiceException.class,()->service.page(1,101));}
    @Test void createReplayDoesNotAllocateSecondResource()throws Exception{var body=json.readTree(create());var first=service.create(body);assertEquals(first.get("resourceId"),service.create(body).get("resourceId"));assertEquals(6,db.queryForObject("SELECT COUNT(*) FROM wx_dim3d_resource",Integer.class));}
    @Test void sameIdDifferentPayloadRejected()throws Exception{service.create(json.readTree(create()));assertThrows(ServiceException.class,()->service.create(json.readTree(create().replace("汽车模板","另一汽车"))));}
    @Test void forgedOwnerAndNumericIdRejected()throws Exception{assertThrows(ServiceException.class,()->service.create(json.readTree(create().replace("\"metadata\":","\"tenantId\":\"3\",\"metadata\":"))));assertThrows(ServiceException.class,()->service.create(json.readTree("{\"operationId\":\"6063365a-210b-486b-8c2d-928b10e66a39\",\"operationType\":\"REPLACE\",\"resourceId\":21,\"expectedRevision\":\"1\"}")));}
    @Test void crossTenantOperationHidden()throws Exception{service.create(json.readTree(create()));when(access.actor()).thenReturn(new ArtResourceAccess.Actor(20,3));assertThrows(ServiceException.class,()->service.operation("6063365a-210b-486b-8c2d-928b10e66a39"));}
    @Test void managedResourceRejectsLegacyWriter(){assertThrows(ServiceException.class,()->service.guardLegacyResource(21L));assertDoesNotThrow(()->service.guardLegacyResource(41L));}
    @Test void concurrentReplayCreatesOneResource()throws Exception{
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var body=json.readTree(create());
            var first=pool.submit(()->service.create(body));var second=pool.submit(()->service.create(body));
            assertEquals(first.get().get("resourceId"),second.get().get("resourceId"));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM wx_dim3d_resource_operation",Integer.class));
            assertEquals(6,db.queryForObject("SELECT COUNT(*) FROM wx_dim3d_resource",Integer.class));
        }finally{pool.shutdownNow();}
    }
    /** 使用真实清单校验器和SQL；签发器必须收到旧冻结前缀，不能回退到当前资源行。 */
    @Test void unreadOldFileAndRenewalRemainPinnedAfterReplacement()throws Exception{
        String hash=committedFixture();
        db.update("UPDATE wx_dim3d_resource SET current_revision=2,content_hash=?,storage_prefix='resources/new/' WHERE id=21","b".repeat(64));
        service.file("21","1",hash,"models/car");service.file("21","1",hash,"models/car");
        verify(signer,times(2)).sign("resources/old/frozen/","models/car");
        verify(signer,never()).sign(eq("resources/new/"),anyString());
    }
    @Test void fileNotInManifestCannotBeSigned()throws Exception{
        String hash=committedFixture();
        assertThrows(ServiceException.class,()->service.file("21","1",hash,"private-sidecar.json"));
        verifyNoInteractions(signer);
    }
    @Test void fileDigestAndRevisionMustMatchSnapshot()throws Exception{
        String hash=committedFixture();
        assertThrows(ServiceException.class,()->service.file("21","2",hash,"models/car"));
        assertThrows(ServiceException.class,()->service.file("21","1","b".repeat(64),"models/car"));
        verifyNoInteractions(signer);
    }
    @Test void crossTenantAndDeletedResourcesCannotRenew()throws Exception{
        String hash=committedFixture();
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(20,3));
        assertThrows(ServiceException.class,()->service.file("21","1",hash,"models/car"));
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(10,2));
        db.update("UPDATE wx_dim3d_resource SET del_flag='1' WHERE id=21");
        assertThrows(ServiceException.class,()->service.file("21","1",hash,"models/car"));
        verifyNoInteractions(store,signer);
    }
    @Test void pendingAndWrongScopeSnapshotsCannotBeSigned()throws Exception{
        String hash=committedFixture();
        db.update("UPDATE wx_dim3d_resource_operation SET status='VERIFYING'");
        assertThrows(ServiceException.class,()->service.file("21","1",hash,"models/car"));
        db.update("UPDATE wx_dim3d_resource_operation SET status='COMMITTED',tenant_id='3'");
        assertThrows(ServiceException.class,()->service.file("21","1",hash,"models/car"));
        verifyNoInteractions(signer);
    }
    @Test void changedManifestAndTraversalCannotBeSigned()throws Exception{
        String hash=committedFixture();
        when(store.readManifest(anyString(),anyString())).thenReturn("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(ServiceException.class,()->service.file("21","1",hash,"models/car"));
        for(String path:List.of("../secret","models/","/models/car","models//car"))
            assertThrows(ServiceException.class,()->service.file("21","1",hash,path));
        verifyNoInteractions(signer);
    }
    private String committedFixture()throws Exception{
        var root=json.createObjectNode();
        root.put("schemaVersion","wx.art-resource.v1");root.put("resourceId","21");root.put("contentFormat","unity.assetbundle.v1");root.put("platform","WebGL");root.put("appCompatibility","test");
        var entry=root.putObject("entry");entry.put("kind","MODEL");entry.put("assetName","car");entry.put("packagePath","models/car");entry.put("nativeManifestPath","native");entry.put("catalogPath","catalog.json");entry.put("packManifestPath","packs.json");
        var files=root.putArray("files");
        for(String path:List.of("models/car","native","catalog.json","packs.json")){
            var file=files.addObject();file.put("path",path);file.put("size","1");file.put("sha256","a".repeat(64));
        }
        String hash=verifier.canonicalHash(root);root.put("contentHash",hash);
        byte[] bytes=json.writeValueAsBytes(root);
        var saved=json.createObjectNode().putObject("resource");
        saved.put("id","21");saved.put("current_revision","1");saved.put("content_hash",hash);saved.put("content_state","READY");saved.put("del_flag","0");
        saved.put("storage_prefix","resources/old/frozen/");saved.put("manifest_path","manifest.json");saved.put("manifest_sha256",ArtResourceManifestVerifier.sha256(bytes));
        var snapshot=json.createObjectNode();snapshot.set("resource",saved);
        db.update("INSERT INTO wx_dim3d_resource_operation(id,resource_id,resource_scope,tenant_id,committed_revision,status,after_snapshot) VALUES('old',21,'TENANT_SHARED','2',1,'COMMITTED',?)",json.writeValueAsString(snapshot));
        when(store.readManifest("resources/old/frozen/","manifest.json")).thenReturn(bytes);
        return hash;
    }
    private String create(){return "{\"operationId\":\"6063365a-210b-486b-8c2d-928b10e66a39\",\"operationType\":\"CREATE\",\"resourceScope\":\"TENANT_SHARED\",\"metadata\":{\"resourceName\":\"汽车模板\",\"resourceType\":\"MODEL\",\"assetName\":\"car\"}}";}
}
