-- 先移除跨租户票据的访问能力，再去掉字段。保留摘要记录供审计。
UPDATE wx_3d_scene_publication_ticket SET status='REVOKED' WHERE scene_grant_id IS NOT NULL AND status='ACTIVE';
ALTER TABLE wx_3d_scene_publication_ticket DROP COLUMN scene_grant_id;
