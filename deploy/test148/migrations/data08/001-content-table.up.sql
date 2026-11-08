-- DATA-08 草案：新增按租户隔离的场景修订共享正文表。
-- 仅供独立库演练，执行前必须完成 README 中的结构预检和备份。
CREATE TABLE wx_3d_scene_content (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 content_hash CHAR(64) NOT NULL,
 schema_version INT NOT NULL,
 scene_json LONGTEXT NOT NULL,
 three_d_json LONGTEXT NOT NULL,
 two_d_json LONGTEXT NOT NULL,
 dependencies_json LONGTEXT NOT NULL,
 content_bytes BIGINT NOT NULL,
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_referenced_time TIMESTAMP NULL,
 deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_scene_content_identity (tenant_id,content_hash,schema_version),
 KEY idx_scene_content_gc (tenant_id,deleted,last_referenced_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
