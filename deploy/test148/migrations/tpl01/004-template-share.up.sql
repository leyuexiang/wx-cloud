-- 跨租户模板共享授权。授权记录独立于模板目录和模板正文，撤销后保留历史行。
CREATE TABLE IF NOT EXISTS wx_3d_scene_template_share (
 id BIGINT NOT NULL PRIMARY KEY,
 owner_tenant_id BIGINT NOT NULL,
 target_tenant_id BIGINT NOT NULL,
 template_id BIGINT NOT NULL,
 template_scene_id BIGINT NOT NULL,
 version_no INT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 reason VARCHAR(255) NOT NULL DEFAULT '',
 operator_id BIGINT NOT NULL,
 created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 revoked_time TIMESTAMP DEFAULT NULL,
 revoked_by BIGINT DEFAULT NULL,
 KEY idx_template_share_lookup (target_tenant_id,status,template_id,version_no),
 KEY idx_template_share_owner (owner_tenant_id,template_id,version_no,created_time),
 KEY idx_template_share_history (template_id,version_no,target_tenant_id,created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
