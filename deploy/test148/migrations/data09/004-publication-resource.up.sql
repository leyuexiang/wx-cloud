-- 发布资源引用与发布记录同事务写入，撤销后保留，供固定版本读取和引用审计使用。
CREATE TABLE IF NOT EXISTS wx_3d_scene_publication_resource (
 id BIGINT NOT NULL,
 publication_id VARCHAR(64) NOT NULL,
 tenant_id BIGINT NOT NULL,
 resource_key VARCHAR(160) COLLATE utf8mb4_bin NOT NULL,
 resource_id BIGINT NOT NULL,
 resource_revision BIGINT NOT NULL,
 content_hash CHAR(64) NOT NULL,
 manifest_sha256 CHAR(64) NOT NULL,
 operation_id VARCHAR(36) NOT NULL,
 resource_scope VARCHAR(32) NOT NULL,
 resource_tenant_id VARCHAR(20) NOT NULL,
 snapshot_json LONGTEXT NOT NULL,
 created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (id),
 UNIQUE KEY uk_publication_resource_key (publication_id,resource_key),
 KEY idx_publication_resource_tenant (tenant_id,publication_id),
 KEY idx_publication_resource_revision (resource_id,resource_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
