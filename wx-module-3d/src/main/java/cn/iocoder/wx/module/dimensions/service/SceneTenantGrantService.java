package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.system.api.tenant.TenantApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 平台管理员维护本租户的场景授权，只扩展目标租户的当前发布读取范围。 */
@Service
public class SceneTenantGrantService {
    @Resource private JdbcTemplate jdbc;
    @Resource private ArtResourceAccess access;
    @Resource private TenantApi tenants;
    @Resource private PlatformTransactionManager transactions;

    /** 在锁外验证真实租户，随后以场景行锁串行化授权及撤销。 */
    public void grant(String sceneId, String tenantId) {
        var actor = administrator();
        long scene = identifier(sceneId), target = identifier(tenantId);
        if (target == actor.tenant()) throw new ServiceException(400, "无需授权给场景所属租户");
        if (!Boolean.TRUE.equals(tenants.validTenant(target).getCheckedData())) throw new ServiceException(400, "目标租户不可用");
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            requireScene(actor, scene, true);
            var existing = jdbc.queryForList("SELECT status FROM wx_3d_scene_grant WHERE scene_id=? AND tenant_id=? AND grant_tenant_id=?",
                    scene, actor.tenant(), target);
            if (existing.isEmpty()) {
                jdbc.update("INSERT INTO wx_3d_scene_grant(id,scene_id,tenant_id,grant_tenant_id,status,granted_by) VALUES(?,?,?,?,1,?)",
                        IdWorker.getId(), scene, actor.tenant(), target, actor.user());
            } else if (((Number) existing.get(0).get("status")).intValue() != 1) {
                // 重新授权更换生效批次，先前签发的凭证不能随授权恢复。
                jdbc.update("UPDATE wx_3d_scene_grant SET id=?,status=1,granted_at=CURRENT_TIMESTAMP,granted_by=?,revoked_by=NULL,revoked_at=NULL"
                        + " WHERE scene_id=? AND tenant_id=? AND grant_tenant_id=?", IdWorker.getId(), actor.user(), scene, actor.tenant(), target);
            }
        });
    }

    public void revoke(String sceneId, String tenantId) {
        var actor = administrator();
        long scene = identifier(sceneId), target = identifier(tenantId);
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            requireScene(actor, scene, true);
            jdbc.update("UPDATE wx_3d_scene_grant SET status=0,revoked_by=?,revoked_at=CURRENT_TIMESTAMP"
                    + " WHERE scene_id=? AND tenant_id=? AND grant_tenant_id=? AND status=1",
                    actor.user(), scene, actor.tenant(), target);
        });
    }

    public List<Map<String, Object>> list(String sceneId) {
        var actor = administrator();
        long scene = identifier(sceneId);
        requireScene(actor, scene, false);
        return jdbc.query("SELECT grant_tenant_id,status,granted_at FROM wx_3d_scene_grant WHERE scene_id=? AND tenant_id=? ORDER BY grant_tenant_id",
                (row, index) -> Map.of("grantTenantId", Long.toString(row.getLong("grant_tenant_id")), "status", row.getInt("status"),
                        "grantedTime", row.getTimestamp("granted_at").toInstant().toString()), scene, actor.tenant());
    }

    private ArtResourceAccess.Actor administrator() {
        var actor = access.actor();
        if (!access.isPlatformAdministrator(actor)) throw new ServiceException(403, "无权维护场景租户授权");
        return actor;
    }

    private void requireScene(ArtResourceAccess.Actor actor, long scene, boolean lock) {
        var rows = jdbc.queryForList("""
                SELECT s.id FROM wx_3d_scene s WHERE s.id=? AND s.tenant_id=? AND s.deleted=0
                AND NOT EXISTS (SELECT 1 FROM wx_3d_scene_template t WHERE t.tenant_id=s.tenant_id AND t.template_scene_id=s.id)
                """ + (lock ? " FOR UPDATE" : ""), scene, actor.tenant());
        if (rows.isEmpty()) throw new ServiceException(404, "场景不存在或无权维护");
    }

    private static long identifier(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0 && Objects.equals(Long.toString(id), value)) return id;
        } catch (NumberFormatException invalid) {
            throw new ServiceException(400, "场景或租户编号无效");
        }
        throw new ServiceException(400, "场景或租户编号无效");
    }
}
