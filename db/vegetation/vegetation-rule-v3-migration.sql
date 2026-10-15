-- 植被规则第三版结果字段迁移。
-- 本脚本仅用于已经存在veg_generation_task表的环境，每个数据库执行一次；
-- 新建环境直接执行vegetation.sql，不需要重复执行本脚本。
ALTER TABLE `veg_generation_task`
  ADD COLUMN `publication_version` varchar(64) DEFAULT NULL COMMENT '正式发布版本' AFTER `generator_version`,
  ADD COLUMN `tileset_url` varchar(512) DEFAULT NULL COMMENT '三维瓦片清单地址' AFTER `manifest_url`,
  ADD COLUMN `source_hash` varchar(128) DEFAULT NULL COMMENT '规则快照和生成器源哈希' AFTER `tileset_url`;
