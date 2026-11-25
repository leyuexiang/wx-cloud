-- 已部署 IoT 表的增量迁移。在 MySQL 8.0 先备份、核对列和索引，再按表执行当前缺少的步骤。
-- 生成列只为活动记录提供非空键，已删除的记录保留原始业务编号和审计信息。
-- 保留原索引列顺序以复用按租户、业务键及删除标记的现有查询。
-- 每条 ALTER 独立提交，遇到中断按表检查列定义和索引最后一列后继续。
-- 回退限制：同一业务键已积累多条软删除记录时，旧全量唯一索引已无法无损恢复。
-- 应用版本回退可保留新索引，旧版仍按 deleted 查询和执行软删除。

SET SESSION lock_wait_timeout = 10;

ALTER TABLE `wx_iot_connector`
  ADD COLUMN `active_unique_key` tinyint GENERATED ALWAYS AS (IF(`deleted` = b'0', 1, NULL)) VIRTUAL COMMENT '仅活动记录参与业务编号唯一校验',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `wx_iot_connector`
  DROP INDEX `uk_iot_connector_tenant_code`,
  ADD UNIQUE INDEX `uk_iot_connector_tenant_code` (`tenant_id`, `connector_id`, `deleted`, `active_unique_key`),
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `wx_iot_device_binding`
  ADD COLUMN `active_unique_key` tinyint GENERATED ALWAYS AS (IF(`deleted` = b'0', 1, NULL)) VIRTUAL COMMENT '仅活动记录参与业务编号唯一校验',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `wx_iot_device_binding`
  DROP INDEX `uk_iot_binding_external_device`,
  ADD UNIQUE INDEX `uk_iot_binding_external_device` (`tenant_id`, `connector_id`, `external_device_id`, `deleted`, `active_unique_key`),
  ALGORITHM=INPLACE, LOCK=NONE;
