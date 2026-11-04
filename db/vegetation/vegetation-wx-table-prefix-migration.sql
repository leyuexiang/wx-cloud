-- 仅用于已部署旧版植被表的数据库；新环境直接执行 vegetation.sql。
-- 执行前完成历史字段迁移，暂停植被任务与发布写入，并备份数据库。
-- 目标表已存在时会报错，避免覆盖数据。
RENAME TABLE
  `veg_generation_task` TO `wx_veg_generation_task`,
  `veg_generation_tile` TO `wx_veg_generation_tile`,
  `veg_publication` TO `wx_veg_publication`,
  `veg_publication_lock` TO `wx_veg_publication_lock`;

-- 回退时先停用新版本，再单独执行反向改名；不要与新版本同时运行。
-- RENAME TABLE
--   `wx_veg_generation_task` TO `veg_generation_task`,
--   `wx_veg_generation_tile` TO `veg_generation_tile`,
--   `wx_veg_publication` TO `veg_publication`,
--   `wx_veg_publication_lock` TO `veg_publication_lock`;
