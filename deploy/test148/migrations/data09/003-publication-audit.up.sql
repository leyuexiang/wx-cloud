-- 发布与票据生命周期审计。审计行只追加，不随发布或票据状态更新而覆盖。
CREATE TABLE IF NOT EXISTS wx_3d_scene_publication_audit (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 publication_id VARCHAR(64) NOT NULL,
 ticket_id VARCHAR(64) DEFAULT NULL,
 action VARCHAR(32) NOT NULL,
 reason VARCHAR(255) NOT NULL DEFAULT '',
 operator_id BIGINT DEFAULT NULL,
 request_id VARCHAR(110) DEFAULT NULL,
 created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_scene_publication_audit_publication (tenant_id,publication_id,created_time),
 KEY idx_scene_publication_audit_ticket (tenant_id,ticket_id,created_time),
 KEY idx_scene_publication_audit_action (tenant_id,action,created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
