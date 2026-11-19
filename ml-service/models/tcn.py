import torch
import torch.nn as nn
from typing import List, Tuple, Dict, Any


class Chomp1d(nn.Module):
    """Trims trailing padding to enforce strict causality (no lookahead)."""
    def __init__(self, chomp_size: int):
        super().__init__()
        self.chomp_size = chomp_size

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        if self.chomp_size <= 0:
            return x
        return x[:, :, :-self.chomp_size].contiguous()


class TemporalBlock(nn.Module):
    """
    Residual block with dilated causal convolutions, batch norm/dropout, and residual addition.
    """
    def __init__(
        self,
        n_inputs: int,
        n_outputs: int,
        kernel_size: int,
        stride: int,
        dilation: int,
        padding: int,
        dropout: float = 0.2,
    ):
        super().__init__()
        self.conv1 = nn.Conv1d(
            n_inputs, n_outputs, kernel_size, stride=stride, padding=padding, dilation=dilation
        )
        self.chomp1 = Chomp1d(padding)
        self.relu1 = nn.GELU()
        self.dropout1 = nn.Dropout(dropout)

        self.conv2 = nn.Conv1d(
            n_outputs, n_outputs, kernel_size, stride=stride, padding=padding, dilation=dilation
        )
        self.chomp2 = Chomp1d(padding)
        self.relu2 = nn.GELU()
        self.dropout2 = nn.Dropout(dropout)

        self.net = nn.Sequential(
            self.conv1, self.chomp1, self.relu1, self.dropout1,
            self.conv2, self.chomp2, self.relu2, self.dropout2,
        )

        self.downsample = nn.Conv1d(n_inputs, n_outputs, 1) if n_inputs != n_outputs else None
        self.relu = nn.GELU()

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        out = self.net(x)
        res = x if self.downsample is None else self.downsample(x)
        return self.relu(out + res)


class TemporalConvNet(nn.Module):
    """
    Temporal Convolutional Network backbone.
    Inputs: (batch_size, num_features, seq_len)
    Outputs: (batch_size, num_channels[-1], seq_len)
    """
    def __init__(self, num_inputs: int, num_channels: List[int], kernel_size: int = 3, dropout: float = 0.2):
        super().__init__()
        layers = []
        num_levels = len(num_channels)
        for i in range(num_levels):
            dilation_size = 2 ** i
            in_channels = num_inputs if i == 0 else num_channels[i - 1]
            out_channels = num_channels[i]
            padding = (kernel_size - 1) * dilation_size
            layers.append(
                TemporalBlock(
                    in_channels,
                    out_channels,
                    kernel_size,
                    stride=1,
                    dilation=dilation_size,
                    padding=padding,
                    dropout=dropout,
                )
            )
        self.network = nn.Sequential(*layers)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.network(x)


class NetraTemporalForecaster(nn.Module):
    """
    Flagship NETRA PyTorch Temporal Forecaster.
    Combines TCN temporal feature extractor with multi-horizon risk projection head
    and MITRE ATT&CK stage classification head.
    """
    def __init__(
        self,
        num_features: int = 15,
        horizon_steps: int = 6,
        num_stages: int = 5,
        num_channels: List[int] = None,
        kernel_size: int = 3,
        dropout: float = 0.2,
    ):
        super().__init__()
        if num_channels is None:
            num_channels = [32, 64, 64]

        self.num_features = num_features
        self.horizon_steps = horizon_steps
        self.num_stages = num_stages

        # Temporal Convolutional Backbone
        self.tcn = TemporalConvNet(num_features, num_channels, kernel_size=kernel_size, dropout=dropout)

        # Multi-Horizon Risk Projection Head (predicts logits for +15m, +30m, ... +120m)
        final_dim = num_channels[-1]
        self.risk_head = nn.Sequential(
            nn.Linear(final_dim, 32),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(32, horizon_steps),
        )

        # MITRE ATT&CK Stage Classification Head
        self.stage_head = nn.Sequential(
            nn.Linear(final_dim, 32),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(32, num_stages),
        )

    def forward(self, x: torch.Tensor) -> Tuple[torch.Tensor, torch.Tensor]:
        """
        x: Tensor of shape (batch_size, seq_len, num_features)
        Returns:
          risk_logits: (batch_size, horizon_steps)
          stage_logits: (batch_size, num_stages)
        """
        # Transpose for Conv1d: (batch_size, num_features, seq_len)
        x_trans = x.transpose(1, 2)
        feat_map = self.tcn(x_trans)

        # Extract features from latest temporal step (strict causality)
        latest_feat = feat_map[:, :, -1]

        risk_logits = self.risk_head(latest_feat)
        stage_logits = self.stage_head(latest_feat)
        return risk_logits, stage_logits

    def predict_horizons_and_stage(
        self, x: torch.Tensor
    ) -> Tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
        """
        Inference helper returning probabilities and stage predictions.
        """
        self.eval()
        with torch.no_grad():
            risk_logits, stage_logits = self.forward(x)
            risk_probs = torch.sigmoid(risk_logits)
            stage_probs = torch.softmax(stage_logits, dim=-1)
            pred_stage = torch.argmax(stage_probs, dim=-1)
        return risk_probs, stage_probs, pred_stage
