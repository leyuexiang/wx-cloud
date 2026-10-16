-- wx BPM 项目表名改造迁移（MySQL）
-- 目的：停机窗口内原位改名，保留流程业务数据、索引和约束。
-- 范围：项目自有 bpm_* 表；Flowable 自动管理的 ACT_*、FLW_* 表不在此脚本内。
-- 前置：停止 BPM 写入并完成备份；确认 wx_bpm_* 目标表不存在。

RENAME TABLE
    `bpm_category` TO `wx_bpm_category`,
    `bpm_form` TO `wx_bpm_form`,
    `bpm_oa_leave` TO `wx_bpm_oa_leave`,
    `bpm_process_definition_info` TO `wx_bpm_process_definition_info`,
    `bpm_process_expression` TO `wx_bpm_process_expression`,
    `bpm_process_instance_copy` TO `wx_bpm_process_instance_copy`,
    `bpm_process_listener` TO `wx_bpm_process_listener`,
    `bpm_user_group` TO `wx_bpm_user_group`;

-- 回滚（仅在目标表未被新版本写入且已确认回滚窗口时执行）：
-- RENAME TABLE
--     `wx_bpm_user_group` TO `bpm_user_group`,
--     `wx_bpm_process_listener` TO `bpm_process_listener`,
--     `wx_bpm_process_instance_copy` TO `bpm_process_instance_copy`,
--     `wx_bpm_process_expression` TO `bpm_process_expression`,
--     `wx_bpm_process_definition_info` TO `bpm_process_definition_info`,
--     `wx_bpm_oa_leave` TO `bpm_oa_leave`,
--     `wx_bpm_form` TO `bpm_form`,
--     `wx_bpm_category` TO `bpm_category`;
