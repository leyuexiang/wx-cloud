-- 当前场景发布的跨租户授权。每次重新授权生成独立的授权编号。
CREATE TABLE IF NOT EXISTS wx_3d_scene_grant (
 id BIGINT NOT NULL PRIMARY KEY,
 scene_id BIGINT NOT NULL,
 tenant_id BIGINT NOT NULL,
 grant_tenant_id BIGINT NOT NULL,
 status TINYINT NOT NULL,
 granted_by BIGINT NOT NULL,
 granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 revoked_by BIGINT DEFAULT NULL,
 revoked_at TIMESTAMP DEFAULT NULL,
 UNIQUE KEY uk_scene_grant_target (scene_id,grant_tenant_id),
 KEY idx_scene_grant_target (grant_tenant_id,scene_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
