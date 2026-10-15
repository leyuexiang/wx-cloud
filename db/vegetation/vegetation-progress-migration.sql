-- 仅增加可空字段，旧服务可以继续读写。必须先迁移数据库，再部署新版Java服务。
-- 本脚本可重复运行，不自动删除观测历史或发布结果；回退服务时保留字段。
SET @vegetation_progress_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'veg_generation_task'
    AND column_name = 'progress_json'
);
SET @vegetation_progress_sql = IF(
  @vegetation_progress_exists = 0,
  'ALTER TABLE `veg_generation_task` ADD COLUMN `progress_json` text DEFAULT NULL COMMENT ''生成阶段耗时与指标摘要JSON''',
  'SELECT ''progress_json已存在'' AS migration_status'
);
PREPARE vegetation_progress_statement FROM @vegetation_progress_sql;
EXECUTE vegetation_progress_statement;
DEALLOCATE PREPARE vegetation_progress_statement;
