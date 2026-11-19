import numpy as np
from sklearn.linear_model import LogisticRegression
from typing import Dict, List, Tuple, Any

from config import FEATURE_NAMES, HORIZON_STEPS


class DeterministicHeuristicBaseline:
    """
    Python mirror of Java DeterministicForecastEngine.
    Computes weighted risk and projects forward with decay/trend.
    """
    def __init__(self, horizon_steps: int = HORIZON_STEPS):
        self.horizon_steps = horizon_steps
        self.weights = {
            "syn_ratio": 0.20,
            "unique_dst_ports": 0.15,
            "flow_rate": 0.15,
            "avg_packet_size_anomaly": 0.10,
            "iat_variance": 0.10,
            "fwd_bwd_imbalance": 0.10,
            "unique_dst_ips": 0.10,
            "packet_rate": 0.10,
        }

    def predict_sequence(self, x_seq: np.ndarray, horizon_steps: int = None) -> np.ndarray:
        """
        x_seq: (window_size, num_features)
        Returns: horizon predictions (horizon_steps,)
        """
        h_steps = horizon_steps or self.horizon_steps
        latest_vec = x_seq[-1]
        score = 0.0
        total_w = 0.0
        for i, fname in enumerate(FEATURE_NAMES):
            if fname in self.weights:
                val = float(latest_vec[i])
                w = self.weights[fname]
                score += w * min(max(val, 0.0), 1.0)
                total_w += w

        base_risk = score / total_w if total_w > 0 else 0.0
        trend_idx = FEATURE_NAMES.index("risk_trend") if "risk_trend" in FEATURE_NAMES else -1
        trend = float(latest_vec[trend_idx]) if trend_idx >= 0 else 0.0

        horizons = []
        for step in range(1, h_steps + 1):
            projected = base_risk + (trend * step * 0.1)
            horizons.append(min(max(projected, 0.0), 1.0))
        return np.array(horizons, dtype=np.float32)

    def predict_batch(self, X: np.ndarray, horizon_steps: int = None) -> np.ndarray:
        h_steps = horizon_steps or self.horizon_steps
        return np.array([self.predict_sequence(seq, h_steps) for seq in X], dtype=np.float32)


class LogisticRegressionTemporalBaseline:
    """
    Simple statistical baseline: Multi-output Logistic Regression.
    Flattens window sequence (W * D) and trains an independent classifier per horizon step.
    """
    def __init__(self):
        self.models: List[LogisticRegression] = []

    def fit(self, X_train: np.ndarray, y_train: np.ndarray):
        """
        X_train: (N, seq_len, num_features)
        y_train: (N, horizon_steps)
        """
        N = len(X_train)
        X_flat = X_train.reshape(N, -1)
        self.models = []

        for h in range(y_train.shape[1]):
            y_h = (y_train[:, h] >= 0.5).astype(int)
            unique_classes = np.unique(y_h)
            if len(unique_classes) > 1:
                clf = LogisticRegression(max_iter=500, random_state=42)
                clf.fit(X_flat, y_h)
            else:
                # Constant predictor fallback if only one class in split
                clf = None
            self.models.append(clf)

    def predict_proba(self, X: np.ndarray) -> np.ndarray:
        N = len(X)
        X_flat = X.reshape(N, -1)
        preds = np.zeros((N, len(self.models)), dtype=np.float32)

        for h, clf in enumerate(self.models):
            if clf is not None and hasattr(clf, "predict_proba"):
                try:
                    preds[:, h] = clf.predict_proba(X_flat)[:, 1]
                except Exception:
                    preds[:, h] = 0.5
            else:
                preds[:, h] = 0.5
        return preds
