-- 增量修正后续新任务的可执行时间，租约以数据库UTC时钟为准。
-- 仅改变默认值，不猜测或批量改写现有时间；已有队列若来自非UTC连接，须先审计。
ALTER TABLE wx_3d_resource_import
  MODIFY COLUMN available_at datetime(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3));
