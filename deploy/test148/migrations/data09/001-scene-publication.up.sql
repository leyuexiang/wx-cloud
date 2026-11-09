-- 场景不可变发布记录。发布正文引用固定修订，撤销只改变准入状态并保留历史行。
CREATE TABLE IF NOT EXISTS wx_3d_scene_publication (
 id BIGINT NOT NULL PRIMARY KEY,
 publication_id VARCHAR(64) NOT NULL,
 tenant_id BIGINT NOT NULL,
 scene_id BIGINT NOT NULL,
 revision_no INT NOT NULL,
 source_hash CHAR(64) NOT NULL,
 index_version INT NOT NULL,
 dependency_snapshot LONGTEXT NOT NULL,
 request_id VARCHAR(110) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
 current_flag TINYINT NOT NULL DEFAULT 1,
 created_by BIGINT NOT NULL,
 created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 revoked_by BIGINT DEFAULT NULL,
 revoked_time TIMESTAMP DEFAULT NULL,
 revoke_reason VARCHAR(255) NOT NULL DEFAULT '',
 UNIQUE KEY uk_scene_publication_id (publication_id),
 UNIQUE KEY uk_scene_publication_request (tenant_id,request_id),
 KEY idx_scene_publication_current (tenant_id,scene_id,current_flag,status),
 KEY idx_scene_publication_history (tenant_id,scene_id,revision_no,created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
