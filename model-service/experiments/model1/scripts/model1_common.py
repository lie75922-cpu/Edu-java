"""Shared helpers for the controlled MODEL-1 Concept Resolution Gate.

The helpers use explicit schemas, paths, counts, deterministic selection, and
deep tensor comparison where needed.  Raw data, large derivatives, runtime
ledgers, and checkpoints are intentionally local ignored artifacts.
"""

from __future__ import annotations

import csv
import importlib.metadata
import json
import os
import random
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

import numpy as np
import psutil
import torch
from sklearn.metrics import accuracy_score, log_loss, mean_squared_error, roc_auc_score


REPOSITORY_ROOT = Path(__file__).resolve().parents[4]
MODEL1_ROOT = REPOSITORY_ROOT / "model-service/experiments/model1"
DERIVED_ROOT = MODEL1_ROOT / "data"
REPORT_ROOT = MODEL1_ROOT / "reports"
MANIFEST_ROOT = MODEL1_ROOT / "manifests"
CONFIG_ROOT = MODEL1_ROOT / "configs"
CHECKPOINT_ROOT = MODEL1_ROOT / "checkpoints"
RUNTIME_ROOT = MODEL1_ROOT / "runtime"


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2, sort_keys=True)
        handle.write("\n")


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")


def set_deterministic_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)
    torch.backends.cudnn.benchmark = False
    torch.backends.cudnn.deterministic = True
    torch.use_deterministic_algorithms(True, warn_only=True)


def choose_device(preference: str) -> torch.device:
    if preference.startswith("cuda") and torch.cuda.is_available():
        return torch.device(preference)
    return torch.device("cpu")


def synchronize(device: torch.device) -> None:
    if device.type == "cuda":
        torch.cuda.synchronize(device)


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
        outcomes=np.asarray(outcomes, dtype=np.float32),
    )


def load_q_matrix(path: Path, expected_rows: int, expected_columns: int) -> np.ndarray:
    rows: list[list[float]] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        for row_number, row in enumerate(csv.reader(handle), start=1):
            values = [float(value) for value in row]
            if len(values) != expected_columns or any(value not in (0.0, 1.0) for value in values):
                raise ValueError(f"Invalid Q-matrix row at {path}:{row_number}")
            if sum(values) != 1.0:
                raise ValueError(f"Q-matrix row is not one-concept-per-Exercise: {row_number}")
            rows.append(values)
    matrix = np.asarray(rows, dtype=np.float32)
    if matrix.shape != (expected_rows, expected_columns):
        raise ValueError(f"Unexpected Q-matrix shape at {path}: {matrix.shape}")
    return matrix


def load_cardinalities(root: Path) -> tuple[int, int, int]:
    return (
        len(read_json(root / "student_index_map.json")),
        len(read_json(root / "exercise_index_map.json")),
        len(read_json(root / "topic_index_map.json")),
    )


@dataclass
class ResourceTracker:
    device: torch.device
    process: psutil.Process
    peak_rss_bytes: int = 0

    @classmethod
    def start(cls, device: torch.device) -> "ResourceTracker":
        if device.type == "cuda":
            torch.cuda.empty_cache()
            torch.cuda.reset_peak_memory_stats(device)
        tracker = cls(device=device, process=psutil.Process(os.getpid()))
        tracker.sample()
        return tracker

    def sample(self) -> None:
        self.peak_rss_bytes = max(self.peak_rss_bytes, self.process.memory_info().rss)

    def report(self) -> dict[str, Any]:
        if self.device.type != "cuda":
            return {
                "peak_process_rss_bytes": self.peak_rss_bytes,
                "gpu": "none",
                "peak_gpu_allocated_bytes": None,
                "peak_gpu_reserved_bytes": None,
            }
        synchronize(self.device)
        self.sample()
        return {
            "peak_process_rss_bytes": self.peak_rss_bytes,
            "gpu": torch.cuda.get_device_name(self.device),
            "peak_gpu_allocated_bytes": torch.cuda.max_memory_allocated(self.device),
            "peak_gpu_reserved_bytes": torch.cuda.max_memory_reserved(self.device),
        }


