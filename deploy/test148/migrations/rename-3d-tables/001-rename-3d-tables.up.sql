-- 适用于 148 已部署的七张 3D 自有表。停写后一次性改名，保留数据和索引。
RENAME TABLE
  `wx_dim3d_icon` TO `wx_3d_icon`,
  `wx_dim3d_model` TO `wx_3d_model`,
  `wx_dim3d_resource` TO `wx_3d_resource`,
  `wx_dim3d_resource_operation` TO `wx_3d_resource_operation`,
  `wx_dim3d_scene` TO `wx_3d_scene`,
  `wx_dim3d_scene_revision` TO `wx_3d_scene_revision`,
  `wx_dim3d_template` TO `wx_3d_template`;
