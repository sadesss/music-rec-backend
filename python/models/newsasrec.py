from __future__ import annotations

import torch
from torch import nn

from models.newsasrec_block import NewSASRecBlock


class NewSASRec(nn.Module):
    """
    Minimal custom SASRec-like model with one modification:
    gated residual connections inside each transformer block.

    Token convention:
    - 0 is padding token
    - real item ids are shifted by +1
    """

    def __init__(
            self,
            num_items: int,
            max_len: int = 50,
            hidden_dim: int = 64,
            n_heads: int = 2,
            n_blocks: int = 2,
            dropout_rate: float = 0.2,
    ) -> None:
        super().__init__()

        self.num_items = num_items
        self.max_len = max_len
        self.hidden_dim = hidden_dim
        self.n_heads = n_heads
        self.n_blocks = n_blocks
        self.dropout_rate = dropout_rate

        # +1 because 0 is reserved for padding
        self.item_embedding = nn.Embedding(
            num_embeddings=num_items + 1,
            embedding_dim=hidden_dim,
            padding_idx=0,
        )
        self.position_embedding = nn.Embedding(
            num_embeddings=max_len,
            embedding_dim=hidden_dim,
        )
        self.input_dropout = nn.Dropout(dropout_rate)

        self.blocks = nn.ModuleList(
            [
                NewSASRecBlock(
                    hidden_dim=hidden_dim,
                    n_heads=n_heads,
                    dropout_rate=dropout_rate,
                )
                for _ in range(n_blocks)
            ]
        )

        self.final_norm = nn.LayerNorm(hidden_dim)

        self._reset_parameters()

    def _reset_parameters(self) -> None:
        nn.init.normal_(self.item_embedding.weight, std=0.02)
        nn.init.normal_(self.position_embedding.weight, std=0.02)

    def _build_attention_mask(self, seq_len: int, device: torch.device) -> torch.Tensor:
        # Upper triangular mask: prevents attention to future positions
        # Shape: (seq_len, seq_len), True means "masked"
        return torch.triu(
            torch.ones(seq_len, seq_len, dtype=torch.bool, device=device),
            diagonal=1,
        )

    def forward(self, input_seqs: torch.Tensor) -> torch.Tensor:
        """
        Args:
            input_seqs: LongTensor of shape [batch, seq_len]
                        values in [0..num_items], where 0 is padding

        Returns:
            hidden states of shape [batch, seq_len, hidden_dim]
        """
        device = input_seqs.device
        batch_size, seq_len = input_seqs.shape

        positions = torch.arange(seq_len, device=device).unsqueeze(0).expand(batch_size, -1)

        x = self.item_embedding(input_seqs) + self.position_embedding(positions)
        x = self.input_dropout(x)

        padding_mask = input_seqs.eq(0)  # [batch, seq_len]
        attn_mask = self._build_attention_mask(seq_len, device)

        for block in self.blocks:
            x = block(
                x=x,
                attn_mask=attn_mask,
                padding_mask=padding_mask,
            )

        x = self.final_norm(x)
        return x

    def get_last_hidden(self, input_seqs: torch.Tensor) -> torch.Tensor:
        """
        Returns the hidden state corresponding to the last non-padding token.
        Shape: [batch, hidden_dim]
        """
        hidden = self.forward(input_seqs)  # [B, L, H]

        non_pad = input_seqs.ne(0).sum(dim=1) - 1
        non_pad = non_pad.clamp(min=0)

        batch_idx = torch.arange(input_seqs.size(0), device=input_seqs.device)
        last_hidden = hidden[batch_idx, non_pad]
        return last_hidden

    def score_all_items(self, input_seqs: torch.Tensor) -> torch.Tensor:
        """
        Scores all real items (without padding index 0).
        Returns shape [batch, num_items]
        """
        last_hidden = self.get_last_hidden(input_seqs)  # [B, H]
        all_item_emb = self.item_embedding.weight[1:]   # [num_items, H]
        scores = last_hidden @ all_item_emb.transpose(0, 1)
        return scores

    def score_candidates(
            self,
            input_seqs: torch.Tensor,
            candidate_item_ids: torch.Tensor,
    ) -> torch.Tensor:
        """
        Args:
            input_seqs: [batch, seq_len]
            candidate_item_ids: [batch, n_candidates] or [n_candidates]
                                real ids shifted by +1, no padding ids

        Returns:
            scores: [batch, n_candidates]
        """
        last_hidden = self.get_last_hidden(input_seqs)  # [B, H]

        if candidate_item_ids.dim() == 1:
            candidate_item_ids = candidate_item_ids.unsqueeze(0).expand(input_seqs.size(0), -1)

        candidate_emb = self.item_embedding(candidate_item_ids)  # [B, C, H]
        scores = torch.einsum("bh,bch->bc", last_hidden, candidate_emb)
        return scores