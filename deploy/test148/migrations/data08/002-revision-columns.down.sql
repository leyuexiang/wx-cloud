-- DATA-08 草案回退：恢复只有旧四包列的修订结构。
ALTER TABLE wx_3d_scene_revision
    DROP INDEX idx_scene_revision_content,
    DROP COLUMN content_id,
    DROP COLUMN content_hash,
    DROP COLUMN content_bytes;
