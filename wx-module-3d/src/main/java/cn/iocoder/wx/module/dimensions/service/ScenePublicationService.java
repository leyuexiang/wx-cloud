package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 场景不可变发布记录服务。只固定已有修订，不复制或改写草稿四包。 */
@Service
public class ScenePublicationService {
    @Resource private JdbcTemplate jdbc;
    @Resource private ArtResourceAccess access;

    /** 按固定修订创建发布记录，重复请求返回首次回执。 */
    @Transactional
    public Map<String, Object> publish(String sceneId, int revisionNo, String requestId) {
        var actor = access.actor();
        long scene = requireId(sceneId);
        if (revisionNo < 1 || requestId == null || requestId.isBlank() || requestId.length() > 110)
            throw bad("发布请求参数无效");
        var replay = jdbc.queryForList("SELECT * FROM wx_3d_scene_publication WHERE tenant_id=? AND request_id=?", actor.tenant(), requestId);
        if (!replay.isEmpty()) {
            var previous = replay.get(0);
            if (!Objects.equals(((Number) previous.get("scene_id")).longValue(), scene)
                    || !Objects.equals(((Number) previous.get("revision_no")).intValue(), revisionNo))
                throw new ServiceException(409, "发布请求号已绑定其他修订");
            return receipt(previous);
        }
        // 锁定场景主记录，串行化同一场景的首次发布和当前指针切换。
        var sceneRows = jdbc.queryForList("SELECT id FROM wx_3d_scene WHERE id=? AND tenant_id=? AND deleted=0 FOR UPDATE", scene, actor.tenant());
        if (sceneRows.size() != 1) throw new ServiceException(404, "场景不存在或无权发布");
        var revisions = jdbc.queryForList("SELECT schema_version,scene_json,three_d_json,two_d_json,dependencies_json,content_hash FROM wx_3d_scene_revision WHERE tenant_id=? AND scene_id=? AND revision_no=?",
                actor.tenant(), scene, revisionNo);
        if (revisions.size() != 1) throw new ServiceException(404, "场景修订不存在");
        var revision = revisions.get(0);
        String sourceHash = Objects.toString(revision.get("content_hash"), "");
        if (sourceHash.isBlank()) {
            sourceHash = SceneRevisionContentHasher.sha256(((Number) revision.get("schema_version")).intValue(),
                    Objects.toString(revision.get("scene_json"), ""), Objects.toString(revision.get("three_d_json"), ""),
                    Objects.toString(revision.get("two_d_json"), ""), Objects.toString(revision.get("dependencies_json"), ""));
        }
        // 同一租户同一场景只保留一个当前消费指针，历史发布行继续保留。
        jdbc.queryForList("SELECT id FROM wx_3d_scene_publication WHERE tenant_id=? AND scene_id=? AND current_flag=1 FOR UPDATE",
                actor.tenant(), scene);
        jdbc.update("UPDATE wx_3d_scene_publication SET current_flag=0 WHERE tenant_id=? AND scene_id=? AND current_flag=1",
                actor.tenant(), scene);
        String publicationId = "PUB-" + IdWorker.getId();
        jdbc.update("INSERT INTO wx_3d_scene_publication(id,publication_id,tenant_id,scene_id,revision_no,source_hash,index_version,dependency_snapshot,request_id,status,current_flag,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                IdWorker.getId(), publicationId, actor.tenant(), scene, revisionNo, sourceHash, 1,
                Objects.toString(revision.get("dependencies_json"), "{}"), requestId, "PUBLISHED", 1, actor.user());
        return Map.of("publicationId", publicationId, "sceneId", sceneId, "revisionNo", revisionNo, "status", "PUBLISHED");
    }

    /** 撤销当前或历史发布记录，不删除正文和依赖快照。 */
    @Transactional
    public void revoke(String publicationId, String reason) {
        var actor = access.actor();
        var row = publication(publicationId, actor);
        if (!"PUBLISHED".equals(row.get("status"))) throw new ServiceException(409, "发布记录已撤销");
        jdbc.update("UPDATE wx_3d_scene_publication SET status='REVOKED',current_flag=0,revoked_by=?,revoked_time=CURRENT_TIMESTAMP,revoke_reason=? WHERE publication_id=? AND tenant_id=?",
                actor.user(), reason == null ? "" : reason.trim(), publicationId, actor.tenant());
    }

    /** 回退创建新的发布编号，原发布记录保持不可变。 */
    @Transactional
    public Map<String, Object> rollback(String publicationId, String requestId) {
        var actor = access.actor();
        var row = publication(publicationId, actor);
        return publish(row.get("scene_id").toString(), ((Number) row.get("revision_no")).intValue(), requestId);
    }

    public Map<String, Object> current(String sceneId) {
        var actor = access.actor();
        var rows = jdbc.queryForList("SELECT * FROM wx_3d_scene_publication WHERE tenant_id=? AND scene_id=? AND current_flag=1 AND status='PUBLISHED'",
                actor.tenant(), requireId(sceneId));
        if (rows.size() != 1) throw new ServiceException(404, "当前发布不存在");
        return receipt(rows.get(0));
    }

    private Map<String, Object> publication(String publicationId, ArtResourceAccess.Actor actor) {
        if (publicationId == null || publicationId.isBlank() || publicationId.length() > 64) throw bad("发布编号无效");
        var rows = jdbc.queryForList("SELECT * FROM wx_3d_scene_publication WHERE publication_id=? AND tenant_id=?", publicationId, actor.tenant());
        if (rows.size() != 1) throw new ServiceException(404, "发布记录不存在");
        return rows.get(0);
    }

    private static Map<String, Object> receipt(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("publicationId", row.get("publication_id"));
        result.put("sceneId", row.get("scene_id").toString());
        result.put("revisionNo", row.get("revision_no"));
        result.put("status", row.get("status"));
        result.put("sourceHash", row.get("source_hash"));
        return result;
    }

    private static long requireId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id < 1 || !Long.toString(id).equals(value)) throw bad("场景编号无效");
            return id;
        } catch (NumberFormatException error) {
            throw bad("场景编号无效");
        }
    }

    private static ServiceException bad(String message) { return new ServiceException(400, message); }
}
