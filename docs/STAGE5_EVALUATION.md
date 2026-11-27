# NETRA — Stage 5: Empirical Model Evaluation & Baseline Comparison

**Project:** SIH26153 — AI-Based Network Attack Forecasting from Network Traffic Data  
**Target Organization:** NTRO (National Technical Research Organisation)  
**Artifact status:** Historical results from the bundled run. The current training code requires a gap between splits and rejects the 16-row sample as too small for independent evaluation.
**Evaluation Date:** October 13, 2026  
**Artifact Source:** `model-artifacts/evaluation_metrics.json`  

---

## 1. Executive Summary

Empirical evaluation was conducted on held-out test data from the locally available CIC-IDS2017 network flow telemetry (`sample-cic-ids-attack.csv`).

The trained **PyTorch Temporal Convolutional Network (TCN)** was evaluated directly against:
1. **Logistic Regression Temporal Baseline** (standard statistical baseline)
2. **Deterministic Heuristic Baseline** (rule-based domain model from Stage 2)

All models were evaluated on the **exact same held-out test sequences** with identical decision thresholds ($\theta = 0.50$) and positive class definition.

---

## 2. Quantitative Performance Comparison

| Metric | PyTorch TCN Forecaster | Logistic Regression Baseline | Java Deterministic Baseline | Note / Interpretation |
| :--- | :---: | :---: | :---: | :--- |
| **Model Name** | `NetraTemporalForecaster` | `LogisticRegression` | `DeterministicForecastEngine` | Active engine implementations |
| **Model Version** | `2.1.0-tcn` | `1.0.0-lr` | `1.0.0` | Model identifiers |
| **Precision** | **0.7000** | 0.7000 | 0.0000 | $\text{TP} / (\text{TP} + \text{FP})$ at $\theta = 0.50$ |
| **Recall (Sensitivity)**| **1.0000** | 1.0000 | 0.0000 | $\text{TP} / (\text{TP} + \text{FN})$ — zero false negatives |
| **F1-Score** | **0.8235** | 0.8235 | 0.0000 | Harmonic mean of precision & recall |
| **PR-AUC (Avg Precision)**| **0.8917** | 0.7000 | 0.7000 | 0.1917 higher average precision on this sample |
| **False Positive Rate** | 1.0000 | 1.0000 | 0.0000 | $\text{FP} / (\text{FP} + \text{TN})$ on small held-out split |
| **Inference Latency** | **2.68 ms** | 0.01 ms | 0.06 ms | Local inference timing |
| **Hardware Device** | CPU (Intel/AMD x86_64) | CPU | CPU | Graceful CPU execution verified |

---

## 3. Detailed Confusion Matrices (Held-Out Test Split)

### 3.1 PyTorch Temporal Convolutional Network (TCN)
$$\begin{pmatrix} \text{TN} = 0 & \text{FP} = 3 \\ \text{FN} = 0 & \text{TP} = 7 \end{pmatrix}$$
- **Total Test Horizon Evaluations:** 10
- **Actual Positive Attack Events:** 7 (PortScan, DoS-SYN Flood, Data Exfiltration)
- **Actual Benign Events:** 3
- **True Positives Detected:** 7 / 7 (100% recall of actual attack events)
- **False Negatives:** 0
- **False Positives:** 3 (all benign horizon outcomes)

### 3.2 Logistic Regression Temporal Baseline
$$\begin{pmatrix} \text{TN} = 0 & \text{FP} = 3 \\ \text{FN} = 0 & \text{TP} = 7 \end{pmatrix}$$
- Linear boundary achieves equivalent point classification at $\theta = 0.5$, but lower PR-AUC ($0.7000$ vs $0.8917$) on this sample.

### 3.3 Java Deterministic Baseline
$$\begin{pmatrix} \text{TN} = 3 & \text{FP} = 0 \\ \text{FN} = 7 & \text{TP} = 0 \end{pmatrix}$$
- The deterministic baseline with default weights stayed below the binary escalation threshold ($\theta = 0.50$) for these test sequences, resulting in zero false positives but failing to trigger predictive alerts without threshold re-tuning.

---

## 4. Interpretation and limits

The dataset has 16 raw flows. The two held-out sequences yielded ten scored horizon outcomes. At the 0.50 threshold the TCN and logistic regression both flagged every outcome. This produced seven true positives, three false positives, and zero true negatives for each model.

Average precision was 0.8917 for the TCN and 0.7000 for each baseline in this run. Average precision measures ranking. Assessing probability calibration would require a separate evaluation. Horizons from the same sequence are related, and this small sample cannot establish performance on other networks.

The recorded TCN inference time averaged 2.68 ms on the evaluation CPU. End-to-end ingestion and alert delivery require their own timing measurements.
