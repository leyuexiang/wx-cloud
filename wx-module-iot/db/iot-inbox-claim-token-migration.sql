-- 已部署 Inbox 表增加抢占令牌；旧记录会在重新抢占或人工重放时生成令牌。
-- 新环境直接执行 iot.sql，无需运行此脚本。
SET @iot_inbox_claim_token_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'iot_message_inbox'
    AND column_name = 'claim_token'
);
SET @iot_inbox_claim_token_add_sql = IF(
  @iot_inbox_claim_token_column_exists = 0,
  'ALTER TABLE `iot_message_inbox` ADD COLUMN `claim_token` varchar(36) DEFAULT NULL COMMENT ''本次事件抢占令牌'' AFTER `status`',
  'SELECT 1'
);
PREPARE iot_inbox_claim_token_add_statement FROM @iot_inbox_claim_token_add_sql;
EXECUTE iot_inbox_claim_token_add_statement;
DEALLOCATE PREPARE iot_inbox_claim_token_add_statement;
