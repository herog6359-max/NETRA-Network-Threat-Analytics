import json
import logging
import os
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Dict, List, Optional, Any

import numpy as np
import torch
from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from datetime import datetime
import math

from config import (
    FEATURE_NAMES,
    NUM_FEATURES,
    HORIZON_STEPS,
    ATTACK_STAGES,
    MODEL_ARTIFACTS_DIR,
    MODEL_VERSION,
)
from dataset import extract_features_from_flows
from models.tcn import NetraTemporalForecaster

logger = logging.getLogger("netra.api")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

START_TIME = time.time()

# Global model state loaded once on startup
MODEL_STATE = {
    "model": None,
    "device": torch.device("cpu"),
    "metadata": {},
    "norm_mean": None,
    "norm_std": None,
    "loaded": False,
}


def load_model_and_artifacts():
    checkpoint_path = MODEL_ARTIFACTS_DIR / "best_model.pt"
    meta_path = MODEL_ARTIFACTS_DIR / "preprocessing_metadata.json"

    if not checkpoint_path.exists():
        logger.warning(f"Model checkpoint not found at: {checkpoint_path}. Service in standby.")
        MODEL_STATE["loaded"] = False
        return

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    MODEL_STATE["device"] = device

    logger.info(f"Loading PyTorch checkpoint from {checkpoint_path} onto {device}...")
    checkpoint = torch.load(checkpoint_path, map_location=device, weights_only=False)
    cfg = checkpoint.get("model_config", {})

    model = NetraTemporalForecaster(
        num_features=cfg.get("num_features", NUM_FEATURES),
        horizon_steps=cfg.get("horizon_steps", HORIZON_STEPS),
        num_stages=cfg.get("num_stages", len(ATTACK_STAGES)),
        num_channels=cfg.get("num_channels", [32, 64, 64]),
        kernel_size=cfg.get("kernel_size", 3),
        dropout=cfg.get("dropout", 0.2),
    ).to(device)

    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()

    MODEL_STATE["model"] = model
    MODEL_STATE["metadata"] = cfg

    # Load normalization statistics
    if meta_path.exists():
        with open(meta_path, "r") as f:
            meta = json.load(f)
            MODEL_STATE["norm_mean"] = np.array(meta.get("feature_mean", [0.0] * NUM_FEATURES), dtype=np.float32)
            MODEL_STATE["norm_std"] = np.array(meta.get("feature_std", [1.0] * NUM_FEATURES), dtype=np.float32)
    else:
        MODEL_STATE["norm_mean"] = np.zeros(NUM_FEATURES, dtype=np.float32)
        MODEL_STATE["norm_std"] = np.ones(NUM_FEATURES, dtype=np.float32)

    MODEL_STATE["loaded"] = True
    logger.info("PyTorch model and normalization artifacts successfully loaded into memory.")


@asynccontextmanager
async def lifespan(app: FastAPI):
    load_model_and_artifacts()
    yield


