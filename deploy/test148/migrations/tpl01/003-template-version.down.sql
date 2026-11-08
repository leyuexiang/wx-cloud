DROP TABLE IF EXISTS wx_3d_scene_template_version;
ALTER TABLE wx_3d_scene_template
  DROP COLUMN previous_published_version_no,
  DROP COLUMN published_version_no,
  DROP COLUMN current_version_no;
