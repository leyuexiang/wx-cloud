-- 模板版本和发布指针。正文版本不可变，发布只切换指针。
ALTER TABLE wx_3d_scene_template ADD COLUMN current_version_no INT NOT NULL DEFAULT 1;
ALTER TABLE wx_3d_scene_template ADD COLUMN published_version_no INT DEFAULT NULL;
ALTER TABLE wx_3d_scene_template ADD COLUMN previous_published_version_no INT DEFAULT NULL;

CREATE TABLE IF NOT EXISTS wx_3d_scene_template_version (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 template_id BIGINT NOT NULL,
 template_scene_id BIGINT NOT NULL,
 version_no INT NOT NULL,
 revision_no INT NOT NULL,
 content_hash CHAR(64) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'READY',
 create_by BIGINT NOT NULL,
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_scene_template_version (tenant_id,template_id,version_no),
 KEY idx_scene_template_version_scene (tenant_id,template_scene_id,version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

UPDATE wx_3d_scene_template SET published_version_no=1 WHERE published_version_no IS NULL;
