-- 此文件使用说明：
-- 这里写数据库表发生变更，避免删表丢数据，这里写表结构变化的sql语句，如果遇到需要数据清洗的，需要写好清洗脚本。
-- 此文件只可以添加，不可以修改和删除。
-- 规则 ： 用途、日期、作者


-- desc: task_event增加字段：
-- date: 2025-5-21
-- author: xwd

-- 添加大车相关字段
ALTER TABLE wx_task_event ADD COLUMN main_car_start_pos json DEFAULT NULL COMMENT '大车起始坐标（世界坐标），格式：{"x":0.0,"y":0.0,"z":0.0}';
ALTER TABLE wx_task_event ADD COLUMN main_car_axis varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '大车移动轴向(字符串枚举)';
ALTER TABLE wx_task_event ADD COLUMN main_car_max_travel float DEFAULT NULL COMMENT '大车最大行程(米)';

-- 添加小车相关字段
ALTER TABLE wx_task_event ADD COLUMN sub_car_start_pos json DEFAULT NULL COMMENT '小车起始坐标（世界坐标），格式：{"x":0.0,"y":0.0,"z":0.0}';
ALTER TABLE wx_task_event ADD COLUMN sub_car_axis varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '小车移动轴向(字符串枚举)';
ALTER TABLE wx_task_event ADD COLUMN sub_car_max_travel float DEFAULT NULL COMMENT '小车最大行程(米)';

-- 添加吊钩相关字段
ALTER TABLE wx_task_event ADD COLUMN hook_start_pos json DEFAULT NULL COMMENT '吊钩起始坐标（世界坐标），格式：{"x":0.0,"y":0.0,"z":0.0}';
ALTER TABLE wx_task_event ADD COLUMN hook_axis varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '吊钩移动轴向(字符串枚举)';
ALTER TABLE wx_task_event ADD COLUMN hook_max_height float DEFAULT NULL COMMENT '吊钩最大升降高度(米)';