def batched_indices(size: int, batch_size: int, shuffle: bool, rng: np.random.Generator) -> Iterable[np.ndarray]:
    indices = np.arange(size, dtype=np.int64)
    if shuffle:
        rng.shuffle(indices)
    for start in range(0, size, batch_size):
        yield indices[start : start + batch_size]


def binary_metrics(labels: np.ndarray, predictions: np.ndarray) -> dict[str, float | None]:
    if labels.shape != predictions.shape:
        raise ValueError("Labels and predictions do not have matching shapes")
    if labels.size == 0:
        raise ValueError("Cannot score an empty evaluation split")
    safe = np.clip(predictions.astype(np.float64), 1e-7, 1.0 - 1e-7)
    return {
        "auc": float(roc_auc_score(labels, safe)) if len(np.unique(labels)) > 1 else None,
        "acc": float(accuracy_score(labels, safe >= 0.5)),
        "rmse": float(np.sqrt(mean_squared_error(labels, safe))),
        "log_loss": float(log_loss(labels, safe, labels=[0, 1])),
    }


def inference_latency(started: float, completed: float, rows: int) -> dict[str, float]:
    total_seconds = completed - started
    return {
        "total_seconds": total_seconds,
        "milliseconds_per_interaction": (total_seconds * 1000 / rows) if rows else 0.0,
    }


def is_better(candidate: Mapping[str, float | None], current: Mapping[str, float | None] | None) -> bool:
    if current is None:
        return True
    candidate_auc = candidate["auc"] if candidate["auc"] is not None else float("-inf")
    current_auc = current["auc"] if current["auc"] is not None else float("-inf")
    if candidate_auc != current_auc:
        return bool(candidate_auc > current_auc)
    candidate_rmse = candidate["rmse"] if candidate["rmse"] is not None else float("inf")
    current_rmse = current["rmse"] if current["rmse"] is not None else float("inf")
    return bool(candidate_rmse < current_rmse)


def make_rate_estimates(train: InteractionSplit, exercise_num: int, topic_num: int) -> dict[str, Any]:
    global_rate = float(train.outcomes.mean())
    exercise_sum = np.bincount(train.exercises, weights=train.outcomes, minlength=exercise_num)
    exercise_count = np.bincount(train.exercises, minlength=exercise_num)
    topic_sum = np.bincount(train.topics, weights=train.outcomes, minlength=topic_num)
    topic_count = np.bincount(train.topics, minlength=topic_num)
    return {
        "global_rate": global_rate,
        "exercise_rate": np.divide(
            exercise_sum,
            exercise_count,
            out=np.full(exercise_num, global_rate, dtype=np.float64),
            where=exercise_count > 0,
        ),
        "topic_rate": np.divide(
            topic_sum,
            topic_count,
            out=np.full(topic_num, global_rate, dtype=np.float64),
            where=topic_count > 0,
        ),
        "exercise_seen_count": int((exercise_count > 0).sum()),
        "topic_seen_count": int((topic_count > 0).sum()),
    }


def rate_predictions(estimates: Mapping[str, Any], split: InteractionSplit, kind: str) -> np.ndarray:
    if kind == "global":
        return np.full(split.size, estimates["global_rate"], dtype=np.float64)
    if kind == "exercise":
        return np.asarray(estimates["exercise_rate"])[split.exercises]
    if kind == "topic":
        return np.asarray(estimates["topic_rate"])[split.topics]
    raise ValueError(f"Unsupported rate baseline: {kind}")


