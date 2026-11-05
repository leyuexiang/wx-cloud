# M1-02 图片分类数据增量

依据：2026-11-04 用户图片分类截图。分类顺序为设备、建筑、药材、仓储；全部由前端汇总生成。截图数量只反映当时资源，不写入字典或图片记录。

| 展示名称 | 新增时的默认编码 | 顺序 |
| --- | --- | --- |
| 设备 | `DEVICE_ICON` | 10 |
| 建筑 | `BUILDING_ICON` | 20 |
| 药材 | `HERB_ICON` | 30 |
| 仓储 | `WAREHOUSE_ICON` | 40 |

仓库没有上述四类的正式历史编码映射。本次为缺失类别定义稳定默认编码；若正式库已有同名分类，则保留原编码和主键，只启用并调整排序。前端继续使用响应中的 `dictValue` 筛选和提交，不硬编码本表默认值。其他类别、其他字典和现有图片记录不删除、不改写。

## 执行前检查

目标仅为现有 MySQL 8 测试库 `wx_editor_test148`。先备份两张字典表的结构及数据，备份按服务器私有文件权限保存；记录迁移文件摘要。不得调用 `schema-core.sql` 或身份初始化脚本覆盖业务库。

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
