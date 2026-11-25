-- 旧票据保持空授权编号。新跨租户票据绑定授权生效批次，撤回后不可重新使用。
ALTER TABLE wx_3d_scene_publication_ticket ADD COLUMN scene_grant_id BIGINT DEFAULT NULL;
