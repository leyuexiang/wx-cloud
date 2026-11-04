-- MARKET-01增量：旧资源保持NULL，不按前端历史平台字段推断来源。
ALTER TABLE wx_dim3d_resource
  ADD COLUMN upload_source varchar(20) COLLATE utf8mb4_0900_as_cs DEFAULT NULL COMMENT '可信上传来源；旧数据待审计',
  ADD CONSTRAINT ck_dim3d_resource_upload_source CHECK (upload_source IS NULL OR upload_source IN ('UNITY_EDITOR','WEB_UPLOAD'));

-- 身份、权限和截止时间通过关联操作读取，不在导入表复制另一份可变租户/操作者。
CREATE TABLE wx_dim3d_resource_import (
  operation_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  resource_id bigint NOT NULL,
  target_format varchar(4) COLLATE utf8mb4_0900_as_cs NOT NULL,
  source_size bigint unsigned NOT NULL,
  source_sha256 char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status varchar(24) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'RECEIVING',
  frozen_source_key varchar(512) DEFAULT NULL COMMENT '已核验原ZIP的不可变私有对象键',
  artifact_descriptor json DEFAULT NULL COMMENT '持久化产物身份，不含签名地址',
  attempt_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  lease_until datetime(3) DEFAULT NULL,
  available_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  cancel_requested boolean NOT NULL DEFAULT false,
  error_code varchar(64) DEFAULT NULL,
  row_version bigint unsigned NOT NULL DEFAULT 0,
  create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (operation_id),
  KEY idx_dim3d_import_schedule (status,available_at,operation_id),
  KEY idx_dim3d_import_lease (status,lease_until,operation_id),
  CONSTRAINT fk_dim3d_import_operation FOREIGN KEY (resource_id,operation_id)
    REFERENCES wx_dim3d_resource_operation(resource_id,id) ON DELETE RESTRICT,
  CONSTRAINT ck_dim3d_import_format CHECK (target_format IN ('gltf','glb')),
  CONSTRAINT ck_dim3d_import_size CHECK (source_size BETWEEN 1 AND 524288000),
  CONSTRAINT ck_dim3d_import_hash CHECK (REGEXP_LIKE(source_sha256,'^[0-9a-f]{64}$','c')),
  CONSTRAINT ck_dim3d_import_version CHECK (row_version<=9223372036854775807),
  CONSTRAINT ck_dim3d_import_cancel CHECK (cancel_requested IN (0,1)),
  CONSTRAINT ck_dim3d_import_descriptor CHECK (artifact_descriptor IS NULL OR JSON_TYPE(artifact_descriptor)='OBJECT'),
  CONSTRAINT ck_dim3d_import_status CHECK (status IN ('RECEIVING','QUEUED','VERIFYING_SOURCE','CONVERTING','VALIDATING','PUBLISHING','COMMITTED','FAILED','CANCELLED','EXPIRED','CONFLICT')),
  CONSTRAINT ck_dim3d_import_attempt CHECK (attempt_id IS NULL OR REGEXP_LIKE(attempt_id,'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')),
  CONSTRAINT ck_dim3d_import_lease CHECK (status NOT IN ('VERIFYING_SOURCE','CONVERTING','VALIDATING','PUBLISHING') OR (attempt_id IS NOT NULL AND lease_until IS NOT NULL)),
  CONSTRAINT ck_dim3d_import_source CHECK (status NOT IN ('CONVERTING','VALIDATING','PUBLISHING') OR (frozen_source_key IS NOT NULL AND CHAR_LENGTH(frozen_source_key)>0)),
  CONSTRAINT ck_dim3d_import_artifact CHECK (status<>'PUBLISHING' OR artifact_descriptor IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='原始模型导入过程，成功事实仍以资源操作COMMITTED为准';
