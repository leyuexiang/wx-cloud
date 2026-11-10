-- 发布访问票据。只保存不可逆摘要，发布撤销时通过关联发布状态一并失效。
CREATE TABLE IF NOT EXISTS wx_3d_scene_publication_ticket (
 id BIGINT NOT NULL PRIMARY KEY,
 ticket_id VARCHAR(64) NOT NULL,
 publication_id VARCHAR(64) NOT NULL,
 tenant_id BIGINT NOT NULL,
 token_hash CHAR(64) NOT NULL,
 scope VARCHAR(32) NOT NULL DEFAULT 'READ',
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 expires_time TIMESTAMP NOT NULL,
 created_by BIGINT NOT NULL,
 created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 revoked_by BIGINT DEFAULT NULL,
 revoked_time TIMESTAMP DEFAULT NULL,
 UNIQUE KEY uk_scene_publication_ticket_id (ticket_id),
 UNIQUE KEY uk_scene_publication_ticket_hash (token_hash),
 KEY idx_scene_publication_ticket_lookup (publication_id,status,expires_time),
 KEY idx_scene_publication_ticket_tenant (tenant_id,created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
