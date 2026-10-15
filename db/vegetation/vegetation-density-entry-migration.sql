-- 植被单发布包多档密度入口迁移。
--
-- 新建环境直接执行 vegetation.sql；已有环境执行本脚本。字段可空，历史发布继续使用
-- layer_tileset_urls_json 的单入口，新发布才写入高清、均衡、性能三条独立入口。
-- densityProfiles中的tileSizeM随snapshot_json冻结保存，无需新增数据库列；本迁移只补齐入口地址字段。

SET @layer_density_tileset_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'veg_generation_task'
    AND column_name = 'layer_density_tileset_urls_json'
);
SET @layer_density_tileset_add_sql = IF(
  @layer_density_tileset_column_exists = 0,
  'ALTER TABLE `veg_generation_task` ADD COLUMN `layer_density_tileset_urls_json` text DEFAULT NULL COMMENT ''按图层保存高清、均衡、性能独立入口JSON'' AFTER `layer_tileset_urls_json`',
  'SELECT 1'
);
PREPARE layer_density_tileset_add_statement FROM @layer_density_tileset_add_sql;
EXECUTE layer_density_tileset_add_statement;
DEALLOCATE PREPARE layer_density_tileset_add_statement;
