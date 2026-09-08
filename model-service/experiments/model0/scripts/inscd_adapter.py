"""Split-safe adapters for the NCDM and ORCDF formulas used by InsCD v1.3.1.

The local InsCD distribution cannot be imported in this Windows runtime because
its pinned PyTorch 2.4.0 wheel requires an unavailable OpenMP DLL.  This module
keeps the relevant MIT-licensed source semantics explicit while avoiding the
toolkit's random interaction split helper and unrelated optional graph imports.

Reference components:
- ``inscd.models.neural.ncdm.NCDM``
- ``inscd.extractor.default.Default``
- ``inscd.interfunc.ncd.NCD_IF``
- ``inscd.models.graph.orcdf.ORCDF``
- ``inscd.extractor.orcdf.ORCDF_EX``
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import torch
from torch import nn


def _xavier_initialise(module: nn.Module) -> None:
    if isinstance(module, (nn.Embedding, nn.Linear)):
        nn.init.xavier_normal_(module.weight)


class NCDInteraction(nn.Module):
    """NCD_IF-compatible interaction function with non-negative MLP weights."""

    def __init__(self, knowledge_num: int, hidden_dims: list[int], dropout: float) -> None:
        super().__init__()
        layers: list[nn.Module] = []
        previous = knowledge_num
        for index, hidden_dim in enumerate(hidden_dims):
            if index:
                layers.append(nn.Dropout(p=dropout))
            layers.append(nn.Linear(previous, hidden_dim))
            layers.append(nn.Tanh())
            previous = hidden_dim
        layers.append(nn.Dropout(p=dropout))
        layers.append(nn.Linear(previous, 1))
        layers.append(nn.Sigmoid())
        self.mlp = nn.Sequential(*layers)
        self.apply(_xavier_initialise)

    def forward(
        self,
        student: torch.Tensor,
        difficulty: torch.Tensor,
        discrimination: torch.Tensor,
        q_mask: torch.Tensor,
    ) -> torch.Tensor:
        features = (
            torch.sigmoid(discrimination)
            * (torch.sigmoid(student) - torch.sigmoid(difficulty))
            * q_mask
        )
        return self.mlp(features).view(-1)

    def enforce_monotonicity(self) -> None:
        with torch.no_grad():
            for layer in self.mlp:
                if isinstance(layer, nn.Linear):
                    layer.weight.clamp_(min=0)


class NCDMAdapter(nn.Module):
    """NCDM architecture matching the referenced InsCD components."""

    def __init__(
        self,
        student_num: int,
        exercise_num: int,
        knowledge_num: int,
        hidden_dims: list[int],
        dropout: float,
    ) -> None:
        super().__init__()
        self.student_embedding = nn.Embedding(student_num, knowledge_num)
        self.knowledge_embedding = nn.Embedding(knowledge_num, knowledge_num)
        self.difficulty_embedding = nn.Embedding(exercise_num, knowledge_num)
        self.discrimination_embedding = nn.Embedding(exercise_num, 1)
        self.interaction = NCDInteraction(knowledge_num, hidden_dims, dropout)
        self.apply(_xavier_initialise)

    def forward(
        self,
        student_ids: torch.Tensor,
        exercise_ids: torch.Tensor,
        q_mask: torch.Tensor,
    ) -> torch.Tensor:
        return self.interaction(
            self.student_embedding(student_ids),
            self.difficulty_embedding(exercise_ids),
            self.discrimination_embedding(exercise_ids),
            q_mask,
        )

    def mastery(self) -> torch.Tensor:
        return torch.sigmoid(self.student_embedding.weight)

    def neutralize_students(self, student_ids: torch.Tensor) -> None:
        with torch.no_grad():
            self.student_embedding.weight.index_fill_(0, student_ids, 0.0)

    def enforce_monotonicity(self) -> None:
        self.interaction.enforce_monotonicity()


@dataclass(frozen=True)
class ResponseGraph:
    right: torch.Tensor
    wrong: torch.Tensor
    evidence: dict[str, Any]


def _normalised_sparse_adjacency(
    student_num: int,
    exercise_num: int,
    knowledge_num: int,
    student_ids: torch.Tensor,
    exercise_ids: torch.Tensor,
    q_matrix: torch.Tensor,
    device: torch.device,
) -> tuple[torch.Tensor, int, int]:
    """Build one weighted, symmetric response/Q graph without a dense matrix."""

    total_nodes = student_num + exercise_num + knowledge_num
    local_students = student_ids.to(dtype=torch.long, device="cpu")
    local_exercises = exercise_ids.to(dtype=torch.long, device="cpu") + student_num
    q_indices = q_matrix.to(dtype=torch.bool, device="cpu").nonzero(as_tuple=False)
    q_exercises = q_indices[:, 0] + student_num
    q_concepts = q_indices[:, 1] + student_num + exercise_num
    self_nodes = torch.arange(total_nodes, dtype=torch.long)
    rows = torch.cat(
        [
            local_students,
            local_exercises,
            q_exercises,
            q_concepts,
            self_nodes,
        ]
    )
    columns = torch.cat(
        [
            local_exercises,
            local_students,
            q_concepts,
            q_exercises,
            self_nodes,
        ]
    )
    values = torch.ones(rows.numel(), dtype=torch.float32)
    adjacency = torch.sparse_coo_tensor(
        torch.stack([rows, columns]),
        values,
        (total_nodes, total_nodes),
        dtype=torch.float32,
    ).coalesce()
    indices = adjacency.indices()
    coalesced_values = adjacency.values()
    degree = torch.zeros(total_nodes, dtype=torch.float32)
    degree.scatter_add_(0, indices[0], coalesced_values)
    inverse_sqrt_degree = degree.rsqrt()
    normalised_values = (
        coalesced_values
        * inverse_sqrt_degree.index_select(0, indices[0])
        * inverse_sqrt_degree.index_select(0, indices[1])
    )
    normalised = torch.sparse_coo_tensor(
        indices,
        normalised_values,
        adjacency.shape,
        dtype=torch.float32,
        device=device,
    ).coalesce()
    pair_count = int(
        torch.unique(torch.stack([student_ids.to("cpu"), exercise_ids.to("cpu")]), dim=1).shape[1]
    )
    return normalised, pair_count, int(adjacency._nnz())


def build_response_graph(
    student_num: int,
    exercise_num: int,
    knowledge_num: int,
    train_students: torch.Tensor,
    train_exercises: torch.Tensor,
    train_outcomes: torch.Tensor,
    q_matrix: torch.Tensor,
    device: torch.device,
) -> ResponseGraph:
    """Build ORCDF's right/wrong response graph from train rows only."""

    right_mask = train_outcomes.to(dtype=torch.bool, device="cpu")
    wrong_mask = ~right_mask
    right, right_pairs, right_nonzeros = _normalised_sparse_adjacency(
        student_num,
        exercise_num,
        knowledge_num,
        train_students[right_mask],
        train_exercises[right_mask],
        q_matrix,
        device,
    )
    wrong, wrong_pairs, wrong_nonzeros = _normalised_sparse_adjacency(
        student_num,
        exercise_num,
        knowledge_num,
        train_students[wrong_mask],
        train_exercises[wrong_mask],
        q_matrix,
        device,
    )
    q_edges = int(q_matrix.to(dtype=torch.bool, device="cpu").sum().item())
    return ResponseGraph(
        right=right,
        wrong=wrong,
        evidence={
            "nodes": student_num + exercise_num + knowledge_num,
            "train_response_rows": int(train_outcomes.numel()),
            "right_response_rows": int(right_mask.sum().item()),
            "wrong_response_rows": int(wrong_mask.sum().item()),
            "unique_right_student_exercise_pairs": right_pairs,
            "unique_wrong_student_exercise_pairs": wrong_pairs,
            "exercise_topic_edges": q_edges,
            "right_normalized_adjacency_nonzeros": right_nonzeros,
            "wrong_normalized_adjacency_nonzeros": wrong_nonzeros,
            "response_edge_semantics": (
                "Train-only student-to-exercise response edges, split by correct and "
                "incorrect outcome; repeated same-outcome pairs are weighted after sparse coalescing."
            ),
            "q_edge_semantics": "Frozen Exercise-to-Topic Q-matrix edges, included in both graph views.",
        },
    )


