package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** 发布读取票据服务。数据库只保存摘要，票据失效由状态、过期时间和发布状态共同决定。 */
@Service
public class ScenePublicationTicketService {
    private static final SecureRandom RANDOM = new SecureRandom();
    @Resource private JdbcTemplate jdbc;
    @Resource private ArtResourceAccess access;

    /** 为当前租户的已发布记录签发短时只读票据。 */
    @Transactional
    public Map<String, Object> issue(String publicationId, int expiresInSeconds) {
        var actor = access.actor();
        if (expiresInSeconds < 60 || expiresInSeconds > 3600) throw new ServiceException(400, "票据有效期必须在60到3600秒之间");
        var publications = jdbc.queryForList("SELECT publication_id FROM wx_3d_scene_publication WHERE publication_id=? AND tenant_id=? AND status='PUBLISHED'",
                publicationId, actor.tenant());
        if (publications.size() != 1) throw new ServiceException(404, "发布记录不存在或已撤销");
        String raw = randomToken();
        String ticketId = "TKT-" + IdWorker.getId();
        jdbc.update("INSERT INTO wx_3d_scene_publication_ticket(id,ticket_id,publication_id,tenant_id,token_hash,scope,status,expires_time,created_by) VALUES(?,?,?,?,?,?,?,?,?)",
                IdWorker.getId(), ticketId, publicationId, actor.tenant(), sha256(raw), "READ", "ACTIVE",
                Timestamp.from(Instant.now().plusSeconds(expiresInSeconds)), actor.user());
        audit(actor, publicationId, ticketId, "TICKET_ISSUE", "");
        return Map.of("ticketId", ticketId, "token", raw, "publicationId", publicationId,
                "expiresInSeconds", expiresInSeconds, "scope", "READ");
    }

    /** 撤销票据。发布记录撤销时，即使票据仍在有效期内也无法通过校验。 */
    @Transactional
    public void revoke(String ticketId) {
        var actor = access.actor();
        var rows = jdbc.queryForList("SELECT publication_id FROM wx_3d_scene_publication_ticket WHERE ticket_id=? AND tenant_id=? AND status='ACTIVE'",
                ticketId, actor.tenant());
        int changed = jdbc.update("UPDATE wx_3d_scene_publication_ticket SET status='REVOKED',revoked_by=?,revoked_time=CURRENT_TIMESTAMP WHERE ticket_id=? AND tenant_id=? AND status='ACTIVE'",
                actor.user(), ticketId, actor.tenant());
        if (changed != 1) throw new ServiceException(404, "活动票据不存在");
        audit(actor, rows.get(0).get("publication_id").toString(), ticketId, "TICKET_REVOKE", "");
    }

    /** 有界标记已过期票据，保留历史行并为每行写入审计。 */
    @Transactional
    public int cleanupExpired(int limit) {
        if (limit < 1 || limit > 1000) throw new ServiceException(400, "清理批次无效");
        var actor = access.actor();
        var rows = jdbc.queryForList("SELECT ticket_id,publication_id FROM wx_3d_scene_publication_ticket WHERE tenant_id=? AND status='ACTIVE' AND expires_time<=? ORDER BY expires_time ASC LIMIT ?",
                actor.tenant(), Timestamp.from(Instant.now()), limit);
        int changed = 0;
        for (var row : rows) {
            int updated = jdbc.update("UPDATE wx_3d_scene_publication_ticket SET status='EXPIRED',revoked_by=?,revoked_time=CURRENT_TIMESTAMP WHERE ticket_id=? AND tenant_id=? AND status='ACTIVE'",
                    actor.user(), row.get("ticket_id"), actor.tenant());
            changed += updated;
            if (updated == 1) audit(actor, row.get("publication_id").toString(), row.get("ticket_id").toString(), "TICKET_EXPIRE", "");
        }
        return changed;
    }

    /** 校验票据并返回固定发布摘要。票据不接受客户端提交租户或发布编号覆盖。 */
    public Map<String, Object> verify(String token) {
        var rows = jdbc.queryForList("SELECT p.publication_id,p.scene_id,p.revision_no,p.source_hash FROM wx_3d_scene_publication_ticket t JOIN wx_3d_scene_publication p ON p.publication_id=t.publication_id AND p.tenant_id=t.tenant_id WHERE t.token_hash=? AND t.scope='READ' AND t.status='ACTIVE' AND t.expires_time>? AND p.status='PUBLISHED'",
                tokenHash(token), Timestamp.from(Instant.now()));
        if (rows.size() != 1) throw new ServiceException(401, "访问票据已失效");
        var row = rows.get(0);
        return Map.of("publicationId", row.get("publication_id"), "sceneId", row.get("scene_id").toString(),
                "revisionNo", row.get("revision_no"), "sourceHash", row.get("source_hash"));
    }

