# M1-02 分类字典数据增量

依据：2026-11-04 用户图片分类截图。分类顺序为设备、建筑、药材、仓储；全部由前端汇总生成。截图数量只反映当时资源，不写入字典或图片记录。

| 展示名称 | 新增时的默认编码 | 顺序 |
| --- | --- | --- |
| 设备 | `DEVICE_ICON` | 10 |
| 建筑 | `BUILDING_ICON` | 20 |
| 药材 | `HERB_ICON` | 30 |
| 仓储 | `WAREHOUSE_ICON` | 40 |

仓库没有上述四类的正式历史编码映射。本次为缺失类别定义稳定默认编码；若正式库已有同名分类，则保留原编码和主键，只启用并调整排序。前端继续使用响应中的 `dictValue` 筛选和提交，不硬编码本表默认值。其他类别、其他字典和现有图片记录不删除、不改写。

## 执行前检查

目标按当次用户确认及真实连接核对确定。2026-11-10 图片分类及 2026-11-11 模型、场景、模板分类的确认目标均为连接 `MySQL_aha7` 下的 `wx_editor`、`wx_editor_data08`。历史目标 `wx_editor_test148` 已不存在，当前配置依据见 03。执行前保存相关字典的迁移前记录、字段结构依据和源文件摘要，备份按本机私有文件权限保管。执行脚本对两张字典表使用完整限定库名。

检查以下查询。发现重复有效类型、重复标签、空业务编码，或默认编码已被其他名称占用时先停止，核对原图片引用和字典含义，不直接重编码或合并历史记录。

```sql
SELECT type, COUNT(*) FROM wx_system_dict_type
WHERE type = 'dim3d_icon_category' AND deleted = 0 GROUP BY type HAVING COUNT(*) > 1;

SELECT label, COUNT(*) FROM wx_system_dict_data
WHERE dict_type = 'dim3d_icon_category' AND deleted = 0
GROUP BY label HAVING COUNT(*) > 1;

SELECT value, COUNT(*) FROM wx_system_dict_data
WHERE dict_type = 'dim3d_icon_category' AND deleted = 0
GROUP BY value HAVING COUNT(*) > 1;

SELECT id, label, value FROM wx_system_dict_data
WHERE dict_type = 'dim3d_icon_category' AND deleted = 0
  AND (TRIM(value) = ''
    OR (value = 'DEVICE_ICON' AND label <> '设备')
    OR (value = 'BUILDING_ICON' AND label <> '建筑')
    OR (value = 'HERB_ICON' AND label <> '药材')
    OR (value = 'WAREHOUSE_ICON' AND label <> '仓储'));
```

迁移须单实例串行执行，期间暂停此字典的维护写入；现有字典表没有对应业务唯一索引，不能以存在性检查替代并发约束。

使用现有私有数据库连接方式，以 `utf8mb4` 客户端编码执行 `001-icon-category.up.sql`，遇错即停止并回滚当前事务。脚本仅操作现有两张字典表，无结构变更；重复执行不会重复插入、复活软删除记录或刷新无变化行。执行后保存摘要、执行时间和下面的脱敏查询结果作为证据：

```sql
SELECT label, value, sort, status FROM wx_system_dict_data
WHERE dict_type = 'dim3d_icon_category' AND deleted = 0
ORDER BY sort, id;
```

四个名称应存在、启用且顺序正确；若库原先有其他分类，则保留。再调用真实字典接口，并在图片管理页验证全部与分类数量由图片分页总数计算，点击分类提交对应真实编码。

## 回退与验收边界

执行中失败回滚当前事务。执行后如需回退，先核对本批备份及后续写入，精确恢复本批修改的旧行和删除本批新增行；禁止按字典类型整批删除。已有图片引用本批新编码时，应先完成引用处置，不能直接移除分类。

数据行为测试运行 `python deploy/test148/test_icon_category_seed.py`，使用内存数据库执行原始数据增量，验证范围和重复执行；不替代真实 MySQL、令牌、租户或浏览器验收。部署和实际验收状态只记录在 [03 近期执行任务](../../../../docs/03_近期执行任务.md#m1-02-字典读取适配执行记录)。

## 模型、场景与模板分类补充

2026-11-11 用户确认在上述两个库补充以下方案。脚本为 [002 分类增量](002-resource-categories.up.sql)，四种字典各六项，每种排序按下表由10递增至60，全部启用。分类名称按现有图片业务及数字孪生资源用途拟定，经用户确认后配置，业务页面读取实际 `dictValue`。

| 字典类型 | 分类名称与新增默认编码，按顺序排列 |
| --- | --- |
| `dim3d_model_category` | 设备 `DEVICE_MODEL`、建筑 `BUILDING_MODEL`、药材 `HERB_MODEL`、仓储 `WAREHOUSE_MODEL`、车辆 `VEHICLE_MODEL`、植被 `VEGETATION_MODEL` |
| `dim3d_scene_category` | 智慧园区 `PARK_SCENE`、智慧工厂 `FACTORY_SCENE`、智慧农业 `AGRICULTURE_SCENE`、智慧仓储 `WAREHOUSE_SCENE`、智慧城市 `CITY_SCENE`、数字展厅 `EXHIBITION_SCENE` |
| `dim3d_template_industry` | 智慧园区 `PARK_INDUSTRY`、智慧工厂 `FACTORY_INDUSTRY`、智慧农业 `AGRICULTURE_INDUSTRY`、智慧仓储 `WAREHOUSE_INDUSTRY`、智慧城市 `CITY_INDUSTRY`、智慧水务 `WATER_INDUSTRY` |
| `dim3d_template_exhibition` | 企业展厅 `CORPORATE_EXHIBITION`、产品展厅 `PRODUCT_EXHIBITION`、文化展馆 `CULTURE_EXHIBITION`、科普教育 `EDUCATION_EXHIBITION`、规划展示 `PLANNING_EXHIBITION`、综合展厅 `GENERAL_EXHIBITION` |

前端采用 `D:/WorkSpace/GitWorkSpace/wx-vue-unity` 的公共字典适配器，统一请求 `/admin-api/system/dict/data/type/{dictType}` 或参考端 `/api/system/dict/data/type/{dictType}`，前缀按部署模式选择。行业方案及展厅模板分别读取其类型专属字典，分页、编辑、另存和资源面板均复用现有调用。

执行前将上面的重复类型、重复标签及编码冲突查询扩展到四种类型，比较既有资源分类及迁移候选。同名历史项保留主键、编码和备注，脚本只启用和重排方案内条目。已删除记录按历史保留，新的缺项按类型独立插入。已有额外分类继续使用原记录。

核验时逐库查询四种有效类型和分类的主键、名称、编码、排序及状态，并保存迁移后的记录。首次空有效基线应得到每库4个类型和24项。回退根据本批真实主键及完整业务字段恢复，先核对后续维护和实际引用，私有回退审核稿默认回滚。数据行为检查执行 `python deploy/test148/test_resource_category_seed.py`，覆盖分类值、重复执行、历史编码、删除记录、其他分类及事务失败。
