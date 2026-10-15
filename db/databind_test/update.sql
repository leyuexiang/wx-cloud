-- 此文件使用说明：
-- 这里写数据库表发生变更，避免删表丢数据，这里写表结构变化的sql语句，如果遇到需要数据清洗的，需要写好清洗脚本。
-- 此文件只可以添加，不可以修改和删除。
-- 规则 ： 用途、日期、作者


-- desc: 组件系统，设备表增加模型缩放字段以及动态修改其值；
-- date: 2025-7-8
-- author: akon
ALTER TABLE `databind_test`.`device_info` ADD COLUMN `model_scaling` varchar(255) NOT NULL DEFAULT '{1,1,1}' COMMENT '模型缩放' AFTER `last_modified_by`;

UPDATE `databind_test`.`device_info`
SET `model_scaling` = CONCAT('{',
                             FLOOR(RAND()*10+1), ',',
                             FLOOR(RAND()*10+1), ',',
                             FLOOR(RAND()*10+1), '}'),
    `update_time` = NOW()


