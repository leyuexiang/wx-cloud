CREATE TABLE veg_generation_task (
  id BIGINT NOT NULL PRIMARY KEY,
  generator_task_id VARCHAR(128),
  scene_id VARCHAR(64) NOT NULL,
  snapshot_version VARCHAR(64) NOT NULL,
  rule_version VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  total_tile_count INT NOT NULL DEFAULT 0,
  completed_tile_count INT NOT NULL DEFAULT 0,
  failed_tile_count INT NOT NULL DEFAULT 0,
  candidate_count BIGINT NOT NULL DEFAULT 0,
  instance_count BIGINT NOT NULL DEFAULT 0,
  generator_version VARCHAR(64),
  publication_version VARCHAR(64),
  manifest_url VARCHAR(512),
  tileset_url VARCHAR(512),
  layer_tileset_urls_json VARCHAR(10000),
  layer_density_tileset_urls_json VARCHAR(30000),
  progress_json VARCHAR(30000),
  source_hash VARCHAR(128),
  snapshot_json VARCHAR(10000) NOT NULL,
  error_message VARCHAR(2000),
  creator BIGINT,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT uk_veg_task_idempotency UNIQUE (idempotency_key),
  CONSTRAINT uk_veg_task_generator_id UNIQUE (generator_task_id)
);

CREATE TABLE veg_generation_tile (
  id BIGINT NOT NULL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  publication_version VARCHAR(64) NOT NULL,
  tile_id VARCHAR(128) NOT NULL,
  tile_row INT NOT NULL,
  tile_column INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  candidate_count INT NOT NULL DEFAULT 0,
  instance_count INT NOT NULL DEFAULT 0,
  content_url VARCHAR(512),
  content_hash VARCHAR(128),
  error_message VARCHAR(2000),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_veg_tile_task_tile UNIQUE (task_id, tile_id)
);

CREATE TABLE veg_publication (
  id BIGINT NOT NULL PRIMARY KEY,
  scene_id VARCHAR(64) NOT NULL,
  publication_version VARCHAR(64) NOT NULL,
  task_id BIGINT NOT NULL,
  rule_version VARCHAR(64) NOT NULL,
  generator_version VARCHAR(64) NOT NULL,
  manifest_url VARCHAR(512) NOT NULL,
  tileset_url VARCHAR(512),
  source_hash VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  instance_count BIGINT NOT NULL DEFAULT 0,
  tile_count INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_veg_publication_version UNIQUE (scene_id, publication_version)
);

-- 与生产表一致：发布事务锁定场景行，确保同一场景只有一个STABLE版本。
CREATE TABLE veg_publication_lock (
  scene_id VARCHAR(64) NOT NULL PRIMARY KEY,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
