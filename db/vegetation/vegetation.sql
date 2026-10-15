-- 植被生成微服务持久化表
-- 执行前请确认当前数据库字符集为 utf8mb4；表只保存规则快照、任务和发布引用，
-- 不保存Cesium对象、Python进程句柄或单株模型实例。

CREATE TABLE IF NOT EXISTS `veg_generation_task` (
  `id` bigint NOT NULL COMMENT '任务主键',
  `generator_task_id` varchar(128) DEFAULT NULL COMMENT 'Python生成器任务编号',
  `scene_id` varchar(64) NOT NULL COMMENT '场景编号',
  `snapshot_version` varchar(64) NOT NULL COMMENT '不可变规则快照版本',
  `rule_version` varchar(64) NOT NULL COMMENT '植被规则版本',
  `idempotency_key` varchar(128) NOT NULL COMMENT '幂等键',
  `status` varchar(32) NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/PARTIAL_FAILED/FAILED/CANCELED',
  `total_tile_count` int NOT NULL DEFAULT 0 COMMENT '总地块数量',
  `completed_tile_count` int NOT NULL DEFAULT 0 COMMENT '已完成地块数量',
  `failed_tile_count` int NOT NULL DEFAULT 0 COMMENT '失败地块数量',
  `candidate_count` bigint NOT NULL DEFAULT 0 COMMENT '候选点数量',
  `instance_count` bigint NOT NULL DEFAULT 0 COMMENT '实例数量',
  `generator_version` varchar(64) DEFAULT NULL COMMENT 'Python生成器版本',
  `publication_version` varchar(64) DEFAULT NULL COMMENT '正式发布版本',
  `manifest_url` varchar(512) DEFAULT NULL COMMENT '发布清单地址',
  `tileset_url` varchar(512) DEFAULT NULL COMMENT '三维瓦片清单地址',
  `layer_tileset_urls_json` text DEFAULT NULL COMMENT '按植被图层拆分的Cesium入口地址JSON',
  `layer_density_tileset_urls_json` text DEFAULT NULL COMMENT '按图层保存高清、均衡、性能独立入口JSON',
  `progress_json` text DEFAULT NULL COMMENT '生成阶段耗时与指标摘要JSON',
  `source_hash` varchar(128) DEFAULT NULL COMMENT '规则快照和生成器源哈希',
  `snapshot_json` json NOT NULL COMMENT '创建任务时冻结的规则快照',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '错误信息',
  `creator` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_veg_task_idempotency` (`idempotency_key`),
  UNIQUE KEY `uk_veg_task_generator_id` (`generator_task_id`),
  KEY `idx_veg_task_scene_status` (`scene_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植被生成任务';

CREATE TABLE IF NOT EXISTS `veg_generation_tile` (
  `id` bigint NOT NULL COMMENT '地块结果主键',
  `task_id` bigint NOT NULL COMMENT '生成任务编号',
  `publication_version` varchar(64) NOT NULL COMMENT '发布版本',
  `tile_id` varchar(128) NOT NULL COMMENT '稳定地块编号',
  `tile_row` int NOT NULL,
  `tile_column` int NOT NULL,
  `status` varchar(32) NOT NULL COMMENT 'PENDING/SUCCESS/FAILED',
  `candidate_count` int NOT NULL DEFAULT 0,
  `instance_count` int NOT NULL DEFAULT 0,
  `content_url` varchar(512) DEFAULT NULL COMMENT '地块实例或瓦片地址',
  `content_hash` varchar(128) DEFAULT NULL COMMENT '内容哈希',
  `error_message` varchar(2000) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_veg_tile_task_tile` (`task_id`, `tile_id`),
  KEY `idx_veg_tile_publication` (`publication_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植被生成地块结果';

CREATE TABLE IF NOT EXISTS `veg_publication` (
  `id` bigint NOT NULL COMMENT '发布版本主键',
  `scene_id` varchar(64) NOT NULL,
  `publication_version` varchar(64) NOT NULL,
  `task_id` bigint NOT NULL,
  `rule_version` varchar(64) NOT NULL,
  `generator_version` varchar(64) NOT NULL,
  `manifest_url` varchar(512) NOT NULL,
  `tileset_url` varchar(512) DEFAULT NULL,
  `source_hash` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL COMMENT 'DRAFT/STABLE/ROLLED_BACK',
  `instance_count` bigint NOT NULL DEFAULT 0,
  `tile_count` int NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_veg_publication_version` (`scene_id`, `publication_version`),
  KEY `idx_veg_publication_scene_status` (`scene_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植被发布版本';

-- 场景级发布锁行。Java发布和回滚事务会对同一scene_id执行FOR UPDATE，
-- 即使当前还没有发布记录，也能把并发切换串行化，避免出现两个STABLE版本。
CREATE TABLE IF NOT EXISTS `veg_publication_lock` (
  `scene_id` varchar(64) NOT NULL COMMENT '场景编号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`scene_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植被场景发布锁';
