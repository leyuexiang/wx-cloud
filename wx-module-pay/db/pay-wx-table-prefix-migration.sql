-- wx 支付域表名改造迁移（MySQL）
-- 范围：当前生产实体 @TableName 对应的 14 张项目自有表。
-- 前置：在实际支付业务库核对全部源表存在、目标 wx_ 表不存在；停止支付、回调、钱包和统计写入并完成备份。
-- 仓库暂无完整的支付生产建表脚本，执行前必须根据真实数据库逐表核对。
-- 原位改名保留已有数据、索引和约束；Redis 键、字典类型、消息名称不随物理表改名。

RENAME TABLE
    `pay_app` TO `wx_pay_app`,
    `pay_channel` TO `wx_pay_channel`,
    `pay_demo_order` TO `wx_pay_demo_order`,
    `pay_demo_transfer` TO `wx_pay_demo_transfer`,
    `pay_notify_log` TO `wx_pay_notify_log`,
    `pay_notify_task` TO `wx_pay_notify_task`,
    `pay_order` TO `wx_pay_order`,
    `pay_order_extension` TO `wx_pay_order_extension`,
    `pay_refund` TO `wx_pay_refund`,
    `pay_transfer` TO `wx_pay_transfer`,
    `pay_wallet` TO `wx_pay_wallet`,
    `pay_wallet_recharge` TO `wx_pay_wallet_recharge`,
    `pay_wallet_recharge_package` TO `wx_pay_wallet_recharge_package`,
    `pay_wallet_transaction` TO `wx_pay_wallet_transaction`;

-- 回退仅在新版本尚未写入时使用：
-- RENAME TABLE
--     `wx_pay_wallet_transaction` TO `pay_wallet_transaction`,
--     `wx_pay_wallet_recharge_package` TO `pay_wallet_recharge_package`,
--     `wx_pay_wallet_recharge` TO `pay_wallet_recharge`,
--     `wx_pay_wallet` TO `pay_wallet`,
--     `wx_pay_transfer` TO `pay_transfer`,
--     `wx_pay_refund` TO `pay_refund`,
--     `wx_pay_order_extension` TO `pay_order_extension`,
--     `wx_pay_order` TO `pay_order`,
--     `wx_pay_notify_task` TO `pay_notify_task`,
--     `wx_pay_notify_log` TO `pay_notify_log`,
--     `wx_pay_demo_transfer` TO `pay_demo_transfer`,
--     `wx_pay_demo_order` TO `pay_demo_order`,
--     `wx_pay_channel` TO `pay_channel`,
--     `wx_pay_app` TO `pay_app`;
