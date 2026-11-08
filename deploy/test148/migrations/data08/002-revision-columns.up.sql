-- DATA-08 草案：给现有修订增加兼容期共享正文引用，保留原四包列作为回退来源。
ALTER TABLE wx_3d_scene_revision
    ADD COLUMN content_id BIGINT NULL COMMENT '共享正文行编号，兼容期可空',
    ADD COLUMN content_hash CHAR(64) NULL COMMENT '共享正文SHA-256，兼容期可空',
    ADD COLUMN content_bytes BIGINT NULL COMMENT '四包正文UTF-8总字节数',
    ADD KEY idx_scene_revision_content (tenant_id,content_id);
