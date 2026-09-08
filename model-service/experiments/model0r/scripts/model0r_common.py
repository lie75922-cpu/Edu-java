"""Shared, split-safe helpers for the MODEL-0R evaluation workspace.

The module deliberately uses explicit schemas, fixed paths, counts, and deep
value comparisons.  It does not create content digests and does not mutate the
archived MODEL-0A workspace.
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
from typing import Any, Iterable, Mapping

import numpy as np
import psutil
import torch
from sklearn.metrics import accuracy_score, log_loss, mean_squared_error, roc_auc_score


REPOSITORY_ROOT = Path(__file__).resolve().parents[4]
MODEL0R_ROOT = REPOSITORY_ROOT / "model-service/experiments/model0r"
DERIVED_ROOT = MODEL0R_ROOT / "data"
REPORT_ROOT = MODEL0R_ROOT / "reports"
MANIFEST_ROOT = MODEL0R_ROOT / "manifests"
CONFIG_ROOT = MODEL0R_ROOT / "configs"
CHECKPOINT_ROOT = MODEL0R_ROOT / "checkpoints"
RUNTIME_ROOT = MODEL0R_ROOT / "runtime"


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
    result = {"python": os.sys.version.split()[0], "torch": torch.__version__}
    for distribution in ("numpy", "scikit-learn", "psutil"):
        try:
            result[distribution] = importlib.metadata.version(distribution)
        except importlib.metadata.PackageNotFoundError:
            result[distribution] = "UNAVAILABLE"
    return result


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


def load_q_matrix(protocol: str) -> np.ndarray:
    path = DERIVED_ROOT / protocol / "q_matrix.csv"
    rows: list[list[float]] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        for row_number, row in enumerate(csv.reader(handle), start=1):
            values = [float(value) for value in row]
            if not values or any(value not in (0.0, 1.0) for value in values):
                raise ValueError(f"Invalid Q-matrix row at {path}:{row_number}")
            if sum(values) != 1.0:
                raise ValueError(f"Q-matrix row is not one-Topic-per-Exercise: {row_number}")
            rows.append(values)
    matrix = np.asarray(rows, dtype=np.float32)
    if matrix.shape != (624, 40):
        raise ValueError(f"Unexpected Q-matrix shape: {matrix.shape}")
    return matrix


def load_cardinalities(protocol: str) -> tuple[int, int, int]:
    root = DERIVED_ROOT / protocol
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


def batched_indices(size: int, batch_size: int, shuffle: bool, rng: np.random.Generator) -> Iterable[np.ndarray]:
    indices = np.arange(size, dtype=np.int64)
    if shuffle:
        rng.shuffle(indices)
    for start in range(0, size, batch_size):
        yield indices[start : start + batch_size]


def is_better(
    candidate: Mapping[str, float | None], current: Mapping[str, float | None] | None
) -> bool:
    if current is None:
        return True
    candidate_auc = candidate["auc"] if candidate["auc"] is not None else float("-inf")
    current_auc = current["auc"] if current["auc"] is not None else float("-inf")
    if candidate_auc != current_auc:
        return bool(candidate_auc > current_auc)
    candidate_rmse = candidate["rmse"] if candidate["rmse"] is not None else float("inf")
    current_rmse = current["rmse"] if current["rmse"] is not None else float("inf")
    return bool(candidate_rmse < current_rmse)


def make_rate_estimates(
    train: InteractionSplit, exercise_num: int, topic_num: int
) -> dict[str, Any]:
    global_rate = float(train.outcomes.mean())
    exercise_sum = np.bincount(train.exercises, weights=train.outcomes, minlength=exercise_num)
    exercise_count = np.bincount(train.exercises, minlength=exercise_num)
    topic_sum = np.bincount(train.topics, weights=train.outcomes, minlength=topic_num)
    topic_count = np.bincount(train.topics, minlength=topic_num)
    return {
        "global_rate": global_rate,
        "majority_probability": 1.0 if global_rate >= 0.5 else 0.0,
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
    if kind == "majority":
        return np.full(split.size, estimates["majority_probability"], dtype=np.float64)
    if kind == "global":
        return np.full(split.size, estimates["global_rate"], dtype=np.float64)
    if kind == "exercise":
        return np.asarray(estimates["exercise_rate"])[split.exercises]
    if kind == "topic":
        return np.asarray(estimates["topic_rate"])[split.topics]
    raise ValueError(f"Unsupported rate baseline: {kind}")


def doa_from_mastery(
    split: InteractionSplit, mastery: np.ndarray, q_matrix: np.ndarray
) -> dict[str, Any]:
    """Exercise-conditioned pairwise DOA using only evaluation labels for scoring.

    The mastery vectors are supplied by a model fitted before the evaluation
    rows.  Each same-exercise correct/incorrect student pair contributes one
    comparison; ties receive half credit.
    """

    if mastery.ndim != 2 or mastery.shape[1] != q_matrix.shape[1]:
        raise ValueError("Mastery dimensions do not match Q-matrix topic dimensions")
    topic_for_exercise = np.argmax(q_matrix, axis=1)
    row_mastery = mastery[split.students, topic_for_exercise[split.exercises]]
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


def mastery_sanity(
    model_name: str,
    mastery: np.ndarray,
    q_matrix: np.ndarray,
    train: InteractionSplit,
    case_count: int,
    minimum_median_topic_std: float,
    maximum_mean_pairwise_cosine: float,
) -> dict[str, Any]:
    if mastery.shape[1] != q_matrix.shape[1]:
        raise ValueError("Mastery matrix does not match the Q-matrix")
    topic_std = mastery.std(axis=0)
    topic_variance = mastery.var(axis=0)
    active_topics = sorted(set(train.topics.tolist()))
    sample_count = min(256, mastery.shape[0])
    sample_positions = np.linspace(0, mastery.shape[0] - 1, sample_count, dtype=np.int64)
    vectors = mastery[sample_positions]
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    normalized = np.divide(vectors, norms, out=np.zeros_like(vectors), where=norms > 0)
    cosine = normalized @ normalized.T
    upper = cosine[np.triu_indices(sample_count, k=1)] if sample_count > 1 else np.asarray([])
    mean_pairwise_cosine = float(upper.mean()) if upper.size else 1.0
    positions = np.linspace(0, mastery.shape[0] - 1, case_count, dtype=np.int64)
    cases = [
        {
            "anonymous_case": f"case-{case_index:03d}",
            "mastery_vector": [round(float(value), 6) for value in mastery[position]],
        }
        for case_index, position in enumerate(positions, start=1)
    ]
    median_topic_std = float(np.median(topic_std))
    collapsed = median_topic_std < minimum_median_topic_std
    oversmoothed = mean_pairwise_cosine > maximum_mean_pairwise_cosine
    return {
        "model": model_name,
        "mastery_distribution": {
            "min": float(mastery.min()),
            "p05": float(np.quantile(mastery, 0.05)),
            "p25": float(np.quantile(mastery, 0.25)),
            "median": float(np.median(mastery)),
            "p75": float(np.quantile(mastery, 0.75)),
            "p95": float(np.quantile(mastery, 0.95)),
            "max": float(mastery.max()),
            "mean": float(mastery.mean()),
            "std": float(mastery.std()),
        },
        "topic_coverage": {
            "topic_columns": int(q_matrix.shape[1]),
            "topics_observed_in_warm_train": len(active_topics),
            "missing_topic_indices": [index for index in range(q_matrix.shape[1]) if index not in active_topics],
        },
        "student_to_student_mastery_variance": {
            "minimum": float(topic_variance.min()),
            "median": float(np.median(topic_variance)),
            "maximum": float(topic_variance.max()),
            "median_topic_standard_deviation": median_topic_std,
        },
        "oversmoothing_check": {
            "sample_size": sample_count,
            "mean_pairwise_cosine": mean_pairwise_cosine,
            "minimum_median_topic_std": minimum_median_topic_std,
            "maximum_mean_pairwise_cosine": maximum_mean_pairwise_cosine,
            "collapsed": collapsed,
            "oversmoothed": oversmoothed,
            "status": "PASS" if not collapsed and not oversmoothed else "FLAGGED",
        },
        "anonymous_mastery_vector_sanity_cases": cases,
    }


def percentile_latency(values: list[float]) -> dict[str, float | None]:
    if not values:
        return {"mean_ms": None, "p50_ms": None, "p95_ms": None}
    array = np.asarray(values, dtype=np.float64)
    return {
        "mean_ms": float(array.mean()),
        "p50_ms": float(np.quantile(array, 0.50)),
        "p95_ms": float(np.quantile(array, 0.95)),
    }


def bytes_text(value: int | None) -> str:
    if value is None:
        return "N/A"
    return f"{value:,} bytes ({value / (1024 * 1024):.2f} MiB)"


def metric_text(value: float | None) -> str:
    return f"{value:.6f}" if value is not None else "N/A"


def minute_second_text(seconds: float | None) -> str:
    return f"{seconds:.3f} s" if seconds is not None else "N/A"
