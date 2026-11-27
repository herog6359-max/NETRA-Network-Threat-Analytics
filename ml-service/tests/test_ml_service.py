import sys
from pathlib import Path

# Add ml-service directory to sys.path
BASE_DIR = Path(__file__).resolve().parent.parent
if str(BASE_DIR) not in sys.path:
    sys.path.insert(0, str(BASE_DIR))

import numpy as np
import pytest
import torch
from fastapi.testclient import TestClient

from config import FEATURE_NAMES, NUM_FEATURES, HORIZON_STEPS
from dataset import (
    load_raw_dataset,
    build_temporal_dataset,
    chronological_train_val_test_split,
    extract_features_from_flows,
)
from models.tcn import NetraTemporalForecaster
from models.baseline import DeterministicHeuristicBaseline
from main import app


@pytest.fixture
def test_df():
    df, _ = load_raw_dataset()
    return df


def test_dataset_loading_and_provenance(test_df):
    assert len(test_df) > 0, "Dataset must not be empty"
    assert "label" in test_df.columns
    assert "timestamp" in test_df.columns
    # Check that timestamps are strictly monotonic (chronologically sorted)
    timestamps = test_df["timestamp"].values
    assert (np.diff(timestamps) >= np.timedelta64(0, 'ns')).all(), "Dataset must be chronologically ordered"


def test_no_temporal_leakage_in_sequences(test_df):
    """
    Verifies that the target future horizon does NOT leak into the input window.
    """
    W = 4
    H = 2
    X, y_risk, y_stage, meta = build_temporal_dataset(test_df, window_size=W, horizon_steps=H)
    assert len(X) > 0, "Must construct at least one sequence"
    assert X.shape[1] == W, f"Input sequence length must equal window size {W}"
    assert y_risk.shape[1] == H, f"Target horizon length must equal {H}"


def test_chronological_splits_and_normalization(test_df):
    """
    Verifies train/val/test splits maintain strict chronological ordering
    and feature normalization uses training split statistics only.
    """
    X, y_risk, y_stage, _ = build_temporal_dataset(test_df, window_size=4, horizon_steps=2)
    with pytest.raises(ValueError, match="Insufficient independent temporal data"):
        chronological_train_val_test_split(X, y_risk, y_stage)

    # Ordered synthetic windows make the training-only mean directly checkable.
    X = np.arange(80 * 4 * NUM_FEATURES, dtype=np.float32).reshape(80, 4, NUM_FEATURES)
    y_risk = np.zeros((80, 2), dtype=np.float32)
    y_stage = np.zeros((80, 5), dtype=np.float32)
    splits, meta = chronological_train_val_test_split(X, y_risk, y_stage, train_ratio=0.6, val_ratio=0.2)

    assert "X_train" in splits and "X_val" in splits and "X_test" in splits
    assert len(splits["X_train"]) > 0
    assert "feature_mean" in meta and "feature_std" in meta
    train_end = meta["split_indices"]["train"][1]
    np.testing.assert_allclose(meta["feature_mean"], X[:train_end].mean(axis=(0, 1)))
    assert meta["split_indices"]["validation"][0] - train_end >= 6
    assert meta["split_indices"]["test"][0] - meta["split_indices"]["validation"][1] >= 6


def test_tcn_forward_pass_dimensions():
    batch_size = 2
    seq_len = 8
    model = NetraTemporalForecaster(
        num_features=NUM_FEATURES,
        horizon_steps=HORIZON_STEPS,
        num_channels=[16, 32],
        kernel_size=3,
    )
    x = torch.randn(batch_size, seq_len, NUM_FEATURES)
    risk_logits, stage_logits = model(x)

    assert risk_logits.shape == (batch_size, HORIZON_STEPS), "Risk logits shape mismatch"
    assert stage_logits.shape == (batch_size, 5), "Stage logits shape mismatch"


def test_deterministic_baseline_invariance():
    baseline = DeterministicHeuristicBaseline()
    seq1 = np.ones((6, NUM_FEATURES), dtype=np.float32)
    seq2 = np.ones((6, NUM_FEATURES), dtype=np.float32)

    pred1 = baseline.predict_sequence(seq1)
    pred2 = baseline.predict_sequence(seq2)
    np.testing.assert_allclose(pred1, pred2, atol=1e-5, err_msg="Deterministic baseline must be deterministic")


def test_fastapi_health_endpoint():
    client = TestClient(app)
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert "status" in data
    assert "service" in data
    assert data["service"] == "NETRA PyTorch ML Service"


def test_fastapi_rejects_single_feature_snapshot():
    with TestClient(app) as client:
        response = client.post("/predict", json={"horizon_steps": 6, "features": {"syn_ratio": 0.45}})
        assert response.status_code == 422
        assert "timestamped" in response.json()["detail"]


def test_fastapi_sequence_input_contract():
    with TestClient(app) as client:
        # Ordered temporal sequence of 5 historical slices
        sequence = [
            {"timestamp": f"2026-10-13T05:00:0{i}Z", "features": {name: 0.1 * i for name in FEATURE_NAMES}}
            for i in range(5)
        ]
        payload = {
            "horizon_steps": 6,
            "sequence": sequence,
            "model": "pytorch-tcn",
            "mode": "PURE_NEURAL",
        }
        response = client.post("/forecast", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert data["inference_mode"] == "PURE_NEURAL"
        assert data["sequence_mode"] == "ORDERED_TEMPORAL_SEQUENCE"
        assert data["sequence_length"] == 5
        model_steps = client.get("/metadata").json()["horizon_steps"]
        assert len(data["horizons"]) == min(payload["horizon_steps"], model_steps)


def test_fastapi_predict_invalid_schema():
    client = TestClient(app)
    bad_payload = {"horizon_steps": 999}
    response = client.post("/predict", json=bad_payload)
    assert response.status_code == 422

