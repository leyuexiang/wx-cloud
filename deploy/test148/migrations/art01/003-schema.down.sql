-- 仅用于尚未启用新资源写入的测试库回退。
-- 执行器必须先检查无新内容、无操作记录、目录关联未被后续修改，再撤销本次回填。
ALTER TABLE `wx_dim3d_resource` DROP FOREIGN KEY `fk_dim3d_resource_current_operation`;
DROP TABLE `wx_dim3d_resource_operation`;
ALTER TABLE `wx_dim3d_template` DROP INDEX `idx_dim3d_template_resource`, DROP COLUMN `resource_id`;
ALTER TABLE `wx_dim3d_icon` DROP INDEX `idx_dim3d_icon_resource`, DROP COLUMN `resource_id`;
ALTER TABLE `wx_dim3d_model` DROP INDEX `idx_dim3d_model_resource`, DROP COLUMN `resource_id`;
ALTER TABLE `wx_dim3d_resource`
  DROP CHECK `ck_dim3d_resource_ready`,
  DROP CHECK `ck_dim3d_resource_hashes`,
  DROP CHECK `ck_dim3d_resource_state`,
  DROP CHECK `ck_dim3d_resource_owner`,
  DROP CHECK `ck_dim3d_resource_scope`,
  DROP INDEX `idx_dim3d_resource_visible`,
  DROP COLUMN `current_operation_id`, DROP COLUMN `current_revision`,
  DROP COLUMN `app_compatibility`, DROP COLUMN `platform`, DROP COLUMN `content_format`,
  DROP COLUMN `content_hash`, DROP COLUMN `manifest_sha256`, DROP COLUMN `manifest_path`,
  DROP COLUMN `storage_prefix`, DROP COLUMN `content_state`, DROP COLUMN `resource_scope`;
