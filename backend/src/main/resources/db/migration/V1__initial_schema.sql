-- ============================================================================
-- NETRA: Network Threat Early-warning and Risk Analytics
-- Flyway Database Migration: V1__initial_schema.sql
-- Target Database: PostgreSQL 14+ (Authoritative)
-- ============================================================================

-- 1. Identity & RBAC
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(200),
    email VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- 2. Network Topology & Assets
CREATE TABLE IF NOT EXISTS networks (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    cidr VARCHAR(50),
    description VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS hosts (
    id BIGSERIAL PRIMARY KEY,
    ip_address VARCHAR(45) NOT NULL,
    hostname VARCHAR(255),
    mac_address VARCHAR(17),
    os_info VARCHAR(500),
    risk_level VARCHAR(20) DEFAULT 'INFO',
    risk_score DOUBLE PRECISION DEFAULT 0.0,
    is_monitored BOOLEAN NOT NULL DEFAULT TRUE,
    network_id BIGINT REFERENCES networks(id) ON DELETE SET NULL,
    first_seen TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_hosts_ip ON hosts(ip_address);
CREATE INDEX IF NOT EXISTS idx_hosts_network ON hosts(network_id);
CREATE INDEX IF NOT EXISTS idx_hosts_risk ON hosts(risk_score DESC);

CREATE TABLE IF NOT EXISTS interfaces (
    id BIGSERIAL PRIMARY KEY,
    host_id BIGINT NOT NULL REFERENCES hosts(id) ON DELETE CASCADE,
    interface_name VARCHAR(100) NOT NULL,
    mac_address VARCHAR(17),
    ip_address VARCHAR(45),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS services (
    id BIGSERIAL PRIMARY KEY,
    host_id BIGINT NOT NULL REFERENCES hosts(id) ON DELETE CASCADE,
    port_number INTEGER NOT NULL,
    protocol VARCHAR(10) NOT NULL DEFAULT 'TCP',
    service_name VARCHAR(100),
    version_info VARCHAR(200),
    state VARCHAR(50) DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_services_host_port ON services(host_id, port_number);

-- 3. Telemetry & Sensor Infrastructure
CREATE TABLE IF NOT EXISTS sensor_configs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    host_ip VARCHAR(45) NOT NULL,
    ssh_port INTEGER NOT NULL DEFAULT 22,
    username VARCHAR(100) NOT NULL,
    auth_type VARCHAR(50) NOT NULL DEFAULT 'KEY', -- KEY, PASSWORD
    telemetry_path VARCHAR(500),
    poll_interval_seconds INTEGER NOT NULL DEFAULT 30,
    status VARCHAR(50) NOT NULL DEFAULT 'DISCONNECTED',
    last_heartbeat TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sensor_connection_events (
    id BIGSERIAL PRIMARY KEY,
    sensor_id BIGINT NOT NULL REFERENCES sensor_configs(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL, -- CONNECTED, DISCONNECTED, ERROR, AUTH_FAIL
    message TEXT,
    event_timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Datasets & Ingestion Provenance
CREATE TABLE IF NOT EXISTS datasets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    dataset_type VARCHAR(50) NOT NULL, -- BENCHMARK, SYNTHETIC, CAPTURE
    source_url VARCHAR(500),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dataset_versions (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
    version_tag VARCHAR(50) NOT NULL,
    file_path VARCHAR(500),
    sha256_hash VARCHAR(64),
    record_count BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ingestion_jobs (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(50) NOT NULL, -- CSV, PCAP, NETFLOW, REMOTE_SENSOR, SYNTHETIC
    source_filename VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_rows BIGINT,
    valid_rows BIGINT,
    rejected_rows BIGINT,
    error_summary VARCHAR(2000),
    dataset_id BIGINT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    version BIGINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_status ON ingestion_jobs(status);

CREATE TABLE IF NOT EXISTS ingestion_errors (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES ingestion_jobs(id) ON DELETE CASCADE,
    row_number BIGINT,
    raw_data TEXT,
    error_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. Network Traffic Flows & Features
CREATE TABLE IF NOT EXISTS traffic_flows (
    id BIGSERIAL PRIMARY KEY,
    src_ip VARCHAR(45) NOT NULL,
    dst_ip VARCHAR(45) NOT NULL,
    src_port INTEGER,
    dst_port INTEGER,
    protocol VARCHAR(10),
    flow_timestamp TIMESTAMPTZ NOT NULL,
    duration_ms BIGINT,
    total_fwd_packets BIGINT,
    total_bwd_packets BIGINT,
    total_fwd_bytes BIGINT,
    total_bwd_bytes BIGINT,
    fwd_pkt_len_mean DOUBLE PRECISION,
    bwd_pkt_len_mean DOUBLE PRECISION,
    flow_iat_mean DOUBLE PRECISION,
    flow_iat_std DOUBLE PRECISION,
    flow_iat_max DOUBLE PRECISION,
    syn_flag_count INTEGER,
    ack_flag_count INTEGER,
    rst_flag_count INTEGER,
    psh_flag_count INTEGER,
    fin_flag_count INTEGER,
    avg_packet_size DOUBLE PRECISION,
    fwd_bwd_ratio DOUBLE PRECISION,
    label VARCHAR(100),
    attack_category VARCHAR(100),
    ingestion_job_id BIGINT,
    dataset_id BIGINT,
    source_row_number BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_flows_src_ip ON traffic_flows(src_ip);
CREATE INDEX IF NOT EXISTS idx_flows_dst_ip ON traffic_flows(dst_ip);
CREATE INDEX IF NOT EXISTS idx_flows_timestamp ON traffic_flows(flow_timestamp);
CREATE INDEX IF NOT EXISTS idx_flows_ingestion ON traffic_flows(ingestion_job_id);

CREATE TABLE IF NOT EXISTS packet_feature_summaries (
    id BIGSERIAL PRIMARY KEY,
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    total_flows BIGINT NOT NULL,
    total_bytes BIGINT NOT NULL,
    syn_ratio DOUBLE PRECISION,
    error_rate DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. Network States & Temporal Aggregations
CREATE TABLE IF NOT EXISTS network_states (
    id BIGSERIAL PRIMARY KEY,
    network_id BIGINT REFERENCES networks(id) ON DELETE CASCADE,
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    state_vector_json TEXT,
    anomaly_score DOUBLE PRECISION DEFAULT 0.0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS state_feature_values (
    id BIGSERIAL PRIMARY KEY,
    state_id BIGINT NOT NULL REFERENCES network_states(id) ON DELETE CASCADE,
    feature_name VARCHAR(100) NOT NULL,
    feature_value DOUBLE PRECISION NOT NULL
);

-- 7. ML Models, Forecaster & Training
CREATE TABLE IF NOT EXISTS model_definitions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    model_family VARCHAR(50) NOT NULL, -- TCN, LSTM, HYBRID, DETERMINISTIC
    description TEXT,
    input_dim INTEGER,
    output_dim INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS model_versions (
    id BIGSERIAL PRIMARY KEY,
    model_def_id BIGINT NOT NULL REFERENCES model_definitions(id) ON DELETE CASCADE,
    version_tag VARCHAR(50) NOT NULL,
    weights_path VARCHAR(500),
    metrics_json TEXT,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS training_runs (
    id BIGSERIAL PRIMARY KEY,
    model_version_id BIGINT REFERENCES model_versions(id) ON DELETE SET NULL,
    epochs INTEGER NOT NULL,
    loss_history_json TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS forecast_runs (
    id BIGSERIAL PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL,
    model_version VARCHAR(50),
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    horizon_steps INTEGER NOT NULL,
    current_risk_score DOUBLE PRECISION,
    max_forecasted_risk DOUBLE PRECISION,
    risk_level VARCHAR(20),
    predicted_stage VARCHAR(100),
    confidence DOUBLE PRECISION,
    data_quality_score DOUBLE PRECISION,
    flow_count_in_window BIGINT,
    host_count_in_window INTEGER,
    dataset_id BIGINT,
    computation_time_ms BIGINT,
    is_demo BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_forecast_timestamp ON forecast_runs(created_at);

CREATE TABLE IF NOT EXISTS forecast_horizons (
    id BIGSERIAL PRIMARY KEY,
    forecast_run_id BIGINT NOT NULL REFERENCES forecast_runs(id) ON DELETE CASCADE,
    step_number INTEGER NOT NULL,
    projected_timestamp TIMESTAMPTZ NOT NULL,
    predicted_risk_score DOUBLE PRECISION NOT NULL,
    predicted_attack_probability DOUBLE PRECISION NOT NULL,
    predicted_traffic_volume DOUBLE PRECISION,
    confidence_lower DOUBLE PRECISION,
    confidence_upper DOUBLE PRECISION
);

CREATE INDEX IF NOT EXISTS idx_forecast_horizons_run ON forecast_horizons(forecast_run_id);

CREATE TABLE IF NOT EXISTS feature_attributions (
    id BIGSERIAL PRIMARY KEY,
    forecast_run_id BIGINT NOT NULL REFERENCES forecast_runs(id) ON DELETE CASCADE,
    feature_name VARCHAR(100) NOT NULL,
    attribution_score DOUBLE PRECISION NOT NULL,
    interpretation VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS attack_stage_mappings (
    id BIGSERIAL PRIMARY KEY,
    stage_name VARCHAR(100) NOT NULL UNIQUE,
    mitre_tactic_id VARCHAR(50),
    mitre_tactic_name VARCHAR(200),
    progression_order INTEGER NOT NULL,
    severity_baseline VARCHAR(20) DEFAULT 'MEDIUM'
);

-- 8. Incidents, Evidence & Alerts
CREATE TABLE IF NOT EXISTS incidents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    assigned_to VARCHAR(100),
    forecast_run_id BIGINT REFERENCES forecast_runs(id) ON DELETE SET NULL,
    primary_host_id BIGINT REFERENCES hosts(id) ON DELETE SET NULL,
    attack_stage VARCHAR(100),
    mitre_tactic VARCHAR(200),
    mitre_technique VARCHAR(200),
    resolution_notes TEXT,
    is_demo BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    created_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_incidents_status ON incidents(status);
CREATE INDEX IF NOT EXISTS idx_incidents_severity ON incidents(severity);
CREATE INDEX IF NOT EXISTS idx_incidents_created ON incidents(created_at);

CREATE TABLE IF NOT EXISTS incident_status_history (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    change_reason VARCHAR(1000),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS evidence_objects (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    evidence_type VARCHAR(50) NOT NULL, -- FLOW_SAMPLE, PCAP_SNIPPET, LOG_LINE, ATTRIBUTION
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS incident_evidence (
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    evidence_id BIGINT NOT NULL REFERENCES evidence_objects(id) ON DELETE CASCADE,
    PRIMARY KEY (incident_id, evidence_id)
);

CREATE TABLE IF NOT EXISTS detection_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    severity VARCHAR(20) NOT NULL,
    condition_sql TEXT,
    condition_json TEXT,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rule_executions (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES detection_rules(id) ON DELETE CASCADE,
    execution_timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    matches_count INTEGER NOT NULL DEFAULT 0,
    execution_time_ms BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT REFERENCES detection_rules(id) ON DELETE SET NULL,
    host_id BIGINT REFERENCES hosts(id) ON DELETE SET NULL,
    title VARCHAR(300) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    is_suppressed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 9. Analyst Actions, Reports & Audit Records
CREATE TABLE IF NOT EXISTS analyst_actions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    incident_id BIGINT REFERENCES incidents(id) ON DELETE SET NULL,
    action_type VARCHAR(100) NOT NULL, -- ASSIGNED, ESCALATED, RESOLVED, COMMENTED
    action_details TEXT,
    action_timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reports (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    report_type VARCHAR(50) NOT NULL, -- EXECUTIVE_SUMMARY, INCIDENT_DOSSIER, FORECAST_VALIDATION
    generated_by VARCHAR(100),
    payload_markdown TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_records (
    id BIGSERIAL PRIMARY KEY,
    actor_username VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100),
    ip_address VARCHAR(45),
    outcome VARCHAR(20) NOT NULL, -- SUCCESS, FAILURE
    metadata_json TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_occurred_at ON audit_records(occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_records(actor_username);
