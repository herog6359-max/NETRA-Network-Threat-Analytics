import os
from pathlib import Path

# Paths
BASE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = BASE_DIR.parent
DATASETS_DIR = PROJECT_ROOT / "datasets"
MODEL_ARTIFACTS_DIR = PROJECT_ROOT / "model-artifacts"
MODEL_ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)

# Dataset file provenance
AVAILABLE_DATASET_PATH = DATASETS_DIR / "sample-cic-ids-attack.csv"

# Temporal Sequence Parameters
WINDOW_SIZE = 12       # Number of historical time-steps in sequence
HORIZON_STEPS = 6      # Prediction steps into future (+15m, +30m, +45m, +60m, +90m, +120m)
HORIZON_MINUTES = [15, 30, 45, 60, 90, 120]

# Standard Feature Schema matching com.netra.service.forecast.DefaultFeatureExtractor
FEATURE_NAMES = [
    "flow_count",
    "flow_rate",
    "unique_src_ips",
    "unique_dst_ips",
    "unique_dst_ports",
    "syn_ratio",
    "ack_ratio",
    "rst_ratio",
    "avg_packet_size_anomaly",
    "iat_variance",
    "fwd_bwd_imbalance",
    "avg_duration_ms",
    "total_bytes",
    "packet_rate",
    "risk_trend",
]
NUM_FEATURES = len(FEATURE_NAMES)

# Attack Stages (MITRE ATT&CK Mapping)
ATTACK_STAGES = [
    "Normal Operations",
    "Reconnaissance",
    "Initial Access Attempt",
    "Active Exploitation",
    "Data Exfiltration",
]
NUM_STAGES = len(ATTACK_STAGES)

# Model Architecture Configuration
TCN_CHANNELS = [32, 64, 64]
KERNEL_SIZE = 3
DROPOUT = 0.2

# Training Hyperparameters
RANDOM_SEED = 42
BATCH_SIZE = 4
LEARNING_RATE = 0.002
EPOCHS = 40
PATIENCE = 8

# Service Configuration
HOST = "0.0.0.0"
PORT = 8000
MODEL_VERSION = "2.1.0-tcn"
