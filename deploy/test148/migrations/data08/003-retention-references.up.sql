-- DATA-08 保留引用层：持久书签和请求幂等保留记录。默认只由新服务写入，旧服务不依赖这两张表。
CREATE TABLE IF NOT EXISTS wx_3d_scene_bookmark (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    scene_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    bookmark_type VARCHAR(32) NOT NULL,
    bookmark_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    expires_time TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_by BIGINT NULL,
    revoked_time TIMESTAMP NULL,
    UNIQUE KEY uk_scene_bookmark_key (tenant_id,bookmark_type,bookmark_key),
    KEY idx_scene_bookmark_revision (tenant_id,scene_id,revision_no,status,expires_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wx_3d_scene_request (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    scene_id BIGINT NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    revision_no INT NOT NULL,
    expires_time TIMESTAMP NOT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_scene_request_retention (tenant_id,scene_id,request_id),
    KEY idx_scene_request_revision (tenant_id,scene_id,revision_no),
    KEY idx_scene_request_expiry (expires_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
