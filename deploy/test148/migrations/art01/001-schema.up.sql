-- ART-01 扩展迁移：保留旧字段和默认值，不改变旧记录的权限。
-- 只在迁移执行器完成结构预检和备份后运行，要求 MySQL 8.0.16 以上。
ALTER TABLE `wx_dim3d_resource`
  ADD COLUMN `resource_scope` varchar(20) COLLATE utf8mb4_0900_as_cs DEFAULT NULL COMMENT '显式范围；NULL为未分类旧资源',
  ADD COLUMN `content_state` varchar(16) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'LEGACY' COMMENT '内容状态：LEGACY/PENDING/READY',
  ADD COLUMN `storage_prefix` varchar(512) DEFAULT NULL COMMENT '当前不可变对象前缀，不含桶名或签名',
  ADD COLUMN `manifest_path` varchar(512) DEFAULT NULL COMMENT '当前清单相对对象键',
  ADD COLUMN `manifest_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '当前清单字节摘要',
  ADD COLUMN `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '当前完整闭包摘要',
  ADD COLUMN `content_format` varchar(32) DEFAULT NULL COMMENT '资源格式，如unity.assetbundle.v1',
  ADD COLUMN `platform` varchar(20) DEFAULT NULL COMMENT 'Unity构建平台标识，首期WebGL',
  ADD COLUMN `app_compatibility` varchar(128) DEFAULT NULL COMMENT '精确运行包兼容标识',
  ADD COLUMN `current_revision` bigint unsigned NOT NULL DEFAULT 0 COMMENT '内部并发修订，不是业务版本',
  ADD COLUMN `current_operation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '最后提交的操作编号',
  ADD INDEX `idx_dim3d_resource_visible` (`resource_scope`,`tenant_id`,`del_flag`,`content_state`,`id`),
  ADD CONSTRAINT `ck_dim3d_resource_scope` CHECK (`resource_scope` IS NULL OR `resource_scope` IN ('GLOBAL_SYSTEM','TENANT_SHARED')),
  ADD CONSTRAINT `ck_dim3d_resource_owner` CHECK (
    `resource_scope` IS NULL OR (`tenant_id` IS NOT NULL AND (
      (`resource_scope`='GLOBAL_SYSTEM' AND BINARY `tenant_id`=BINARY '0') OR
      (`resource_scope`='TENANT_SHARED' AND REGEXP_LIKE(`tenant_id`,'^[1-9][0-9]{0,18}$','c') AND CAST(`tenant_id` AS UNSIGNED)<=9223372036854775807)
    ))),
  ADD CONSTRAINT `ck_dim3d_resource_state` CHECK (`content_state` IN ('LEGACY','PENDING','READY')),
  ADD CONSTRAINT `ck_dim3d_resource_hashes` CHECK (
    (`manifest_sha256` IS NULL OR REGEXP_LIKE(`manifest_sha256`,'^[0-9a-f]{64}$','c')) AND
    (`content_hash` IS NULL OR REGEXP_LIKE(`content_hash`,'^[0-9a-f]{64}$','c'))),
  ADD CONSTRAINT `ck_dim3d_resource_ready` CHECK (`content_state`<>'READY' OR (
    `resource_scope` IS NOT NULL AND `current_revision`>0 AND `current_operation_id` IS NOT NULL AND
    `storage_prefix` IS NOT NULL AND CHAR_LENGTH(`storage_prefix`)>0 AND
    `manifest_path` IS NOT NULL AND CHAR_LENGTH(`manifest_path`)>0 AND
    `manifest_sha256` IS NOT NULL AND `content_hash` IS NOT NULL AND
    `content_format` IS NOT NULL AND CHAR_LENGTH(`content_format`)>0 AND
    `platform` IS NOT NULL AND BINARY `platform`=BINARY 'WebGL' AND
    `app_compatibility` IS NOT NULL AND CHAR_LENGTH(`app_compatibility`)>0
  ));

-- 三类目录旧文件编号含义不同，显式关联避免按相同数值误认资源。
ALTER TABLE `wx_dim3d_model`
  ADD COLUMN `resource_id` bigint DEFAULT NULL COMMENT '明确关联稳定资源主键；旧文件字段保留',
  ADD INDEX `idx_dim3d_model_resource` (`resource_id`);
ALTER TABLE `wx_dim3d_icon`
  ADD COLUMN `resource_id` bigint DEFAULT NULL COMMENT '明确关联稳定资源主键；不等于旧ossId',
  ADD INDEX `idx_dim3d_icon_resource` (`resource_id`);
ALTER TABLE `wx_dim3d_template`
  ADD COLUMN `resource_id` bigint DEFAULT NULL COMMENT '可复用资源模板关联；不替代业务场景快照编号',
  ADD INDEX `idx_dim3d_template_resource` (`resource_id`);

-- 操作表统一承担幂等、校验进度和前后快照，不增加面向用户的版本选择。
CREATE TABLE `wx_dim3d_resource_operation` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端幂等操作UUID，服务端校验并绑定身份',
  `resource_id` bigint NOT NULL COMMENT '稳定资源主键；创建任务先分配主记录',
  `operation_type` varchar(16) NOT NULL COMMENT 'CREATE/REPLACE/DELETE/RESTORE',
  `resource_scope` varchar(20) NOT NULL COMMENT '操作目标的显式资源范围',
  `tenant_id` varchar(20) NOT NULL COMMENT 'GLOBAL_SYSTEM为0；共享资源为认证租户的规范十进制值',
  `operator_id` bigint NOT NULL COMMENT '认证操作者',
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化业务载荷摘要，不含临时授权',
  `expected_revision` bigint unsigned NOT NULL COMMENT '提交前期望修订',
  `committed_revision` bigint unsigned DEFAULT NULL COMMENT '事务提交后的修订',
  `status` varchar(16) NOT NULL DEFAULT 'UPLOADING' COMMENT 'UPLOADING/VERIFYING/COMMITTED/FAILED/CONFLICT/CANCELLED/EXPIRED',
  `storage_prefix` varchar(512) DEFAULT NULL COMMENT '服务端分配的候选对象前缀',
  `candidate_descriptor` json DEFAULT NULL COMMENT '待校验清单身份与兼容信息；不含密钥或签名URL',
  `before_snapshot` json DEFAULT NULL COMMENT '切换前资源及必要目录关联；首次创建可为空',
  `after_snapshot` json DEFAULT NULL COMMENT '提交后资源及必要目录关联，用于回执和恢复',
  `error_code` varchar(64) DEFAULT NULL COMMENT '稳定错误标识，诊断信息不含凭据',
  `expires_at` datetime(3) NOT NULL COMMENT '任务截止时间，服务器UTC',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dim3d_operation_resource_id` (`resource_id`,`id`),
  KEY `idx_dim3d_operation_resource` (`resource_id`,`create_time`),
  KEY `idx_dim3d_operation_expiry` (`status`,`expires_at`),
  KEY `idx_dim3d_operation_owner` (`resource_scope`,`tenant_id`,`operator_id`,`create_time`),
  CONSTRAINT `fk_dim3d_operation_resource` FOREIGN KEY (`resource_id`) REFERENCES `wx_dim3d_resource` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_dim3d_operation_type` CHECK (`operation_type` IN ('CREATE','REPLACE','DELETE','RESTORE')),
  CONSTRAINT `ck_dim3d_operation_scope` CHECK (`resource_scope` IN ('GLOBAL_SYSTEM','TENANT_SHARED')),
  CONSTRAINT `ck_dim3d_operation_owner` CHECK (
    (`resource_scope`='GLOBAL_SYSTEM' AND BINARY `tenant_id`=BINARY '0') OR
    (`resource_scope`='TENANT_SHARED' AND REGEXP_LIKE(`tenant_id`,'^[1-9][0-9]{0,18}$','c') AND CAST(`tenant_id` AS UNSIGNED)<=9223372036854775807)),
  CONSTRAINT `ck_dim3d_operation_hash` CHECK (REGEXP_LIKE(`request_hash`,'^[0-9a-f]{64}$','c')),
  CONSTRAINT `ck_dim3d_operation_status` CHECK (`status` IN ('UPLOADING','VERIFYING','COMMITTED','FAILED','CONFLICT','CANCELLED','EXPIRED')),
  CONSTRAINT `ck_dim3d_operation_commit` CHECK (`status`<>'COMMITTED' OR (
    `after_snapshot` IS NOT NULL AND `committed_revision` IS NOT NULL AND
    `committed_revision`=`expected_revision`+1 AND `error_code` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='资源替换操作与恢复快照，不是业务版本表';

-- 复合外键保证当前指针引用的操作确实属于同一资源，而不仅是存在一个同名操作。
ALTER TABLE `wx_dim3d_resource`
  ADD CONSTRAINT `fk_dim3d_resource_current_operation` FOREIGN KEY (`id`,`current_operation_id`)
  REFERENCES `wx_dim3d_resource_operation` (`resource_id`,`id`) ON DELETE RESTRICT;
