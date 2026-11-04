-- M1-02：按用户截图补充图片分类；适用于既有 wx_ 表结构，不重跑初始化。
-- 默认编码是本次新增分类的稳定编码，不是从截图数量或历史图片编号推断得到的编码。
-- 已有同名分类保留原业务编码和主键；执行前须按 README 检查重复标签及编码冲突。
-- 本脚本不迁移图片、不改其他字典，也不保存“全部”或任何固定图片数量。
BEGIN;

-- 仅补充缺失的有效字典类型，历史软删除记录仍然保留。
INSERT INTO wx_system_dict_type (name, type, status, remark, creator, updater)
SELECT '图片分类', 'dim3d_icon_category', 0, 'M1-02：图片分类，按用户确认的四类维护', 'M1-02', 'M1-02'
WHERE NOT EXISTS (
    SELECT 1 FROM wx_system_dict_type WHERE type = 'dim3d_icon_category' AND deleted = 0
);

-- 用户要求这四类可选，启用此类型；不改已有类型名称或其他元数据。
UPDATE wx_system_dict_type SET status = 0, updater = 'M1-02'
WHERE type = 'dim3d_icon_category' AND deleted = 0 AND status <> 0;

-- 一次集合插入缺项，不逐条查询；按标签或默认编码去重，避免重跑或既有自定义编码产生重复分类。
INSERT INTO wx_system_dict_data (sort, label, value, dict_type, status, remark, creator, updater)
SELECT seed.sort, seed.label, seed.value, 'dim3d_icon_category', 0,
       'M1-02：用户确认的图片分类', 'M1-02', 'M1-02'
FROM (
    SELECT 10 AS sort, '设备' AS label, 'DEVICE_ICON' AS value
    UNION ALL SELECT 20, '建筑', 'BUILDING_ICON'
    UNION ALL SELECT 30, '药材', 'HERB_ICON'
    UNION ALL SELECT 40, '仓储', 'WAREHOUSE_ICON'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM wx_system_dict_data existing
    WHERE existing.dict_type = 'dim3d_icon_category' AND existing.deleted = 0
      AND (existing.label = seed.label OR existing.value = seed.value)
);

-- 已有同名分类只调整展示顺序和启用状态，保留 value，确保现有图片的 category 引用继续有效。
-- 仅更新有差异的行，重复执行不刷新审计字段或制造无意义写入。
UPDATE wx_system_dict_data
SET sort = CASE label WHEN '设备' THEN 10 WHEN '建筑' THEN 20 WHEN '药材' THEN 30 WHEN '仓储' THEN 40 END,
    status = 0, updater = 'M1-02'
WHERE dict_type = 'dim3d_icon_category' AND deleted = 0
  AND label IN ('设备', '建筑', '药材', '仓储')
  AND (status <> 0 OR sort <> CASE label
       WHEN '设备' THEN 10 WHEN '建筑' THEN 20 WHEN '药材' THEN 30 WHEN '仓储' THEN 40 END);

COMMIT;
