-- IMG-01 首批受控普通图片上传归属字段；必须先由现场核验脚本对照 information_schema 与备份。
-- 候选列均可空，使旧文件继续保持 LEGACY/未归属；执行前仍须核对目标 schema 无同名列，未知旧租户数据不会覆盖。
-- 本候选假设三个归属列均不存在且类型兼容；现场门禁必须检查全部列/索引后才可调整执行。
ALTER TABLE `wx_infra_file`
  ADD COLUMN `tenant_id` bigint NULL COMMENT '图片文件可信租户归属；NULL=历史待核验',
  ADD COLUMN `uploader_id` bigint NULL COMMENT '图片文件可信上传者用户编号；NULL=历史待核验',
  ADD COLUMN `visibility_scope` varchar(20) NULL COMMENT '图片文件可见范围：PRIVATE_UPLOAD/TENANT/GLOBAL/NULL=历史待核验';

CREATE INDEX `idx_infra_file_scope_owner`
  ON `wx_infra_file` (`visibility_scope`, `tenant_id`, `uploader_id`, `id`);

-- 历史下载入口需要按配置和对象键核验图片归属，使用索引避免逐次扫描文件表。
CREATE INDEX `idx_infra_file_config_path`
  ON `wx_infra_file` (`config_id`, `path`);
