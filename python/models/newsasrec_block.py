from __future__ import annotations

import torch
from torch import nn


class PositionwiseFeedForward(nn.Module):
    def __init__(self, hidden_dim: int, dropout_rate: float) -> None:
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(hidden_dim, hidden_dim * 4),
            nn.GELU(),
            nn.Dropout(dropout_rate),
            nn.Linear(hidden_dim * 4, hidden_dim),
            nn.Dropout(dropout_rate),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.net(x)


class NewSASRecBlock(nn.Module):
    """
    SASRec block with a single lightweight modification:
    gated residual connections.

    Standard residual:
        x = x + attn_out
        x = x + ffn_out

    Our modified residual:
        x = x + sigmoid(Wg_attn(x)) * attn_out
        x = x + sigmoid(Wg_ffn(x)) * ffn_out
    """

    def __init__(
            self,
            hidden_dim: int,
            n_heads: int,
            dropout_rate: float,
    ) -> None:
        super().__init__()

        self.attn_norm = nn.LayerNorm(hidden_dim)
        self.self_attn = nn.MultiheadAttention(
            embed_dim=hidden_dim,
            num_heads=n_heads,
            dropout=dropout_rate,
            batch_first=True,
        )
        self.attn_dropout = nn.Dropout(dropout_rate)
        self.attn_gate = nn.Linear(hidden_dim, hidden_dim)

        self.ffn_norm = nn.LayerNorm(hidden_dim)
        self.ffn = PositionwiseFeedForward(
            hidden_dim=hidden_dim,
            dropout_rate=dropout_rate,
        )
        self.ffn_dropout = nn.Dropout(dropout_rate)
        self.ffn_gate = nn.Linear(hidden_dim, hidden_dim)

    def forward(
            self,
            x: torch.Tensor,
            attn_mask: torch.Tensor,
            padding_mask: torch.Tensor | None = None,
    ) -> torch.Tensor:
        # ---- Self-attention sublayer ----
        x_norm = self.attn_norm(x)
        attn_out, _ = self.self_attn(
            query=x_norm,
            key=x_norm,
            value=x_norm,
            attn_mask=attn_mask,
            key_padding_mask=padding_mask,
            need_weights=False,
        )
        attn_out = self.attn_dropout(attn_out)

        gate_attn = torch.sigmoid(self.attn_gate(x))
        x = x + gate_attn * attn_out

        # ---- FFN sublayer ----
        x_norm = self.ffn_norm(x)
        ffn_out = self.ffn(x_norm)
        ffn_out = self.ffn_dropout(ffn_out)

        gate_ffn = torch.sigmoid(self.ffn_gate(x))
        x = x + gate_ffn * ffn_out

        return x