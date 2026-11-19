# NETRA — Stage 5: Temporal Forecasting Model Architecture & Design

**Project:** SIH26153 — AI-Based Network Attack Forecasting from Network Traffic Data  
**Target Organization:** NTRO (National Technical Research Organisation)  
**Author:** NETRA Core Engineering & Applied ML Research  
**Date:** October 13, 2026  
**Status:** IMPLEMENTED AND TESTED  

---

## 1. Problem Formulation & Prediction Target Definition

Network attack forecasting requires predicting future threat events and risk trajectories *before* attacks reach peak impact or critical stages, rather than merely classifying past or current packets.

### 1.1 Unit of Prediction
- **Temporal Flow Quantum ($\tau$):** An aggregated network observation window spanning discrete packet/flow events.
- **Input Feature Vector $\mathbf{x}_t \in \mathbb{R}^{D}$ ($D = 15$):** Standardized statistical telemetry features calculated from network flows up to cutoff timestamp $t$.
- **Historical Sequence $\mathbf{X}_t \in \mathbb{R}^{W \times D}$ ($W \in [5, 12]$ steps):** The ordered chronological sequence of observation windows $[\mathbf{x}_{t - W + 1}, \dots, \mathbf{x}_t]$.

### 1.2 Target Definition
- **Future Attack Presence $y_h \in \{0, 1\}$:** Binary indicator whether malicious traffic ($\text{label} \neq \text{'BENIGN'}$) occurs at future horizon step $h \in \{1, 2, \dots, H\}$.
- **Prediction Horizons ($H = 6$ steps):** Projections at $+15\text{m}$, $+30\text{m}$, $+45\text{m}$, $+60\text{m}$, $+90\text{m}$, and $+120\text{m}$ into the future.
- **Future MITRE ATT&CK Stage $s \in \{0, 1, 2, 3, 4\}$:**
  - $0$: Normal Operations / Benign
  - $1$: Reconnaissance (PortScan, Network Probing)
  - $2$: Initial Access (Exploit Attempt, Infiltration)
  - $3$: Active Exploitation (DoS-SYN Flood, Brute Force, Socket Exhaustion)
  - $4$: Data Exfiltration (C2 Beaconing, Outbound Asymmetric Transfer)

---

## 2. Dataset Provenance & Preprocessing

### 2.1 Available Dataset Provenance
- **Dataset File:** `datasets/sample-cic-ids-attack.csv`
- **Origin & Schema:** Canadian Institute for Cybersecurity (CIC-IDS2017) flow record format.
- **Raw Row Count:** 16 verified flow records.
- **Threat Categories Present:**
  - `PortScan`: 7 flows (reconnaissance targeting ports 21, 22, 23, 80, 443, 445, 3389)
  - `DoS-SYN_Flood`: 5 flows (socket exhaustion targeting port 80)
  - `DataExfiltration`: 2 flows (outbound asymmetric transfer to external IP `198.51.100.88`)
  - `BENIGN`: 2 flows (normal DNS and HTTPS web browsing)
- **Academic Disclosure:** Real local training was conducted strictly on available non-fabricated telemetry records. No synthetic records were passed off as real-world dataset samples.

### 2.2 Feature Engineering (Standardized Schema)
Alinged with `com.netra.service.forecast.DefaultFeatureExtractor`:

