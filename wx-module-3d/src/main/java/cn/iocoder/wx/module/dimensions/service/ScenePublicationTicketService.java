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
        return Map.of("ticketId", ticketId, "token", raw, "publicationId", publicationId,
                "expiresInSeconds", expiresInSeconds, "scope", "READ");
    }

    /** 撤销票据。发布记录撤销时，即使票据仍在有效期内也无法通过校验。 */
    @Transactional
    public void revoke(String ticketId) {
        var actor = access.actor();
        int changed = jdbc.update("UPDATE wx_3d_scene_publication_ticket SET status='REVOKED',revoked_by=?,revoked_time=CURRENT_TIMESTAMP WHERE ticket_id=? AND tenant_id=? AND status='ACTIVE'",
                actor.user(), ticketId, actor.tenant());
        if (changed != 1) throw new ServiceException(404, "活动票据不存在");
    }

    /** 校验票据并返回固定发布摘要。票据不接受客户端提交租户或发布编号覆盖。 */
    public Map<String, Object> verify(String token) {
        if (token == null || token.isBlank() || token.length() > 256) throw new ServiceException(401, "访问票据无效");
        var rows = jdbc.queryForList("SELECT p.publication_id,p.scene_id,p.revision_no,p.source_hash FROM wx_3d_scene_publication_ticket t JOIN wx_3d_scene_publication p ON p.publication_id=t.publication_id AND p.tenant_id=t.tenant_id WHERE t.token_hash=? AND t.status='ACTIVE' AND t.expires_time>? AND p.status='PUBLISHED'",
                sha256(token), Timestamp.from(Instant.now()));
        if (rows.size() != 1) throw new ServiceException(401, "访问票据已失效");
        var row = rows.get(0);
        return Map.of("publicationId", row.get("publication_id"), "sceneId", row.get("scene_id").toString(),
                "revisionNo", row.get("revision_no"), "sourceHash", row.get("source_hash"));
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
}
