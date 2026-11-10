package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实绑定 SQL 与清单解析，存储和签名边界替换为受控测试对象。 */
class ScenePublicationFileServiceTest {
    private ScenePublicationFileService service;
    private ScenePublicationTicketService tickets;
    private ArtResourceObjectStore store;
    private ArtResourceDownloadSigner signer;
    private JdbcTemplate jdbc;
    private ScenePublicationTicketService.ResourceGrant grant;
    private ArtResourceAccess access;

    @BeforeEach void setup() throws Exception {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE wx_3d_scene_publication_resource(publication_id VARCHAR(64),tenant_id BIGINT,resource_key VARCHAR(160),resource_id BIGINT,resource_revision BIGINT,content_hash VARCHAR(64),manifest_sha256 VARCHAR(64),operation_id VARCHAR(36),resource_scope VARCHAR(32),resource_tenant_id VARCHAR(20),snapshot_json CLOB)");
        jdbc.execute("CREATE TABLE wx_3d_resource(id BIGINT,resource_scope VARCHAR(32),tenant_id VARCHAR(20),del_flag CHAR,content_state VARCHAR(16),current_revision BIGINT)");
        jdbc.execute("CREATE TABLE wx_3d_resource_operation(id VARCHAR(36),resource_id BIGINT,committed_revision BIGINT,resource_scope VARCHAR(32),tenant_id VARCHAR(20),status VARCHAR(16),after_snapshot CLOB)");
        var parser = new ArtResourceManifestVerifier();
        var json = new ObjectMapper();
        var manifest = json.createObjectNode().put("schemaVersion", "wx.art-resource.v1").put("resourceId", "21")
                .put("contentFormat", "unity.assetbundle.v1").put("platform", "WebGL").put("appCompatibility", "test");
        manifest.putObject("entry").put("kind", "MODEL").put("assetName", "car").put("packagePath", "models/car")
                .put("nativeManifestPath", "native").put("catalogPath", "catalog.json").put("packManifestPath", "packs.json");
        var files = manifest.putArray("files");
        for (String path : List.of("models/car", "native", "catalog.json", "packs.json"))
            files.addObject().put("path", path).put("size", "1").put("sha256", "a".repeat(64));
        String hash = parser.canonicalHash(manifest);
        manifest.put("contentHash", hash);
        byte[] bytes = json.writeValueAsBytes(manifest);
        String manifestHash = ArtResourceManifestVerifier.sha256(bytes);
        var snapshot = json.createObjectNode();
        snapshot.putObject("resource").put("id", "21").put("current_revision", "1").put("content_hash", hash)
                .put("manifest_sha256", manifestHash).put("content_state", "READY").put("del_flag", "0")
                .put("content_format", "unity.assetbundle.v1").put("platform", "WebGL").put("app_compatibility", "test")
                .put("storage_prefix", "resources/old/").put("manifest_path", "manifest.json");
        jdbc.update("INSERT INTO wx_3d_scene_publication_resource VALUES('PUB-1',2,'model',21,1,?,?,'operation','TENANT_SHARED','2',?)", hash, manifestHash, snapshot.toString());
        jdbc.update("INSERT INTO wx_3d_resource VALUES(21,'TENANT_SHARED','2','0','READY',2)");
        jdbc.update("INSERT INTO wx_3d_resource_operation VALUES('operation',21,1,'TENANT_SHARED','2','COMMITTED',?)", snapshot.toString());
        tickets = mock(ScenePublicationTicketService.class);
        grant = new ScenePublicationTicketService.ResourceGrant("PUB-1", 2, Instant.now().plusSeconds(60));
        when(tickets.authorizeResources("ticket")).thenReturn(grant);
        store = mock(ArtResourceObjectStore.class);
        when(store.readManifest("resources/old/", "manifest.json")).thenReturn(bytes);
        signer = mock(ArtResourceDownloadSigner.class);
        when(signer.signUntil(anyString(), anyString(), any())).thenReturn(Map.of("manifestUrl", "https://example.invalid/unit-file", "downloadExpiresAt", grant.expiresAt().toString()));
        access = mock(ArtResourceAccess.class);
        var resources = new ArtResourceService();
        ReflectionTestUtils.setField(resources, "verifier", parser);
        ReflectionTestUtils.setField(resources, "modelVerifier", new ModelResourceManifestVerifier());
        ReflectionTestUtils.setField(resources, "store", store);
        ReflectionTestUtils.setField(resources, "access", access);
        service = new ScenePublicationFileService();
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "tickets", tickets);
        ReflectionTestUtils.setField(service, "resources", resources);
        ReflectionTestUtils.setField(service, "parser", parser);
        ReflectionTestUtils.setField(service, "store", store);
        ReflectionTestUtils.setField(service, "signer", signer);
    }

    @Test void listAndFileKeepHistoricalVersionWithoutLoginIdentity() {
        var list = (List<?>) service.list("ticket").get("resources");
        assertEquals(1, list.size());
        assertEquals("1", ((Map<?, ?>) list.get(0)).get("revision"));
        assertFalse(list.toString().contains("storage_prefix"));
        service.file("ticket", "model", "manifest.json");
        service.file("ticket", "model", "models/car");
        verify(signer).signUntil("resources/old/", "manifest.json", grant.expiresAt());
        verify(signer).signUntil("resources/old/", "models/car", grant.expiresAt());
        verify(store).verifyAvailable(eq("resources/old/"), argThat(file -> file.path().equals("models/car") && file.size() == 1));
        verifyNoInteractions(access);
    }

    @Test void traversalUnknownResourceAndPrivateSidecarsNeverSign() {
        assertThrows(ServiceException.class, () -> service.file("ticket", "other", "models/car"));
        assertThrows(ServiceException.class, () -> service.file("ticket", "model", "../secret"));
        assertThrows(ServiceException.class, () -> service.file("ticket", "model", "converted.zip"));
        verifyNoInteractions(signer);
    }

    @Test void unavailableOrChangedBindingIsRejected() {
        jdbc.update("UPDATE wx_3d_resource SET del_flag='1'");
        assertThrows(ServiceException.class, () -> service.list("ticket"));
        assertThrows(ServiceException.class, () -> service.file("ticket", "model", "manifest.json"));
        jdbc.update("UPDATE wx_3d_resource SET del_flag='0'");
        jdbc.update("UPDATE wx_3d_resource_operation SET after_snapshot='{}'");
        assertThrows(ServiceException.class, () -> service.file("ticket", "model", "manifest.json"));
        verifyNoInteractions(signer);
    }

    @Test void wrongTenantGrantCannotReachAnotherPublicationBindings() {
        when(tickets.authorizeResources("ticket")).thenReturn(new ScenePublicationTicketService.ResourceGrant("PUB-1", 3, grant.expiresAt()));
        assertEquals(List.of(), service.list("ticket").get("resources"));
        assertThrows(ServiceException.class, () -> service.file("ticket", "model", "manifest.json"));
        verifyNoInteractions(store, signer);
    }

    @Test void revokedDuringManifestReadCannotCreateNewSignedUrl() {
        when(tickets.authorizeResources("ticket")).thenReturn(grant).thenThrow(new ServiceException(401, "访问票据已失效"));
        assertThrows(ServiceException.class, () -> service.file("ticket", "model", "models/car"));
        verifyNoInteractions(signer);
    }

    @Test void corruptManifestOrMissingMemberCannotSign() {
        doThrow(new ServiceException(404, "测试成员缺失")).when(store).verifyAvailable(anyString(), any());
        assertThrows(ServiceException.class, () -> service.file("ticket", "model", "models/car"));
        when(store.readManifest(anyString(), anyString())).thenReturn("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(ServiceException.class, () -> service.file("ticket", "model", "manifest.json"));
        verifyNoInteractions(signer);
    }
}
