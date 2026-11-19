import json
import logging
from pathlib import Path
from typing import Dict, List, Tuple, Any, Optional

import numpy as np
import pandas as pd

from config import (
    FEATURE_NAMES,
    NUM_FEATURES,
    WINDOW_SIZE,
    HORIZON_STEPS,
    ATTACK_STAGES,
    MODEL_ARTIFACTS_DIR,
    AVAILABLE_DATASET_PATH,
)

logger = logging.getLogger("netra.dataset")
logging.basicConfig(level=logging.INFO)


def load_raw_dataset(csv_path: Optional[Path] = None) -> Tuple[pd.DataFrame, Dict[str, Any]]:
    """
    Loads network flow dataset from CSV with schema validation,
    chronological sorting, and provenance tracking.
    """
    target_path = csv_path or AVAILABLE_DATASET_PATH
    if not target_path.exists():
        raise FileNotFoundError(f"Dataset file not found at: {target_path}")

    logger.info(f"Loading dataset from: {target_path}")
    df = pd.read_csv(target_path)

    # Standardize column names
    df.columns = [col.strip().lower() for col in df.columns]

    provenance = {
        "source_file": str(target_path.name),
        "source_path": str(target_path.resolve()),
        "raw_row_count": len(df),
        "raw_columns": list(df.columns),
    }

    # Expected column mapping / aliases
    col_aliases = {
        "src ip": "src_ip",
        "source ip": "src_ip",
        "dst ip": "dst_ip",
        "destination ip": "dst_ip",
        "src port": "src_port",
        "source port": "src_port",
        "dst port": "dst_port",
        "destination port": "dst_port",
    }
    df = df.rename(columns=col_aliases)

    # Parse and sort by timestamp
    if "timestamp" in df.columns:
        df["timestamp"] = pd.to_datetime(df["timestamp"], errors="coerce")
        null_ts = df["timestamp"].isna().sum()
        if null_ts > 0:
            logger.warning(f"Dropping {null_ts} rows with invalid timestamps")
            df = df.dropna(subset=["timestamp"])
        df = df.sort_values(by="timestamp").reset_index(drop=True)
    else:
        raise ValueError("Training/evaluation requires capture timestamps")

    # Clean missing values in numeric fields
    numeric_cols = df.select_dtypes(include=[np.number]).columns
    df[numeric_cols] = df[numeric_cols].fillna(0.0)

    # Standardize label
    if "label" not in df.columns:
        raise ValueError("Training/evaluation requires explicit ground-truth labels")
    df["label"] = df["label"].astype(str).str.strip()

    # Track label distribution in provenance
    label_dist = df["label"].value_counts().to_dict()
    provenance["clean_row_count"] = len(df)
    provenance["label_distribution"] = label_dist
    provenance["is_attack_count"] = int((df["label"] != "BENIGN").sum())
    provenance["benign_count"] = int((df["label"] == "BENIGN").sum())

    logger.info(
        f"Loaded {len(df)} cleaned rows. Labels: {label_dist}"
    )
    return df, provenance


