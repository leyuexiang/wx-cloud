package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
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

    @BeforeEach void setup() throws Exception {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(data);
        String publicationSchema = Files.readString(Path.of("../deploy/test148/migrations/data09/001-scene-publication.up.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        String ticketSchema = Files.readString(Path.of("../deploy/test148/migrations/data09/002-publication-ticket.up.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String sql : publicationSchema.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        for (String sql : ticketSchema.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        String auditSchema = Files.readString(Path.of("../deploy/test148/migrations/data09/003-publication-audit.up.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String sql : auditSchema.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        jdbc.update("INSERT INTO wx_3d_scene_publication(id,publication_id,tenant_id,scene_id,revision_no,source_hash,index_version,dependency_snapshot,request_id,status,current_flag,created_by) VALUES(1,'PUB-1',2,101,1,?,1,?,'request-1','PUBLISHED',1,2)", "a".repeat(64), "{\"resources\":[]}");
        jdbc.execute("CREATE TABLE wx_3d_scene_revision(tenant_id BIGINT,scene_id BIGINT,revision_no INT,schema_version INT,scene_json LONGTEXT,three_d_json LONGTEXT,two_d_json LONGTEXT,dependencies_json LONGTEXT)");
        jdbc.update("INSERT INTO wx_3d_scene_revision VALUES(2,101,1,1,'{}','{}','{}',?)", "{\"resources\":[]}");
        jdbc.update("UPDATE wx_3d_scene_publication SET source_hash=?", SceneRevisionContentHasher.sha256(1, "{}", "{}", "{}", "{\"resources\":[]}"));
        access = mock(ArtResourceAccess.class);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(2, 2));
        var target = new ScenePublicationTicketService();
        ReflectionTestUtils.setField(target, "jdbc", jdbc);
        ReflectionTestUtils.setField(target, "access", access);
        var factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data), new AnnotationTransactionAttributeSource()));
        service = (ScenePublicationTicketService) factory.getProxy();
    }

    @Test void issueVerifyAndRevokeTicket() {
        var issued = service.issue("PUB-1", 300);
        assertTrue(issued.get("token").toString().length() > 40);
        assertEquals("101", service.verify(issued.get("token").toString()).get("sceneId"));
        service.revoke(issued.get("ticketId").toString());
        assertThrows(ServiceException.class, () -> service.verify(issued.get("token").toString()));
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
        jdbc.update("UPDATE wx_3d_scene_publication_ticket SET expires_time=CURRENT_TIMESTAMP");
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
}
