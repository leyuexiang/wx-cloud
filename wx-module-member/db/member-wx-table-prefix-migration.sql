-- wx 会员域表名改造迁移（MySQL）
-- 依据：当前生产实体 @TableName 对应的 11 张项目自有表。
-- 前置：在实际业务库核对下列源表均存在、目标 wx_ 表均不存在；停止会员及统计写入并完成备份。
-- 说明：仓库暂无这 11 张表的生产建表脚本，不得未经实际库核对直接执行。
-- 原位改名保留已有行、索引和约束；测试专用 member_brokerage_record 不在此迁移范围。

RENAME TABLE
    `member_address` TO `wx_member_address`,
    `member_config` TO `wx_member_config`,
    `member_experience_record` TO `wx_member_experience_record`,
    `member_group` TO `wx_member_group`,
    `member_level` TO `wx_member_level`,
    `member_level_record` TO `wx_member_level_record`,
    `member_point_record` TO `wx_member_point_record`,
    `member_sign_in_config` TO `wx_member_sign_in_config`,
    `member_sign_in_record` TO `wx_member_sign_in_record`,
    `member_tag` TO `wx_member_tag`,
    `member_user` TO `wx_member_user`;

-- 回退仅在新版本尚未写入时使用：
-- RENAME TABLE
--     `wx_member_user` TO `member_user`,
--     `wx_member_tag` TO `member_tag`,
--     `wx_member_sign_in_record` TO `member_sign_in_record`,
--     `wx_member_sign_in_config` TO `member_sign_in_config`,
--     `wx_member_point_record` TO `member_point_record`,
--     `wx_member_level_record` TO `member_level_record`,
--     `wx_member_level` TO `member_level`,
--     `wx_member_group` TO `member_group`,
--     `wx_member_experience_record` TO `member_experience_record`,
--     `wx_member_config` TO `member_config`,
--     `wx_member_address` TO `member_address`;