| # | Feature Name | Description | Normalization / Bounds |
| :- | :--- | :--- | :--- |
| 1 | `flow_count` | Total flows in observation quantum | Count $\ge 0$ |
| 2 | `flow_rate` | Flows created per second | $\text{count} / \Delta t$ |
| 3 | `unique_src_ips` | Source IP diversity ratio | $\min(\text{src\_ips} / \text{flow\_count}, 1.0)$ |
| 4 | `unique_dst_ips` | Destination IP diversity ratio | $\min(\text{dst\_ips} / \text{flow\_count}, 1.0)$ |
| 5 | `unique_dst_ports` | Destination port dispersion (scanning proxy) | $\min(\text{dst\_ports} / 1000, 1.0)$ |
| 6 | `syn_ratio` | TCP SYN flag ratio | $\text{SYN} / (\text{SYN} + \text{ACK} + \text{RST} + 1)$ |
| 7 | `ack_ratio` | TCP ACK flag ratio | $\text{ACK} / (\text{SYN} + \text{ACK} + \text{RST} + 1)$ |
| 8 | `rst_ratio` | TCP RST flag ratio | $\text{RST} / (\text{SYN} + \text{ACK} + \text{RST} + 1)$ |
| 9 | `avg_packet_size_anomaly` | Jumbo/tiny packet anomaly indicator | $1.0 \text{ if } > 1400 \lor (< 40 \land > 0) \text{ else } 0.0$ |
| 10 | `iat_variance` | Inter-arrival time / duration variance | $\min(\sigma_{\text{duration}} / 100000, 1.0)$ |
| 11 | `fwd_bwd_imbalance` | Forward vs backward payload asymmetry | $\|B_{\text{fwd}} - B_{\text{bwd}}\| / (B_{\text{tot}} + 1)$ |
| 12 | `avg_duration_ms` | Mean flow duration in ms | Mean duration |
| 13 | `total_bytes` | Total payload transfer volume | Forward + backward bytes |
| 14 | `attack_label_ratio` | Observed threat label ratio | Ratio of attack flows |
| 15 | `risk_trend` | Gradient across observation halves | $\Delta \text{attack\_ratio}$ |

### 2.3 Chronological Splitting & Leakage Prevention
1. **Strict Monotonic Ordering:** Records sorted chronologically by timestamp ($t_1 \le t_2 \le \dots \le t_N$).
2. **Causal Sequence Slicing:** Sequence input $\mathbf{X}_i = [\mathbf{x}_{s}, \dots, \mathbf{x}_{s + W - 1}]$. Target $\mathbf{y}_i = [\text{attack}_{s + W}, \dots, \text{attack}_{s + W + H - 1}]$. No future features leak into input $\mathbf{X}_i$.
3. **Chronological Train/Val/Test Split:**
   - 60% Train, 20% Validation, 20% Held-Out Test.
4. **Isolated Normalization:** Feature mean $\boldsymbol{\mu}_{\text{train}}$ and standard deviation $\boldsymbol{\sigma}_{\text{train}}$ calculated strictly on training split and applied to val/test:
   $$\hat{\mathbf{x}} = \frac{\mathbf{x} - \boldsymbol{\mu}_{\text{train}}}{\boldsymbol{\sigma}_{\text{train}} + \epsilon}$$
5. **Class Imbalance Weighting:** Positive weight calculated strictly on training targets:
   $$w_{\text{pos}} = \frac{N_{\text{neg}}}{N_{\text{pos}}}$$

---

## 3. PyTorch Model Architecture (Temporal Convolutional Network)

A deep Temporal Convolutional Network (TCN) was selected over recurrent architectures (RNN/Vanilla LSTM) due to:
- **Strict Causality:** Guaranteed zero future lookahead via dilated causal 1D convolutions with Chomp layers.
- **Large Receptive Field:** Exponentially growing dilations ($d = 2^0, 2^1, 2^2$) allowing the model to capture long-range temporal trends without vanishing gradients.
- **Parallel Training:** Convolutional operations train significantly faster than sequential recurrent unrolling.

```
Input Sequence: (Batch, Seq_Len=5, Features=15)
                     │
                     ▼
         [Transpose to (Batch, 15, 5)]
                     │
                     ▼
       ┌───────────────────────────┐
       │     TemporalBlock 1       │
       │   Conv1d(15->32, d=1)     │  <── Chomp1d (causal padding strip)
       │   GELU + Dropout(0.2)     │
       │   Conv1d(32->32, d=1)     │
       │   Residual Conv1d(15->32) │
       └─────────────┬─────────────┘
                     │
                     ▼
       ┌───────────────────────────┐
       │     TemporalBlock 2       │
       │   Conv1d(32->64, d=2)     │  <── Chomp1d (causal padding strip)
       │   GELU + Dropout(0.2)     │
       │   Conv1d(64->64, d=2)     │
       │   Residual Conv1d(32->64) │
       └─────────────┬─────────────┘
                     │
                     ▼
       ┌───────────────────────────┐
       │     TemporalBlock 3       │
       │   Conv1d(64->64, d=4)     │  <── Chomp1d (causal padding strip)
       │   GELU + Dropout(0.2)     │
       │   Conv1d(64->64, d=4)     │
       │   Residual Skip (Identity)│
       └─────────────┬─────────────┘
                     │
                     ▼
       Latest Temporal Step Embedding: (Batch, 64)
              ┌──────┴──────┐
              ▼             ▼
       ┌─────────────┐ ┌─────────────┐
       │  Risk Head  │ │ Stage Head  │
       │ Linear(64)  │ │ Linear(64)  │
       │ Linear(H=6) │ │ Linear(C=5) │
       └─────────────┘ └─────────────┘
              │             │
              ▼             ▼
       Multi-Horizon   MITRE Stage
       Logits (6)      Logits (5)
```

