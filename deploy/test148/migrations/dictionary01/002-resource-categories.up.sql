-- M1-02 模型、场景及两类模板分类增量，分类方案见本目录 README。
-- 执行前核对目标库、既有标签/编码冲突并备份。已有同名项保留主键及编码，历史删除项保留。
-- 单库事务执行，遇错回滚。分类维护并发执行须由执行者协调，存在性检查不替代业务唯一约束。
BEGIN;

-- 四种类型一次集合补齐，保留既有类型名称和其他元数据。
INSERT INTO wx_system_dict_type (name, type, status, remark, creator, updater)
SELECT seed.name, seed.type, 0, 'M1-02：模型场景及模板分类配置', 'M1-02', 'M1-02'
FROM (
    SELECT '模型分类' AS name, 'dim3d_model_category' AS type
    UNION ALL SELECT '场景分类', 'dim3d_scene_category'
    UNION ALL SELECT '行业方案模板分类', 'dim3d_template_industry'
    UNION ALL SELECT '展厅模板分类', 'dim3d_template_exhibition'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM wx_system_dict_type existing WHERE existing.type = seed.type AND existing.deleted = 0
);

UPDATE wx_system_dict_type SET status = 0, updater = 'M1-02'
WHERE type IN ('dim3d_model_category', 'dim3d_scene_category', 'dim3d_template_industry', 'dim3d_template_exhibition')
  AND deleted = 0 AND status <> 0;

-- 分类名称和默认编码按本轮用户确认方案维护，每类独立去重，前端提交返回的实际编码。
INSERT INTO wx_system_dict_data (sort, label, value, dict_type, status, remark, creator, updater)
SELECT seed.sort, seed.label, seed.value, seed.dict_type, 0,
       'M1-02：模型场景及模板分类配置', 'M1-02', 'M1-02'
FROM (
    SELECT 'dim3d_model_category' AS dict_type, 10 AS sort, '设备' AS label, 'DEVICE_MODEL' AS value
    UNION ALL SELECT 'dim3d_model_category', 20, '建筑', 'BUILDING_MODEL'
    UNION ALL SELECT 'dim3d_model_category', 30, '药材', 'HERB_MODEL'
    UNION ALL SELECT 'dim3d_model_category', 40, '仓储', 'WAREHOUSE_MODEL'
    UNION ALL SELECT 'dim3d_model_category', 50, '车辆', 'VEHICLE_MODEL'
    UNION ALL SELECT 'dim3d_model_category', 60, '植被', 'VEGETATION_MODEL'
    UNION ALL SELECT 'dim3d_scene_category', 10, '智慧园区', 'PARK_SCENE'
    UNION ALL SELECT 'dim3d_scene_category', 20, '智慧工厂', 'FACTORY_SCENE'
    UNION ALL SELECT 'dim3d_scene_category', 30, '智慧农业', 'AGRICULTURE_SCENE'
    UNION ALL SELECT 'dim3d_scene_category', 40, '智慧仓储', 'WAREHOUSE_SCENE'
    UNION ALL SELECT 'dim3d_scene_category', 50, '智慧城市', 'CITY_SCENE'
    UNION ALL SELECT 'dim3d_scene_category', 60, '数字展厅', 'EXHIBITION_SCENE'
    UNION ALL SELECT 'dim3d_template_industry', 10, '智慧园区', 'PARK_INDUSTRY'
    UNION ALL SELECT 'dim3d_template_industry', 20, '智慧工厂', 'FACTORY_INDUSTRY'
    UNION ALL SELECT 'dim3d_template_industry', 30, '智慧农业', 'AGRICULTURE_INDUSTRY'
    UNION ALL SELECT 'dim3d_template_industry', 40, '智慧仓储', 'WAREHOUSE_INDUSTRY'
    UNION ALL SELECT 'dim3d_template_industry', 50, '智慧城市', 'CITY_INDUSTRY'
    UNION ALL SELECT 'dim3d_template_industry', 60, '智慧水务', 'WATER_INDUSTRY'
    UNION ALL SELECT 'dim3d_template_exhibition', 10, '企业展厅', 'CORPORATE_EXHIBITION'
    UNION ALL SELECT 'dim3d_template_exhibition', 20, '产品展厅', 'PRODUCT_EXHIBITION'
    UNION ALL SELECT 'dim3d_template_exhibition', 30, '文化展馆', 'CULTURE_EXHIBITION'
    UNION ALL SELECT 'dim3d_template_exhibition', 40, '科普教育', 'EDUCATION_EXHIBITION'
    UNION ALL SELECT 'dim3d_template_exhibition', 50, '规划展示', 'PLANNING_EXHIBITION'
    UNION ALL SELECT 'dim3d_template_exhibition', 60, '综合展厅', 'GENERAL_EXHIBITION'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM wx_system_dict_data existing
    WHERE existing.dict_type = seed.dict_type AND existing.deleted = 0
      AND (existing.label = seed.label OR existing.value = seed.value)
);

