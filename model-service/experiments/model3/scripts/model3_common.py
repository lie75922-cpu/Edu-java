"""Shared, auditable helpers for the MODEL-3 final-validation protocol.

The module deliberately implements only an ExerciseRate baseline, Rasch/IRT-1PL,
and a MAP-L2 regularized student-Topic deviation extension.  Final-response
materialisation is kept separate from development fitting so the runner can
fail closed before it opens the final correctness column.
"""

from __future__ import annotations

import copy
import csv
import importlib.metadata
import json
import os
import random
import time
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

import numpy as np
import psutil
import torch
from sklearn.metrics import accuracy_score, log_loss, roc_auc_score
from torch import nn
from torch.nn import functional as functional


REPOSITORY_ROOT = Path(__file__).resolve().parents[4]
MODEL3_ROOT = REPOSITORY_ROOT / "model-service/experiments/model3"
CONFIG_ROOT = MODEL3_ROOT / "configs"
MANIFEST_ROOT = MODEL3_ROOT / "manifests"
REPORT_ROOT = MODEL3_ROOT / "reports"
DERIVED_ROOT = MODEL3_ROOT / "data"
CHECKPOINT_ROOT = MODEL3_ROOT / "checkpoints"
RUNTIME_ROOT = MODEL3_ROOT / "runtime"

RAW_METADATA_COLUMNS = [
    "name",
    "live",
    "prerequisites",
    "h_position",
    "v_position",
    "creation_date",
    "seconds_per_fast_problem",
    "pretty_display_name",
    "short_display_name",
    "topic",
    "area",
]
RAW_LOG_COLUMNS = [
    "user_id",
    "exercise",
    "problem_type",
    "problem_number",
    "topic_mode",
    "suggested",
    "review_mode",
    "time_done",
    "time_taken",
    "time_taken_attempts",
    "correct",
    "count_attempts",
    "hint_used",
    "count_hints",
    "hint_time_taken_list",
    "earned_proficiency",
    "points_earned",
]
MEDIUM_EXERCISE_FIELDS = {
    "area",
    "display_name",
    "exercise_external_id",
    "live",
    "prerequisite_raw",
    "topic",
}
MEDIUM_INTERACTION_FIELDS = {
    "attempts",
    "correct",
    "count_hints",
    "duration_seconds",
    "earned_proficiency",
    "exercise_external_id",
    "hint_used",
    "occurred_at",
    "student_external_id",
}


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, value: Any) -> None:
    """Write a small tracked evidence file atomically without content hashes."""

    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2, sort_keys=True)
        handle.write("\n")
    temporary.replace(path)


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")


def normalise(value: Any) -> str:
    return value.strip() if isinstance(value, str) else ""


def parse_timestamp(value: str) -> int | None:
    try:
        parsed = int(value.strip())
    except ValueError:
        return None
    return parsed if parsed >= 0 else None


def parse_correct(value: str) -> int:
    accepted = {"true": 1, "1": 1, "false": 0, "0": 0}
    parsed = accepted.get(normalise(value).lower())
    if parsed is None:
        raise ValueError("Final eligible row has a non-binary correctness value")
    return parsed


def set_deterministic_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.backends.cudnn.benchmark = False
    torch.backends.cudnn.deterministic = True
    torch.use_deterministic_algorithms(True, warn_only=True)


def runtime_versions() -> dict[str, str]:
    versions = {"python": os.sys.version.split()[0], "torch": torch.__version__}
    for distribution in ("numpy", "scikit-learn", "psutil"):
        try:
            versions[distribution] = importlib.metadata.version(distribution)
        except importlib.metadata.PackageNotFoundError:
            versions[distribution] = "UNAVAILABLE"
    return versions


@dataclass(frozen=True)
class InteractionSplit:
    students: np.ndarray
    exercises: np.ndarray
    topics: np.ndarray
    outcomes: np.ndarray

    @property
    def size(self) -> int:
        return int(self.outcomes.size)

    def subset(self, mask: np.ndarray) -> "InteractionSplit":
        checked = np.asarray(mask, dtype=bool)
        if checked.shape != self.outcomes.shape:
            raise ValueError("Interaction mask is not aligned to outcomes")
        return InteractionSplit(
            students=self.students[checked],
            exercises=self.exercises[checked],
            topics=self.topics[checked],
            outcomes=self.outcomes[checked],
        )


@dataclass(frozen=True)
class DevelopmentProtocol:
    train: InteractionSplit
    valid: InteractionSplit
    diagnostic: InteractionSplit
    exercise_index: dict[str, int]
    topic_index: dict[str, int]

    @property
    def student_count(self) -> int:
        return int(max(
            self.train.students.max(), self.valid.students.max(), self.diagnostic.students.max()
        )) + 1

    @property
    def exercise_count(self) -> int:
        return len(self.exercise_index)

    @property
    def topic_count(self) -> int:
        return len(self.topic_index)


@dataclass(frozen=True)
class FinalMaterialization:
    calibration: InteractionSplit
    evaluation: InteractionSplit
    total_interactions_by_student: np.ndarray
    calibration_interactions_by_student: np.ndarray
    evaluation_interactions_by_student: np.ndarray
    source_rows_read: int
    eligible_rows_materialized: int


def _as_contiguous_index(payload: Any, expected_name: str) -> dict[str, int]:
    if not isinstance(payload, dict) or not payload:
        raise ValueError(f"{expected_name} index map must be a nonempty object")
    direct: dict[str, int] = {}
    if all(isinstance(key, str) and isinstance(value, int) for key, value in payload.items()):
        direct = {key: int(value) for key, value in payload.items()}
    elif all(isinstance(key, str) and key.isdigit() and isinstance(value, str) for key, value in payload.items()):
        direct = {value: int(key) for key, value in payload.items()}
    else:
        raise ValueError(f"{expected_name} index map has an unsupported schema")
    values = sorted(direct.values())
    if values != list(range(len(values))) or len(set(direct)) != len(direct):
        raise ValueError(f"{expected_name} index map is not a contiguous one-to-one mapping")
    return direct


def load_interactions(path: Path) -> InteractionSplit:
    students: list[int] = []
    exercises: list[int] = []
    topics: list[int] = []
    outcomes: list[float] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        expected = {"student_id", "exercise_id", "topic_id", "correct"}
        if set(reader.fieldnames or []) != expected:
            raise ValueError(f"Unexpected interaction schema in {path}: {reader.fieldnames}")
        for row_number, row in enumerate(reader, start=2):
            try:
                student = int(row["student_id"])
                exercise = int(row["exercise_id"])
                topic = int(row["topic_id"])
                correct = int(row["correct"])
            except (TypeError, ValueError) as exc:
                raise ValueError(f"Malformed interaction at {path}:{row_number}") from exc
            if min(student, exercise, topic) < 0 or correct not in (0, 1):
                raise ValueError(f"Invalid interaction value at {path}:{row_number}")
            students.append(student)
            exercises.append(exercise)
            topics.append(topic)
            outcomes.append(float(correct))
    if not outcomes:
        raise ValueError(f"No interaction rows in {path}")
    return InteractionSplit(
        students=np.asarray(students, dtype=np.int64),
        exercises=np.asarray(exercises, dtype=np.int64),
        topics=np.asarray(topics, dtype=np.int64),
        outcomes=np.asarray(outcomes, dtype=np.float64),
    )


def combine_splits(*splits: InteractionSplit) -> InteractionSplit:
    if not splits:
        raise ValueError("At least one interaction split is required")
    return InteractionSplit(
        students=np.concatenate([split.students for split in splits]),
        exercises=np.concatenate([split.exercises for split in splits]),
        topics=np.concatenate([split.topics for split in splits]),
        outcomes=np.concatenate([split.outcomes for split in splits]),
    )


