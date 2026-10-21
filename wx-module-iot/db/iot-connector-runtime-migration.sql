-- 旧库增量迁移：停写并备份后执行一次；先完成已有表前缀迁移。
-- 新库使用 iot.sql，不重复执行本文件。本脚本不在应用启动时自动执行。
ALTER TABLE `wx_iot_connector`
  ADD COLUMN `runtime_owner` varchar(36) DEFAULT NULL COMMENT '运行所有者令牌' AFTER `version`,
  ADD COLUMN `runtime_lease_until` datetime(3) DEFAULT NULL COMMENT '数据库运行所有权截止时间' AFTER `runtime_owner`,
  ADD KEY `idx_iot_connector_discovery` (`tenant_id`, `connector_type`, `enabled`, `deleted`, `id`);
