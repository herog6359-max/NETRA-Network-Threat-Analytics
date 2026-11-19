-- ============================================================================
-- NETRA Flyway Migration: V3__digital_twin_and_scenarios.sql
-- Digital Twin what-if scenarios, analytical views, and window function support
-- ============================================================================

-- 1. Counterfactual What-If Scenarios Table
CREATE TABLE IF NOT EXISTS whatif_scenarios (
    id BIGSERIAL PRIMARY KEY,
    parent_forecast_id BIGINT REFERENCES forecast_runs(id) ON DELETE SET NULL,
    intervention_type VARCHAR(50) NOT NULL, -- ISOLATE_HOST, THROTTLE_RATE, BLOCK_PORT, SUPPRESS_SYN
    target_host VARCHAR(45),
    target_port INTEGER,
    baseline_risk DOUBLE PRECISION NOT NULL,
    scenario_risk DOUBLE PRECISION NOT NULL,
    risk_reduction_percent DOUBLE PRECISION NOT NULL,
    explanation TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_whatif_forecast ON whatif_scenarios(parent_forecast_id);

-- 2. Analytical View: Forecast and Incident Correlation
CREATE OR REPLACE VIEW v_forecast_incident_summary AS
SELECT 
    f.id AS forecast_id,
    f.model_name,
    f.current_risk_score,
    f.max_forecasted_risk,
    f.risk_level AS forecast_severity,
    f.predicted_stage,
    f.confidence,
    f.created_at AS forecast_time,
    i.id AS incident_id,
    i.title AS incident_title,
    i.status AS incident_status,
    i.severity AS incident_severity
FROM forecast_runs f
LEFT JOIN incidents i ON i.forecast_run_id = f.id;

-- 3. Analytical View: Host Threat Ranking with Window Functions
CREATE OR REPLACE VIEW v_host_threat_ranking AS
SELECT 
    h.id AS host_id,
    h.ip_address,
    h.hostname,
    h.risk_score,
    h.risk_level,
    COUNT(t.id) AS observed_flow_count,
    COALESCE(SUM(t.total_fwd_bytes + t.total_bwd_bytes), 0) AS total_bytes_transferred,
    RANK() OVER (ORDER BY h.risk_score DESC) AS threat_rank
FROM hosts h
LEFT JOIN traffic_flows t ON (t.src_ip = h.ip_address OR t.dst_ip = h.ip_address)
GROUP BY h.id, h.ip_address, h.hostname, h.risk_score, h.risk_level;
