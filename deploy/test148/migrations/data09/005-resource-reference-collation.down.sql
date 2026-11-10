-- 仅供明确授权的结构演练；正常程序回退应保留已兼容的排序规则与引用记录。
ALTER TABLE wx_3d_scene_publication_resource
 MODIFY operation_id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
 MODIFY resource_scope VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
 MODIFY resource_tenant_id VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL;
