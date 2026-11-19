import json
import logging
import time
from pathlib import Path
from typing import Dict, Any, List

import numpy as np
import torch
from sklearn.metrics import (
    precision_score,
    recall_score,
    f1_score,
    average_precision_score,
    confusion_matrix,
)

from config import (
    MODEL_ARTIFACTS_DIR,
    NUM_STAGES,
    ATTACK_STAGES,
)
from dataset import (
    load_raw_dataset,
    build_temporal_dataset,
    chronological_train_val_test_split,
)
from models.tcn import NetraTemporalForecaster
from models.baseline import (
    DeterministicHeuristicBaseline,
    LogisticRegressionTemporalBaseline,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("netra.evaluate")


def compute_binary_metrics(y_true: np.ndarray, y_pred_prob: np.ndarray, threshold: float = 0.5) -> Dict[str, Any]:
    y_true_bin = (y_true.flatten() >= 0.5).astype(int)
    y_pred_bin = (y_pred_prob.flatten() >= threshold).astype(int)
    y_scores = y_pred_prob.flatten()

    unique_classes = np.unique(y_true_bin)
    has_both_classes = len(unique_classes) > 1

    prec = float(precision_score(y_true_bin, y_pred_bin, zero_division=0))
    rec = float(recall_score(y_true_bin, y_pred_bin, zero_division=0))
    f1 = float(f1_score(y_true_bin, y_pred_bin, zero_division=0))

    pr_auc = float(average_precision_score(y_true_bin, y_scores)) if has_both_classes else None

    cm = confusion_matrix(y_true_bin, y_pred_bin, labels=[0, 1])
    tn, fp, fn, tp = int(cm[0, 0]), int(cm[0, 1]), int(cm[1, 0]), int(cm[1, 1])
    fpr = float(fp / max(1, fp + tn))

    return {
        "precision": round(prec, 4),
        "recall": round(rec, 4),
        "f1_score": round(f1, 4),
        "pr_auc": round(pr_auc, 4) if pr_auc is not None else None,
        "fpr": round(fpr, 4),
        "confusion_matrix": {"tn": tn, "fp": fp, "fn": fn, "tp": tp},
        "sample_count": len(y_true_bin),
        "positive_count": int(y_true_bin.sum()),
        "negative_count": int(len(y_true_bin) - y_true_bin.sum()),
        "decision_threshold": threshold,
    }


def evaluate_models() -> Dict[str, Any]:
    checkpoint_path = MODEL_ARTIFACTS_DIR / "best_model.pt"
    if not checkpoint_path.exists():
        raise FileNotFoundError(f"Checkpoint not found at {checkpoint_path}. Train model first via train.py")

    logger.info(f"Loading checkpoint from: {checkpoint_path}")
    checkpoint = torch.load(checkpoint_path, map_location=torch.device("cpu"), weights_only=False)

    # 1. Prepare data splits
    df, provenance = load_raw_dataset()
    X, y_risk, y_stage, seq_meta = build_temporal_dataset(df)
    splits, split_meta = chronological_train_val_test_split(X, y_risk, y_stage)

    X_test = splits["X_test"]
    y_risk_test = splits["y_risk_test"]
    y_stage_test = splits["y_stage_test"]

    # 2. Evaluate PyTorch TCN Forecaster
    cfg = checkpoint["model_config"]
    model = NetraTemporalForecaster(
        num_features=cfg["num_features"],
        horizon_steps=cfg["horizon_steps"],
        num_stages=cfg["num_stages"],
        num_channels=cfg["num_channels"],
        kernel_size=cfg["kernel_size"],
        dropout=cfg["dropout"],
    )
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()

    X_test_t = torch.tensor(X_test, dtype=torch.float32)

    # Measure latency
    latencies = []
    with torch.no_grad():
        for _ in range(50):
            t0 = time.perf_counter()
            _ = model(X_test_t)
            latencies.append((time.perf_counter() - t0) * 1000.0)
    avg_latency_ms = round(float(np.mean(latencies)), 2)

    with torch.no_grad():
        risk_logits, stage_logits = model(X_test_t)
        tcn_risk_probs = torch.sigmoid(risk_logits).numpy()

    tcn_metrics = compute_binary_metrics(y_risk_test, tcn_risk_probs)
    tcn_metrics["inference_latency_ms"] = avg_latency_ms
    tcn_metrics["model_name"] = "PyTorch Temporal Convolutional Network (TCN)"
    tcn_metrics["model_version"] = cfg.get("version", "2.1.0-tcn")

    # 3. Evaluate Logistic Regression Baseline
    lr_baseline = LogisticRegressionTemporalBaseline()
    lr_baseline.fit(splits["X_train"], splits["y_risk_train"])

    t0 = time.perf_counter()
    lr_preds = lr_baseline.predict_proba(X_test)
    lr_latency_ms = round((time.perf_counter() - t0) * 1000.0, 2)

    lr_metrics = compute_binary_metrics(y_risk_test, lr_preds)
    lr_metrics["inference_latency_ms"] = lr_latency_ms
    lr_metrics["model_name"] = "Logistic Regression Temporal Baseline"

    # 4. Evaluate Deterministic Heuristic Baseline
    det_baseline = DeterministicHeuristicBaseline(horizon_steps=y_risk_test.shape[1])
    t0 = time.perf_counter()
    det_preds = det_baseline.predict_batch(X_test, horizon_steps=y_risk_test.shape[1])
    det_latency_ms = round((time.perf_counter() - t0) * 1000.0, 2)

    det_metrics = compute_binary_metrics(y_risk_test, det_preds)
    det_metrics["inference_latency_ms"] = det_latency_ms
    det_metrics["model_name"] = "Java Deterministic Rule-Based Baseline"

    comparison = {
        "evaluation_timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "dataset_provenance": provenance,
        "split_information": {
            "test_sequences": len(X_test),
            "total_horizon_evaluations": y_risk_test.size,
            "test_attack_ratio": float(y_risk_test.mean()),
        },
        "models": {
            "pytorch_tcn": tcn_metrics,
            "logistic_regression": lr_metrics,
            "deterministic_baseline": det_metrics,
        },
        "academic_disclosure": (
            "Evaluation performed strictly on held-out test data from chronologically ordered "
            "CIC-IDS network flow telemetry without overlapping-window leakage. "
            "Positive class denotes future horizon attack presence (label != BENIGN)."
        ),
    }

    metrics_path = MODEL_ARTIFACTS_DIR / "evaluation_metrics.json"
    with open(metrics_path, "w") as f:
        json.dump(comparison, f, indent=2)

    logger.info("=== EVALUATION COMPARISON SUMMARY ===")
    logger.info(f"PyTorch TCN:      F1={tcn_metrics['f1_score']}, PR-AUC={tcn_metrics['pr_auc']}, Latency={avg_latency_ms}ms")
    logger.info(f"Logistic Reg:     F1={lr_metrics['f1_score']}, PR-AUC={lr_metrics['pr_auc']}, Latency={lr_latency_ms}ms")
    logger.info(f"Deterministic:    F1={det_metrics['f1_score']}, PR-AUC={det_metrics['pr_auc']}, Latency={det_latency_ms}ms")
    logger.info(f"Saved evaluation metrics to: {metrics_path}")

    return comparison


if __name__ == "__main__":
    evaluate_models()
