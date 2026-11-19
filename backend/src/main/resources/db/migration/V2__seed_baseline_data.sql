-- ============================================================================
-- NETRA Flyway Migration: V2__seed_baseline_data.sql
-- Baseline reference data: Roles, Users, MITRE ATT&CK Stages, Model Definitions, Detection Rules
-- ============================================================================

-- Roles
INSERT INTO roles (name, description) VALUES
    ('ROLE_ADMIN', 'System Administrator with full access to configuration, models, and sensor telemetry'),
    ('ROLE_ANALYST', 'Security Analyst with access to dashboards, forecasts, incident triage, and queries'),
    ('ROLE_OPERATOR', 'SOC Operator for incident monitoring and alert response'),
    ('ROLE_VIEWER', 'Read-only viewer for executive reporting and audit review')
ON CONFLICT (name) DO NOTHING;

-- Default Users (passwords hashed with BCrypt, cost factor 10)
-- admin / netra2026: $2a$10$p1W0hY8aR5Y937gM1iV04Omv8x6xY25GqYkYnK1J3pWd9Yg1lG6bK (sample)
-- We will insert default accounts; Java SecurityConfig also provides in-memory fallback
INSERT INTO users (username, password_hash, display_name, email, enabled) VALUES
    ('admin', '$2a$10$rCg5Y4u5G6zY6v0g1j7X0eIqH2z1m5w4v5y6z7a8b9c0d1e2f3g4h', 'NETRA Administrator', 'admin@netra.soc', true),
    ('analyst', '$2a$10$rCg5Y4u5G6zY6v0g1j7X0eIqH2z1m5w4v5y6z7a8b9c0d1e2f3g4h', 'Senior Threat Analyst', 'analyst@netra.soc', true),
    ('operator', '$2a$10$rCg5Y4u5G6zY6v0g1j7X0eIqH2z1m5w4v5y6z7a8b9c0d1e2f3g4h', 'Tier-1 SOC Operator', 'operator@netra.soc', true)
ON CONFLICT (username) DO NOTHING;

-- MITRE ATT&CK Attack Stage Mappings
INSERT INTO attack_stage_mappings (stage_name, mitre_tactic_id, mitre_tactic_name, progression_order, severity_baseline) VALUES
    ('Reconnaissance', 'TA0043', 'Reconnaissance', 1, 'LOW'),
    ('Initial Access', 'TA0001', 'Initial Access', 2, 'MEDIUM'),
    ('Execution', 'TA0002', 'Execution', 3, 'HIGH'),
    ('Persistence', 'TA0003', 'Persistence', 4, 'HIGH'),
    ('Privilege Escalation', 'TA0004', 'Privilege Escalation', 5, 'HIGH'),
    ('Defense Evasion', 'TA0005', 'Defense Evasion', 6, 'MEDIUM'),
    ('Credential Access', 'TA0006', 'Credential Access', 7, 'HIGH'),
    ('Discovery', 'TA0007', 'Discovery', 8, 'MEDIUM'),
    ('Lateral Movement', 'TA0008', 'Lateral Movement', 9, 'CRITICAL'),
    ('Collection', 'TA0009', 'Collection', 10, 'HIGH'),
    ('Command and Control', 'TA0011', 'Command and Control', 11, 'CRITICAL'),
    ('Exfiltration', 'TA0010', 'Exfiltration', 12, 'CRITICAL'),
    ('Impact', 'TA0040', 'Impact', 13, 'CRITICAL')
ON CONFLICT (stage_name) DO NOTHING;

-- Default Model Definitions
INSERT INTO model_definitions (name, model_family, description, input_dim, output_dim) VALUES
    ('NETRA-TCN-Forecaster-v1', 'TCN', 'Temporal Convolutional Network with dilated causal convolutions for multi-step attack state forecasting', 32, 12),
    ('NETRA-Deterministic-Baseline', 'DETERMINISTIC', 'Statistical rate-of-change and sliding-window heuristic baseline forecaster', 16, 6)
ON CONFLICT (name) DO NOTHING;

-- Baseline Detection Rules
INSERT INTO detection_rules (name, severity, condition_sql, condition_json, is_enabled) VALUES
    ('SYN Flood Anomaly', 'HIGH', 'syn_flag_count > 500 AND ack_flag_count < 50', '{"threshold": 500, "metric": "syn_flag_count"}', true),
    ('Port Scan Burst Pattern', 'MEDIUM', 'count(distinct dst_port) > 50', '{"threshold": 50, "metric": "distinct_ports"}', true),
    ('Data Exfiltration Volume Surge', 'CRITICAL', 'total_fwd_bytes > 50000000 AND fwd_bwd_ratio > 10.0', '{"threshold_bytes": 50000000}', true)
ON CONFLICT (name) DO NOTHING;

-- Default Network
INSERT INTO networks (name, cidr, description) VALUES
    ('Enterprise Core Security Zone', '10.0.0.0/16', 'Primary enterprise subnet housing core application and database servers'),
    ('DMZ Edge Perimeter', '192.168.10.0/24', 'External-facing boundary with ingress and egress inspection points')
ON CONFLICT DO NOTHING;
