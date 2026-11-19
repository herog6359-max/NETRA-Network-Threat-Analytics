-- ============================================================================
-- NETRA Flyway Migration: V4__telemetry_pipeline_and_replay.sql
-- Telemetry ingestion idempotency, chronological replay sessions, and passive sensor tracking
-- ============================================================================

-- 1. Add duplicate tracking to ingestion jobs
ALTER TABLE ingestion_jobs ADD COLUMN IF NOT EXISTS duplicate_rows BIGINT DEFAULT 0;

-- 2. Add flow deduplication hash to traffic_flows for idempotent ingestion
ALTER TABLE traffic_flows ADD COLUMN IF NOT EXISTS flow_hash VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_flows_hash ON traffic_flows(flow_hash);

-- 3. Telemetry Replay Sessions
CREATE TABLE IF NOT EXISTS telemetry_replay_sessions (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT REFERENCES ingestion_jobs(id) ON DELETE SET NULL,
    source_filename VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'IDLE', -- IDLE, PLAYING, PAUSED, COMPLETED, STOPPED
    playback_speed INTEGER NOT NULL DEFAULT 1,  -- 1, 2, 5, 10
    total_flows BIGINT NOT NULL DEFAULT 0,
    emitted_flows BIGINT NOT NULL DEFAULT 0,
    current_cursor_timestamp TIMESTAMPTZ,
    window_start TIMESTAMPTZ,
    window_end TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_replay_status ON telemetry_replay_sessions(status);

-- 4. Passive Sensor Adapter Configurations & Audit Log
CREATE TABLE IF NOT EXISTS passive_sensor_configs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    interface_name VARCHAR(100) NOT NULL,
    bind_address VARCHAR(45) NOT NULL DEFAULT '127.0.0.1',
    listen_port INTEGER NOT NULL DEFAULT 2055,
    authorized_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM_OPERATOR',
    status VARCHAR(50) NOT NULL DEFAULT 'INACTIVE', -- INACTIVE, ACTIVE, ERROR
    flows_captured BIGINT NOT NULL DEFAULT 0,
    last_captured_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS passive_sensor_audit_events (
    id BIGSERIAL PRIMARY KEY,
    sensor_id BIGINT REFERENCES passive_sensor_configs(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL, -- AUTHORIZED, STARTED, STOPPED, BIND_ERROR, FLOW_RECEIVED
    details TEXT,
    event_timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
