-- 3D 资源管理新表结构迁移。
-- 说明：本脚本只创建新表，不修改旧 resource/resource_info 表，保证旧项目资源接口继续可用。

CREATE TABLE IF NOT EXISTS `wx_dim3d_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_name` varchar(50) NOT NULL COMMENT '模型名称',
  `model_desc` varchar(500) DEFAULT NULL COMMENT '模型描述',
  `category` varchar(32) NOT NULL COMMENT '模型分类字典值',
  `resource_belong` varchar(16) NOT NULL DEFAULT 'SYSTEM' COMMENT '资源归属：SYSTEM系统资源，MINE我的资源',
  `is_paid` varchar(16) NOT NULL DEFAULT 'FREE' COMMENT '是否付费：FREE免费，PAID付费',
  `price` int DEFAULT NULL COMMENT '价格，单位分',
  `cover_image_id` bigint DEFAULT NULL COMMENT '封面文件ID',
  `model_file_id` bigint NOT NULL COMMENT '模型文件ID',
  `status` varchar(16) NOT NULL DEFAULT 'ONLINE' COMMENT '状态：ONLINE上线，OFFLINE下线',
  `data_user_id` bigint DEFAULT NULL COMMENT '业务归属人', `data_dept_id` bigint DEFAULT NULL COMMENT '业务归属部门',
  `tenant_id` varchar(20) DEFAULT '000000' COMMENT '租户编号', `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者', `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者', `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`id`), KEY `idx_dim3d_model_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='3D模型资源表';

CREATE TABLE IF NOT EXISTS `wx_dim3d_icon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', `icon_name` varchar(50) NOT NULL COMMENT '图标名称',
  `icon_desc` varchar(500) DEFAULT NULL COMMENT '图标描述', `category` varchar(32) NOT NULL COMMENT '一级分类',
  `secondary_category` varchar(32) DEFAULT NULL COMMENT '二级分类', `resource_belong` varchar(16) NOT NULL DEFAULT 'SYSTEM' COMMENT '资源归属',
  `icon_file_id` bigint NOT NULL COMMENT '图标文件ID', `status` varchar(16) NOT NULL DEFAULT 'ONLINE' COMMENT '状态',
  `data_user_id` bigint DEFAULT NULL COMMENT '业务归属人', `data_dept_id` bigint DEFAULT NULL COMMENT '业务归属部门',
  `tenant_id` varchar(20) DEFAULT '000000' COMMENT '租户编号', `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者', `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者', `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志', PRIMARY KEY (`id`), KEY `idx_dim3d_icon_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='3D图标资源表';

CREATE TABLE IF NOT EXISTS `wx_dim3d_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID', `template_name` varchar(50) NOT NULL COMMENT '模板名称',
  `template_desc` varchar(500) DEFAULT NULL COMMENT '模板描述', `template_type` varchar(16) NOT NULL DEFAULT 'INDUSTRY' COMMENT '模板类型',
  `category` varchar(32) NOT NULL COMMENT '模板分类', `resource_belong` varchar(16) NOT NULL DEFAULT 'SYSTEM' COMMENT '资源归属',
  `cover_image_id` bigint DEFAULT NULL COMMENT '封面文件ID', `template_file_id` bigint NOT NULL COMMENT '模板文件ID',
  `status` varchar(16) NOT NULL DEFAULT 'ONLINE' COMMENT '状态', `data_user_id` bigint DEFAULT NULL COMMENT '业务归属人',
  `data_dept_id` bigint DEFAULT NULL COMMENT '业务归属部门', `tenant_id` varchar(20) DEFAULT '000000' COMMENT '租户编号',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门', `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`id`), KEY `idx_dim3d_template_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='3D模板资源表';

CREATE TABLE IF NOT EXISTS `wx_dim3d_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '转换资源主键，沿用转换服务返回ID', `resource_name` varchar(150) NOT NULL COMMENT '资源名称',
  `resource_type` varchar(20) NOT NULL COMMENT '资源类型', `asset_name` varchar(150) NOT NULL COMMENT '资源文件名称',
  `resource_relative_path` varchar(255) NOT NULL COMMENT '资源相对路径', `data_user_id` bigint DEFAULT NULL COMMENT '业务归属人',
  `data_dept_id` bigint DEFAULT NULL COMMENT '业务归属部门', `tenant_id` varchar(20) DEFAULT '000000' COMMENT '租户编号',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门', `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`id`), KEY `idx_dim3d_resource_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='3D转换资源表';