-- desc: Minio大文件上传
-- date: 2025-6-3
-- author: xwd
CREATE TABLE `wx_files_minio`(
    `id`               bigint(20)   NOT NULL AUTO_INCREMENT,
    `upload_id`        varchar(255)      DEFAULT NULL COMMENT '文件上传id',
    `md5`              varchar(255)      DEFAULT NULL COMMENT '文件计算md5',
    `url`              varchar(255)      DEFAULT NULL COMMENT '文件访问地址',
    `bucket`           varchar(64)       DEFAULT NULL COMMENT '存储桶',
    `object`           varchar(255)      DEFAULT NULL COMMENT 'minio中文件名',
    `origin_file_name` varchar(255)      DEFAULT NULL COMMENT '原始文件名',
    `size`             bigint(20)        DEFAULT NULL COMMENT '文件大小',
    `type`             varchar(64)       DEFAULT NULL COMMENT '文件类型',
    `chunk_size`       mediumtext COMMENT '分片大小',
    `chunk_count`      int(11)           DEFAULT NULL COMMENT '分片数量',
    `is_delete`        char(1)           DEFAULT '0' COMMENT '是否删除',
    `create_time`      timestamp(6) NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='Minio文件表';


-- desc: 图标管理和图标分类管理
-- date: 2025-6-4
-- author: xwd
CREATE TABLE `wx_icon_info`(
    `id`                int(11)                                                                                     NOT NULL AUTO_INCREMENT,
    `project_id`        varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci                                NOT NULL COMMENT '项目id',
    `icon_name`         varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci                                    DEFAULT NULL COMMENT '图标名称',
    `unique_id`         varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci                                     DEFAULT NULL COMMENT '图标唯一标识',
    `is_collection`     int(11)                                                                                          DEFAULT NULL COMMENT '是否收藏',
    `type_id`           int(11)                                                                                          DEFAULT NULL COMMENT '图标类型id',
    `type_name`         varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci                                    DEFAULT NULL COMMENT '图标类型名称',
    `res_from_platform` enum ('BIM','BABYLON_WEB','U3D_WEB','U3D') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '平台类型:''BIM'',''BABYLON_WEB'',''U3D_WEB'',''U3D''',
    `icon_path`         varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci                                    DEFAULT NULL COMMENT '图标地址',
    `create_user`       varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci                                    DEFAULT NULL COMMENT '创建者',
    `update_user`       varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci                                    DEFAULT NULL COMMENT '更新人',
    `create_time`       timestamp                                                                                   NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       timestamp                                                                                   NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by`        bigint(20)                                                                                       DEFAULT NULL COMMENT '创建者Id',
    `last_modified_by`  bigint(20)                                                                                       DEFAULT NULL COMMENT '更新者Id',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC COMMENT ='图标信息表';

CREATE TABLE `wx_icon_type`(
    `id`               int(11)   NOT NULL AUTO_INCREMENT,
    `type_name`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类型名称',
    `status`           int(1)                                                        DEFAULT NULL COMMENT '状态',
    `create_user`      varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建者',
    `update_user`      varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
    `org_id_path`      varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组织机构id',
    `app_id`           bigint(20)                                                    DEFAULT NULL COMMENT '应用id',
    `create_time`      timestamp NULL                                                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      timestamp NULL                                                DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by`       bigint(20)                                                    DEFAULT NULL COMMENT '创建者Id',
    `last_modified_by` bigint(20)                                                    DEFAULT NULL COMMENT '更新者Id',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;


-- desc: 虚拟相机增加字段
-- date: 2025-6-12
-- author: xwd
ALTER TABLE wx_virtual_camera ADD COLUMN `camera_external_id` int(8) NOT NULL COMMENT '外部虚拟相机ID' AFTER `scene_id`;


-- desc: 任务事件增加字段
-- date: 2025-6-13
-- author: xwd
ALTER TABLE wx_task_event ADD COLUMN `external_task_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '外部任务名称' AFTER `virtual_camera_id`;

-- desc: 资源类型增加字段
-- date: 2025-6-13
-- author: xwd
ALTER TABLE `wx_resource_type` ADD COLUMN `type_code` varchar(255) NULL COMMENT '类型编码' AFTER `id`;

-- desc: 图标类型增加字段
-- date: 2025-6-13
-- author: xwd
ALTER TABLE `wx_icon_type` ADD COLUMN `type_code` varchar(255) NULL COMMENT '类型编码' AFTER `id`;

-- desc: task_type字段增加枚举：RingHolding
-- date: 2025-6-13
-- author: xwd
ALTER TABLE `wx_task_event` MODIFY COLUMN `task_type` enum('Path','Custom','NodeActive','AniTimeline','AniSystem','VehicleMovement','NodeBind','AniAxisControl','StateChange','HeavyLoadHoisting','RingHolding') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务类型：\r\nPath       // 路径任务   \r\nCustom // 自定义任务\r\nAniTimeline  //Timeline\r\nAniSystem  //动画控制器\r\nNodeActive //节点显隐\r\nVehicleMovement //车辆移动\r\nNodeBind //节点换绑\r\nAniAxisControl //轴向控制器\r\nStateChange //状态变更\r\nHeavyLoadHoisting //重载吊车\r\nring_holding //抱环' AFTER `task_id`;

-- desc: 任务事件表增加三个字段
-- date: 2025-6-13
-- author: xwd
ALTER TABLE `wx_task_event` ADD COLUMN `coordinate_system` int(8) NULL COMMENT '坐标系' AFTER `hook_max_height`, ADD COLUMN `move_node` varchar(255) NULL COMMENT '移动节点' AFTER `coordinate_system`, ADD COLUMN `move_axis` varchar(255) NULL COMMENT '移动轴向' AFTER `move_node`;

-- desc: 后续这里要整体调整一下，改为项目配置文件地址信息
-- date: 2025-6-17
-- author: xwd
INSERT INTO `wx_signal_ling` (`id`, `prop`, `value`, `description`) VALUES (1, 'signal', 'http://127.0.0.1:8923/', '信令服务地址');
INSERT INTO `wx_signal_ling` (`id`, `prop`, `value`, `description`) VALUES (2, 'signalWS', 'ws://127.0.0.1:8923', '信令服务websocket地址');
INSERT INTO `wx_signal_ling` (`id`, `prop`, `value`, `description`) VALUES (3, 'webWS', 'ws://127.0.0.1:48092/dimensions/ws', '数字孪生服务websocket地址');
INSERT INTO `wx_signal_ling` (`id`, `prop`, `value`, `description`) VALUES (4, 'management', 'http://127.0.0.1:58000', '大管理端前端地址');
INSERT INTO `wx_signal_ling` (`id`, `prop`, `value`, `description`) VALUES (5, 'configuration', 'http://127.0.0.1/account', '组态地址');
INSERT INTO `wx_signal_ling` (`id`, `prop`, `value`, `description`) VALUES (6, 'minio', 'http://127.0.0.1:19001', 'MiniO地址');

-- desc: 场景表里面维护图标信息
-- date: 2025-6-23
-- author: xwd
ALTER TABLE `wx_engineering` ADD COLUMN `icon_infos` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '图标信息列表' AFTER `scene_config_info`;

-- desc: 任务表添加悬臂吊任务及相关字段
-- date: 2025-7-1
-- author: wtj
ALTER TABLE wx_task_event
    RENAME COLUMN virtual_camera_id TO virtual_camera,
    ADD COLUMN node_rotation VARCHAR(255) COMMENT '悬臂吊旋转节点',
    ADD COLUMN node_slide VARCHAR(255) COMMENT '悬臂吊滑块节点',
    ADD COLUMN node_fixed_hinge VARCHAR(255) COMMENT '悬臂吊固定吊点节点',
    ADD COLUMN node_move_hinge1 VARCHAR(255) COMMENT '悬臂吊移动吊点1',
    ADD COLUMN node_move_hinge2 VARCHAR(255) COMMENT '悬臂吊移动吊点2',
    ADD COLUMN slider_direction VARCHAR(50) COMMENT '滑块节点移动方向',
    ADD COLUMN move_hinge1_direction VARCHAR(50) COMMENT '移动吊点1移动方向',
    ADD COLUMN move_hinge2_direction VARCHAR(50) COMMENT '移动吊点2移动方向';


ALTER TABLE wx_task_event
    MODIFY COLUMN task_type ENUM(
    'Path',             -- 路径任务
    'Custom',           -- 自定义任务
    'AniTimeline',      -- Timeline
    'AniSystem',        -- 动画控制器
    'NodeActive',       -- 节点显隐
    'VehicleMovement',  -- 车辆移动
    'NodeBind',         -- 节点换绑
    'AniAxisControl',   -- 轴向控制器
    'StateChange',      -- 状态变更
    'HeavyLoadHoisting',-- 重载吊车
    'ring_holding',     -- 抱环
    'SmallCrane'        -- 悬臂吊
    ) COMMENT '任务类型: Path // 路径任务, Custom // 自定义任务, AniTimeline // Timeline, AniSystem // 动画控制器, NodeActive // 节点显隐, VehicleMovement // 车辆移动, NodeBind // 节点换绑, AniAxisControl // 轴向控制器, StateChange // 状态变更, HeavyLoadHoisting // 重载吊车, ring_holding // 抱环, SmallCrane // 悬臂吊';


-- desc: 任务表添加AGV升降节点相关字段
-- date: 2025-7-10
-- author: wtj
ALTER TABLE `3d`.`task_event` ADD COLUMN `lifting_node` varchar(255) NULL COMMENT 'AGV升降节点' AFTER `move_hinge2_direction`;