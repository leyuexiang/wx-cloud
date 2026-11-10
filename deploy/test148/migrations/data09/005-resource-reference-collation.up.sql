-- 旧资源范围/租户列混用不同排序规则；引用字段采用二进制比较，避免关联失败及大小写混淆。
-- 操作编号与既有操作主键统一为 ASCII 二进制；这些字段仅存规范编号和固定枚举。
ALTER TABLE wx_3d_scene_publication_resource
 MODIFY operation_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 MODIFY resource_scope VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 MODIFY resource_tenant_id VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