### 3.1 Loss Function
Multi-task loss combining weighted binary cross-entropy for risk horizons and cross-entropy for MITRE stage:
$$\mathcal{L}_{\text{total}} = \text{BCEWithLogitsLoss}(\hat{\mathbf{y}}_{\text{risk}}, \mathbf{y}_{\text{risk}}; w_{\text{pos}}) + 0.5 \cdot \text{CrossEntropyLoss}(\hat{\mathbf{y}}_{\text{stage}}, y_{\text{stage}})$$

---

## 4. Honest Baselines Established

Two baselines were implemented and evaluated on the exact same chronological test split:

1. **Java Deterministic Rule-Based Baseline:**
   Weighted linear scoring formula based on domain expertise:
   $$\text{Risk}(t) = \frac{\sum w_i \cdot \phi_i(t)}{\sum w_i}$$
   Projected forward with temporal gradient extrapolation:
   $$\text{Risk}(t + h) = \text{Risk}(t) + (\text{risk\_trend} \cdot h \cdot 0.1)$$
2. **Logistic Regression Temporal Baseline:**
   Flattens the historical observation sequence $\mathbf{X}_t \in \mathbb{R}^{W \cdot D}$ and fits independent binary logistic regression models per horizon step with L2 regularization.

---

## 5. Microservice & Spring Boot Integration Architecture

```
┌────────────────────────────────────────────────────────┐
│               Frontend Command Centre / Lab             │
└───────────────────────────┬────────────────────────────┘
                            │ HTTP POST /api/forecast/run
                            ▼
┌────────────────────────────────────────────────────────┐
│           Spring Boot ForecastController               │
│                                                        │
│  resolveEngine(model):                                 │
│    ├── "pytorch-tcn" / "python-ml" / "tcn"            │
│    │     └──> PythonMLForecastEngine                  │
│    └── "deterministic"                                 │
│          └──> DeterministicForecastEngine              │
└──────────────┬──────────────────────────┬──────────────┘
               │                          │
               ▼                          ▼
┌──────────────────────────────┐ ┌───────────────────────────────┐
│   PythonMLForecastEngine     │ │  DeterministicForecastEngine  │
│  (NetworkForecastEngine SPI) │ │   (Deterministic Baseline)    │
└──────────────┬───────────────┘ └───────────────────────────────┘
               │ HTTP POST /predict (Timeout 3s)
               ▼
┌────────────────────────────────────────────────────────┐
│         FastAPI PyTorch ML Microservice (:8000)        │
│                                                        │
│  GET  /health   -> Status UP, Model Loaded, Device     │
│  GET  /metadata -> Features, Horizons, Metrics         │
│  POST /predict  -> TCN Inference + XAI Attributions    │
│                                                        │
│  Checkpoint: model-artifacts/best_model.pt (Epoch 10)  │
└────────────────────────────────────────────────────────┘
```

### 5.1 Failover & Truthful Provenance
- If the Python ML service is offline or times out:
  - When `netra.forecasting.fallback-to-deterministic: true`:
    - Automatically executes the deterministic fallback engine.
    - **Crucial Integrity Rule:** The resulting `ForecastRun` records `model_name: "deterministic-fallback (ML service offline)"`. It is *never* falsely labelled as a neural model output.
  - When fallback is disabled:
    - Throws `IllegalStateException` with descriptive diagnostic message.