-- 仅启用和重排方案内的同名项。类型与标签共同限定，其他业务自定义分类不参与更新。
-- 相同标签在场景和行业方案中的顺序一致，编码仍各自保留，重复执行不刷新无变化行。
UPDATE wx_system_dict_data
SET sort = CASE label
        WHEN '设备' THEN 10 WHEN '建筑' THEN 20 WHEN '药材' THEN 30
        WHEN '仓储' THEN 40 WHEN '车辆' THEN 50 WHEN '植被' THEN 60
        WHEN '智慧园区' THEN 10 WHEN '智慧工厂' THEN 20 WHEN '智慧农业' THEN 30
        WHEN '智慧仓储' THEN 40 WHEN '智慧城市' THEN 50 WHEN '数字展厅' THEN 60 WHEN '智慧水务' THEN 60
        WHEN '企业展厅' THEN 10 WHEN '产品展厅' THEN 20 WHEN '文化展馆' THEN 30
        WHEN '科普教育' THEN 40 WHEN '规划展示' THEN 50 WHEN '综合展厅' THEN 60 END,
    status = 0, updater = 'M1-02'
WHERE deleted = 0 AND (dict_type, label) IN (
    ('dim3d_model_category', '设备'), ('dim3d_model_category', '建筑'), ('dim3d_model_category', '药材'),
    ('dim3d_model_category', '仓储'), ('dim3d_model_category', '车辆'), ('dim3d_model_category', '植被'),
    ('dim3d_scene_category', '智慧园区'), ('dim3d_scene_category', '智慧工厂'), ('dim3d_scene_category', '智慧农业'),
    ('dim3d_scene_category', '智慧仓储'), ('dim3d_scene_category', '智慧城市'), ('dim3d_scene_category', '数字展厅'),
    ('dim3d_template_industry', '智慧园区'), ('dim3d_template_industry', '智慧工厂'), ('dim3d_template_industry', '智慧农业'),
    ('dim3d_template_industry', '智慧仓储'), ('dim3d_template_industry', '智慧城市'), ('dim3d_template_industry', '智慧水务'),
    ('dim3d_template_exhibition', '企业展厅'), ('dim3d_template_exhibition', '产品展厅'), ('dim3d_template_exhibition', '文化展馆'),
    ('dim3d_template_exhibition', '科普教育'), ('dim3d_template_exhibition', '规划展示'), ('dim3d_template_exhibition', '综合展厅')
)
AND (status <> 0 OR sort <> CASE label
    WHEN '设备' THEN 10 WHEN '建筑' THEN 20 WHEN '药材' THEN 30
    WHEN '仓储' THEN 40 WHEN '车辆' THEN 50 WHEN '植被' THEN 60
    WHEN '智慧园区' THEN 10 WHEN '智慧工厂' THEN 20 WHEN '智慧农业' THEN 30
    WHEN '智慧仓储' THEN 40 WHEN '智慧城市' THEN 50 WHEN '数字展厅' THEN 60 WHEN '智慧水务' THEN 60
    WHEN '企业展厅' THEN 10 WHEN '产品展厅' THEN 20 WHEN '文化展馆' THEN 30
    WHEN '科普教育' THEN 40 WHEN '规划展示' THEN 50 WHEN '综合展厅' THEN 60 END);

COMMIT;