    /** 匿名消费只使用票据绑定的租户、发布和修订；一次查询避免校验后再读取的身份切换。 */
    public Map<String, Object> consumeSnapshot(String token) {
        var rows = jdbc.queryForList("SELECT p.publication_id,p.scene_id,p.revision_no,p.source_hash,p.index_version,p.dependency_snapshot,"
                        + "r.schema_version,r.scene_json,r.three_d_json,r.two_d_json,r.dependencies_json "
                        + "FROM wx_3d_scene_publication_ticket t JOIN wx_3d_scene_publication p ON p.publication_id=t.publication_id AND p.tenant_id=t.tenant_id "
                        + "LEFT JOIN wx_3d_scene_revision r ON r.tenant_id=p.tenant_id AND r.scene_id=p.scene_id AND r.revision_no=p.revision_no "
                        + "WHERE t.token_hash=? AND t.scope='READ' AND t.status='ACTIVE' AND t.expires_time>? AND p.status='PUBLISHED'",
                tokenHash(token), Timestamp.from(Instant.now()));
        if (rows.size() != 1) throw new ServiceException(401, "访问票据已失效");
        var row = rows.get(0);
        if (!(row.get("schema_version") instanceof Number schema) || schema.intValue() < 1
                || !(row.get("scene_json") instanceof String scene)
                || !(row.get("three_d_json") instanceof String threeD)
                || !(row.get("two_d_json") instanceof String twoD)
                || !(row.get("dependencies_json") instanceof String dependencies))
            throw new ServiceException(409, "发布正文缺失");
        // 修订行仍是兼容期的四包原文，必须与发布摘要逐字节一致，不能回退最新草稿。
        String calculated = SceneRevisionContentHasher.sha256(schema.intValue(), scene, threeD, twoD, dependencies);
        if (!calculated.equals(row.get("source_hash")) || !dependencies.equals(row.get("dependency_snapshot")))
            throw new ServiceException(409, "发布正文与固定摘要不一致");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("publicationId", row.get("publication_id"));
        result.put("sceneId", row.get("scene_id").toString());
        result.put("revisionNo", row.get("revision_no"));
        result.put("sourceHash", calculated);
        result.put("indexVersion", row.get("index_version"));
        result.put("schemaVersion", schema.intValue());
        result.put("sceneJson", scene);
        result.put("threeDJson", threeD);
        result.put("twoDJson", twoD);
        result.put("dependenciesJson", dependencies);
        return result;
    }

    private static String tokenHash(String token) {
        if (token == null || token.isBlank() || token.length() > 256) throw new ServiceException(401, "访问票据无效");
        return sha256(token);
    }

    public record ResourceGrant(String publicationId, long tenant, Instant expiresAt) {}

    /** 资源准入仅由票据绑定推导，不读取登录角色或调用者传入的租户。 */
    public ResourceGrant authorizeResources(String token) {
        var rows = jdbc.query("SELECT p.publication_id,p.tenant_id,p.index_version,t.expires_time FROM wx_3d_scene_publication_ticket t "
                + "JOIN wx_3d_scene_publication p ON p.publication_id=t.publication_id AND p.tenant_id=t.tenant_id "
                + "WHERE t.token_hash=? AND t.scope='READ' AND t.status='ACTIVE' AND t.expires_time>? AND p.status='PUBLISHED'",
                (result, number) -> {
                    if (result.getInt("index_version") != 2) throw new ServiceException(409, "发布缺少固定资源索引");
                    // 通过 JDBC 时间读取器兼容不同驱动对 DATETIME 的对象类型选择。
                    return new ResourceGrant(result.getString("publication_id"), result.getLong("tenant_id"), result.getTimestamp("expires_time").toInstant());
                }, tokenHash(token), Timestamp.from(Instant.now()));
        if (rows.size() != 1) throw new ServiceException(401, "访问票据已失效");
        return rows.get(0);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("票据摘要算法不可用", error);
        }
    }

    private void audit(ArtResourceAccess.Actor actor, String publicationId, String ticketId,
                       String action, String reason) {
        jdbc.update("INSERT INTO wx_3d_scene_publication_audit(id,tenant_id,publication_id,ticket_id,action,reason,operator_id) VALUES(?,?,?,?,?,?,?)",
                IdWorker.getId(), actor.tenant(), publicationId, ticketId, action, reason, actor.user());
    }
}