def bootstrap_auc_delta(
    labels: np.ndarray,
    candidate: np.ndarray,
    baseline: np.ndarray,
    resamples: int,
    confidence_level: float,
    seed: int,
) -> dict[str, float | int | None]:
    rng = np.random.default_rng(seed)
    deltas: list[float] = []
    for _ in range(resamples):
        indices = rng.integers(0, labels.size, size=labels.size)
        sampled_labels = labels[indices]
        if len(np.unique(sampled_labels)) < 2:
            continue
        deltas.append(
            float(roc_auc_score(sampled_labels, candidate[indices]) - roc_auc_score(sampled_labels, baseline[indices]))
        )
    alpha = (1.0 - confidence_level) / 2.0
    observed = float(roc_auc_score(labels, candidate) - roc_auc_score(labels, baseline))
    return {
        "observed_auc_delta": observed,
        "resamples_requested": resamples,
        "resamples_valid": len(deltas),
        "confidence_level": confidence_level,
        "lower_bound": float(np.quantile(deltas, alpha)) if deltas else None,
        "upper_bound": float(np.quantile(deltas, 1.0 - alpha)) if deltas else None,
        "method": "Paired nonparametric bootstrap over final-test interactions; report-only and not used for selection.",
    }


def doa_from_mastery(split: InteractionSplit, mastery: np.ndarray, q_matrix: np.ndarray) -> dict[str, Any]:
    """Score Q-selected mastery using final labels only as a later outcome check."""

    if mastery.ndim != 2 or mastery.shape[1] != q_matrix.shape[1]:
        raise ValueError("Mastery dimensions do not match the Q-matrix")
    concept_for_exercise = np.argmax(q_matrix, axis=1)
    row_mastery = mastery[split.students, concept_for_exercise[split.exercises]]
    total_pairs = 0
    agreement = 0.0
    eligible_exercises = 0
    for exercise in np.unique(split.exercises):
        selector = split.exercises == exercise
        correct_scores = row_mastery[selector & (split.outcomes == 1)]
        wrong_scores = row_mastery[selector & (split.outcomes == 0)]
        if not correct_scores.size or not wrong_scores.size:
            continue
        eligible_exercises += 1
        ordered_wrong = np.sort(wrong_scores)
        less = np.searchsorted(ordered_wrong, correct_scores, side="left")
        less_or_equal = np.searchsorted(ordered_wrong, correct_scores, side="right")
        agreement += float(less.sum()) + 0.5 * float((less_or_equal - less).sum())
        total_pairs += int(correct_scores.size * wrong_scores.size)
    if total_pairs == 0:
        return {
            "doa": None,
            "status": "NOT_APPLICABLE_NO_MIXED_OUTCOME_EXERCISE_PAIRS",
            "pair_count": 0,
            "eligible_exercises": 0,
        }
    return {
        "doa": agreement / total_pairs,
        "status": "APPLICABLE_EXERCISE_CONDITIONED_PAIRWISE",
        "pair_count": total_pairs,
        "eligible_exercises": eligible_exercises,
    }


