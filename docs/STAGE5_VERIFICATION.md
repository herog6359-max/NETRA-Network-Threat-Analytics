# NETRA — Stage 5 Verification & End-to-End Audit Record

**Project:** SIH26153 — AI-Based Network Attack Forecasting from Network Traffic Data  
**Target Organization:** NTRO (National Technical Research Organisation)  
**Execution Date:** October 13, 2026  
**Status:** COMPLETED AND EMPIRICALLY VERIFIED  

---

## 1. Executive Summary

Stage 5 successfully established a genuine, reproducible temporal deep-learning forecasting pipeline, trained a PyTorch Temporal Convolutional Network (TCN), and integrated it into the Spring Boot backend through the pluggable `NetworkForecastEngine` SPI.

All components were verified using empirical test suites, database persistence audits, and live HTTP API queries.

---

## 2. Phase-by-Phase Verification Matrix

| Phase | Description | Implemented Components | Verification Evidence | Status |
| :--- | :--- | :--- | :--- | :---: |
| **Phase 1** | Target Definition & Inspection | `config.py`, inspection of `ml-service/`, dataset audits | Documented in `STAGE5_MODEL_DESIGN.md` | ✅ **VERIFIED** |
| **Phase 2** | Dataset Preparation & Leakage Prevention | `dataset.py` with causal slicing and chronological train/val/test split | Provenance recorded in `preprocessing_metadata.json` | ✅ **VERIFIED** |
| **Phase 3** | Honest Baselines | `models/baseline.py`: Deterministic Heuristic + Logistic Regression | Evaluated on exact same test split | ✅ **VERIFIED** |
| **Phase 4** | Temporal Model Training | `models/tcn.py`, `train.py` (PyTorch 2.14.1+cpu) | Checkpoint saved: `best_model.pt` (Epoch 10) | ✅ **VERIFIED** |
| **Phase 5** | Empirical Evaluation | `evaluate.py` (Precision, Recall, F1, PR-AUC, Latency) | Metrics saved in `evaluation_metrics.json` | ✅ **VERIFIED** |
| **Phase 6** | FastAPI Inference Microservice | `main.py` (:8000) with `/health`, `/ready`, `/metadata`, `/predict` | HTTP 200 OK from `/health` and `/metadata` | ✅ **VERIFIED** |
| **Phase 7** | Spring Boot Engine Integration | `PythonMLForecastEngine.java`, `ForecastController.java` | Live forecast run #3 persisted to PostgreSQL | ✅ **VERIFIED** |
| **Phase 8** | Full Regression Suite | Pytest (8/8), JUnit (15/15), Vite build (1907 modules) | Zero errors across all test harnesses | ✅ **VERIFIED** |

---

## 3. Empirical Test Suite Outcomes

### 3.1 Python ML Test Suite (`pytest`)
- **Command:** `ml-service/.venv/Scripts/pytest.exe ml-service/tests/test_ml_service.py`
- **Output:** `8 passed in 5.77s`
- **Tests Executed:**
  1. `test_dataset_loading_and_provenance` — PASSED
  2. `test_no_temporal_leakage_in_sequences` — PASSED
  3. `test_chronological_splits_and_normalization` — PASSED
  4. `test_tcn_forward_pass_dimensions` — PASSED
  5. `test_deterministic_baseline_invariance` — PASSED
  6. `test_fastapi_health_endpoint` — PASSED
  7. `test_fastapi_predict_endpoint_valid_payload` — PASSED
  8. `test_fastapi_predict_invalid_schema` — PASSED

### 3.2 Java Backend Test Suite (`JUnit 5`)
- **Command:** `mvnw.cmd test`
- **Output:** `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`
- **Tests Executed:**
  1. `IncidentEntityTest` (2 tests) — PASSED
  2. `DigitalTwinServiceTest` (4 tests) — PASSED
  3. `DefaultFeatureExtractorTest` (2 tests) — PASSED
  4. `DeterministicForecastEngineTest` (2 tests) — PASSED
  5. `PythonMLForecastEngineTest` (3 tests) — PASSED
  6. `CsvIngestionStrategyTest` (2 tests) — PASSED

### 3.3 Frontend Production Build (`TypeScript & Vite`)
- **Command:** `npm.cmd run build` (in `frontend/`)
- **Output:** `✓ built in 1.29s (1907 modules transformed, 0 TypeScript errors)`

---

## 4. End-to-End Live Workflow Verification

### 4.1 FastAPI Service Health Probe
```powershell
curl http://localhost:8000/health
```
```json
{
  "status": "UP",
  "service": "NETRA PyTorch ML Service",
  "model_loaded": true,
  "model_version": "2.1.0-tcn",
  "device": "cpu",
  "uptime_seconds": 33.1
}
```

### 4.2 Spring Boot Unified Health Probe
```powershell
curl http://localhost:8080/api/health
```
```json
{
  "application": "NETRA",
  "status": "UP",
  "database": {
    "engine": "PostgreSQL",
    "status": "UP",
    "state_label": "CONNECTED",
    "storage_mode": "POSTGRESQL_DURABLE"
  },
  "ml_service": {
    "service": "FastAPI PyTorch ML Service",
    "configured_url": "http://localhost:8000",
    "status": "UP",
    "http_code": 200
  }
}
```

### 4.3 Pluggable Engine Discovery Probe
```powershell
curl http://localhost:8080/api/forecast/engines
```
```json
[
  {
    "id": "python-ml",
    "name": "pytorch-tcn",
    "version": "2.1.0-tcn",
    "active": true,
    "available": true,
    "description": "PyTorch Temporal Convolutional Network (TCN) multi-horizon neural forecaster"
  },
  {
    "id": "deterministic",
    "name": "deterministic-baseline",
    "version": "1.0.0",
    "active": false,
    "available": true,
    "description": "Deterministic Rule-Based Statistical Baseline"
  }
]
```

### 4.4 PostgreSQL Relational Persistence Verification
```sql
SELECT id, model_name, model_version, max_forecasted_risk, created_at 
FROM forecast_runs 
ORDER BY id DESC LIMIT 1;
```
```
 id |                  model_name                  | model_version | max_forecasted_risk |            created_at            
----+----------------------------------------------+---------------+---------------------+----------------------------------
  3 | PyTorch Temporal Convolutional Network (TCN) | 2.1.0-tcn     |               0.792 | 2026-10-13 09:45:35.208709+05:30
```

```sql
SELECT count(*) FROM forecast_horizons WHERE forecast_run_id = 3;
-- 6 rows (+15m to +120m persisted)

SELECT count(*) FROM feature_attributions WHERE forecast_run_id = 3;
-- 5 rows (XAI feature contributions persisted)

SELECT id, title, severity, status, forecast_run_id FROM incidents WHERE forecast_run_id = 3;
-- 1 row (Incident #2 automatically escalated from predicted threat threshold breach)
```

---

## 5. Summary of System State

- **Authoritative Database:** PostgreSQL 16.6 on port 5432 (`POSTGRESQL_DURABLE`)
- **Backend Application:** Spring Boot 3.4.1 on port 8080 (`UP`)
- **Neural Forecaster Microservice:** FastAPI / PyTorch 2.14.1 on port 8000 (`UP`)
- **Frontend Dashboard:** React 19 + TypeScript on port 5173 (`UP`)
- **Forecasting Mode:** Pluggable Strategy Pattern with live PyTorch TCN active and truthful deterministic fallback.
