package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.system.api.tenant.TenantApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证发布票据只读、过期、撤销和发布状态联动。 */
class ScenePublicationTicketServiceTest {
    private ScenePublicationTicketService service;
    private JdbcTemplate jdbc;
    private ArtResourceAccess access;
    private SceneTenantGrantService grants;

    @BeforeEach void setup() throws Exception {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(data);
        String publicationSchema = Files.readString(Path.of("../deploy/test148/migrations/data09/001-scene-publication.up.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        String ticketSchema = Files.readString(Path.of("../deploy/test148/migrations/data09/002-publication-ticket.up.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String sql : publicationSchema.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        for (String sql : ticketSchema.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        jdbc.execute(Files.readString(Path.of("../deploy/test148/migrations/scene02/001-scene-grant.up.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", ""));
        jdbc.execute(Files.readString(Path.of("../deploy/test148/migrations/scene02/002-ticket-grant.up.sql")));
        jdbc.execute("CREATE TABLE wx_3d_scene(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted TINYINT DEFAULT 0)");
        jdbc.execute("CREATE TABLE wx_3d_scene_template(tenant_id BIGINT,template_scene_id BIGINT)");
        jdbc.update("INSERT INTO wx_3d_scene(id,tenant_id) VALUES(101,2)");
        String auditSchema = Files.readString(Path.of("../deploy/test148/migrations/data09/003-publication-audit.up.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String sql : auditSchema.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        jdbc.update("INSERT INTO wx_3d_scene_publication(id,publication_id,tenant_id,scene_id,revision_no,source_hash,index_version,dependency_snapshot,request_id,status,current_flag,created_by) VALUES(1,'PUB-1',2,101,1,?,1,?,'request-1','PUBLISHED',1,2)", "a".repeat(64), "{\"resources\":[]}");
        jdbc.execute("CREATE TABLE wx_3d_scene_revision(tenant_id BIGINT,scene_id BIGINT,revision_no INT,schema_version INT,scene_json LONGTEXT,three_d_json LONGTEXT,two_d_json LONGTEXT,dependencies_json LONGTEXT)");
        jdbc.update("INSERT INTO wx_3d_scene_revision VALUES(2,101,1,1,'{}','{}','{}',?)", "{\"resources\":[]}");
        jdbc.update("UPDATE wx_3d_scene_publication SET source_hash=?", SceneRevisionContentHasher.sha256(1, "{}", "{}", "{}", "{\"resources\":[]}"));
        access = mock(ArtResourceAccess.class);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(2, 2));
        when(access.isPlatformAdministrator(new ArtResourceAccess.Actor(2, 2))).thenReturn(true);
        var tenants = mock(TenantApi.class);
        when(tenants.validTenant(3L)).thenReturn(CommonResult.success(true));
        grants = new SceneTenantGrantService();
        ReflectionTestUtils.setField(grants, "jdbc", jdbc);
        ReflectionTestUtils.setField(grants, "access", access);
        ReflectionTestUtils.setField(grants, "tenants", tenants);
        ReflectionTestUtils.setField(grants, "transactions", new DataSourceTransactionManager(data));
        var target = new ScenePublicationTicketService();
        ReflectionTestUtils.setField(target, "jdbc", jdbc);
        ReflectionTestUtils.setField(target, "access", access);
        var factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data), new AnnotationTransactionAttributeSource()));
        service = (ScenePublicationTicketService) factory.getProxy();
    }

    /** 撤回覆盖正文和资源读取；重新授权也不能恢复上一批票据。 */
    @Test void tenantGrantsOnlyCurrentPublicationAndRevocationInvalidatesIssuedTickets() {
        jdbc.update("UPDATE wx_3d_scene_publication SET index_version=2 WHERE publication_id='PUB-1'");
        var owner = new ArtResourceAccess.Actor(2, 2);
        var target = new ArtResourceAccess.Actor(3, 3);
        String ownerToken = service.issue("PUB-1", 300).get("token").toString();
        when(access.actor()).thenReturn(target);
        assertEquals(404, assertThrows(ServiceException.class, () -> service.issue("PUB-1", 300)).getCode());
        assertEquals(403, assertThrows(ServiceException.class, () -> grants.grant("101", "2")).getCode());
        when(access.actor()).thenReturn(owner);
        grants.grant("101", "3");
        grants.grant("101", "3");
        assertEquals(1, grants.list("101").size());
        assertEquals("3", grants.list("101").get(0).get("grantTenantId"));
        when(access.actor()).thenReturn(target);
        String token = service.issue("PUB-1", 300).get("token").toString();
        assertEquals("101", service.consumeSnapshot(token).get("sceneId"));
        assertEquals(2, service.authorizeResources(token).tenant());
        jdbc.update("UPDATE wx_3d_scene_publication SET current_flag=0 WHERE publication_id='PUB-1'");
        assertEquals(404, assertThrows(ServiceException.class, () -> service.issue("PUB-1", 300)).getCode());
        assertEquals(401, assertThrows(ServiceException.class, () -> service.verify(token)).getCode());
        jdbc.update("UPDATE wx_3d_scene_publication SET current_flag=1 WHERE publication_id='PUB-1'");
        when(access.actor()).thenReturn(owner);
        grants.revoke("101", "3");
        assertEquals(0, grants.list("101").get(0).get("status"));
        assertEquals(401, assertThrows(ServiceException.class, () -> service.consumeSnapshot(token)).getCode());
        assertEquals(401, assertThrows(ServiceException.class, () -> service.authorizeResources(token)).getCode());
        assertEquals("101", service.verify(ownerToken).get("sceneId"));
        grants.grant("101", "3");
        assertEquals(401, assertThrows(ServiceException.class, () -> service.verify(token)).getCode());
        when(access.actor()).thenReturn(target);
        assertNotNull(service.issue("PUB-1", 300).get("ticketId"));
    }

    @Test void issueVerifyAndRevokeTicket() {
        var issued = service.issue("PUB-1", 300);
        assertTrue(issued.get("token").toString().length() > 40);
        assertEquals("101", service.verify(issued.get("token").toString()).get("sceneId"));
        service.revoke(issued.get("ticketId").toString());
        assertThrows(ServiceException.class, () -> service.verify(issued.get("token").toString()));
    }

    /** 回退脚本先封禁授权票据，普通存量票据继续保持原状态。 */
    @Test void grantMigrationRollbackKeepsOnlyOrdinaryTicketsActive() throws Exception {
        String ownerTicket = service.issue("PUB-1", 300).get("ticketId").toString();
        grants.grant("101", "3");
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(3, 3));
        String grantedTicket = service.issue("PUB-1", 300).get("ticketId").toString();
        for (String filename : java.util.List.of("002-ticket-grant.down.sql", "001-scene-grant.down.sql")) {
            for (String sql : Files.readString(Path.of("../deploy/test148/migrations/scene02", filename)).split(";")) {
                if (!sql.isBlank()) jdbc.execute(sql);
            }
        }
        String status = "SELECT status FROM wx_3d_scene_publication_ticket WHERE ticket_id=?";
        assertEquals("ACTIVE", jdbc.queryForObject(status, String.class, ownerTicket));
        assertEquals("REVOKED", jdbc.queryForObject(status, String.class, grantedTicket));
    }

    @Test void revokedPublicationInvalidatesExistingTicket() {
        var issued = service.issue("PUB-1", 300);
        jdbc.update("UPDATE wx_3d_scene_publication SET status='REVOKED' WHERE publication_id='PUB-1'");
        assertThrows(ServiceException.class, () -> service.verify(issued.get("token").toString()));
    }

    @Test void expiryBoundaryAndUnknownTokenAreRejected() {
        assertThrows(ServiceException.class, () -> service.issue("PUB-1", 59));
        assertThrows(ServiceException.class, () -> service.issue("PUB-1", 3601));
        assertThrows(ServiceException.class, () -> service.verify("unknown-token"));
    }

    @Test void nonReadScopeCannotAuthorizeConsumption() {
        var issued = service.issue("PUB-1", 300);
        // 数据异常也必须拒绝，不能因为状态有效便隐式授予读取范围。
        jdbc.update("UPDATE wx_3d_scene_publication_ticket SET scope='WRITE' WHERE ticket_id=?", issued.get("ticketId"));
        assertEquals(401, assertThrows(ServiceException.class, () -> service.verify(issued.get("token").toString())).getCode());
        assertEquals(401, assertThrows(ServiceException.class, () -> service.consumeSnapshot(issued.get("token").toString())).getCode());
        assertEquals(401, assertThrows(ServiceException.class, () -> service.authorizeResources(issued.get("token").toString())).getCode());
    }

    @Test void resourceGrantRequiresIndexTwoAndDiesWithTicket() {
        var issued = service.issue("PUB-1", 300);
        String token = issued.get("token").toString();
        assertEquals(409, assertThrows(ServiceException.class, () -> service.authorizeResources(token)).getCode());
        jdbc.update("UPDATE wx_3d_scene_publication SET index_version=2");
        assertEquals(2L, service.authorizeResources(token).tenant());
        service.revoke(issued.get("ticketId").toString());
        assertEquals(401, assertThrows(ServiceException.class, () -> service.authorizeResources(token)).getCode());
    }

    @Test void anonymousConsumptionKeepsBoundTenantAndRevisionWhenCurrentPointerMoves() {
        var issued = service.issue("PUB-1", 300);
        // 相同场景编号的其他租户数据及后续修订不得影响固定票据读取。
        jdbc.update("INSERT INTO wx_3d_scene_revision VALUES(3,101,1,1,'other-tenant','{}','{}','{}')");
        jdbc.update("INSERT INTO wx_3d_scene_revision VALUES(2,101,2,1,'new-draft','{}','{}','{}')");
        jdbc.update("UPDATE wx_3d_scene_publication SET current_flag=0");
        clearInvocations(access);
        var snapshot = service.consumeSnapshot(issued.get("token").toString());
        assertEquals("PUB-1", snapshot.get("publicationId"));
        assertEquals("101", snapshot.get("sceneId"));
        assertEquals(1, snapshot.get("revisionNo"));
        assertEquals("{}", snapshot.get("sceneJson"));
        assertEquals("{\"resources\":[]}", snapshot.get("dependenciesJson"));
        assertEquals(10, snapshot.size());
        verifyNoInteractions(access);
    }

    @Test void expiredRevokedAndUnknownTicketsCannotConsume() {
        var issued = service.issue("PUB-1", 300);
        String token = issued.get("token").toString();
        // 使用明确的过去时间，避免秒级日期列将当前时间舍入到未来一秒，造成过期断言偶发失败。
        jdbc.update("UPDATE wx_3d_scene_publication_ticket SET expires_time='2000-01-01 00:00:00'");
        assertEquals(401, assertThrows(ServiceException.class, () -> service.consumeSnapshot(token)).getCode());
        jdbc.update("UPDATE wx_3d_scene_publication_ticket SET expires_time='2099-01-01 00:00:00',status='REVOKED'");
        assertEquals(401, assertThrows(ServiceException.class, () -> service.consumeSnapshot(token)).getCode());
        jdbc.update("UPDATE wx_3d_scene_publication_ticket SET status='ACTIVE'");
        jdbc.update("UPDATE wx_3d_scene_publication SET status='REVOKED'");
        assertEquals(401, assertThrows(ServiceException.class, () -> service.consumeSnapshot(token)).getCode());
        for (String invalid : new String[]{null, "", "unknown", "x".repeat(257)})
            assertEquals(401, assertThrows(ServiceException.class, () -> service.consumeSnapshot(invalid)).getCode());
    }

    @Test void modifiedOrMissingSnapshotIsRejectedWithoutDraftFallback() {
        String token = service.issue("PUB-1", 300).get("token").toString();
        jdbc.update("UPDATE wx_3d_scene_revision SET scene_json='changed'");
        assertEquals(409, assertThrows(ServiceException.class, () -> service.consumeSnapshot(token)).getCode());
        jdbc.update("UPDATE wx_3d_scene_revision SET scene_json='{}'");
        jdbc.update("UPDATE wx_3d_scene_publication SET dependency_snapshot='{}'");
        assertEquals(409, assertThrows(ServiceException.class, () -> service.consumeSnapshot(token)).getCode());
        jdbc.update("DELETE FROM wx_3d_scene_revision");
        assertEquals(409, assertThrows(ServiceException.class, () -> service.consumeSnapshot(token)).getCode());
    }

    @Test void cleanupExpiredMarksTicketAndWritesAudit() {
        var issued = service.issue("PUB-1", 300);
        jdbc.update("UPDATE wx_3d_scene_publication_ticket SET expires_time='2000-01-01 00:00:00' WHERE ticket_id=?", issued.get("ticketId"));
        assertEquals(1, service.cleanupExpired(10));
        assertEquals("EXPIRED", jdbc.queryForObject("SELECT status FROM wx_3d_scene_publication_ticket WHERE ticket_id=?", String.class, issued.get("ticketId")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication_audit WHERE action='TICKET_EXPIRE'", Integer.class));
    }

    @Test void liveGrantUsesFixedSnapshotAndStopsAfterTicketOrPublicationRevocation() {
        var issue = service.issue("PUB-1", 300);
        String token = issue.get("token").toString();
        var current = service.issue("PUB-1", 300);
        String ticketId = issue.get("ticketId").toString();
        String currentId = current.get("ticketId").toString();
        // 草稿推进后仍读取票据固定的修订，且票据不能被其他租户或发布复用。
        jdbc.update("INSERT INTO wx_3d_scene_revision VALUES(2,101,2,1,'new-draft','{}','{}','{}')");
        var snapshot = service.consumeLiveSnapshot(token, "PUB-1", 2);
        assertEquals(ticketId, snapshot.ticketId());
        assertEquals(1, snapshot.snapshot().get("revisionNo"));
        assertEquals(401, assertThrows(ServiceException.class, () -> service.consumeLiveSnapshot(token, "PUB-1", 3)).getCode());
        assertEquals(401, assertThrows(ServiceException.class, () -> service.consumeLiveSnapshot(token, "PUB-2", 2)).getCode());
        assertEquals(java.util.Set.of(ticketId, currentId), service.activeLiveTickets(2, java.util.Set.of(ticketId, currentId)));
        assertTrue(service.activeLiveTickets(3, java.util.Set.of(ticketId)).isEmpty());
        service.revoke(ticketId);
        assertEquals(java.util.Set.of(currentId), service.activeLiveTickets(2, java.util.Set.of(ticketId, currentId)));
        jdbc.update("UPDATE wx_3d_scene_publication SET status='REVOKED'");
        assertTrue(service.activeLiveTickets(2, java.util.Set.of(currentId)).isEmpty());
        assertEquals(401, assertThrows(ServiceException.class,
                () -> service.consumeLiveSnapshot(current.get("token").toString(), "PUB-1", 2)).getCode());
    }


}