def _distribution(values: np.ndarray) -> dict[str, float | None]:
    finite = values[np.isfinite(values)]
    if not finite.size:
        return {name: None for name in ("min", "p05", "p25", "median", "p75", "p95", "max", "mean", "std")}
    return {
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


def _mean_pairwise_cosine(mastery: np.ndarray, sample_size: int) -> tuple[float | None, int]:
    if mastery.shape[0] == 0 or not np.isfinite(mastery).all():
        return None, 0
    count = min(sample_size, mastery.shape[0])
    positions = np.linspace(0, mastery.shape[0] - 1, count, dtype=np.int64)
    vectors = mastery[positions]
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    normalized = np.divide(vectors, norms, out=np.zeros_like(vectors), where=norms > 0)
    cosine = normalized @ normalized.T
    upper = cosine[np.triu_indices(count, k=1)] if count > 1 else np.asarray([], dtype=np.float64)
    return (float(upper.mean()) if upper.size else 1.0), count


def anonymous_cases(mastery: np.ndarray, count: int) -> list[dict[str, Any]]:
    positions = np.linspace(0, mastery.shape[0] - 1, count, dtype=np.int64)
    return [
        {
            "anonymous_case": f"case-{case_index:03d}",
            "mastery_vector": [round(float(value), 6) for value in mastery[position]],
        }
        for case_index, position in enumerate(positions, start=1)
    ]


def mastery_sanity(
    model_name: str,
    mastery: np.ndarray,
    q_matrix: np.ndarray,
    train: InteractionSplit,
    case_count: int,
    cosine_sample_size: int,
    constant_tolerance: float,
    minimum_median_standard_deviation: float,
    maximum_mean_pairwise_cosine: float,
) -> dict[str, Any]:
    if mastery.ndim != 2 or mastery.shape[1] != q_matrix.shape[1]:
        raise ValueError("Mastery dimensions do not match Q-matrix concept columns")
    if mastery.shape[0] <= int(train.students.max()):
        raise ValueError("Mastery rows do not cover the trained student index")
    finite = np.isfinite(mastery)
    nan_count = int(np.isnan(mastery).sum())
    infinite_count = int(np.isinf(mastery).sum())
    if finite.all():
        concept_std = mastery.std(axis=0)
        concept_variance = mastery.var(axis=0)
        median_std: float | None = float(np.median(concept_std))
        constant_columns = np.flatnonzero(concept_std <= constant_tolerance).astype(int).tolist()
    else:
        concept_std = np.full(mastery.shape[1], np.nan, dtype=np.float64)
        concept_variance = np.full(mastery.shape[1], np.nan, dtype=np.float64)
        median_std = None
        constant_columns = []
    active_exercises = np.unique(train.exercises)
    covered_columns = np.flatnonzero(q_matrix[active_exercises].sum(axis=0) > 0).astype(int).tolist()
    all_columns = set(range(q_matrix.shape[1]))
    missing_columns = sorted(all_columns - set(covered_columns))
    cosine, actual_sample_size = _mean_pairwise_cosine(mastery, cosine_sample_size)
    collapsed = median_std is None or median_std < minimum_median_standard_deviation
    oversmoothed = cosine is None or cosine > maximum_mean_pairwise_cosine
    return {
        "model": model_name,
        "mastery_distribution": _distribution(mastery),
        "nonfinite_values": {"nan_count": nan_count, "infinite_count": infinite_count},
        "concept_coverage": {
            "concept_columns": int(q_matrix.shape[1]),
            "concepts_with_train_coverage": len(covered_columns),
            "train_coverage_fraction": len(covered_columns) / q_matrix.shape[1],
            "missing_model_concept_ids": missing_columns,
        },
        "student_to_student_concept_variance": {
            "minimum": float(np.nanmin(concept_variance)) if finite.all() else None,
            "median": float(np.nanmedian(concept_variance)) if finite.all() else None,
            "maximum": float(np.nanmax(concept_variance)) if finite.all() else None,
            "median_concept_standard_deviation": median_std,
            "distribution": _distribution(concept_std),
        },
        "constant_column_check": {
            "standard_deviation_tolerance": constant_tolerance,
            "constant_column_count": len(constant_columns),
            "constant_column_fraction": len(constant_columns) / q_matrix.shape[1],
            "constant_model_concept_ids": constant_columns,
        },
        "oversmoothing_check": {
            "sample_size": actual_sample_size,
            "mean_pairwise_cosine": cosine,
            "minimum_median_concept_standard_deviation": minimum_median_standard_deviation,
            "maximum_mean_pairwise_cosine": maximum_mean_pairwise_cosine,
            "collapsed": collapsed,
            "oversmoothed": oversmoothed,
            "status": "PASS" if not collapsed and not oversmoothed else "FLAGGED",
        },
        "anonymous_mastery_vector_sanity_cases": anonymous_cases(mastery, case_count),
    }


def aggregate_fine_mastery_to_topics(
    mastery: np.ndarray,
    fine_concept_topic_indices: Sequence[int],
    topic_labels: Sequence[str],
    cosine_sample_size: int,
    constant_tolerance: float,
    minimum_median_standard_deviation: float,
    maximum_mean_pairwise_cosine: float,
) -> dict[str, Any]:
    if mastery.shape[1] != len(fine_concept_topic_indices):
        raise ValueError("Fine mastery columns do not match the catalog topic projection")
    topic_count = len(topic_labels)
    aggregate_columns: list[np.ndarray] = []
    topic_rows: list[dict[str, Any]] = []
    missing_topic_ids: list[int] = []
    for topic_index, topic_label in enumerate(topic_labels):
        concept_indices = [
            concept_index
            for concept_index, mapped_topic in enumerate(fine_concept_topic_indices)
            if mapped_topic == topic_index
        ]
        if not concept_indices:
            missing_topic_ids.append(topic_index)
            topic_rows.append(
                {
                    "topic_id": topic_index,
                    "topic": topic_label,
                    "fine_model_concept_count": 0,
                    "student_mean_mastery": None,
                    "student_standard_deviation": None,
                    "projection_status": "NO_FINE_MODEL_CONCEPT_IN_CURRENT_ELIGIBLE_SCOPE",
                }
            )
            continue
        values = mastery[:, concept_indices].mean(axis=1)
        aggregate_columns.append(values)
        topic_rows.append(
            {
                "topic_id": topic_index,
                "topic": topic_label,
                "fine_model_concept_count": len(concept_indices),
                "student_mean_mastery": float(values.mean()) if np.isfinite(values).all() else None,
                "student_standard_deviation": float(values.std()) if np.isfinite(values).all() else None,
                "projection_status": "PROJECTED",
            }
        )
    if not aggregate_columns:
        raise ValueError("No Topic has a fine ModelConcept projection")
    aggregate = np.stack(aggregate_columns, axis=1)
    finite = np.isfinite(aggregate)
    topic_std = aggregate.std(axis=0) if finite.all() else np.full(aggregate.shape[1], np.nan)
    median_std = float(np.median(topic_std)) if finite.all() else None
    cosine, sample_size = _mean_pairwise_cosine(aggregate, cosine_sample_size)
    constants = (
        [
            topic_id
            for topic_id, value in zip(
                [index for index in range(topic_count) if index not in missing_topic_ids],
                topic_std,
            )
            if value <= constant_tolerance
        ]
        if finite.all()
        else []
    )
    stable = (
        finite.all()
        and median_std is not None
        and median_std >= minimum_median_standard_deviation
        and cosine is not None
        and cosine <= maximum_mean_pairwise_cosine
        and not constants
    )
    return {
        "projection": "Mean fine ModelConcept mastery over the catalog's one-Topic-per-Exercise membership; display-layer Topic IDs are never reused as fine ModelConcept IDs.",
        "topic_count": topic_count,
        "topics_with_fine_model_concepts": topic_count - len(missing_topic_ids),
        "topics_without_fine_model_concepts": missing_topic_ids,
        "topic_rows": topic_rows,
        "aggregate_mastery_distribution": _distribution(aggregate),
        "aggregate_nonfinite_values": {
            "nan_count": int(np.isnan(aggregate).sum()),
            "infinite_count": int(np.isinf(aggregate).sum()),
        },
        "student_to_student_topic_standard_deviation": {
            "median": median_std,
            "distribution": _distribution(topic_std),
        },
        "mean_pairwise_cosine": cosine,
        "sample_size": sample_size,
        "constant_topic_ids": constants,
        "minimum_median_standard_deviation": minimum_median_standard_deviation,
        "maximum_mean_pairwise_cosine": maximum_mean_pairwise_cosine,
        "status": "PASS" if stable else "FLAGGED",
    }


def metric_text(value: float | None) -> str:
    return f"{value:.6f}" if value is not None else "N/A"


def bytes_text(value: int | None) -> str:
    if value is None:
        return "N/A"
    return f"{value:,} bytes ({value / (1024 * 1024):.2f} MiB)"
