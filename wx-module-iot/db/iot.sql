-- 物联网连接器与外部设备绑定表
-- 凭据只存 credential_ref，真实密钥由部署环境或密钥服务管理。

CREATE TABLE IF NOT EXISTS `wx_iot_connector` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `connector_id` varchar(64) NOT NULL COMMENT '连接器业务编号',
  `name` varchar(128) NOT NULL COMMENT '连接器名称',
  `connector_type` varchar(16) NOT NULL COMMENT '协议类型：MQTT、HTTPS、MOCK',
  `status` varchar(32) NOT NULL DEFAULT 'DISABLED' COMMENT '运行状态',
  `endpoint` varchar(512) DEFAULT NULL COMMENT '外部服务地址',
  `credential_ref` varchar(128) DEFAULT NULL COMMENT '凭据引用',
  `config_json` json DEFAULT NULL COMMENT '协议扩展配置',
  `enabled` tinyint NOT NULL DEFAULT 0 COMMENT '是否启用',
  `version` int NOT NULL DEFAULT 1 COMMENT '配置版本',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator` varchar(64) DEFAULT NULL COMMENT '创建者',
  `updater` varchar(64) DEFAULT NULL COMMENT '更新者',
  `deleted` bit NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iot_connector_tenant_code` (`tenant_id`, `connector_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物联网外部连接器';

CREATE TABLE IF NOT EXISTS `wx_iot_device_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `connector_id` varchar(64) NOT NULL COMMENT '连接器业务编号',
  `external_device_id` varchar(128) NOT NULL COMMENT '外部设备编号',
  `device_id` varchar(128) DEFAULT NULL COMMENT '内部设备编号',
  `scene_id` varchar(128) DEFAULT NULL COMMENT '数字孪生场景编号',
  `object_id` varchar(128) DEFAULT NULL COMMENT '数字孪生对象编号',
  `mapping_revision` varchar(64) NOT NULL COMMENT '点位映射版本',
  `point_mapping_json` json DEFAULT NULL COMMENT '点位、类型、单位映射',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator` varchar(64) DEFAULT NULL COMMENT '创建者',
  `updater` varchar(64) DEFAULT NULL COMMENT '更新者',
  `deleted` bit NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iot_binding_external_device` (`tenant_id`, `connector_id`, `external_device_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物联网外部设备绑定';

CREATE TABLE IF NOT EXISTS `wx_iot_message_inbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `event_id` varchar(64) NOT NULL COMMENT '统一事件编号',
  `connector_id` varchar(64) NOT NULL COMMENT '连接器业务编号',
  `external_device_id` varchar(128) NOT NULL COMMENT '外部设备编号',
  `point_code` varchar(128) NOT NULL COMMENT '标准点位编号',
  `payload_json` json NOT NULL COMMENT '统一事件快照，用于失败重放',
  `status` varchar(32) NOT NULL DEFAULT 'CLAIMED' COMMENT 'CLAIMED、PUBLISHED、FAILED、DEAD_LETTER、ARCHIVED',
  `claim_token` varchar(36) NOT NULL COMMENT '本次事件抢占令牌',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '失败重试次数',
  `received_at` datetime NOT NULL COMMENT '平台接收时间',
  `error_message` varchar(500) DEFAULT NULL COMMENT '最近一次投递错误',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator` varchar(64) DEFAULT NULL COMMENT '创建者',
  `updater` varchar(64) DEFAULT NULL COMMENT '更新者',
  `deleted` bit NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iot_inbox_event` (`tenant_id`, `event_id`, `deleted`),
  KEY `idx_iot_inbox_received_at` (`tenant_id`, `received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物联网事件接入去重记录';
