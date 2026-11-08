-- DATA-08 草案回退：只允许在确认没有修订引用后删除共享正文表。
-- 正式回退应先关闭共享读取开关并恢复旧四包读取路径。
DROP TABLE wx_3d_scene_content;