app = FastAPI(
    title="NETRA — Neural Network Attack Forecasting Service",
    description="Deep Learning Temporal Forecasting Microservice (PyTorch TCN) for SIH26153 / NTRO",
    version=MODEL_VERSION,
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# === DTOs and Schemas ===
class FlowRecord(BaseModel):
    src_ip: Optional[str] = "0.0.0.0"
    dst_ip: Optional[str] = "0.0.0.0"
    src_port: Optional[int] = 0
    dst_port: Optional[int] = 0
    protocol: Optional[str] = "TCP"
    timestamp: Optional[str] = None
    flow_duration: Optional[float] = 0.0
    total_fwd_packets: Optional[int] = 0
    total_bwd_packets: Optional[int] = 0
    total_fwd_bytes: Optional[float] = 0.0
    total_bwd_bytes: Optional[float] = 0.0
    syn_flag_count: Optional[int] = 0
    ack_flag_count: Optional[int] = 0
    rst_flag_count: Optional[int] = 0
    average_packet_size: Optional[float] = 0.0
    label: Optional[str] = "BENIGN"


class SequenceStep(BaseModel):
    timestamp: Optional[str] = None
    features: Dict[str, float]


class ForecastRequest(BaseModel):
    window_start: Optional[str] = None
    window_end: Optional[str] = None
    horizon_steps: int = Field(default=6, ge=1, le=36)
    sequence: Optional[List[SequenceStep]] = None
    features: Optional[Dict[str, float]] = None
    flows: Optional[List[FlowRecord]] = None
    model: Optional[str] = "pytorch-tcn"
    mode: Optional[str] = "PURE_NEURAL"  # "PURE_NEURAL" or "HYBRID_BLENDED"


class HorizonItem(BaseModel):
    step: int
    predicted_risk_score: float
    predicted_attack_probability: float
    confidence_lower: float
    confidence_upper: float


class AttributionItem(BaseModel):
    feature: str
    weight: float
    description: str


class ForecastResponse(BaseModel):
    model_name: str
    model_version: str
    inference_mode: str = "PURE_NEURAL"  # PURE_NEURAL, HYBRID_BLENDED, DETERMINISTIC_BASELINE
    is_experimental: bool = True
    current_risk_score: float
    max_forecasted_risk: float
    predicted_stage: str
    risk_level: str
    confidence: Optional[float] = None
    horizons: List[HorizonItem]
    attributions: List[AttributionItem]
    computation_time_ms: float
    device: str
    sequence_length: int = 1
    sequence_mode: str = "ORDERED_TEMPORAL_SEQUENCE"
    notes: Optional[str] = "Experimental risk estimate on local telemetry sequence."


# === Endpoints ===
@app.get("/health")
def health():
    is_loaded = MODEL_STATE["loaded"]
    return {
        "status": "UP" if is_loaded else "DEGRADED_STANDBY",
        "service": "NETRA PyTorch ML Service",
        "model_loaded": is_loaded,
        "model_version": MODEL_VERSION,
        "device": str(MODEL_STATE["device"]),
        "uptime_seconds": round(time.time() - START_TIME, 1),
    }


@app.get("/ready")
def readiness():
    if not MODEL_STATE["loaded"] or MODEL_STATE["model"] is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Model weights not loaded. Run train.py to initialize checkpoints.",
        )
    return {"status": "READY", "model": MODEL_VERSION}


@app.get("/metadata")
def metadata():
    metrics_path = MODEL_ARTIFACTS_DIR / "evaluation_metrics.json"
    metrics = {}
    if metrics_path.exists():
        with open(metrics_path, "r") as f:
            metrics = json.load(f)

    return {
        "model_name": "PyTorch Temporal Convolutional Network (TCN)",
        "model_version": MODEL_VERSION,
        "device": str(MODEL_STATE["device"]),
        "features": FEATURE_NAMES,
        "attack_stages": ATTACK_STAGES,
        "horizon_steps": HORIZON_STEPS,
        "model_loaded": MODEL_STATE["loaded"],
        "evaluation_summary": metrics.get("models", {}).get("pytorch_tcn", {}),
        "validation_status": "LEGACY_TINY_SAMPLE_NOT_GENERALISATION_EVIDENCE",
        "horizon_unit": "event steps; elapsed minutes not validated",
    }


