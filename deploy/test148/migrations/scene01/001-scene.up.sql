-- 独立场景身份和不可变四包修订，不重写旧wx_engineering记录。
CREATE TABLE IF NOT EXISTS wx_dim3d_scene (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 name VARCHAR(100) NOT NULL,
 scene_desc VARCHAR(500) NOT NULL DEFAULT '',
 scene_category VARCHAR(100) NOT NULL DEFAULT '',
 scene_type VARCHAR(16) NOT NULL,
 cover_asset_id VARCHAR(32) NOT NULL DEFAULT '',
 current_revision INT NOT NULL DEFAULT 1,
 lock_user BIGINT NULL,
 lock_client VARCHAR(128) NULL,
 lock_token VARCHAR(64) NULL,
 lock_expire TIMESTAMP NULL,
 create_by BIGINT NOT NULL,
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted TINYINT NOT NULL DEFAULT 0,
 KEY idx_scene_tenant (tenant_id,deleted,update_time,id),
 CONSTRAINT chk_scene_type CHECK (scene_type IN ('local3d','gis'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wx_dim3d_scene_revision (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 scene_id BIGINT NOT NULL,
 revision_no INT NOT NULL,
 schema_version INT NOT NULL,
 scene_json LONGTEXT NOT NULL,
 three_d_json LONGTEXT NOT NULL,
 two_d_json LONGTEXT NOT NULL,
 dependencies_json LONGTEXT NOT NULL,
 request_id VARCHAR(128) NOT NULL,
 request_hash CHAR(64) NOT NULL,
 create_by BIGINT NOT NULL,
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_scene_revision (tenant_id,scene_id,revision_no),
 UNIQUE KEY uk_scene_request (tenant_id,scene_id,request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