def load_development_protocol(model1_asset_root: Path) -> DevelopmentProtocol:
    protocol_root = model1_asset_root / "data/junyi_external_holdout_v1"
    required = {
        "student_index": protocol_root / "student_index_map.json",
        "exercise_index": protocol_root / "exercise_index_map.json",
        "topic_index": protocol_root / "topic_index_map.json",
        "train": protocol_root / "interactions_train.csv",
        "valid": protocol_root / "interactions_valid.csv",
        "diagnostic": protocol_root / "interactions_test.csv",
    }
    missing = [name for name, path in required.items() if not path.is_file()]
    if missing:
        raise FileNotFoundError(f"Missing local MODEL-1 development assets: {missing}")
    student_index = _as_contiguous_index(read_json(required["student_index"]), "student")
    exercise_index = _as_contiguous_index(read_json(required["exercise_index"]), "exercise")
    topic_index = _as_contiguous_index(read_json(required["topic_index"]), "topic")
    train = load_interactions(required["train"])
    valid = load_interactions(required["valid"])
    diagnostic = load_interactions(required["diagnostic"])
    for name, split in (("train", train), ("valid", valid), ("diagnostic", diagnostic)):
        if (
            split.students.max() >= len(student_index)
            or split.exercises.max() >= len(exercise_index)
            or split.topics.max() >= len(topic_index)
        ):
            raise ValueError(f"MODEL-1 {name} rows exceed frozen index cardinalities")
    return DevelopmentProtocol(train, valid, diagnostic, exercise_index, topic_index)


def load_membership(path: Path, dataset_name: str, expected_count: int) -> set[str]:
    payload = read_json(path)
    members = payload.get("members") if isinstance(payload, dict) else None
    if payload.get("dataset_name") != dataset_name or not isinstance(members, list):
        raise ValueError(f"Unexpected membership schema at {path}")
    if len(members) != expected_count or len(set(members)) != expected_count:
        raise ValueError(f"Membership at {path} must contain {expected_count} unique students")
    if not all(isinstance(member, str) and member for member in members):
        raise ValueError(f"Membership at {path} contains an invalid external student identity")
    return set(members)


def load_original_medium_membership(path: Path) -> set[str]:
    payload = read_json(path)
    if set(payload) != {"train", "valid", "test"}:
        raise ValueError("Original Medium membership must contain train, valid, and test")
    expected = {"train": 7000, "valid": 1000, "test": 2000}
    members: list[str] = []
    for split, required_count in expected.items():
        values = payload[split]
        if not isinstance(values, list) or len(values) != required_count:
            raise ValueError(f"Original Medium {split} membership has an unexpected count")
        if not all(isinstance(value, str) and value for value in values):
            raise ValueError(f"Original Medium {split} membership contains an invalid member")
        members.extend(values)
    if len(set(members)) != 10000:
        raise ValueError("Original Medium membership contains a duplicate or overlap")
    return set(members)


def assert_final_membership_disjoint(
    final_members: set[str], model1_members: set[str], medium_members: set[str]
) -> dict[str, int]:
    """Fail the final protocol if any frozen final student overlaps prior cohorts."""

    model1_overlap = len(final_members & model1_members)
    medium_overlap = len(final_members & medium_members)
    if model1_overlap or medium_overlap:
        raise RuntimeError("Frozen final membership overlaps a prior research cohort")
    return {
        "final_member_count": len(final_members),
        "model1_overlap": model1_overlap,
        "original_medium_overlap": medium_overlap,
    }