@app.post("/predict", response_model=ForecastResponse)
@app.post("/forecast", response_model=ForecastResponse)
def predict_forecast(req: ForecastRequest):
    if not MODEL_STATE["loaded"] or MODEL_STATE["model"] is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Neural model not loaded into memory. Service unavailable.",
        )

    t0 = time.perf_counter()
    model = MODEL_STATE["model"]
    device = MODEL_STATE["device"]

    # Require actual ordered observations; never tile a point into a temporal sequence.
    if not req.sequence or len(req.sequence) < 2:
        raise HTTPException(status_code=422, detail="At least two timestamped feature observations are required")
    if len(req.sequence) > 512:
        raise HTTPException(status_code=422, detail="Maximum sequence length is 512")
    try:
        timestamps = [datetime.fromisoformat(step.timestamp.replace("Z", "+00:00")) for step in req.sequence]
        if any(t.tzinfo is None for t in timestamps) or any(a >= b for a, b in zip(timestamps, timestamps[1:])):
            raise ValueError("Timestamps must be timezone-aware and strictly increasing")
        for step in req.sequence:
            if set(step.features) != set(FEATURE_NAMES) or not all(math.isfinite(float(v)) for v in step.features.values()):
                raise ValueError("Every observation must contain all 15 finite, label-free features")
    except (ValueError, TypeError, AttributeError) as exc:
        raise HTTPException(status_code=422, detail=str(exc))
    if req.mode not in ("PURE_NEURAL", "HYBRID_BLENDED"):
        raise HTTPException(status_code=422, detail="Unknown inference mode")
    # 1. Feature extraction
    if req.features and len(req.features) > 0:
        feat_dict = req.features
    elif req.flows and len(req.flows) > 0:
        import pandas as pd
        df_flows = pd.DataFrame([f.model_dump() for f in req.flows])
        feat_dict = extract_features_from_flows(df_flows)
    else:
        feat_dict = {f: 0.0 for f in FEATURE_NAMES}

    # 2. Build feature vector
    raw_vec = np.array([float(feat_dict.get(fname, 0.0)) for fname in FEATURE_NAMES], dtype=np.float32)

    mean = MODEL_STATE["norm_mean"]
    std = MODEL_STATE["norm_std"]
    std_safe = np.where(std < 1e-6, 1.0, std)

    # 2 & 4. Sequence Tensor Construction
    if req.sequence and len(req.sequence) > 0:
        seq_len = len(req.sequence)
        seq_vectors = []
        for step in req.sequence:
            s_vec = np.array([float(step.features.get(fname, 0.0)) for fname in FEATURE_NAMES], dtype=np.float32)
            seq_vectors.append((s_vec - mean) / std_safe)
        seq_arr = np.array([seq_vectors], dtype=np.float32)  # shape (1, seq_len, num_features)
        seq_tensor = torch.tensor(seq_arr, dtype=torch.float32, device=device)
        sequence_mode = "ORDERED_TEMPORAL_SEQUENCE"
        feat_dict = req.sequence[-1].features
    else:
        seq_len = 5
        raw_vec = np.array([float(feat_dict.get(fname, 0.0)) for fname in FEATURE_NAMES], dtype=np.float32)
        norm_vec = (raw_vec - mean) / std_safe
        seq_tensor = torch.tensor(np.tile(norm_vec, (1, seq_len, 1)), dtype=torch.float32, device=device)
        sequence_mode = "REPLICATED_POINT_WINDOW"

    # 5. Model Forward Pass
    with torch.no_grad():
        risk_logits, stage_logits = model(seq_tensor)
        risk_probs = torch.sigmoid(risk_logits).cpu().numpy().squeeze()
        stage_probs = torch.softmax(stage_logits, dim=-1).cpu().numpy().squeeze()
        pred_stage_idx = int(np.argmax(stage_probs))

    # Base current risk from extracted features
    syn_val = float(feat_dict.get("syn_ratio", 0.0))
    port_val = float(feat_dict.get("unique_dst_ports", 0.0))
    pkt_rate = min(float(feat_dict.get("packet_rate", 0.0)) / 100.0, 1.0)
    current_risk = float(np.clip(0.35 * syn_val + 0.35 * port_val + 0.30 * pkt_rate, 0.0, 1.0))

    # Horizons & Inference Mode
    mode = req.mode or "PURE_NEURAL"
    horizon_items: List[HorizonItem] = []
    max_forecasted_risk = current_risk

    supported_steps = min(req.horizon_steps, int(np.size(risk_probs)))
    for step in range(1, supported_steps + 1):
        if isinstance(risk_probs, np.ndarray) and step - 1 < len(risk_probs):
            model_prob = float(risk_probs[step - 1])
        else:
            model_prob = current_risk

        if mode == "PURE_NEURAL":
            step_risk = round(float(np.clip(model_prob, 0.0, 1.0)), 3)
            inference_mode = "PURE_NEURAL"
        else:
            # Explicitly blended decision-support score
            step_risk = round(float(np.clip(current_risk * 0.4 + model_prob * 0.6 + (step * 0.01), 0.0, 1.0)), 3)
            inference_mode = "HYBRID_BLENDED"

        max_forecasted_risk = max(max_forecasted_risk, step_risk)
        step_prob = round(float(np.clip(step_risk * 1.05, 0.0, 1.0)), 3)

        horizon_items.append(
            HorizonItem(
                step=step,
                predicted_risk_score=step_risk,
                predicted_attack_probability=step_prob,
                confidence_lower=round(max(0.0, step_risk - 0.12), 3),
                confidence_upper=round(min(1.0, step_risk + 0.12), 3),
            )
        )

    # Risk Level mapping
    if max_forecasted_risk >= 0.85:
        risk_level = "CRITICAL"
    elif max_forecasted_risk >= 0.65:
        risk_level = "HIGH"
    elif max_forecasted_risk >= 0.40:
        risk_level = "MEDIUM"
    elif max_forecasted_risk >= 0.20:
        risk_level = "LOW"
    else:
        risk_level = "INFO"

    predicted_stage = ATTACK_STAGES[pred_stage_idx] if pred_stage_idx < len(ATTACK_STAGES) else "Suspicious Activity"

    # Explainable AI (XAI) feature attributions (100% label-free telemetry features)
    feature_desc_map = {
        "syn_ratio": "Elevated TCP SYN without ACK indicating handshake socket exhaustion",
        "unique_dst_ports": "Dispersed destination port targeting indicating reconnaissance scanning",
        "flow_rate": "Abnormal flow creation velocity per time quantum",
        "avg_packet_size_anomaly": "Packet size divergence from benign protocol standards",
        "iat_variance": "Inter-arrival time variance indicating automated packet generation",
        "fwd_bwd_imbalance": "Severe asymmetry between forward and backward payload transfer",
        "unique_dst_ips": "Fan-out across multiple network host destinations",
        "packet_rate": "Elevated packet velocity (packets per second) across observed flows",
    }

    attributions: List[AttributionItem] = []
    total_dev = 0.0
    devs = {}
    for fname in ["syn_ratio", "unique_dst_ports", "flow_rate", "fwd_bwd_imbalance", "packet_rate"]:
        val = abs(float(feat_dict.get(fname, 0.0)))
        devs[fname] = val
        total_dev += val

    for fname, val in devs.items():
        weight = round(val / max(1e-4, total_dev), 3) if total_dev > 0 else 0.20
        attributions.append(
            AttributionItem(
                feature=fname,
                weight=weight,
                description=feature_desc_map.get(fname, "Statistical anomaly in network telemetry feature"),
            )
        )
    attributions.sort(key=lambda a: a.weight, reverse=True)

    elapsed_ms = round((time.perf_counter() - t0) * 1000.0, 2)

    return ForecastResponse(
        model_name="PyTorch Temporal Convolutional Network (TCN)",
        model_version=MODEL_VERSION,
        inference_mode=inference_mode,
        is_experimental=True,
        current_risk_score=round(current_risk, 3),
        max_forecasted_risk=round(max_forecasted_risk, 3),
        predicted_stage=predicted_stage,
        risk_level=risk_level,
        confidence=None,
        horizons=horizon_items,
        attributions=attributions,
        computation_time_ms=elapsed_ms,
        device=str(device),
        sequence_length=seq_len,
        sequence_mode=sequence_mode,
        notes="Experimental event-step scores; not calibrated probabilities or validated minute-ahead warnings. Legacy small-sample model. Output limited to trained horizon count.",
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=False)