def extract_features_from_flows(flows_df: pd.DataFrame) -> Dict[str, float]:
    """
    Extracts numerical feature vector from a collection of flows in a time window.
    Strictly aligns with com.netra.service.forecast.DefaultFeatureExtractor.
    """
    if flows_df.empty:
        return {f: 0.0 for f in FEATURE_NAMES}

    flow_count = float(len(flows_df))
    features = {"flow_count": flow_count}

    # Time span & flow rate
    if "timestamp" in flows_df.columns and len(flows_df) >= 2:
        ts_min = flows_df["timestamp"].min()
        ts_max = flows_df["timestamp"].max()
        duration_s = max((ts_max - ts_min).total_seconds(), 1.0)
    else:
        duration_s = 1.0
    features["flow_rate"] = flow_count / duration_s

    # Unique host ratios (normalized by flow count)
    src_ips = flows_df["src_ip"].nunique() if "src_ip" in flows_df.columns else 1
    dst_ips = flows_df["dst_ip"].nunique() if "dst_ip" in flows_df.columns else 1
    features["unique_src_ips"] = min(float(src_ips) / max(flow_count, 1.0), 1.0)
    features["unique_dst_ips"] = min(float(dst_ips) / max(flow_count, 1.0), 1.0)

    # Destination port diversity (normalized by 1000)
    dst_ports = flows_df["dst_port"].nunique() if "dst_port" in flows_df.columns else 1
    features["unique_dst_ports"] = min(float(dst_ports) / 1000.0, 1.0)

    # TCP flag ratios
    syn = float(flows_df["syn_flag_count"].sum()) if "syn_flag_count" in flows_df.columns else 0.0
    ack = float(flows_df["ack_flag_count"].sum()) if "ack_flag_count" in flows_df.columns else 0.0
    rst = float(flows_df["rst_flag_count"].sum()) if "rst_flag_count" in flows_df.columns else 0.0
    total_flags = syn + ack + rst + 1.0
    features["syn_ratio"] = syn / total_flags
    features["ack_ratio"] = ack / total_flags
    features["rst_ratio"] = rst / total_flags

    # Packet size anomaly (indicator for jumbo/tiny anomalous sizes)
    avg_pkt_size = float(flows_df["average_packet_size"].mean()) if "average_packet_size" in flows_df.columns else 0.0
    features["avg_packet_size_anomaly"] = 1.0 if (avg_pkt_size > 1400 or (avg_pkt_size < 40 and avg_pkt_size > 0)) else 0.0

    # Inter-arrival time / duration variance
    durations = flows_df["flow_duration"].astype(float) if "flow_duration" in flows_df.columns else pd.Series([0.0])
    iat_var = float(durations.std()) if len(durations) > 1 else 0.0
    features["iat_variance"] = min(iat_var / 100000.0, 1.0)

    # Forward / backward byte imbalance
    fwd_bytes = float(flows_df["total_fwd_bytes"].sum()) if "total_fwd_bytes" in flows_df.columns else 0.0
    bwd_bytes = float(flows_df["total_bwd_bytes"].sum()) if "total_bwd_bytes" in flows_df.columns else 0.0
    tot_bytes = fwd_bytes + bwd_bytes + 1.0
    features["fwd_bwd_imbalance"] = abs(fwd_bytes - bwd_bytes) / tot_bytes
    features["total_bytes"] = tot_bytes

    # Average duration
    features["avg_duration_ms"] = float(durations.mean())

    # Packet rate (packets per second) — label-free telemetry feature
    fwd_pkts = float(flows_df["total_fwd_packets"].sum()) if "total_fwd_packets" in flows_df.columns else 0.0
    bwd_pkts = float(flows_df["total_bwd_packets"].sum()) if "total_bwd_packets" in flows_df.columns else 0.0
    total_pkts = fwd_pkts + bwd_pkts if (fwd_pkts + bwd_pkts > 0) else flow_count
    features["packet_rate"] = total_pkts / duration_s

    # Risk trend — label-free rate of change in SYN activity across window
    if len(flows_df) >= 4 and "syn_flag_count" in flows_df.columns:
        half = len(flows_df) // 2
        s1 = float(flows_df.iloc[:half]["syn_flag_count"].sum())
        s2 = float(flows_df.iloc[half:]["syn_flag_count"].sum())
        features["risk_trend"] = float((s2 - s1) / max(half, 1))
    else:
        features["risk_trend"] = 0.1 if features.get("syn_ratio", 0.0) > 0.3 else -0.05

    return features


def map_label_to_stage(label_str: str) -> int:
    """Maps threat label string to MITRE ATT&CK stage index."""
    lbl = str(label_str).upper()
    if "PORT" in lbl or "SCAN" in lbl:
        return 1  # Reconnaissance
    elif "ACCESS" in lbl or "INFILTRATION" in lbl:
        return 2  # Initial Access
    elif "DOS" in lbl or "SYN" in lbl or "EXPLOIT" in lbl or "BOT" in lbl:
        return 3  # Active Exploitation
    elif "EXFIL" in lbl or "LEAK" in lbl:
        return 4  # Data Exfiltration
    return 0      # Normal Operations / BENIGN


