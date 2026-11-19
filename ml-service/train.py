import json
import logging
import random
import time
from pathlib import Path
from typing import Dict, Any, Tuple

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset

from config import (
    NUM_FEATURES,
    HORIZON_STEPS,
    NUM_STAGES,
    TCN_CHANNELS,
    KERNEL_SIZE,
    DROPOUT,
    RANDOM_SEED,
    BATCH_SIZE,
    LEARNING_RATE,
    EPOCHS,
    PATIENCE,
    MODEL_ARTIFACTS_DIR,
    MODEL_VERSION,
)
from dataset import (
    load_raw_dataset,
    build_temporal_dataset,
    chronological_train_val_test_split,
)
from models.tcn import NetraTemporalForecaster

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("netra.train")


def set_seed(seed: int = RANDOM_SEED):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def get_device() -> torch.device:
    if torch.cuda.is_available():
        device = torch.device("cuda")
        logger.info(f"CUDA accelerator detected: {torch.cuda.get_device_name(0)}")
    else:
        device = torch.device("cpu")
        logger.info("No CUDA GPU detected. Gracefully running on CPU.")
    return device


def train_model() -> Dict[str, Any]:
    start_time = time.time()
    set_seed(RANDOM_SEED)
    device = get_device()

    # 1. Load dataset & provenance
    df, provenance = load_raw_dataset()
    logger.info(f"Provenance recorded: {provenance['source_file']} with {len(df)} rows")

    # 2. Build temporal sequences
    X, y_risk, y_stage, seq_meta = build_temporal_dataset(df)
    if len(X) == 0:
        raise ValueError("Insufficient temporal data points to construct sequences.")

    # 3. Chronological split without lookahead leakage
    splits, split_meta = chronological_train_val_test_split(X, y_risk, y_stage)
    # Only a real training run may update preprocessing artifacts; tests/evaluation never do.
    with open(MODEL_ARTIFACTS_DIR / "preprocessing_metadata.json", "w") as output:
        json.dump(split_meta, output, indent=2)

    X_train_t = torch.tensor(splits["X_train"], dtype=torch.float32)
    y_risk_train_t = torch.tensor(splits["y_risk_train"], dtype=torch.float32)
    y_stage_train_t = torch.tensor(splits["y_stage_train"], dtype=torch.float32)

    X_val_t = torch.tensor(splits["X_val"], dtype=torch.float32)
    y_risk_val_t = torch.tensor(splits["y_risk_val"], dtype=torch.float32)
    y_stage_val_t = torch.tensor(splits["y_stage_val"], dtype=torch.float32)

    train_dataset = TensorDataset(X_train_t, y_risk_train_t, y_stage_train_t)
    val_dataset = TensorDataset(X_val_t, y_risk_val_t, y_stage_val_t)

    actual_batch_size = max(1, min(BATCH_SIZE, len(X_train_t)))
    train_loader = DataLoader(train_dataset, batch_size=actual_batch_size, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=max(1, min(actual_batch_size, len(X_val_t))), shuffle=False)

    # 4. Instantiate Model
    model = NetraTemporalForecaster(
        num_features=X.shape[2],
        horizon_steps=y_risk.shape[1],
        num_stages=NUM_STAGES,
        num_channels=TCN_CHANNELS,
        kernel_size=KERNEL_SIZE,
        dropout=DROPOUT,
    ).to(device)

    # 5. Loss functions with class imbalance handling
    pos_weight = torch.tensor([split_meta["pos_weight"]], device=device, dtype=torch.float32)
    risk_criterion = nn.BCEWithLogitsLoss(pos_weight=pos_weight)
    stage_criterion = nn.CrossEntropyLoss()

    optimizer = torch.optim.AdamW(model.parameters(), lr=LEARNING_RATE, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(optimizer, mode="min", factor=0.5, patience=3)

    best_val_loss = float("inf")
    best_epoch = 0
    patience_counter = 0
    checkpoint_path = MODEL_ARTIFACTS_DIR / "best_model.pt"

    history = []

    logger.info(f"Starting training: {EPOCHS} epochs, batch_size={actual_batch_size}, lr={LEARNING_RATE}")
    for epoch in range(1, EPOCHS + 1):
        model.train()
        train_loss = 0.0

        for b_x, b_risk, b_stage in train_loader:
            b_x = b_x.to(device)
            b_risk = b_risk.to(device)
            b_stage = b_stage.to(device)

            optimizer.zero_grad()
            risk_logits, stage_logits = model(b_x)

            loss_risk = risk_criterion(risk_logits, b_risk)
            target_stage_idx = torch.argmax(b_stage, dim=-1)
            loss_stage = stage_criterion(stage_logits, target_stage_idx)

            loss = loss_risk + 0.5 * loss_stage
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()

            train_loss += loss.item() * len(b_x)

        train_loss /= max(1, len(train_dataset))

        # Validation phase
        model.eval()
        val_loss = 0.0
        with torch.no_grad():
            for b_x, b_risk, b_stage in val_loader:
                b_x = b_x.to(device)
                b_risk = b_risk.to(device)
                b_stage = b_stage.to(device)

                risk_logits, stage_logits = model(b_x)
                loss_risk = risk_criterion(risk_logits, b_risk)
                loss_stage = stage_criterion(stage_logits, torch.argmax(b_stage, dim=-1))
                loss = loss_risk + 0.5 * loss_stage
                val_loss += loss.item() * len(b_x)

        val_loss /= max(1, len(val_dataset))
        scheduler.step(val_loss)

        history.append({
            "epoch": epoch,
            "train_loss": round(train_loss, 4),
            "val_loss": round(val_loss, 4),
            "lr": optimizer.param_groups[0]["lr"],
        })

        if epoch % 5 == 0 or epoch == 1:
            logger.info(f"Epoch {epoch:2d}/{EPOCHS}: Train Loss = {train_loss:.4f}, Val Loss = {val_loss:.4f}")

        # Model Checkpointing based on validation loss
        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_epoch = epoch
            patience_counter = 0
            torch.save({
                "model_state_dict": model.state_dict(),
                "model_config": {
                    "num_features": X.shape[2],
                    "horizon_steps": y_risk.shape[1],
                    "num_stages": NUM_STAGES,
                    "num_channels": TCN_CHANNELS,
                    "kernel_size": KERNEL_SIZE,
                    "dropout": DROPOUT,
                    "version": MODEL_VERSION,
                },
                "split_metadata": split_meta,
                "epoch": epoch,
                "val_loss": val_loss,
            }, checkpoint_path)
        else:
            patience_counter += 1
            if patience_counter >= PATIENCE:
                logger.info(f"Early stopping triggered at epoch {epoch}. Best epoch was {best_epoch}.")
                break

    duration_s = time.time() - start_time
    logger.info(f"Training completed in {duration_s:.2f}s. Best checkpoint saved at {checkpoint_path} (epoch {best_epoch}, val_loss {best_val_loss:.4f})")

    summary = {
        "model_version": MODEL_VERSION,
        "device": str(device),
        "best_epoch": best_epoch,
        "best_val_loss": round(best_val_loss, 4),
        "total_epochs": len(history),
        "duration_seconds": round(duration_s, 2),
        "checkpoint_path": str(checkpoint_path),
        "provenance": provenance,
        "split_metadata": split_meta,
        "history": history[-10:],
    }

    summary_path = MODEL_ARTIFACTS_DIR / "training_summary.json"
    with open(summary_path, "w") as f:
        json.dump(summary, f, indent=2)

    return summary


if __name__ == "__main__":
    train_model()
