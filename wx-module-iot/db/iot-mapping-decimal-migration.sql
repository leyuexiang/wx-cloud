-- 旧JSON列会将长小数舍入为double；先停写、备份及核对字段类型，再执行一次。
-- 此迁移保留现有记录与JSON当前文本，但无法恢复过去已被舍入的原始数字。
-- 新表直接采用iot.sql；已为longtext且存在检查约束时不得重复执行。
ALTER TABLE `wx_iot_device_binding`
  MODIFY COLUMN `point_mapping_json` longtext DEFAULT NULL COMMENT '点位映射JSON原文，保留十进制精度',
  ADD CONSTRAINT `chk_iot_binding_mapping_json`
    CHECK (`point_mapping_json` IS NULL OR JSON_VALID(`point_mapping_json`));
