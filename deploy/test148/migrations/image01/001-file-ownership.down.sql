-- 仅在确认本迁移新增列/索引没有消费者写入和引用时可执行；不删除任何文件行或对象。
DROP INDEX `idx_infra_file_scope_owner` ON `wx_infra_file`;
DROP INDEX `idx_infra_file_config_path` ON `wx_infra_file`;
ALTER TABLE `wx_infra_file`
  DROP COLUMN `visibility_scope`,
  DROP COLUMN `uploader_id`,
  DROP COLUMN `tenant_id`;
