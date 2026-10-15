-- 植被发布一致性迁移。
--
-- 适用于已经执行旧版建表脚本的环境。新环境直接执行vegetation.sql即可。
-- layer_tileset_urls_json允许前端按图层绑定独立tileset.json；锁表用于场景级发布和回滚互斥。
-- 该迁移只增加可空字段和锁表，不修改或删除既有任务、瓦片和发布数据。

-- 使用信息架构查询保持迁移可重复执行：新建表脚本可能已经包含该字段，
-- 已执行过部分部署脚本的环境也不会因为重复ADD COLUMN中断发布。
SET @layer_tileset_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'veg_generation_task'
    AND column_name = 'layer_tileset_urls_json'
);
SET @layer_tileset_add_sql = IF(
  @layer_tileset_column_exists = 0,
  'ALTER TABLE `veg_generation_task` ADD COLUMN `layer_tileset_urls_json` text DEFAULT NULL COMMENT ''按植被图层拆分的Cesium入口地址JSON'' AFTER `tileset_url`',
  'SELECT 1'
);
PREPARE layer_tileset_add_statement FROM @layer_tileset_add_sql;
EXECUTE layer_tileset_add_statement;
DEALLOCATE PREPARE layer_tileset_add_statement;

CREATE TABLE IF NOT EXISTS `veg_publication_lock` (
  `scene_id` varchar(64) NOT NULL COMMENT '场景编号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`scene_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植被场景发布锁';