class ORCDFNCDAdapter(nn.Module):
    """ORCDF response-graph extractor paired with the InsCD NCD interaction."""

    def __init__(
        self,
        student_num: int,
        exercise_num: int,
        knowledge_num: int,
        latent_dim: int,
        gcn_layers: int,
        ssl_temperature: float,
        ssl_weight: float,
        hidden_dims: list[int],
        dropout: float,
    ) -> None:
        super().__init__()
        self.student_num = student_num
        self.exercise_num = exercise_num
        self.knowledge_num = knowledge_num
        self.gcn_layers = gcn_layers
        self.ssl_temperature = ssl_temperature
        self.ssl_weight = ssl_weight
        self.student_embedding = nn.Embedding(student_num, latent_dim)
        self.exercise_embedding = nn.Embedding(exercise_num, latent_dim)
        self.knowledge_embedding = nn.Embedding(knowledge_num, latent_dim)
        self.discrimination_embedding = nn.Embedding(exercise_num, 1)
        self.concat_layer = nn.Linear(2 * latent_dim, latent_dim)
        self.student_transfer = nn.Linear(latent_dim, knowledge_num)
        self.exercise_transfer = nn.Linear(latent_dim, knowledge_num)
        self.knowledge_transfer = nn.Linear(latent_dim, knowledge_num)
        self.interaction = NCDInteraction(knowledge_num, hidden_dims, dropout)
        self.apply(_xavier_initialise)

    def _all_embeddings(self) -> torch.Tensor:
        return torch.cat(
            [
                self.student_embedding.weight,
                self.exercise_embedding.weight,
                self.knowledge_embedding.weight,
            ],
            dim=0,
        )

    def _convolve(self, right: torch.Tensor, wrong: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
        all_embeddings = self._all_embeddings()
        aggregated = [all_embeddings]
        right_embeddings = all_embeddings
        wrong_embeddings = all_embeddings
        for _ in range(self.gcn_layers):
            right_embeddings = torch.sparse.mm(right, right_embeddings)
            wrong_embeddings = torch.sparse.mm(wrong, wrong_embeddings)
            all_embeddings = self.concat_layer(torch.cat([right_embeddings, wrong_embeddings], dim=1))
            aggregated.append(all_embeddings)
        output = torch.stack(aggregated, dim=1).mean(dim=1)
        return (
            output[: self.student_num],
            output[self.student_num : self.student_num + self.exercise_num],
            output[self.student_num + self.exercise_num :],
        )

    def _prediction_from_embeddings(
        self,
        student_ids: torch.Tensor,
        exercise_ids: torch.Tensor,
        q_mask: torch.Tensor,
        right: torch.Tensor,
        wrong: torch.Tensor,
    ) -> tuple[torch.Tensor, tuple[torch.Tensor, torch.Tensor, torch.Tensor]]:
        student_all, exercise_all, knowledge_all = self._convolve(right, wrong)
        prediction = self.interaction(
            self.student_transfer(student_all.index_select(0, student_ids)),
            self.exercise_transfer(exercise_all.index_select(0, exercise_ids)),
            self.discrimination_embedding(exercise_ids),
            q_mask,
        )
        return prediction, (student_all, exercise_all, knowledge_all)

    @staticmethod
    def _info_nce(view_one: torch.Tensor, view_two: torch.Tensor, temperature: float) -> torch.Tensor:
        logits = (view_one @ view_two.T) / temperature
        return -torch.diagonal(torch.log_softmax(logits, dim=1)).mean()

    def forward(
        self,
        student_ids: torch.Tensor,
        exercise_ids: torch.Tensor,
        q_mask: torch.Tensor,
        graph: ResponseGraph,
        flipped_graph: ResponseGraph | None = None,
        ssl_student_ids: torch.Tensor | None = None,
    ) -> tuple[torch.Tensor, torch.Tensor]:
        prediction, normal_embeddings = self._prediction_from_embeddings(
            student_ids, exercise_ids, q_mask, graph.right, graph.wrong
        )
        if flipped_graph is None:
            return prediction, torch.zeros((), device=prediction.device)
        flipped_students, flipped_exercises, _ = self._convolve(
            flipped_graph.right, flipped_graph.wrong
        )
        if ssl_student_ids is None:
            ssl_students = normal_embeddings[0]
            ssl_flipped_students = flipped_students
        else:
            ssl_students = normal_embeddings[0].index_select(0, ssl_student_ids)
            ssl_flipped_students = flipped_students.index_select(0, ssl_student_ids)
        extra_loss = self.ssl_weight * (
            self._info_nce(ssl_students, ssl_flipped_students, self.ssl_temperature)
            + self._info_nce(normal_embeddings[1], flipped_exercises, self.ssl_temperature)
        )
        return prediction, extra_loss

    def mastery(self, graph: ResponseGraph) -> torch.Tensor:
        students, _, _ = self._convolve(graph.right, graph.wrong)
        return torch.sigmoid(self.student_transfer(students))

    def neutralize_students(self, student_ids: torch.Tensor) -> None:
        with torch.no_grad():
            self.student_embedding.weight.index_fill_(0, student_ids, 0.0)

    def enforce_monotonicity(self) -> None:
        self.interaction.enforce_monotonicity()

