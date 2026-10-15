/*
 Navicat Premium Dump SQL

 Source Server         : 110-root
 Source Server Type    : MySQL
 Source Server Version : 80018 (8.0.18)
 Source Host           : 127.0.0.1:3306
 Source Schema         : 3d

 Target Server Type    : MySQL
 Target Server Version : 80018 (8.0.18)
 File Encoding         : 65001

 Date: 02/07/2025 14:35:16
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for alert_scheme
-- ----------------------------
DROP TABLE IF EXISTS `alert_scheme`;
CREATE TABLE `alert_scheme`  (
  `id` int(8) NOT NULL AUTO_INCREMENT COMMENT '自增id',
  `scheme_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预警名称',
  `alert_type` int(1) NULL DEFAULT NULL COMMENT '预警类型-0：模型范围光提示（U3D用）1：全局弹窗提示（WEB用）',
  `color` json NULL COMMENT '颜色-json类型',
  `location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '位置',
  `text_style` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文本样式',
  `display_mode` int(1) NULL DEFAULT NULL COMMENT '显示方式-0：直接显示;1:鼠标移入显示',
  `alert_text` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预警文本',
  `unique_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '唯一标识',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT NULL COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`, `unique_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 27 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for association_object
-- ----------------------------
DROP TABLE IF EXISTS `association_object`;
CREATE TABLE `association_object`  (
  `value_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '节点值',
  `label_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示名称',
  `children` json NULL COMMENT '子节点',
  `is_leaf` tinyint(1) NULL DEFAULT NULL COMMENT '是否是叶子节点',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT NULL COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for batch_conversion
-- ----------------------------
DROP TABLE IF EXISTS `batch_conversion`;
CREATE TABLE `batch_conversion`  (
  `id` int(8) NOT NULL AUTO_INCREMENT,
  `resource_directory_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源目录名',
  `resource_web_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源web页面显示名称',
  `resource_size` double(8, 2) NULL DEFAULT NULL COMMENT '资源大小_单位MB',
  `resource_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源类型',
  `upload_time` datetime NULL DEFAULT NULL COMMENT '上传时间',
  `upload_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上传文件名',
  `unique_upload_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件名+UUID—唯一名',
  `conversion_status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '转换状态',
  `user_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户名',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT NULL COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 56 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for component_heat_map
-- ----------------------------
DROP TABLE IF EXISTS `component_heat_map`;
CREATE TABLE `component_heat_map`  (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '自增id',
  `component_info_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组件信息ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '名字',
  `heat_pos` json NULL COMMENT '热力点',
  `intensity` float NULL DEFAULT NULL COMMENT '热力强度',
  `pos_type` int(11) NULL DEFAULT NULL COMMENT '坐标类型',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 25 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '组件热力图表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for component_info
-- ----------------------------
DROP TABLE IF EXISTS `component_info`;
CREATE TABLE `component_info`  (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '自增id',
  `project_id` int(11) NULL DEFAULT NULL COMMENT '项目ID',
  `component_unique_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组件唯一标识',
  `component_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组件名称',
  `component_show_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组件展示名称',
  `component_type` int(11) NULL DEFAULT NULL COMMENT '组件类型',
  `component_prefab_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组件预制名',
  `component_asset_bundel_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组件预制相对路径',
  `component_asset_bundel_root_dir` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '根目录',
  `component_dll_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组件程序集名称',
  `component_dll_root_dir` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组件程序集根路径',
  `component_rule` json NULL COMMENT '组件规则JSON信息',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT NULL COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 30 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '组件信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for device_info_cbds
-- ----------------------------
DROP TABLE IF EXISTS `device_info_cbds`;
CREATE TABLE `device_info_cbds`  (
  `id` int(8) NOT NULL COMMENT '自增id',
  `device_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备id',
  `device_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备名',
  `area_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '园区id',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '园区名',
  `building_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '楼栋id',
  `building_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '楼栋名',
  `floor_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '楼层id',
  `floor_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '楼层名',
  `space_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '空间id',
  `space_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '空间名',
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型',
  `type_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型名称',
  `lon` double(8, 2) NULL DEFAULT NULL COMMENT '经度',
  `lat` double(8, 2) NULL DEFAULT NULL COMMENT '纬度',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NOT NULL COMMENT '应用id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `query1`(`device_id` ASC, `device_name` ASC) USING BTREE,
  INDEX `query2`(`area_id` ASC, `area_name` ASC) USING BTREE,
  INDEX `query3`(`building_id` ASC, `building_name` ASC) USING BTREE,
  INDEX `query4`(`floor_id` ASC, `floor_name` ASC) USING BTREE,
  INDEX `query5`(`type` ASC, `type_name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for device_info_ppfmter
-- ----------------------------
DROP TABLE IF EXISTS `device_info_ppfmter`;
CREATE TABLE `device_info_ppfmter`  (
  `id` int(8) NOT NULL COMMENT '自增id',
  `device_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备id',
  `device_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备名',
  `area_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '园区id',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '园区名',
  `building_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '楼栋id',
  `building_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '楼栋名',
  `floor_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '楼层id',
  `floor_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '楼层名',
  `space_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '空间id',
  `space_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '空间名',
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型',
  `type_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型名称',
  `lon` double(8, 2) NULL DEFAULT NULL COMMENT '经度',
  `lat` double(8, 2) NULL DEFAULT NULL COMMENT '纬度',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NOT NULL COMMENT '应用id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `query1`(`device_id` ASC, `device_name` ASC) USING BTREE,
  INDEX `query2`(`area_id` ASC, `area_name` ASC) USING BTREE,
  INDEX `query3`(`building_id` ASC, `building_name` ASC) USING BTREE,
  INDEX `query4`(`floor_id` ASC, `floor_name` ASC) USING BTREE,
  INDEX `query5`(`type` ASC, `type_name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for device_status_cbds
-- ----------------------------
DROP TABLE IF EXISTS `device_status_cbds`;
CREATE TABLE `device_status_cbds`  (
  `id` int(8) NOT NULL AUTO_INCREMENT COMMENT '自增id',
  `device_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备id',
  `device_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备名',
  `temperature` int(4) NULL DEFAULT NULL COMMENT '温度',
  `humidity` double(8, 2) NULL DEFAULT NULL COMMENT '湿度',
  `co` double(8, 2) NULL DEFAULT NULL COMMENT 'co含量',
  `co2` double(8, 3) NULL DEFAULT NULL COMMENT 'co2含量',
  `state` int(4) NULL DEFAULT NULL COMMENT '状态',
  `state_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '状态名',
  `warning` tinyint(1) NULL DEFAULT NULL COMMENT '告警',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NOT NULL COMMENT '应用id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 36 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for device_status_ppfmter
-- ----------------------------
DROP TABLE IF EXISTS `device_status_ppfmter`;
CREATE TABLE `device_status_ppfmter`  (
  `id` int(8) NOT NULL AUTO_INCREMENT COMMENT '自增id',
  `device_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备id',
  `device_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备名',
  `participants` int(4) NULL DEFAULT NULL COMMENT '在线数',
  `departures` int(4) NULL DEFAULT NULL COMMENT '离线数',
  `head_count` int(4) NULL DEFAULT NULL COMMENT '数量',
  `state` int(4) NULL DEFAULT NULL COMMENT '状态',
  `state_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '状态名',
  `warning` tinyint(1) NULL DEFAULT NULL COMMENT '告警',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NOT NULL COMMENT '应用id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 36 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for deviceall
-- ----------------------------
DROP TABLE IF EXISTS `deviceall`;
CREATE TABLE `deviceall`  (
  `id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '唯一标识',
  `serverCode` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '服务器编码',
  `areaName` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '园区',
  `buildingName` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '楼栋',
  `floorName` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '楼层',
  `zonesubname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '机房名称',
  `zonesubno` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '机房编号',
  `deviceTypeName` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备类型名称',
  `deviceTypeCode` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备类型编码',
  `deviceId` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备id',
  `deviceName` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备名称',
  `position` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '空间坐标',
  `roll` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '旋转',
  `categoryName` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备类型名称',
  `categoryNo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备类型编码',
  `zoneTelephone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '机房电话号码',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for engineering
-- ----------------------------
DROP TABLE IF EXISTS `engineering`;
CREATE TABLE `engineering`  (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '自增id',
  `gis_info` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'gis信息',
  `project` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '对应的项目编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '名称',
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'u3d' COMMENT '类型；u3d，2d,2.5d',
  `scene_res_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'U3D唯一ID',
  `nodes` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '节点',
  `platform_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '3d-unity' COMMENT '平台类型;3d-web,3d-unity,3d-ue',
  `com_points` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '组件信息集合',
  `coms_instance` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '场景信息',
  `hotfix_components` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '组件-身上的脚本集合',
  `dic_com_system_info` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '组件-实例信息',
  `locked_game_object_list` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '锁定的节点列表',
  `camera_initial_position` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '相机初始位置',
  `camera_initial_euler` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '相机初始角度',
  `release_state_uuid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '唯一标识',
  `state_infos` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '状态信息',
  `comm_com_jsons` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '公共组件属性列表',
  `is_publish` int(1) NULL DEFAULT NULL COMMENT '是否公开',
  `version` int(11) NULL DEFAULT NULL COMMENT '当前版本号 每更新一次增加1',
  `status` int(11) NULL DEFAULT NULL COMMENT '状态 0 删除 1正常 2旧版本',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `upload_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '上传时间',
  `editor` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '编辑者',
  `photo_Address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '场景图片',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT NULL COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  `scene_config_info` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '场景配置信息，babylon用',
  `icon_infos` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '图标信息列表',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 778 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for engineering_data
-- ----------------------------
DROP TABLE IF EXISTS `engineering_data`;
CREATE TABLE `engineering_data`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `engineering_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '3D场景唯一标识',
  `engineering_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '3D场景名称',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT NULL COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '3D场景数据' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for engineering_data_list_column
-- ----------------------------
DROP TABLE IF EXISTS `engineering_data_list_column`;
CREATE TABLE `engineering_data_list_column`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `row_id` int(11) NULL DEFAULT NULL COMMENT '列id',
  `column_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '行数据',
  `column_sort` int(11) NULL DEFAULT NULL COMMENT '行排序',
  `status` int(1) NULL DEFAULT NULL COMMENT '状态 1正常 0删除',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT NULL COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for engineering_data_list_row
-- ----------------------------
DROP TABLE IF EXISTS `engineering_data_list_row`;
CREATE TABLE `engineering_data_list_row`  (
  `id` int(10) NOT NULL AUTO_INCREMENT,
  `project_engineering_id` int(11) NULL DEFAULT NULL COMMENT '场景id',
  `row_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列名称',
  `row_sort` int(11) NULL DEFAULT NULL COMMENT '列序号',
  `status` int(2) NULL DEFAULT NULL COMMENT '状态0 删除 1正常',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT NULL COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 817 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for file_download_info
-- ----------------------------
DROP TABLE IF EXISTS `file_download_info`;
CREATE TABLE `file_download_info`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件名',
  `file_id` int(11) NULL DEFAULT NULL COMMENT '文件id',
  `download_user_id` int(1) NULL DEFAULT NULL COMMENT '下载用户id',
  `download_user_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '下载用户名',
  `download_time` datetime NULL DEFAULT NULL COMMENT '下载时间',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT NULL COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for file_info
-- ----------------------------
DROP TABLE IF EXISTS `file_info`;
CREATE TABLE `file_info`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件名',
  `file_level` int(11) NULL DEFAULT NULL COMMENT '文件等级，在多少层',
  `file_type` int(1) NULL DEFAULT NULL COMMENT '文件类型 0文件夹 1文件',
  `parent_id` int(11) NULL DEFAULT NULL COMMENT '父文件id',
  `file_size` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件大小',
  `upload_time` datetime NULL DEFAULT NULL COMMENT '上传时间',
  `upload_user_id` int(11) NULL DEFAULT NULL COMMENT '上传用户Id',
  `upload_user_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上传用户名',
  `status` int(255) NULL DEFAULT NULL COMMENT '文件状态',
  `has_next` int(11) NULL DEFAULT NULL COMMENT '是否有下一层目录 0没有，1有',
  `unique_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件的唯一号,目录及子文件一致',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT 1 COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 46394 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for file_only_info
-- ----------------------------
DROP TABLE IF EXISTS `file_only_info`;
CREATE TABLE `file_only_info`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件名',
  `parent_id` int(11) NULL DEFAULT NULL COMMENT '上一级id',
  `parent_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上一级名称',
  `project_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '项目Id',
  `file_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件码',
  `status` int(1) NULL DEFAULT NULL COMMENT '状态',
  `unique_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件的唯一号,目录及子文件一致',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT 1 COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 42994 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for files_minio
-- ----------------------------
DROP TABLE IF EXISTS `files_minio`;
CREATE TABLE `files_minio`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `upload_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件上传id',
  `md5` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件计算md5',
  `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件访问地址',
  `bucket` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '存储桶',
  `object` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'minio中文件名',
  `origin_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '原始文件名',
  `size` bigint(20) NULL DEFAULT NULL COMMENT '文件大小',
  `type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件类型',
  `chunk_size` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '分片大小',
  `chunk_count` int(11) NULL DEFAULT NULL COMMENT '分片数量',
  `is_delete` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '是否删除',
  `create_time` timestamp(6) NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Minio文件表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for icon_info
-- ----------------------------
DROP TABLE IF EXISTS `icon_info`;
CREATE TABLE `icon_info`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `project_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '项目id',
  `icon_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标名称',
  `unique_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标唯一标识',
  `is_collection` int(11) NULL DEFAULT NULL COMMENT '是否收藏',
  `type_id` int(11) NULL DEFAULT NULL COMMENT '图标类型id',
  `type_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标类型名称',
  `res_from_platform` enum('BIM','BABYLON_WEB','U3D_WEB','U3D') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '平台类型:\'BIM\',\'BABYLON_WEB\',\'U3D_WEB\',\'U3D\'',
  `icon_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标地址',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 52 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '图标信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for icon_type
-- ----------------------------
DROP TABLE IF EXISTS `icon_type`;
CREATE TABLE `icon_type`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型编码',
  `type_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型名称',
  `status` int(1) NULL DEFAULT NULL COMMENT '状态',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT NULL COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 25 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for material
-- ----------------------------
DROP TABLE IF EXISTS `material`;
CREATE TABLE `material`  (
  `id` int(4) NOT NULL AUTO_INCREMENT,
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `type_id` int(4) NULL DEFAULT NULL,
  `previews` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `maps` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `photo_address_prefix` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `glass` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 28 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for resource
-- ----------------------------
DROP TABLE IF EXISTS `resource`;
CREATE TABLE `resource`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `art_res_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源文件目录',
  `art_res_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源类型',
  `asset_bundel_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源文件相对路径(art_res_name/asset_name）',
  `asset_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源文件名称',
  `preview_out_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预览路径',
  `unique_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源唯一标识',
  `key_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'keyid',
  `project_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '资源项目Id',
  `is_collection` int(11) NULL DEFAULT NULL COMMENT '是否收藏',
  `type_id` int(11) NULL DEFAULT NULL COMMENT '资源类型id',
  `type_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源类型名称',
  `resource_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源名称',
  `upload_time` datetime NULL DEFAULT NULL COMMENT '上传时间',
  `upload_user_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上传人名字',
  `upload_user_id` int(11) NULL DEFAULT NULL COMMENT '上传人Id',
  `banner_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '展示图地址',
  `banner_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '展示图名字',
  `res_from_platform` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上传渠道',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `resource_data_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源数据类型',
  `resource_data_type_id` int(11) NULL DEFAULT NULL COMMENT '资源数据类型id',
  `root_dir_name` varchar(225) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件根目录',
  `preview_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源文件绝对路径',
  `gif_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'gif图路径',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT 1 COMMENT '应用id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`, `unique_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1779 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for resource_data_type
-- ----------------------------
DROP TABLE IF EXISTS `resource_data_type`;
CREATE TABLE `resource_data_type`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型名称',
  `status` int(1) NULL DEFAULT NULL COMMENT '状态',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT NULL COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for resource_info
-- ----------------------------
DROP TABLE IF EXISTS `resource_info`;
CREATE TABLE `resource_info`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `res_from_platform` enum('BIM','BABYLON_WEB','U3D_WEB','U3D') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '平台类型:\'BIM\',\'BABYLON_WEB\',\'U3D_WEB\',\'U3D\'',
  `project_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '项目id',
  `art_res_name` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源名称',
  `art_res_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源类型',
  `art_res_version_id` int(11) NULL DEFAULT NULL COMMENT '资源版本号id',
  `art_res_version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源版本号',
  `asset_bundel_name` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'AB资源相对路径',
  `asset_name` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源预制体名称',
  `unique_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源唯一标识',
  `art_res_relative_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源在文件服务器上相对路径',
  `art_res_raw_data` json NULL COMMENT '美术资源原始JSON信息',
  `is_collection` int(11) NULL DEFAULT NULL COMMENT '是否收藏',
  `type_id` int(11) NULL DEFAULT NULL COMMENT '资源类型id',
  `type_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源类型名称',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  `resource_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源名称（用户自定义）',
  `banner_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '展示图地址',
  `banner_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '展示图名字',
  `root_dir_name` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '美术资源文件根目录',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9756462 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for resource_type
-- ----------------------------
DROP TABLE IF EXISTS `resource_type`;
CREATE TABLE `resource_type`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型编码',
  `type_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型名',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT NULL COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9754554 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for resource_version
-- ----------------------------
DROP TABLE IF EXISTS `resource_version`;
CREATE TABLE `resource_version`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `version_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '版本号',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9754501 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for scene_def
-- ----------------------------
DROP TABLE IF EXISTS `scene_def`;
CREATE TABLE `scene_def`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `gis_info` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'gis信息',
  `project` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '对应的项目编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '名称',
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'u3d' COMMENT '类型',
  `scene_res_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'U3D唯一ID',
  `nodes` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '节点',
  `com_points` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '组件信息集合',
  `components` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '场景信息',
  `com_point_components` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '组件-身上的脚本集合',
  `locked_gameobject_list` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '锁定的节点列表',
  `camera_initial_position` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '相机初始位置',
  `camera_initial_euler` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '相机初始角度',
  `release_state_uuid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '唯一标识',
  `state_infos` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '状态信息',
  `is_publish` int(1) NULL DEFAULT NULL COMMENT '是否公开',
  `comm_com_jsons` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '公共组件属性列表',
  `version` int(11) NULL DEFAULT NULL COMMENT '当前版本号 每更新一次增加1',
  `status` int(11) NULL DEFAULT NULL COMMENT '状态 0 删除 1正常 2旧版本',
  `upload_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '上传时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `photo_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图片路径',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `org_id_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组织机构id',
  `app_id` bigint(20) NULL DEFAULT NULL COMMENT '应用id',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for signal_ling
-- ----------------------------
DROP TABLE IF EXISTS `signal_ling`;
CREATE TABLE `signal_ling`  (
  `id` int(4) NOT NULL,
  `prop` varchar(225) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT 'key名称',
  `value` text CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT 'value值',
  `description` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '描述',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for space
-- ----------------------------
DROP TABLE IF EXISTS `space`;
CREATE TABLE `space`  (
  `area` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '园区',
  `building` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '所属楼栋',
  `floor` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '所属楼层',
  `spaceName` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '所属空间名称',
  `spaceID` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '所属空间编码',
  `spacePosition` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '空间坐标x,y,z',
  `data_auto_0` int(11) NULL DEFAULT NULL COMMENT '增长字段'
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = 'space' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for state
-- ----------------------------
DROP TABLE IF EXISTS `state`;
CREATE TABLE `state`  (
  `deviceID` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '设备编码',
  `deviceName` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '设备名称',
  `state` int(11) NULL DEFAULT NULL COMMENT '状态',
  `stateName` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '状态名称',
  `warning` int(11) NULL DEFAULT NULL COMMENT '是否告警',
  `data_auto_0` int(11) NULL DEFAULT NULL COMMENT '增长字段'
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = 'state' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for task_event
-- ----------------------------
DROP TABLE IF EXISTS `task_event`;
CREATE TABLE `task_event`  (
  `id` int(8) NOT NULL AUTO_INCREMENT COMMENT '自增id',
  `virtual_camera` int(8) NULL DEFAULT NULL COMMENT '虚拟相机ID',
  `external_task_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '外部任务ID',
  `external_task_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '外部任务名称',
  `scene_id` int(8) NOT NULL COMMENT '场景ID',
  `task_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务ID',
  `task_type` enum('Path','Custom','AniTimeline','AniSystem','NodeActive','VehicleMovement','NodeBind','AniAxisControl','StateChange','HeavyLoadHoisting','ring_holding','SmallCrane') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务类型: Path // 路径任务, Custom // 自定义任务, AniTimeline // Timeline, AniSystem // 动画控制器, NodeActive // 节点显隐, VehicleMovement // 车辆移动, NodeBind // 节点换绑, AniAxisControl // 轴向控制器, StateChange // 状态变更, HeavyLoadHoisting // 重载吊车, ring_holding // 抱环, SmallCrane // 悬臂吊',
  `unique_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '对象的唯一标识',
  `animation_clip_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '动画片段名字',
  `timeline_asset_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'timeline资源名字',
  `start_time` float NULL DEFAULT NULL COMMENT '动画开始时间',
  `end_time` float NULL DEFAULT NULL COMMENT '动画结束时间',
  `is_loop` tinyint(1) NOT NULL DEFAULT 0 COMMENT '动画是否循环播放',
  `execution_time` bigint(30) NULL DEFAULT NULL COMMENT '任务执行时间(毫秒)',
  `path_points` json NULL COMMENT '路径点(可选)-json类型',
  `active_node` json NULL COMMENT '是否隐藏节点',
  `hidden_node` json NULL COMMENT '隐藏节点名字',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  `command` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务Code',
  `device_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备Code',
  `use_smooth_movement` tinyint(1) NOT NULL,
  `move_duration` float NULL DEFAULT NULL COMMENT '平滑移动持续时间（秒）',
  `use_smooth_rotation` bigint(1) NULL DEFAULT NULL COMMENT '是否使用平滑旋转',
  `custom_axis_constraint` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '旋转轴约束（锁定特定轴）',
  `unbind_node` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '解绑节点',
  `unbind_sub_node` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '解绑节点的父节点',
  `unbind_target_node` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '解绑节点的目标节点',
  `state_colors` json NULL COMMENT '状态颜色',
  `main_car` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '大车',
  `main_car_start_pos` json NULL COMMENT '大车起始坐标（世界坐标），格式：{\"x\":0.0,\"y\":0.0,\"z\":0.0}',
  `main_car_axis` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '大车移动轴向(字符串枚举)',
  `main_car_max_travel` float NULL DEFAULT NULL COMMENT '大车最大行程(米)',
  `sub_car` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '小车',
  `sub_car_start_pos` json NULL COMMENT '小车起始坐标（世界坐标），格式：{\"x\":0.0,\"y\":0.0,\"z\":0.0}',
  `sub_car_axis` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '小车移动轴向(字符串枚举)',
  `sub_car_max_travel` float NULL DEFAULT NULL COMMENT '小车最大行程(米)',
  `hook` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '吊钩',
  `hook_start_pos` json NULL COMMENT '吊钩起始坐标（世界坐标），格式：{\"x\":0.0,\"y\":0.0,\"z\":0.0}',
  `hook_axis` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '吊钩移动轴向(字符串枚举)',
  `hook_max_height` float NULL DEFAULT NULL COMMENT '吊钩最大升降高度(米)',
  `coordinate_system` int(8) NULL DEFAULT NULL COMMENT '坐标系',
  `move_node` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '移动节点',
  `move_axis` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '移动轴向',
  `node_rotation` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '悬臂吊旋转节点',
  `node_slide` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '悬臂吊滑块节点',
  `node_fixed_hinge` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '悬臂吊固定吊点节点',
  `node_move_hinge1` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '悬臂吊移动吊点1',
  `node_move_hinge2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '悬臂吊移动吊点2',
  `slider_direction` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '滑块节点移动方向',
  `move_hinge1_direction` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '移动吊点1移动方向',
  `move_hinge2_direction` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '移动吊点2移动方向',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 88 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '任务事件表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for task_message
-- ----------------------------
DROP TABLE IF EXISTS `task_message`;
CREATE TABLE `task_message`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务id',
  `version` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '版本号',
  `type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '数据类型',
  `data` json NULL COMMENT 'json数据',
  `create_time` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  `business_module` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '业务模块',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 257062 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for time_line
-- ----------------------------
DROP TABLE IF EXISTS `time_line`;
CREATE TABLE `time_line`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '任务id',
  `content` json NULL COMMENT 'json数据',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for virtual_camera
-- ----------------------------
DROP TABLE IF EXISTS `virtual_camera`;
CREATE TABLE `virtual_camera`  (
  `id` int(8) NOT NULL AUTO_INCREMENT COMMENT '自增id',
  `scene_id` int(8) NOT NULL COMMENT '场景ID',
  `camera_external_id` int(8) NOT NULL COMMENT '外部虚拟相机ID',
  `camera_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '相机名称',
  `vc_pos` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '相机坐标',
  `vc_rotation` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '相机旋转',
  `vc_priority` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '相机权重',
  `is_following` tinyint(1) NULL DEFAULT NULL COMMENT '是否跟随',
  `vc_follow_object` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '相机跟随',
  `vc_follow_offset` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '跟随路径偏移',
  `vc_look_at` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '朝向',
  `create_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者Id',
  `update_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `update_time` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_modified_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者Id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 30 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '虚拟相机表' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