def build_temporal_dataset(
    df: pd.DataFrame,
    window_size: int = WINDOW_SIZE,
    horizon_steps: int = HORIZON_STEPS,
    step_quantum: int = 1,
) -> Tuple[np.ndarray, np.ndarray, np.ndarray, Dict[str, Any]]:
    """
    Constructs chronologically ordered temporal sequences without lookahead leakage.
    Each sample i has:
      X[i] shape: (window_size, num_features)  -- historical observation window [t - W + 1 ... t]
      y_risk[i] shape: (horizon_steps,)        -- future attack probability at steps [t + 1 ... t + H]
      y_stage[i] shape: (num_stages,)          -- future attack stage one-hot
    """
    total_rows = len(df)
    min_required = window_size + horizon_steps
    if total_rows < min_required:
        # Transparently handle smaller datasets by adjusting window size gracefully to yield multiple sequences
        actual_window = min(window_size, max(4, total_rows // 3))
        actual_horizon = min(horizon_steps, max(2, (total_rows - actual_window) // 2))
        logger.warning(
            f"Dataset has {total_rows} rows < {min_required}. "
            f"Adapting window_size={actual_window}, horizon_steps={actual_horizon}"
        )
        window_size = actual_window
        horizon_steps = actual_horizon

    # Step 1: Precompute per-step feature vectors and labels
    step_features: List[List[float]] = []
    step_is_attack: List[float] = []
    step_stages: List[int] = []

    for i in range(0, total_rows):
        sub_df = df.iloc[max(0, i - 1): i + 1]
        feat_dict = extract_features_from_flows(sub_df)
        vec = [float(feat_dict.get(fname, 0.0)) for fname in FEATURE_NAMES]
        step_features.append(vec)

        row_lbl = str(df.iloc[i].get("label", "BENIGN"))
        is_attk = 1.0 if row_lbl != "BENIGN" else 0.0
        step_is_attack.append(is_attk)
        step_stages.append(map_label_to_stage(row_lbl))

    step_feat_arr = np.array(step_features, dtype=np.float32)
    step_attk_arr = np.array(step_is_attack, dtype=np.float32)
    step_stage_arr = np.array(step_stages, dtype=np.int64)

    # Step 2: Construct non-overlapping sliding windows
    X_list, y_risk_list, y_stage_list = [], [], []

    max_start = total_rows - window_size - horizon_steps + 1
    for start_idx in range(0, max_start):
        cutoff_idx = start_idx + window_size
        target_end_idx = cutoff_idx + horizon_steps

        # Strictly historical input: [start_idx : cutoff_idx]
        X_seq = step_feat_arr[start_idx:cutoff_idx]

        # Strictly future targets: [cutoff_idx : target_end_idx]
        future_attacks = step_attk_arr[cutoff_idx:target_end_idx]

        # Stage in future window: highest severity MITRE stage reached
        future_stage_idx = int(step_stage_arr[cutoff_idx:target_end_idx].max())
        stage_onehot = np.zeros(len(ATTACK_STAGES), dtype=np.float32)
        stage_onehot[future_stage_idx] = 1.0

        X_list.append(X_seq)
        y_risk_list.append(future_attacks)
        y_stage_list.append(stage_onehot)

    X = np.array(X_list, dtype=np.float32)
    y_risk = np.array(y_risk_list, dtype=np.float32)
    y_stage = np.array(y_stage_list, dtype=np.float32)

    metadata = {
        "sequence_count": len(X),
        "window_size": window_size,
        "horizon_steps": horizon_steps,
        "num_features": len(FEATURE_NAMES),
        "attack_sequence_ratio": float((y_risk.sum(axis=1) > 0).mean()) if len(y_risk) > 0 else 0.0,
    }
    logger.info(f"Generated {len(X)} temporal sequences. X shape: {X.shape}, y_risk shape: {y_risk.shape}")
    return X, y_risk, y_stage, metadata


def chronological_train_val_test_split(
    X: np.ndarray,
    y_risk: np.ndarray,
    y_stage: np.ndarray,
    train_ratio: float = 0.60,
    val_ratio: float = 0.20,
) -> Tuple[Dict[str, np.ndarray], Dict[str, Any]]:
    """
    Splits sequences strictly chronologically to prevent temporal data leakage.
    Normalizes features using training split statistics only.
    """
    n = len(X)
    # Each sample consumes W+H raw rows plus one preceding feature row.
    # Purge adjacent samples so no raw observation crosses a split boundary.
    gap = X.shape[1] + y_risk.shape[1]
    train_end = int(n * train_ratio)
    val_end = int(n * (train_ratio + val_ratio))
    val_start = train_end + gap
    test_start = val_end + gap
    if train_end < 1 or val_start >= val_end or test_start >= n:
        raise ValueError("Insufficient independent temporal data after leakage purge. Supply a larger labelled capture; existing artifacts are preserved.")
    X_train, y_risk_train, y_stage_train = X[:train_end], y_risk[:train_end], y_stage[:train_end]
    X_val, y_risk_val, y_stage_val = X[val_start:val_end], y_risk[val_start:val_end], y_stage[val_start:val_end]
    X_test, y_risk_test, y_stage_test = X[test_start:], y_risk[test_start:], y_stage[test_start:]
    # Feature standardization: fit on train ONLY
    mean = X_train.mean(axis=(0, 1), keepdims=True)
    std = X_train.std(axis=(0, 1), keepdims=True)
    std = np.where(std < 1e-6, 1.0, std)

    X_train_norm = (X_train - mean) / std
    X_val_norm = (X_val - mean) / std
    X_test_norm = (X_test - mean) / std

    # Class balance calculation on train only
    pos_samples = float(y_risk_train.sum())
    total_targets = float(y_risk_train.size)
    neg_samples = max(1.0, total_targets - pos_samples)
    pos_weight = float(neg_samples / max(1.0, pos_samples))

    splits = {
        "X_train": X_train_norm, "y_risk_train": y_risk_train, "y_stage_train": y_stage_train,
        "X_val": X_val_norm,     "y_risk_val": y_risk_val,     "y_stage_val": y_stage_val,
        "X_test": X_test_norm,   "y_risk_test": y_risk_test,   "y_stage_test": y_stage_test,
    }

    metadata = {
        "train_samples": len(X_train),
        "val_samples": len(X_val),
        "test_samples": len(X_test),
        "feature_mean": mean.squeeze().tolist(),
        "feature_std": std.squeeze().tolist(),
        "pos_weight": pos_weight,
        "train_attack_rate": float(pos_samples / max(1.0, total_targets)),
    }

    metadata["purge_gap_samples"] = gap
    metadata["split_indices"] = {"train": [0, train_end], "validation": [val_start, val_end], "test": [test_start, n]}

    return splits, metadata
