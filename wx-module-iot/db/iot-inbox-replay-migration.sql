-- 物联网 Inbox 事件快照迁移。
-- 适用于已经执行旧版 iot.sql 的环境；新环境直接执行 iot.sql 即可。
-- 旧记录没有原始事件快照，因此只能继续查询和保留状态，不能直接重放。
SET @iot_inbox_payload_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'iot_message_inbox'
    AND column_name = 'payload_json'
);
SET @iot_inbox_payload_add_sql = IF(
  @iot_inbox_payload_column_exists = 0,
  'ALTER TABLE `iot_message_inbox` ADD COLUMN `payload_json` json DEFAULT NULL COMMENT ''统一事件快照，用于失败重放'' AFTER `point_code`',
  'SELECT 1'
);
PREPARE iot_inbox_payload_add_statement FROM @iot_inbox_payload_add_sql;
EXECUTE iot_inbox_payload_add_statement;
DEALLOCATE PREPARE iot_inbox_payload_add_statement;

SET @iot_inbox_retry_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'iot_message_inbox'
    AND column_name = 'retry_count'
);
SET @iot_inbox_retry_add_sql = IF(
  @iot_inbox_retry_column_exists = 0,
  'ALTER TABLE `iot_message_inbox` ADD COLUMN `retry_count` int NOT NULL DEFAULT 0 COMMENT ''失败重试次数'' AFTER `status`',
  'SELECT 1'
);
PREPARE iot_inbox_retry_add_statement FROM @iot_inbox_retry_add_sql;
EXECUTE iot_inbox_retry_add_statement;
DEALLOCATE PREPARE iot_inbox_retry_add_statement;
