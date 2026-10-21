-- 仅用于已部署旧版 IoT 表的数据库；新环境直接执行 iot.sql。
-- 执行前先完成 iot-inbox-replay-migration.sql、iot-inbox-claim-token-migration.sql，
-- 并暂停 IoT 接入写入、备份数据库。目标表已存在时会报错，避免覆盖数据。
RENAME TABLE
  `iot_connector` TO `wx_iot_connector`,
  `iot_device_binding` TO `wx_iot_device_binding`,
  `iot_message_inbox` TO `wx_iot_message_inbox`;

-- 如需回退，停用新版本后执行以下反向改名；不要与新版本同时运行。
-- RENAME TABLE
--   `wx_iot_connector` TO `iot_connector`,
--   `wx_iot_device_binding` TO `iot_device_binding`,
--   `wx_iot_message_inbox` TO `iot_message_inbox`;