def iter_jsonl(path: Path) -> Iterable[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                raise ValueError(f"Blank JSONL row at {path}:{line_number}")
            value = json.loads(line)
            if not isinstance(value, dict):
                raise ValueError(f"Non-object JSONL row at {path}:{line_number}")
            yield value


def load_medium_topic_scope(medium_root: Path) -> tuple[dict[str, str], set[str]]:
    topic_by_exercise: dict[str, str] = {}
    for record in iter_jsonl(medium_root / "exercises.jsonl"):
        if set(record) != MEDIUM_EXERCISE_FIELDS:
            raise ValueError("Frozen Medium Exercise schema differs from the audited contract")
        exercise = record["exercise_external_id"]
        topic = record["topic"]
        if not isinstance(exercise, str) or not exercise or not isinstance(topic, str) or not topic.strip():
            raise ValueError("Frozen Medium contains an invalid Exercise-to-Topic record")
        if exercise in topic_by_exercise:
            raise ValueError("Frozen Medium contains duplicate Exercise metadata identity")
        topic_by_exercise[exercise] = topic.strip()
    observed: set[str] = set()
    for record in iter_jsonl(medium_root / "interactions.jsonl"):
        if set(record) != MEDIUM_INTERACTION_FIELDS:
            raise ValueError("Frozen Medium interaction schema differs from the audited contract")
        exercise = record["exercise_external_id"]
        if not isinstance(exercise, str) or not exercise:
            raise ValueError("Frozen Medium contains an invalid interaction Exercise")
        if exercise in topic_by_exercise:
            observed.add(exercise)
    if len(topic_by_exercise) != 815 or len(observed) != 624:
        raise ValueError("Frozen Medium Exercise scope does not match the MODEL-3 contract")
    if len(set(topic_by_exercise.values())) != 40:
        raise ValueError("Frozen Medium does not retain the 40-Topic display layer")
    return topic_by_exercise, observed


def audit_metadata_topic_scope(
    metadata_path: Path, topic_by_exercise: Mapping[str, str], eligible_exercises: set[str]
) -> dict[str, int]:
    grouped: dict[str, list[str]] = defaultdict(list)
    with metadata_path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != RAW_METADATA_COLUMNS:
            raise ValueError(f"Unexpected metadata header at {metadata_path}: {reader.fieldnames}")
        for row_number, row in enumerate(reader, start=2):
            if None in row:
                raise ValueError(f"Malformed metadata row at {metadata_path}:{row_number}")
            grouped[normalise(row["name"])].append(normalise(row["topic"]))
    mismatches: list[str] = []
    for exercise in sorted(eligible_exercises):
        values = grouped.get(exercise, [])
        if len(values) != 1 or values[0] != topic_by_exercise[exercise]:
            mismatches.append(exercise)
    if mismatches:
        raise ValueError("Frozen eligible Exercise identity or Topic differs from raw metadata")
    return {
        "metadata_rows": sum(len(values) for values in grouped.values()),
        "distinct_nonempty_exercise_ids": len([name for name in grouped if name]),
        "eligible_exercises_checked": len(eligible_exercises),
        "identity_topic_mismatch_count": len(mismatches),
    }


def build_final_exercise_scope(
    development: DevelopmentProtocol, medium_root: Path
) -> dict[str, tuple[int, int]]:
    topic_by_exercise, observed_exercises = load_medium_topic_scope(medium_root)
    if set(development.exercise_index) != observed_exercises:
        raise ValueError("MODEL-1 Exercise identity does not equal frozen final eligible scope")
    if set(development.topic_index) != set(topic_by_exercise.values()):
        raise ValueError("MODEL-1 Topic identity does not equal frozen final eligible scope")
    output: dict[str, tuple[int, int]] = {}
    for exercise in observed_exercises:
        topic = topic_by_exercise[exercise]
        output[exercise] = (development.exercise_index[exercise], development.topic_index[topic])
    return output


def clip_probability(predictions: np.ndarray | Sequence[float]) -> np.ndarray:
    return np.clip(np.asarray(predictions, dtype=np.float64), 1e-7, 1.0 - 1e-7)


def binary_metrics(labels: np.ndarray, predictions: np.ndarray, threshold: float = 0.5) -> dict[str, float | None]:
    values = np.asarray(labels, dtype=np.float64)
    safe = clip_probability(predictions)
    if values.shape != safe.shape:
        raise ValueError("Labels and predictions do not have matching shapes")
    if values.size == 0:
        raise ValueError("Cannot score an empty evaluation split")
    return {
        "auc": float(roc_auc_score(values, safe)) if len(np.unique(values)) > 1 else None,
        "acc": float(accuracy_score(values, safe >= threshold)),
        "rmse": float(np.sqrt(np.mean(np.square(values - safe)))),
        "log_loss": float(log_loss(values, safe, labels=[0, 1])),
        "brier": float(np.mean(np.square(values - safe))),
    }


def distribution(values: np.ndarray | Sequence[float]) -> dict[str, float | int | None]:
    finite = np.asarray(values, dtype=np.float64)
    finite = finite[np.isfinite(finite)]
    if finite.size == 0:
        return {
            "count": 0,
            "min": None,
            "p05": None,
            "p25": None,
            "median": None,
            "p75": None,
            "p95": None,
            "max": None,
            "mean": None,
            "std": None,
        }
    return {
        "count": int(finite.size),
        "min": float(finite.min()),
        "p05": float(np.quantile(finite, 0.05)),
        "p25": float(np.quantile(finite, 0.25)),
        "median": float(np.median(finite)),
        "p75": float(np.quantile(finite, 0.75)),
        "p95": float(np.quantile(finite, 0.95)),
        "max": float(finite.max()),
        "mean": float(finite.mean()),
        "std": float(finite.std()),
    }


def calibration_bins(labels: np.ndarray, predictions: np.ndarray, bin_count: int) -> list[dict[str, float | int | None]]:
    if bin_count < 2:
        raise ValueError("Calibration requires at least two bins")
    values = np.asarray(labels, dtype=np.float64)
    safe = clip_probability(predictions)
    rows: list[dict[str, float | int | None]] = []
    for index in range(bin_count):
        lower = index / bin_count
        upper = (index + 1) / bin_count
        selected = (safe >= lower) & (safe < upper if index < bin_count - 1 else safe <= upper)
        if not selected.any():
            rows.append({
                "bin": index + 1,
                "lower": lower,
                "upper": upper,
                "count": 0,
                "mean_prediction": None,
                "outcome_rate": None,
                "absolute_gap": None,
            })
            continue
        mean_prediction = float(safe[selected].mean())
        outcome_rate = float(values[selected].mean())
        rows.append({
            "bin": index + 1,
            "lower": lower,
            "upper": upper,
            "count": int(selected.sum()),
            "mean_prediction": mean_prediction,
            "outcome_rate": outcome_rate,
            "absolute_gap": abs(mean_prediction - outcome_rate),
        })
    return rows


def expected_calibration_error(bins: Sequence[Mapping[str, float | int | None]], total_rows: int) -> float:
    if total_rows < 1:
        raise ValueError("Expected calibration error requires rows")
    weighted_gap = 0.0
    for row in bins:
        count = int(row["count"])
        gap = row["absolute_gap"]
        if count and gap is not None:
            weighted_gap += count * float(gap)
    return weighted_gap / total_rows


def metrics_with_calibration(
    labels: np.ndarray, predictions: np.ndarray, threshold: float, bin_count: int
) -> dict[str, Any]:
    bins = calibration_bins(labels, predictions, bin_count)
    return {
        "metrics": binary_metrics(labels, predictions, threshold),
        "calibration_bins": bins,
        "expected_calibration_error": expected_calibration_error(bins, int(len(labels))),
    }


@dataclass
class ResourceTracker:
    process: psutil.Process
    peak_rss_bytes: int = 0

    @classmethod
    def start(cls) -> "ResourceTracker":
        tracker = cls(process=psutil.Process(os.getpid()))
        tracker.sample()
        return tracker

    def sample(self) -> None:
        self.peak_rss_bytes = max(self.peak_rss_bytes, self.process.memory_info().rss)

    def report(self) -> dict[str, Any]:
        self.sample()
        return {
            "peak_process_rss_bytes": self.peak_rss_bytes,
            "gpu": "none",
            "peak_gpu_allocated_bytes": None,
            "peak_gpu_reserved_bytes": None,
        }


@dataclass(frozen=True)
class ScoreOrder:
    order: np.ndarray
    tie_starts: np.ndarray


def score_order(predictions: np.ndarray) -> ScoreOrder:
    safe = clip_probability(predictions)
    order = np.argsort(safe, kind="mergesort")
    sorted_scores = safe[order]
    tie_starts = np.concatenate([
        np.asarray([0], dtype=np.int64),
        np.flatnonzero(np.diff(sorted_scores) != 0.0).astype(np.int64) + 1,
    ])
    return ScoreOrder(order=order, tie_starts=tie_starts)


def weighted_auc_from_order(labels: np.ndarray, ordering: ScoreOrder, row_weights: np.ndarray) -> float | None:
    ordered_labels = np.asarray(labels, dtype=np.int8)[ordering.order]
    weights = np.asarray(row_weights, dtype=np.float64)[ordering.order]
    positive = weights * (ordered_labels == 1)
    negative = weights * (ordered_labels == 0)
    total_positive = float(positive.sum())
    total_negative = float(negative.sum())
    if total_positive == 0.0 or total_negative == 0.0:
        return None
    grouped_positive = np.add.reduceat(positive, ordering.tie_starts)
    grouped_negative = np.add.reduceat(negative, ordering.tie_starts)
    negative_before = np.concatenate([[0.0], np.cumsum(grouped_negative)[:-1]])
    numerator = float(np.sum(grouped_positive * (negative_before + 0.5 * grouped_negative)))
    return numerator / (total_positive * total_negative)


def student_cluster_paired_bootstrap_auc_deltas(
    labels: np.ndarray,
    predictions: Mapping[str, np.ndarray],
    comparisons: Mapping[str, tuple[str, str]],
    student_ids: np.ndarray,
    resamples: int,
    confidence_level: float,
    seed: int,
) -> dict[str, dict[str, float | int | None | str]]:
    """Paired AUC intervals that retain complete student response clusters."""

    labels_array = np.asarray(labels, dtype=np.int8)
    students = np.asarray(student_ids, dtype=np.int64)
    if labels_array.ndim != 1 or labels_array.shape != students.shape:
        raise ValueError("Cluster bootstrap labels and student IDs must be aligned one-dimensional arrays")
    if resamples < 1:
        raise ValueError("Cluster bootstrap requires at least one resample")
    unique_students, inverse = np.unique(students, return_inverse=True)
    if unique_students.size < 2:
        raise ValueError("Cluster bootstrap requires at least two students")
    orders = {name: score_order(value) for name, value in predictions.items()}
    for name, prediction in predictions.items():
        if np.asarray(prediction).shape != labels_array.shape:
            raise ValueError(f"Prediction {name} is not aligned to labels")
    for comparison, (candidate_name, baseline_name) in comparisons.items():
        if candidate_name not in orders or baseline_name not in orders:
            raise ValueError(f"Comparison {comparison} references an unknown prediction")
    observed_auc = {
        name: float(roc_auc_score(labels_array, clip_probability(value))) if len(np.unique(labels_array)) > 1 else None
        for name, value in predictions.items()
    }
    samples: dict[str, list[float]] = {name: [] for name in comparisons}
    rng = np.random.default_rng(seed)
    for _ in range(resamples):
        sampled = rng.integers(0, unique_students.size, size=unique_students.size)
        cluster_multiplicity = np.bincount(sampled, minlength=unique_students.size)
        row_weights = cluster_multiplicity[inverse]
        auc_values = {
            name: weighted_auc_from_order(labels_array, ordering, row_weights)
            for name, ordering in orders.items()
        }
        for name, (candidate_name, baseline_name) in comparisons.items():
            candidate_auc = auc_values[candidate_name]
            baseline_auc = auc_values[baseline_name]
            if candidate_auc is not None and baseline_auc is not None:
                samples[name].append(candidate_auc - baseline_auc)
    alpha = (1.0 - confidence_level) / 2.0
    method = (
        "Student-cluster paired bootstrap: students are resampled with replacement, "
        "and every selected student's evaluation rows retain the same cluster multiplicity."
    )
    result: dict[str, dict[str, float | int | None | str]] = {}
    for name, (candidate_name, baseline_name) in comparisons.items():
        deltas = samples[name]
        candidate_auc = observed_auc[candidate_name]
        baseline_auc = observed_auc[baseline_name]
        result[name] = {
            "candidate": candidate_name,
            "baseline": baseline_name,
            "observed_auc_delta": (candidate_auc - baseline_auc)
            if candidate_auc is not None and baseline_auc is not None
            else None,
            "lower_bound": float(np.quantile(deltas, alpha)) if deltas else None,
            "upper_bound": float(np.quantile(deltas, 1.0 - alpha)) if deltas else None,
            "resamples_requested": resamples,
            "resamples_valid": len(deltas),
            "confidence_level": confidence_level,
            "student_cluster_count": int(unique_students.size),
            "method": method,
        }
    return result


def metric_text(value: float | int | None, digits: int = 6) -> str:
    if value is None:
        return "N/A"
    if isinstance(value, int):
        return str(value)
    return f"{value:.{digits}f}"


class JointRasch(nn.Module):
    """Scalar Rasch/IRT-1PL parameters for the development-only cohort."""

    def __init__(self, student_count: int, exercise_count: int) -> None:
        super().__init__()
        self.ability = nn.Embedding(student_count, 1)
        self.difficulty = nn.Embedding(exercise_count, 1)
        self.intercept = nn.Parameter(torch.zeros(1))
        nn.init.zeros_(self.ability.weight)
        nn.init.zeros_(self.difficulty.weight)

    def logits(self, students: torch.Tensor, exercises: torch.Tensor, topics: torch.Tensor | None = None) -> torch.Tensor:
        del topics
        return (
            self.intercept
            + self.ability(students).reshape(-1)
            - self.difficulty(exercises).reshape(-1)
        )

    def forward(self, students: torch.Tensor, exercises: torch.Tensor, topics: torch.Tensor | None = None) -> torch.Tensor:
        return torch.sigmoid(self.logits(students, exercises, topics))

    def center_parameters(self) -> None:
        """Keep the two Rasch location terms identifiable without changing logits."""

        with torch.no_grad():
            ability_offset = self.ability.weight.mean()
            difficulty_offset = self.difficulty.weight.mean()
            self.ability.weight.sub_(ability_offset)
            self.difficulty.weight.sub_(difficulty_offset)
            self.intercept.add_(ability_offset - difficulty_offset)


class HierarchicalRasch(JointRasch):
    """Rasch plus one explicitly regularized scalar delta for each student-Topic pair."""

    def __init__(self, student_count: int, exercise_count: int, topic_count: int) -> None:
        super().__init__(student_count, exercise_count)
        self.topic_count = int(topic_count)
        self.delta = nn.Embedding(student_count * topic_count, 1)
        nn.init.zeros_(self.delta.weight)

    def delta_indices(self, students: torch.Tensor, topics: torch.Tensor) -> torch.Tensor:
        return students * self.topic_count + topics

    def delta_values(self, students: torch.Tensor, topics: torch.Tensor) -> torch.Tensor:
        return self.delta(self.delta_indices(students, topics)).reshape(-1)

    def logits(self, students: torch.Tensor, exercises: torch.Tensor, topics: torch.Tensor | None = None) -> torch.Tensor:
        if topics is None:
            raise ValueError("Hierarchical Rasch requires Topic indices")
        return super().logits(students, exercises) + self.delta_values(students, topics)


@dataclass
class FittedJointModel:
    model: JointRasch
    selected_epoch: int
    validation_metrics: dict[str, float | None] | None
    epoch_records: list[dict[str, Any]]
    training_wall_seconds: float
    resources: dict[str, Any]


def _model_for_kind(
    kind: str, student_count: int, exercise_count: int, topic_count: int
) -> JointRasch:
    if kind == "RASCH_1PL_V1":
        return JointRasch(student_count, exercise_count)
    if kind == "HIER_RASCH_TOPIC_V1":
        return HierarchicalRasch(student_count, exercise_count, topic_count)
    raise ValueError(f"Unsupported MODEL-3 candidate: {kind}")


def _candidate_is_better(
    candidate: Mapping[str, float | None],
    current: Mapping[str, float | None] | None,
    candidate_epoch: int,
    current_epoch: int | None,
) -> bool:
    if current is None or current_epoch is None:
        return True
    candidate_auc = candidate["auc"] if candidate["auc"] is not None else float("-inf")
    current_auc = current["auc"] if current["auc"] is not None else float("-inf")
    if candidate_auc != current_auc:
        return bool(candidate_auc > current_auc)
    candidate_loss = candidate["log_loss"] if candidate["log_loss"] is not None else float("inf")
    current_loss = current["log_loss"] if current["log_loss"] is not None else float("inf")
    if candidate_loss != current_loss:
        return bool(candidate_loss < current_loss)
    candidate_brier = candidate["brier"] if candidate["brier"] is not None else float("inf")
    current_brier = current["brier"] if current["brier"] is not None else float("inf")
    if candidate_brier != current_brier:
        return bool(candidate_brier < current_brier)
    return candidate_epoch < current_epoch


def _predict_joint(
    model: JointRasch,
    split: InteractionSplit,
    batch_size: int,
    tracker: ResourceTracker,
) -> tuple[np.ndarray, dict[str, float]]:
    model.eval()
    started = time.perf_counter()
    chunks: list[np.ndarray] = []
    with torch.no_grad():
        for start in range(0, split.size, batch_size):
            end = min(split.size, start + batch_size)
            students = torch.as_tensor(split.students[start:end], dtype=torch.long)
            exercises = torch.as_tensor(split.exercises[start:end], dtype=torch.long)
            topics = torch.as_tensor(split.topics[start:end], dtype=torch.long)
            chunks.append(model(students, exercises, topics).detach().cpu().numpy())
            tracker.sample()
    elapsed = time.perf_counter() - started
    return np.concatenate(chunks), {
        "total_seconds": elapsed,
        "milliseconds_per_interaction": elapsed * 1000 / split.size,
    }


def fit_joint_model(
    train: InteractionSplit,
    valid: InteractionSplit,
    student_count: int,
    exercise_count: int,
    topic_count: int,
    kind: str,
    config: Mapping[str, Any],
    delta_lambda: float | None,
    *,
    fixed_epochs: int | None = None,
) -> FittedJointModel:
    """Fit only the allowed scalar-model parameters on development data.

    With ``fixed_epochs`` omitted, validation chooses the epoch.  With it set,
    the method is a refit: it performs no model selection and has no access to
    final-holdout rows.
    """

    if kind == "HIER_RASCH_TOPIC_V1" and (delta_lambda is None or delta_lambda <= 0.0):
        raise ValueError("Hierarchical Rasch requires a positive frozen lambda_delta")
    if kind == "RASCH_1PL_V1" and delta_lambda is not None:
        raise ValueError("Rasch/IRT-1PL must not receive a Topic-deviation penalty")
    total_epochs = int(fixed_epochs if fixed_epochs is not None else config["max_epochs"])
    if total_epochs < 1:
        raise ValueError("MODEL-3 training needs at least one epoch")
    set_deterministic_seed(int(config["seed"]))
    model = _model_for_kind(kind, student_count, exercise_count, topic_count)
    optimizer = torch.optim.Adam(
        model.parameters(),
        lr=float(config["learning_rate"]),
        weight_decay=float(config["weight_decay"]),
    )
    tracker = ResourceTracker.start()
    rng = np.random.default_rng(int(config["seed"]))
    batch_size = int(config["batch_size"])
    best_state: dict[str, torch.Tensor] | None = None
    best_metrics: dict[str, float | None] | None = None
    best_epoch: int | None = None
    no_improvement = 0
    records: list[dict[str, Any]] = []
    started = time.perf_counter()
    for epoch in range(1, total_epochs + 1):
        model.train()
        order = np.arange(train.size, dtype=np.int64)
        rng.shuffle(order)
        losses: list[float] = []
        for start in range(0, train.size, batch_size):
            batch = order[start : start + batch_size]
            students = torch.as_tensor(train.students[batch], dtype=torch.long)
            exercises = torch.as_tensor(train.exercises[batch], dtype=torch.long)
            topics = torch.as_tensor(train.topics[batch], dtype=torch.long)
            outcomes = torch.as_tensor(train.outcomes[batch], dtype=torch.float32)
            prediction = model(students, exercises, topics)
            loss = functional.binary_cross_entropy(prediction, outcomes)
            if isinstance(model, HierarchicalRasch):
                penalty = model.delta_values(students, topics).square().mean()
                loss = loss + float(delta_lambda) * penalty
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            optimizer.step()
            model.center_parameters()
            losses.append(float(loss.detach().cpu()))
            tracker.sample()
        if fixed_epochs is not None:
            records.append({"epoch": epoch, "mean_train_objective": float(np.mean(losses))})
            continue
        valid_prediction, valid_latency = _predict_joint(model, valid, batch_size, tracker)
        validation_metrics = binary_metrics(valid.outcomes, valid_prediction)
        records.append({
            "epoch": epoch,
            "mean_train_objective": float(np.mean(losses)),
            "validation_metrics": validation_metrics,
            "validation_inference": valid_latency,
        })
        if _candidate_is_better(validation_metrics, best_metrics, epoch, best_epoch):
            best_state = copy.deepcopy(model.state_dict())
            best_metrics = validation_metrics
            best_epoch = epoch
            no_improvement = 0
        else:
            no_improvement += 1
            if no_improvement >= int(config["early_stopping_patience"]):
                break
    if fixed_epochs is None:
        if best_state is None or best_metrics is None or best_epoch is None:
            raise RuntimeError("Development model selection did not produce a validation state")
        model.load_state_dict(best_state, strict=True)
        selected_epoch = best_epoch
        validation_metrics = best_metrics
    else:
        selected_epoch = total_epochs
        validation_metrics = None
    return FittedJointModel(
        model=model,
        selected_epoch=selected_epoch,
        validation_metrics=validation_metrics,
        epoch_records=records,
        training_wall_seconds=time.perf_counter() - started,
        resources=tracker.report(),
    )


@dataclass(frozen=True)
class ExerciseRateParameters:
    rates: np.ndarray
    known: np.ndarray
    exposure: np.ndarray
    global_rate: float
    alpha: float
    beta: float


def fit_exercise_rate(
    split: InteractionSplit, exercise_count: int, alpha: float, beta: float, minimum_exposure: int
) -> ExerciseRateParameters:
    if alpha <= 0.0 or beta <= 0.0 or minimum_exposure < 1:
        raise ValueError("ExerciseRate smoothing and exposure policy must be positive")
    exposure = np.zeros(exercise_count, dtype=np.int64)
    correct = np.zeros(exercise_count, dtype=np.float64)
    np.add.at(exposure, split.exercises, 1)
    np.add.at(correct, split.exercises, split.outcomes)
    global_rate = float((correct.sum() + alpha) / (exposure.sum() + alpha + beta))
    rates = (correct + alpha) / (exposure + alpha + beta)
    known = exposure >= minimum_exposure
    return ExerciseRateParameters(rates, known, exposure, global_rate, alpha, beta)


def predict_exercise_rate(
    parameters: ExerciseRateParameters, exercises: np.ndarray
) -> tuple[np.ndarray, np.ndarray]:
    values = np.full(len(exercises), parameters.global_rate, dtype=np.float64)
    in_range = (exercises >= 0) & (exercises < len(parameters.rates))
    known = np.zeros(len(exercises), dtype=bool)
    known[in_range] = parameters.known[exercises[in_range]]
    selected = in_range & known
    values[selected] = parameters.rates[exercises[selected]]
    return values, known


@dataclass(frozen=True)
class GlobalParameters:
    exercise_rate: ExerciseRateParameters
    rasch_mu: float
    rasch_difficulty: np.ndarray
    hier_mu: float
    hier_difficulty: np.ndarray
    selected_lambda_delta: float


def global_parameters_from_refits(
    exercise_rate: ExerciseRateParameters,
    rasch: FittedJointModel,
    hierarchical: FittedJointModel,
    selected_lambda_delta: float,
) -> GlobalParameters:
    if not isinstance(hierarchical.model, HierarchicalRasch):
        raise TypeError("Hierarchical global refit is not a HierarchicalRasch model")
    return GlobalParameters(
        exercise_rate=exercise_rate,
        rasch_mu=float(rasch.model.intercept.detach().cpu().item()),
        rasch_difficulty=rasch.model.difficulty.weight.detach().cpu().numpy().reshape(-1).copy(),
        hier_mu=float(hierarchical.model.intercept.detach().cpu().item()),
        hier_difficulty=hierarchical.model.difficulty.weight.detach().cpu().numpy().reshape(-1).copy(),
        selected_lambda_delta=float(selected_lambda_delta),
    )


def save_global_parameters(path: Path, parameters: GlobalParameters) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(
        path,
        exercise_rate=parameters.exercise_rate.rates,
        exercise_known=parameters.exercise_rate.known.astype(np.int8),
        exercise_exposure=parameters.exercise_rate.exposure,
        exercise_global_rate=np.asarray([parameters.exercise_rate.global_rate]),
        exercise_alpha=np.asarray([parameters.exercise_rate.alpha]),
        exercise_beta=np.asarray([parameters.exercise_rate.beta]),
        rasch_mu=np.asarray([parameters.rasch_mu]),
        rasch_difficulty=parameters.rasch_difficulty,
        hier_mu=np.asarray([parameters.hier_mu]),
        hier_difficulty=parameters.hier_difficulty,
        selected_lambda_delta=np.asarray([parameters.selected_lambda_delta]),
    )


def load_global_parameters(path: Path) -> GlobalParameters:
    if not path.is_file():
        raise FileNotFoundError("Development-refit global parameter artifact is required before final validation")
    with np.load(path, allow_pickle=False) as payload:
        exercise = ExerciseRateParameters(
            rates=np.asarray(payload["exercise_rate"], dtype=np.float64),
            known=np.asarray(payload["exercise_known"], dtype=np.int8).astype(bool),
            exposure=np.asarray(payload["exercise_exposure"], dtype=np.int64),
            global_rate=float(np.asarray(payload["exercise_global_rate"])[0]),
            alpha=float(np.asarray(payload["exercise_alpha"])[0]),
            beta=float(np.asarray(payload["exercise_beta"])[0]),
        )
        return GlobalParameters(
            exercise_rate=exercise,
            rasch_mu=float(np.asarray(payload["rasch_mu"])[0]),
            rasch_difficulty=np.asarray(payload["rasch_difficulty"], dtype=np.float64),
            hier_mu=float(np.asarray(payload["hier_mu"])[0]),
            hier_difficulty=np.asarray(payload["hier_difficulty"], dtype=np.float64),
            selected_lambda_delta=float(np.asarray(payload["selected_lambda_delta"])[0]),
        )


class PersonalCalibration(nn.Module):
    """Final-cohort personal parameters with frozen global buffers.

    The global intercept and difficulty vector are buffers, deliberately absent
    from the optimizer.  This is the direct enforcement point for the final
    protocol's global-parameter freeze.
    """

    def __init__(
        self,
        student_count: int,
        topic_count: int,
        global_mu: float,
        global_difficulty: np.ndarray,
        hierarchical: bool,
    ) -> None:
        super().__init__()
        self.topic_count = int(topic_count)
        self.hierarchical = bool(hierarchical)
        self.theta = nn.Embedding(student_count, 1)
        nn.init.zeros_(self.theta.weight)
        if hierarchical:
            self.delta = nn.Embedding(student_count * topic_count, 1)
            nn.init.zeros_(self.delta.weight)
        else:
            self.delta = None
        self.register_buffer("global_mu", torch.tensor([global_mu], dtype=torch.float32))
        self.register_buffer("global_difficulty", torch.as_tensor(global_difficulty, dtype=torch.float32).clone())

    def delta_indices(self, students: torch.Tensor, topics: torch.Tensor) -> torch.Tensor:
        return students * self.topic_count + topics

    def delta_values(self, students: torch.Tensor, topics: torch.Tensor) -> torch.Tensor:
        if self.delta is None:
            return torch.zeros_like(students, dtype=torch.float32)
        return self.delta(self.delta_indices(students, topics)).reshape(-1)

    def logits(self, students: torch.Tensor, exercises: torch.Tensor, topics: torch.Tensor) -> torch.Tensor:
        return (
            self.global_mu
            + self.theta(students).reshape(-1)
            - self.global_difficulty.index_select(0, exercises)
            + self.delta_values(students, topics)
        )

    def forward(self, students: torch.Tensor, exercises: torch.Tensor, topics: torch.Tensor) -> torch.Tensor:
        return torch.sigmoid(self.logits(students, exercises, topics))


def topic_exposure_matrix(split: InteractionSplit, student_count: int, topic_count: int) -> np.ndarray:
    exposure = np.zeros((student_count, topic_count), dtype=np.int64)
    np.add.at(exposure, (split.students, split.topics), 1)
    return exposure


def assert_global_buffers_frozen(model: PersonalCalibration, snapshot: Mapping[str, torch.Tensor]) -> None:
    for name, expected in snapshot.items():
        current = getattr(model, name)
        if not torch.equal(current, expected):
            raise AssertionError(f"Final personal calibration modified frozen global parameter {name}")


def fit_personal_calibration(
    calibration: InteractionSplit,
    student_count: int,
    topic_count: int,
    global_mu: float,
    global_difficulty: np.ndarray,
    *,
    hierarchical: bool,
    delta_lambda: float | None,
    learning_rate: float,
    batch_size: int,
    epochs: int,
    theta_l2: float,
    seed: int,
    tracker: ResourceTracker,
) -> tuple[PersonalCalibration, dict[str, Any]]:
    if calibration.size < 1:
        raise ValueError("Final personal calibration requires calibration rows")
    if hierarchical and (delta_lambda is None or delta_lambda <= 0.0):
        raise ValueError("Hierarchical personal calibration requires selected positive lambda_delta")
    if not hierarchical and delta_lambda is not None:
        raise ValueError("Rasch personal calibration must not use a Topic deviation penalty")
    set_deterministic_seed(seed)
    model = PersonalCalibration(student_count, topic_count, global_mu, global_difficulty, hierarchical)
    frozen_snapshot = {
        "global_mu": model.global_mu.detach().clone(),
        "global_difficulty": model.global_difficulty.detach().clone(),
    }
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
    rng = np.random.default_rng(seed)
    started = time.perf_counter()
    epoch_objectives: list[float] = []
    for _ in range(epochs):
        order = np.arange(calibration.size, dtype=np.int64)
        rng.shuffle(order)
        objectives: list[float] = []
        model.train()
        for start in range(0, calibration.size, batch_size):
            batch = order[start : start + batch_size]
            students = torch.as_tensor(calibration.students[batch], dtype=torch.long)
            exercises = torch.as_tensor(calibration.exercises[batch], dtype=torch.long)
            topics = torch.as_tensor(calibration.topics[batch], dtype=torch.long)
            outcomes = torch.as_tensor(calibration.outcomes[batch], dtype=torch.float32)
            prediction = model(students, exercises, topics)
            objective = functional.binary_cross_entropy(prediction, outcomes)
            objective = objective + theta_l2 * model.theta(students).reshape(-1).square().mean()
            if model.delta is not None:
                objective = objective + float(delta_lambda) * model.delta_values(students, topics).square().mean()
            optimizer.zero_grad(set_to_none=True)
            objective.backward()
            optimizer.step()
            assert_global_buffers_frozen(model, frozen_snapshot)
            objectives.append(float(objective.detach().cpu()))
            tracker.sample()
        epoch_objectives.append(float(np.mean(objectives)))
    elapsed = time.perf_counter() - started
    return model, {
        "total_seconds": elapsed,
        "milliseconds_per_student": elapsed * 1000 / student_count,
        "epochs": epochs,
        "mean_objective_by_epoch": epoch_objectives,
        "frozen_global_parameters_verified": True,
    }


def predict_personal(
    model: PersonalCalibration,
    split: InteractionSplit,
    known_exercises: np.ndarray,
    fallback_rate: float,
    batch_size: int,
    tracker: ResourceTracker,
) -> tuple[np.ndarray, np.ndarray, dict[str, Any]]:
    """Predict only.  This function contains no optimizer or parameter update."""

    if split.exercises.max() >= len(known_exercises):
        raise ValueError("Final exercise IDs exceed frozen development cardinality")
    parameter_snapshot = {
        "theta": model.theta.weight.detach().clone(),
        "delta": model.delta.weight.detach().clone() if model.delta is not None else None,
        "global_mu": model.global_mu.detach().clone(),
        "global_difficulty": model.global_difficulty.detach().clone(),
    }
    model.eval()
    chunks: list[np.ndarray] = []
    started = time.perf_counter()
    with torch.no_grad():
        for start in range(0, split.size, batch_size):
            end = min(split.size, start + batch_size)
            students = torch.as_tensor(split.students[start:end], dtype=torch.long)
            exercises = torch.as_tensor(split.exercises[start:end], dtype=torch.long)
            topics = torch.as_tensor(split.topics[start:end], dtype=torch.long)
            chunks.append(model(students, exercises, topics).detach().cpu().numpy())
            tracker.sample()
    values = np.concatenate(chunks)
    known = known_exercises[split.exercises]
    values[~known] = fallback_rate
    if not torch.equal(model.theta.weight, parameter_snapshot["theta"]):
        raise AssertionError("Evaluation rows updated theta_student")
    if model.delta is not None and not torch.equal(model.delta.weight, parameter_snapshot["delta"]):
        raise AssertionError("Evaluation rows updated delta_student_topic")
    assert_global_buffers_frozen(model, {
        "global_mu": parameter_snapshot["global_mu"],
        "global_difficulty": parameter_snapshot["global_difficulty"],
    })
    elapsed = time.perf_counter() - started
    return values, known, {
        "total_seconds": elapsed,
        "milliseconds_per_interaction": elapsed * 1000 / split.size,
        "evaluation_updated_personal_parameters": False,
        "frozen_global_parameters_verified": True,
    }


def personal_delta_values(model: PersonalCalibration, split: InteractionSplit) -> np.ndarray:
    if model.delta is None:
        return np.zeros(split.size, dtype=np.float64)
    model.eval()
    with torch.no_grad():
        students = torch.as_tensor(split.students, dtype=torch.long)
        topics = torch.as_tensor(split.topics, dtype=torch.long)
        return model.delta_values(students, topics).detach().cpu().numpy().astype(np.float64)


def topic_statuses(exposure: np.ndarray, students: np.ndarray, topics: np.ndarray) -> np.ndarray:
    counts = exposure[students, topics]
    status = np.full(counts.shape, "UNKNOWN", dtype="<U16")
    positive = counts > 0
    status[positive] = np.asarray([f"OBSERVED_{value}" for value in counts[positive]], dtype="<U16")
    return status


def _partition_records(
    records: Sequence[tuple[int, int, int, int, int]], calibration_fraction: float
) -> tuple[Sequence[tuple[int, int, int, int, int]], Sequence[tuple[int, int, int, int, int]]]:
    if not 0.0 < calibration_fraction < 1.0:
        raise ValueError("Final calibration fraction must be strictly between zero and one")
    ordered = sorted(records, key=lambda row: (row[0], row[1]))
    split_at = int(np.floor(len(ordered) * calibration_fraction))
    if split_at < 1 or split_at >= len(ordered):
        raise ValueError("Final chronological split does not leave both calibration and evaluation rows")
    return ordered[:split_at], ordered[split_at:]


def materialize_final_interactions(
    problem_log: Path,
    membership_path: Path,
    final_scope: Mapping[str, tuple[int, int]],
    *,
    expected_students: int,
    minimum_interactions: int,
    calibration_fraction: float,
) -> FinalMaterialization:
    """Open correctness only for frozen final members and eligible Exercises.

    The header and the identity/Exercise/timestamp fields are checked before the
    code ever accesses a row's correctness field.  No raw response row is
    serialized by this function.
    """

    members = load_membership(membership_path, "JUNYI_FINAL_HOLDOUT_V1", expected_students)
    ordered_members = sorted(members)
    student_index = {member: index for index, member in enumerate(ordered_members)}
    per_student: list[list[tuple[int, int, int, int, int]]] = [[] for _ in ordered_members]
    raw_rows = 0
    eligible_rows = 0
    with problem_log.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.reader(handle)
        try:
            header = next(reader)
        except StopIteration as exc:
            raise ValueError("ProblemLog is empty") from exc
        if header != RAW_LOG_COLUMNS:
            raise ValueError("ProblemLog header differs from the frozen Junyi contract")
        user_index = header.index("user_id")
        exercise_index = header.index("exercise")
        timestamp_index = header.index("time_done")
        correct_index = header.index("correct")
        expected_width = len(header)
        for source_row, row in enumerate(reader, start=2):
            raw_rows += 1
            if len(row) != expected_width:
                raise ValueError(f"Malformed ProblemLog row at source row {source_row}")
            external_student = normalise(row[user_index])
            external_exercise = normalise(row[exercise_index])
            timestamp = parse_timestamp(normalise(row[timestamp_index]))
            if external_student not in student_index or external_exercise not in final_scope or timestamp is None:
                continue
            # Correctness is first accessed only after the row is final-eligible.
            outcome = parse_correct(row[correct_index])
            exercise, topic = final_scope[external_exercise]
            per_student[student_index[external_student]].append((timestamp, source_row, exercise, topic, outcome))
            eligible_rows += 1
    calibration_rows: list[tuple[int, int, int, int]] = []
    evaluation_rows: list[tuple[int, int, int, int]] = []
    totals = np.zeros(expected_students, dtype=np.int64)
    calibration_counts = np.zeros(expected_students, dtype=np.int64)
    evaluation_counts = np.zeros(expected_students, dtype=np.int64)
    for student, records in enumerate(per_student):
        if len(records) < minimum_interactions:
            raise ValueError("Frozen final member no longer meets the minimum chronological interaction contract")
        calibration, evaluation = _partition_records(records, calibration_fraction)
        source_rows = {record[1] for record in calibration} | {record[1] for record in evaluation}
        if len(source_rows) != len(records):
            raise AssertionError("Final calibration and evaluation rows overlap")
        totals[student] = len(records)
        calibration_counts[student] = len(calibration)
        evaluation_counts[student] = len(evaluation)
        calibration_rows.extend((student, exercise, topic, outcome) for _, _, exercise, topic, outcome in calibration)
        evaluation_rows.extend((student, exercise, topic, outcome) for _, _, exercise, topic, outcome in evaluation)
    if len(calibration_rows) + len(evaluation_rows) != eligible_rows:
        raise AssertionError("Final materialization lost or duplicated eligible response rows")

    def as_split(rows: Sequence[tuple[int, int, int, int]]) -> InteractionSplit:
        return InteractionSplit(
            students=np.asarray([row[0] for row in rows], dtype=np.int64),
            exercises=np.asarray([row[1] for row in rows], dtype=np.int64),
            topics=np.asarray([row[2] for row in rows], dtype=np.int64),
            outcomes=np.asarray([row[3] for row in rows], dtype=np.float64),
        )

    return FinalMaterialization(
        calibration=as_split(calibration_rows),
        evaluation=as_split(evaluation_rows),
        total_interactions_by_student=totals,
        calibration_interactions_by_student=calibration_counts,
        evaluation_interactions_by_student=evaluation_counts,
        source_rows_read=raw_rows,
        eligible_rows_materialized=eligible_rows,
    )


def calibration_rule_beta_scores(
    calibration: InteractionSplit,
    evaluation: InteractionSplit,
    student_count: int,
    topic_count: int,
    alpha: float = 1.0,
    beta: float = 1.0,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Return calibration-only Topic Beta values and observation counts for audit.

    This is an offline comparator for the audited RuleBeta relationship.  It is
    not a Java provider invocation and UNKNOWN values stay missing in the
    returned score instead of being converted to 0.5 mastery.
    """

    exposure = topic_exposure_matrix(calibration, student_count, topic_count)
    correct = np.zeros((student_count, topic_count), dtype=np.float64)
    np.add.at(correct, (calibration.students, calibration.topics), calibration.outcomes)
    counts = exposure[evaluation.students, evaluation.topics]
    scores = np.full(evaluation.size, np.nan, dtype=np.float64)
    observed = counts > 0
    scores[observed] = (
        correct[evaluation.students[observed], evaluation.topics[observed]] + alpha
    ) / (counts[observed] + alpha + beta)
    return scores, counts, exposure


def _average_ranks(values: np.ndarray) -> np.ndarray:
    array = np.asarray(values, dtype=np.float64)
    order = np.argsort(array, kind="mergesort")
    sorted_values = array[order]
    ranks = np.empty(array.size, dtype=np.float64)
    start = 0
    while start < array.size:
        end = start + 1
        while end < array.size and sorted_values[end] == sorted_values[start]:
            end += 1
        ranks[order[start:end]] = (start + 1 + end) / 2.0
        start = end
    return ranks


def spearman_correlation(left: np.ndarray, right: np.ndarray) -> float | None:
    left_values = np.asarray(left, dtype=np.float64)
    right_values = np.asarray(right, dtype=np.float64)
    if left_values.shape != right_values.shape or left_values.size == 0:
        return None
    left_rank = _average_ranks(left_values)
    right_rank = _average_ranks(right_values)
    left_centered = left_rank - left_rank.mean()
    right_centered = right_rank - right_rank.mean()
    denominator = float(np.sqrt(np.dot(left_centered, left_centered) * np.dot(right_centered, right_centered)))
    if denominator == 0.0:
        return None
    return float(np.dot(left_centered, right_centered) / denominator)


def topic_deviation_audit(
    calibration: InteractionSplit,
    evaluation: InteractionSplit,
    student_count: int,
    topic_count: int,
    hierarchical: PersonalCalibration,
    hier_prediction: np.ndarray,
    rasch_prediction: np.ndarray,
    thresholds: Sequence[int] = (1, 3, 5),
) -> dict[str, Any]:
    if hierarchical.delta is None:
        raise ValueError("Topic-deviation audit requires a hierarchical calibration model")
    exposure = topic_exposure_matrix(calibration, student_count, topic_count)
    delta_values = personal_delta_values(hierarchical, evaluation)
    rule_beta, row_exposure, _ = calibration_rule_beta_scores(
        calibration, evaluation, student_count, topic_count
    )
    threshold_results: dict[str, Any] = {}
    for threshold in thresholds:
        observed = row_exposure >= int(threshold)
        selected_delta = delta_values[observed]
        selected_labels = evaluation.outcomes[observed]
        selected_rule = rule_beta[observed]
        association_auc = (
            float(roc_auc_score(selected_labels, selected_delta))
            if selected_labels.size and len(np.unique(selected_labels)) > 1
            else None
        )
        correlation = None
        if selected_delta.size > 1 and float(np.std(selected_delta)) > 0.0 and float(np.std(selected_labels)) > 0.0:
            correlation = float(np.corrcoef(selected_delta, selected_labels)[0, 1])
        hierarchy_metrics = binary_metrics(selected_labels, hier_prediction[observed]) if selected_labels.size else None
        rasch_metrics = binary_metrics(selected_labels, rasch_prediction[observed]) if selected_labels.size else None
        threshold_results[str(threshold)] = {
            "evaluation_rows": int(selected_labels.size),
            "student_count": int(len(np.unique(evaluation.students[observed]))) if selected_labels.size else 0,
            "delta_distribution": distribution(selected_delta),
            "future_response_association": {
                "delta_rank_auc": association_auc,
                "delta_outcome_pearson_correlation": correlation,
                "interpretation": "Descriptive future-response association only; no causal claim.",
            },
            "incremental_signal_over_rasch_residual": {
                "hierarchical_metrics": hierarchy_metrics,
                "rasch_metrics": rasch_metrics,
                "auc_delta": (
                    hierarchy_metrics["auc"] - rasch_metrics["auc"]
                    if hierarchy_metrics is not None
                    and rasch_metrics is not None
                    and hierarchy_metrics["auc"] is not None
                    and rasch_metrics["auc"] is not None
                    else None
                ),
                "mean_prediction_difference": float(np.mean(hier_prediction[observed] - rasch_prediction[observed]))
                if selected_labels.size
                else None,
            },
            "rule_beta_relation": {
                "offline_calibration_only": True,
                "row_count": int(np.isfinite(selected_rule).sum()),
                "spearman_delta_vs_rule_beta": spearman_correlation(selected_delta, selected_rule),
                "unknown_rows_excluded": int((~observed).sum()),
            },
        }
    matrix = hierarchical.delta.weight.detach().cpu().numpy().reshape(student_count, topic_count)
    observed_matrix = exposure > 0
    return {
        "semantic_boundary": "delta is a local logit-space deviation. UNKNOWN means calibration exposure=0, delta=0, and is never mastery=0.5.",
        "exposure_coverage": {
            str(threshold): {
                "student_topic_pairs": int((exposure >= threshold).sum()),
                "fraction_of_all_student_topic_pairs": float((exposure >= threshold).mean()),
                "evaluation_rows": int((row_exposure >= threshold).sum()),
            }
            for threshold in thresholds
        },
        "observed_delta_distribution": distribution(matrix[observed_matrix]),
        "unknown_delta_exactly_zero": bool(np.array_equal(matrix[~observed_matrix], np.zeros(int((~observed_matrix).sum())))),
        "thresholds": threshold_results,
    }


def anonymized_student_cases(
    materialization: FinalMaterialization,
    calibration_exposure: np.ndarray,
    rasch: PersonalCalibration,
    hierarchical: PersonalCalibration,
    count: int = 20,
) -> list[dict[str, Any]]:
    if hierarchical.delta is None:
        raise ValueError("Anonymized cases require hierarchical parameters")
    selected = min(count, len(materialization.total_interactions_by_student))
    theta_rasch = rasch.theta.weight.detach().cpu().numpy().reshape(-1)
    theta_hier = hierarchical.theta.weight.detach().cpu().numpy().reshape(-1)
    delta = hierarchical.delta.weight.detach().cpu().numpy().reshape(calibration_exposure.shape)
    cases: list[dict[str, Any]] = []
    for student in range(selected):
        observed = calibration_exposure[student] > 0
        cases.append({
            "anonymous_case": f"student-{student + 1:03d}",
            "total_interactions": int(materialization.total_interactions_by_student[student]),
            "calibration_interactions": int(materialization.calibration_interactions_by_student[student]),
            "evaluation_interactions": int(materialization.evaluation_interactions_by_student[student]),
            "observed_topic_count": int(observed.sum()),
            "unknown_topic_count": int((~observed).sum()),
            "rasch_theta": float(theta_rasch[student]),
            "hierarchical_theta": float(theta_hier[student]),
            "observed_delta_distribution": distribution(delta[student, observed]),
        })
    return cases


def sanitized_bad_cases(
    evaluation: InteractionSplit,
    prediction: np.ndarray,
    statuses: np.ndarray,
    limit_per_error: int = 10,
) -> dict[str, Any]:
    values = clip_probability(prediction)
    false_positive = np.flatnonzero((values >= 0.5) & (evaluation.outcomes == 0))[:limit_per_error]
    false_negative = np.flatnonzero((values < 0.5) & (evaluation.outcomes == 1))[:limit_per_error]

    def rows(indices: np.ndarray, prefix: str) -> list[dict[str, Any]]:
        return [
            {
                "anonymous_case": f"{prefix}-{position + 1:03d}",
                "topic_index": int(evaluation.topics[row]),
                "topic_status": str(statuses[row]),
                "prediction": float(values[row]),
                "outcome": int(evaluation.outcomes[row]),
            }
            for position, row in enumerate(indices)
        ]

    return {
        "false_positive_count": int(((values >= 0.5) & (evaluation.outcomes == 0)).sum()),
        "false_negative_count": int(((values < 0.5) & (evaluation.outcomes == 1)).sum()),
        "unknown_topic_evaluation_rows": int((statuses == "UNKNOWN").sum()),
        "false_positive_examples": rows(false_positive, "false-positive"),
        "false_negative_examples": rows(false_negative, "false-negative"),
        "privacy_boundary": "No external student ID, Exercise external ID, timestamp, or raw response row is included.",
    }
