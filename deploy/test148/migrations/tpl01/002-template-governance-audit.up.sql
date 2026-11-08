-- 模板治理审计。只记录状态、删除等治理动作，不改变模板正文和实例修订。
CREATE TABLE IF NOT EXISTS wx_3d_scene_template_audit (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 template_id BIGINT NOT NULL,
 template_scene_id BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL,
 from_status VARCHAR(16) DEFAULT NULL,
 to_status VARCHAR(16) DEFAULT NULL,
 reason VARCHAR(255) NOT NULL DEFAULT '',
 operator_id BIGINT NOT NULL,
 request_id VARCHAR(128) DEFAULT NULL,
 created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_template_audit_template (tenant_id,template_id,created_time,id),
 KEY idx_template_audit_operator (tenant_id,operator_id,created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
