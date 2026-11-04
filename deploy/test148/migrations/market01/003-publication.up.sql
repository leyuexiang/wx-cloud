-- 发布目录请求固定一次，原 ZIP 和最终文件计划仍保存在各自字段。
ALTER TABLE wx_dim3d_resource_import
  ADD COLUMN publish_descriptor json DEFAULT NULL COMMENT '服务端校验后固定的目录输入',
  ADD CONSTRAINT ck_dim3d_import_publish_descriptor CHECK (publish_descriptor IS NULL OR JSON_TYPE(publish_descriptor)='OBJECT');

-- 受管目录由 resource_id 指向稳定资源，旧目录继续保留文件编号。
ALTER TABLE wx_dim3d_model
  MODIFY COLUMN model_file_id bigint DEFAULT NULL COMMENT '旧模型文件ID',
  ADD CONSTRAINT ck_dim3d_model_resource_link CHECK (model_file_id IS NOT NULL OR resource_id IS NOT NULL);
