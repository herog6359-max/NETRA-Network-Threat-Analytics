# NETRA — Java Architecture & Academic DBMS Specification

---

## 1. Object-Oriented Programming (OOP) Principles Implementation

### A. Encapsulation & Domain Invariants
- **Private Fields & Strong Accessors:** All domain entities (`User`, `Incident`, `ForecastRun`, `TrafficFlow`, `Host`) enforce strict field privacy.
- **Entity State Machine:** `Incident.transitionTo(newStatus)` enforces lifecycle state invariants inside the domain entity rather than leaking state mutation logic into controllers.

### B. Abstraction & Pluggable Interfaces
- **`NetworkForecastEngine`:** Interface defining `ForecastRun forecast(...)`. Decouples forecasting algorithms so `DeterministicForecastEngine` can operate today and a future Python PyTorch TCN model can plug in tomorrow without changing controllers or UI.
- **`TrafficIngestionStrategy`:** Interface defining multi-format ingestion (`CsvIngestionStrategy`, `PcapIngestionStrategy`, `SyntheticTrafficGenerator`).
- **`TrafficFeatureExtractor`:** Interface defining mathematical feature calculation over collections of flows.

### C. Polymorphism & Strategy Pattern
- `TrafficIngestionStrategy` demonstrates runtime polymorphism: the system selects the appropriate parsing algorithm based on file MIME type / extension (`.csv`, `.pcap`, `.sim`).

### D. Composition over Inheritance
- `Host` has a relationship to `Network` (`@ManyToOne`).
- `ForecastRun` has-many `ForecastHorizon` records and has-many `FeatureAttribution` records (`@OneToMany`).
- `Incident` aggregates evidence objects, linked forecast runs, and assigned hosts.

---

## 2. Mermaid Architecture & Sequence Diagrams

### Diagram 1: Entity Relationship Diagram (ERD)
```mermaid
erDiagram
    NETWORKS ||--o{ HOSTS : contains
    HOSTS ||--o{ SERVICES : exposes
    HOSTS ||--o{ TRAFFIC_FLOWS : participates_in
    INGESTION_JOBS ||--o{ TRAFFIC_FLOWS : produces
    TRAFFIC_FLOWS ||--o{ NETWORK_STATES : aggregated_into
    FORECAST_RUNS ||--o{ FORECAST_HORIZONS : projects
    FORECAST_RUNS ||--o{ FEATURE_ATTRIBUTIONS : explains
    FORECAST_RUNS ||--o{ INCIDENTS : triggers
    INCIDENTS ||--o{ INCIDENT_STATUS_HISTORY : tracks_audit
    USERS ||--o{ USER_ROLES : assigned
    ROLES ||--o{ USER_ROLES : defines
```

### Diagram 2: Traffic Ingestion Sequence
```mermaid
sequenceDiagram
    autonumber
    actor Analyst as Security Analyst
    participant API as IngestionController
    participant Strategy as CsvIngestionStrategy
    participant Repo as TrafficFlowRepository
    participant DB as PostgreSQL Database

    Analyst->>API: POST /api/ingestion/upload (CSV file)
    API->>Strategy: ingest(InputStream, filename, job)
    Strategy->>Strategy: parseHeader & normalize column aliases
    loop For each CSV row
        Strategy->>Strategy: parseLine & validate IP / port / timestamp
        alt Valid Flow
            Strategy->>Strategy: Add to batch buffer
        else Invalid / Corrupt Row
            Strategy->>Strategy: Record row error & increment rejectedRows
        end
        opt Batch size >= 500
            Strategy->>Repo: saveAll(batch)
            Repo->>DB: Batch INSERT into traffic_flows
        end
    end
    Strategy->>API: Return IngestionJob summary (valid vs rejected)
    API->>Analyst: 200 OK (Job Summary)
```

### Diagram 3: Forecast Generation & Automated Escalation Sequence
```mermaid
sequenceDiagram
    autonumber
    actor SOC as SOC Operator
    participant Controller as ForecastController
    participant Engine as DeterministicForecastEngine
    participant Extractor as DefaultFeatureExtractor
    participant HorizonRepo as ForecastHorizonRepository
    participant IncidentRepo as IncidentRepository
    participant DB as PostgreSQL Database

    SOC->>Controller: POST /api/forecast/run (windowHours, horizonSteps)
    Controller->>Engine: forecast(windowStart, windowEnd, horizonSteps)
    Engine->>DB: Query flows in time window
    Engine->>Extractor: extractFeatures(flows)
    Extractor-->>Engine: Feature vector (syn_ratio, ports, flow_rate)
    Engine->>Engine: computeRiskScore & project horizons
    Engine->>DB: Save ForecastRun
    Engine->>HorizonRepo: saveAll(ForecastHorizons)
    Engine->>DB: saveAll(FeatureAttributions)
    Engine-->>Controller: Return ForecastRun
    alt Peak Forecasted Risk >= 60%
        Controller->>IncidentRepo: save(new Incident)
        IncidentRepo->>DB: INSERT into incidents (Status: NEW)
    end
    Controller->>SOC: 200 OK (ForecastRunResponse with Horizons)
```

### Diagram 4: What-If Defensive Counterfactual Simulation Sequence
```mermaid
sequenceDiagram
    autonumber
    actor Analyst as Threat Analyst
    participant API as DigitalTwinController
    participant Twin as DigitalTwinService
    participant Extractor as TrafficFeatureExtractor

    Analyst->>API: POST /api/twin/what-if (Intervention: ISOLATE_HOST, Target: 10.0.0.15)
    API->>Twin: simulateWhatIfScenario(request)
    Twin->>Twin: Fetch baseline historical flows
    Twin->>Extractor: extractFeatures(baselineFlows)
    Twin->>Twin: Apply hypothetical intervention (filter out targetHost flows)
    Twin->>Extractor: extractFeatures(scenarioFlows)
    Twin->>Twin: Calculate risk reduction delta & comparison curve
    Twin-->>API: WhatIfScenarioResponse (Curves, Feature Diff, Explanation)
    API->>Analyst: 200 OK (Baseline vs Scenario Comparison)
```

### Diagram 5: Atomic Incident State Transition
```mermaid
sequenceDiagram
    autonumber
    actor Lead as SOC Lead
    participant API as IncidentController
    participant Entity as Incident (Domain Entity)
    participant HistRepo as IncidentStatusHistoryRepository
    participant DB as PostgreSQL Database

    Lead->>API: POST /api/incidents/1/transition { newStatus: "INVESTIGATING" }
    Note over API: @Transactional starts ACID boundary
    API->>Entity: transitionTo(INVESTIGATING)
    alt Legal Transition (e.g. ACKNOWLEDGED -> INVESTIGATING)
        Entity-->>API: Validated state updated
        API->>DB: UPDATE incidents SET status = 'INVESTIGATING'
        API->>HistRepo: save(IncidentStatusHistory)
        HistRepo->>DB: INSERT into incident_status_history
        Note over API: Transaction COMMITS atomically
        API->>Lead: 200 OK (Updated Incident & Audit History)
    else Illegal Transition (e.g. NEW -> CLOSED)
        Entity-->>API: throws IllegalStateException
        Note over API: Transaction ROLLS BACK entirely
        API->>Lead: 400 Bad Request (Cannot transition from NEW to CLOSED)
    end
```
