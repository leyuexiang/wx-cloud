-- 仅在确认新服务未写入引用且完成备份后执行；正常程序回退不得删除保留审计表。
DROP TABLE IF EXISTS wx_3d_scene_request;
DROP TABLE IF EXISTS wx_3d_scene_bookmark;
