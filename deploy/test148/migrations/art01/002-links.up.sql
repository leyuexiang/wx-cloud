-- 单独的数据迁移：仅回填有真实资源、同租户且未删除的模型关联。
-- 旧图标为ossId，模板为业务快照，均不得仅按数字相同自动关联。
UPDATE `wx_dim3d_model` m
JOIN `wx_dim3d_resource` r ON r.id=m.model_file_id
  AND BINARY r.tenant_id=BINARY m.tenant_id
SET m.resource_id=r.id, m.update_time=m.update_time
WHERE m.resource_id IS NULL AND m.del_flag='0' AND r.del_flag='0'
  AND REGEXP_LIKE(m.tenant_id,'^[1-9][0-9]{0,18}$','c')
  AND CAST(m.tenant_id AS UNSIGNED)<=9223372036854775807;

-- resource_scope 不自动回填；旧默认000000和SYSTEM均不代表全局权限。
