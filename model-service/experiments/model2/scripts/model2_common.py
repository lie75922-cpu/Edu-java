"""Shared helpers for the controlled MODEL-2 signal stop gate.

The workspace deliberately uses explicit paths, schemas, fixed seeds, counts,
and deep numeric comparisons. Raw data, derived tables, checkpoints, and
runtime ledgers are local ignored artifacts.
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
from sklearn.metrics import accuracy_score, log_loss, roc_auc_score


REPOSITORY_ROOT = Path(__file__).resolve().parents[4]
MODEL2_ROOT = REPOSITORY_ROOT / "model-service/experiments/model2"
CONFIG_ROOT = MODEL2_ROOT / "configs"
MANIFEST_ROOT = MODEL2_ROOT / "manifests"
REPORT_ROOT = MODEL2_ROOT / "reports"
DERIVED_ROOT = MODEL2_ROOT / "data"
CHECKPOINT_ROOT = MODEL2_ROOT / "checkpoints"
RUNTIME_ROOT = MODEL2_ROOT / "runtime"


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


def choose_device(preference: str = "cpu") -> torch.device:
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
        outcomes=np.asarray(outcomes, dtype=np.float64),
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
    matrix = np.asarray(rows, dtype=np.float64)
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


def batched_indices(size: int, batch_size: int) -> Iterable[np.ndarray]:
    for start in range(0, size, batch_size):
        yield np.arange(start, min(size, start + batch_size), dtype=np.int64)


def clip_probability(predictions: np.ndarray) -> np.ndarray:
    return np.clip(np.asarray(predictions, dtype=np.float64), 1e-7, 1.0 - 1e-7)


def binary_metrics(labels: np.ndarray, predictions: np.ndarray) -> dict[str, float | None]:
    labels = np.asarray(labels, dtype=np.float64)
    safe = clip_probability(predictions)
    if labels.shape != safe.shape:
        raise ValueError("Labels and predictions do not have matching shapes")
    if labels.size == 0:
        raise ValueError("Cannot score an empty evaluation split")
    return {
        "auc": float(roc_auc_score(labels, safe)) if len(np.unique(labels)) > 1 else None,
        "acc": float(accuracy_score(labels, safe >= 0.5)),
        "rmse": float(np.sqrt(np.mean(np.square(labels - safe)))),
        "log_loss": float(log_loss(labels, safe, labels=[0, 1])),
        "brier": float(np.mean(np.square(labels - safe))),
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
    labels = np.asarray(labels, dtype=np.float64)
    safe = clip_probability(predictions)
    rows: list[dict[str, float | int | None]] = []
    for index in range(bin_count):
        lower = index / bin_count
        upper = (index + 1) / bin_count
        include = (safe >= lower) & (safe < upper if index < bin_count - 1 else safe <= upper)
        if not include.any():
            rows.append(
                {
                    "bin": index + 1,
                    "lower": lower,
                    "upper": upper,
                    "count": 0,
                    "mean_prediction": None,
                    "outcome_rate": None,
                    "absolute_gap": None,
                }
            )
            continue
        mean_prediction = float(safe[include].mean())
        outcome_rate = float(labels[include].mean())
        rows.append(
            {
                "bin": index + 1,
                "lower": lower,
                "upper": upper,
                "count": int(include.sum()),
                "mean_prediction": mean_prediction,
                "outcome_rate": outcome_rate,
                "absolute_gap": abs(mean_prediction - outcome_rate),
            }
        )
    return rows


def within_topic_rank_agreement(split: InteractionSplit, predictions: np.ndarray) -> dict[str, Any]:
    predictions = clip_probability(predictions)
    topic_rows: list[dict[str, float | int | None]] = []
    aucs: list[float] = []
    weights: list[int] = []
    for topic in np.unique(split.topics):
        selected = split.topics == topic
        labels = split.outcomes[selected]
        if labels.size < 2 or len(np.unique(labels)) < 2:
            topic_rows.append({"topic_id": int(topic), "rows": int(labels.size), "auc": None})
            continue
        auc = float(roc_auc_score(labels, predictions[selected]))
        topic_rows.append({"topic_id": int(topic), "rows": int(labels.size), "auc": auc})
        aucs.append(auc)
        weights.append(int(labels.size))
    return {
        "definition": "Per-Topic future-outcome AUC; macro and row-weighted aggregates are rank-agreement summaries that do not compare different Topics.",
        "eligible_topic_count": len(aucs),
        "macro_auc": float(np.mean(aucs)) if aucs else None,
        "row_weighted_auc": float(np.average(aucs, weights=weights)) if aucs else None,
        "topic_rows": topic_rows,
    }


def _average_ranks(values: np.ndarray) -> np.ndarray:
    values = np.asarray(values, dtype=np.float64)
    order = np.argsort(values, kind="mergesort")
    sorted_values = values[order]
    ranks = np.empty(values.size, dtype=np.float64)
    start = 0
    while start < values.size:
        end = start + 1
        while end < values.size and sorted_values[end] == sorted_values[start]:
            end += 1
        rank = (start + 1 + end) / 2.0
        ranks[order[start:end]] = rank
        start = end
    return ranks


def spearman_correlation(left: np.ndarray, right: np.ndarray) -> float | None:
    left_rank = _average_ranks(left)
    right_rank = _average_ranks(right)
    left_centered = left_rank - left_rank.mean()
    right_centered = right_rank - right_rank.mean()
    denominator = float(np.sqrt(np.dot(left_centered, left_centered) * np.dot(right_centered, right_centered)))
    if denominator == 0.0:
        return None
    return float(np.dot(left_centered, right_centered) / denominator)


@dataclass(frozen=True)
class ScoreOrder:
    order: np.ndarray
    tie_starts: np.ndarray


def score_order(predictions: np.ndarray) -> ScoreOrder:
    safe = clip_probability(predictions)
    order = np.argsort(safe, kind="mergesort")
    sorted_scores = safe[order]
    tie_starts = np.concatenate(
        [np.asarray([0], dtype=np.int64), np.flatnonzero(np.diff(sorted_scores) != 0.0).astype(np.int64) + 1]
    )
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
    """Return paired AUC-delta intervals while resampling complete students.

    Each resample draws student clusters with replacement. A student drawn more
    than once gives every one of that student's response rows the same positive
    multiplicity, so within-student dependence is retained.
    """

    labels = np.asarray(labels, dtype=np.int8)
    students = np.asarray(student_ids, dtype=np.int64)
    if labels.ndim != 1 or labels.shape != students.shape:
        raise ValueError("Cluster bootstrap labels and student IDs must be aligned one-dimensional arrays")
    if resamples < 1:
        raise ValueError("Cluster bootstrap requires at least one resample")
    unique_students, inverse = np.unique(students, return_inverse=True)
    if unique_students.size < 2:
        raise ValueError("Cluster bootstrap requires at least two students")
    orders = {name: score_order(value) for name, value in predictions.items()}
    for name, prediction in predictions.items():
        if np.asarray(prediction).shape != labels.shape:
            raise ValueError(f"Prediction {name} is not aligned to labels")
    for comparison, (candidate_name, baseline_name) in comparisons.items():
        if candidate_name not in orders or baseline_name not in orders:
            raise ValueError(f"Comparison {comparison} references an unknown prediction")
    observed_auc = {
        name: float(roc_auc_score(labels, clip_probability(value))) if len(np.unique(labels)) > 1 else None
        for name, value in predictions.items()
    }
    samples: dict[str, list[float]] = {name: [] for name in comparisons}
    rng = np.random.default_rng(seed)
    for _ in range(resamples):
        sampled = rng.integers(0, unique_students.size, size=unique_students.size)
        cluster_multiplicity = np.bincount(sampled, minlength=unique_students.size)
        row_weights = cluster_multiplicity[inverse]
        auc_values = {
            name: weighted_auc_from_order(labels, ordering, row_weights)
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
    output: dict[str, dict[str, float | int | None | str]] = {}
    for name, (candidate_name, baseline_name) in comparisons.items():
        deltas = samples[name]
        observed_candidate = observed_auc[candidate_name]
        observed_baseline = observed_auc[baseline_name]
        output[name] = {
            "candidate": candidate_name,
            "baseline": baseline_name,
            "observed_auc_delta": (observed_candidate - observed_baseline)
            if observed_candidate is not None and observed_baseline is not None
            else None,
            "lower_bound": float(np.quantile(deltas, alpha)) if deltas else None,
            "upper_bound": float(np.quantile(deltas, 1.0 - alpha)) if deltas else None,
            "resamples_requested": resamples,
            "resamples_valid": len(deltas),
            "confidence_level": confidence_level,
            "student_cluster_count": int(unique_students.size),
            "method": method,
        }
    return output


def exposure_matrices(
    train: InteractionSplit, student_num: int, topic_num: int
) -> tuple[np.ndarray, np.ndarray]:
    if train.students.max() >= student_num or train.topics.max() >= topic_num:
        raise ValueError("Train rows exceed supplied student or Topic cardinality")
    exposure = np.zeros((student_num, topic_num), dtype=np.int32)
    correct = np.zeros((student_num, topic_num), dtype=np.int32)
    np.add.at(exposure, (train.students, train.topics), 1)
    np.add.at(correct, (train.students, train.topics), train.outcomes.astype(np.int32))
    return exposure, correct


def _sample_distinct_pairs(student_num: int, requested: int, seed: int) -> tuple[np.ndarray, np.ndarray]:
    if student_num < 2 or requested < 1:
        return np.asarray([], dtype=np.int64), np.asarray([], dtype=np.int64)
    maximum = student_num * (student_num - 1) // 2
    target = min(requested, maximum)
    rng = np.random.default_rng(seed)
    pairs: set[tuple[int, int]] = set()
    while len(pairs) < target:
        left = int(rng.integers(0, student_num))
        right = int(rng.integers(0, student_num - 1))
        if right >= left:
            right += 1
        pairs.add((left, right) if left < right else (right, left))
    ordered = sorted(pairs)
    return (
        np.asarray([left for left, _ in ordered], dtype=np.int64),
        np.asarray([right for _, right in ordered], dtype=np.int64),
    )


def _full_vector_summary(mastery: np.ndarray, pair_sample_size: int, seed: int) -> dict[str, Any]:
    if mastery.ndim != 2 or not np.isfinite(mastery).all():
        raise ValueError("Full mastery summary requires a finite two-dimensional mastery matrix")
    concept_std = mastery.std(axis=0)
    left, right = _sample_distinct_pairs(mastery.shape[0], pair_sample_size, seed)
    if left.size:
        first = mastery[left]
        second = mastery[right]
        norms = np.linalg.norm(first, axis=1) * np.linalg.norm(second, axis=1)
        cosine = np.divide(
            np.sum(first * second, axis=1),
            norms,
            out=np.zeros(left.size, dtype=np.float64),
            where=norms > 0,
        )
        mean_absolute_difference = np.abs(first - second).mean(axis=1)
    else:
        cosine = np.asarray([], dtype=np.float64)
        mean_absolute_difference = np.asarray([], dtype=np.float64)
    return {
        "semantics": "Historical full-vector compatibility summary. It includes UNKNOWN pairs and is not used as observed-mastery evidence.",
        "median_concept_standard_deviation": float(np.median(concept_std)),
        "concept_standard_deviation_distribution": distribution(concept_std),
        "mean_pairwise_cosine": float(cosine.mean()) if cosine.size else None,
        "pairwise_mean_absolute_difference": float(mean_absolute_difference.mean()) if mean_absolute_difference.size else None,
        "pair_sample_size": int(left.size),
    }


def observability_summary(
    mastery: np.ndarray,
    exposure: np.ndarray,
    thresholds: Sequence[int],
    minimum_topic_sample_size: int,
    pairwise_common_minimum: int,
    pairwise_summary_sample_size: int,
    pairwise_seed: int,
) -> dict[str, Any]:
    if mastery.shape != exposure.shape:
        raise ValueError("Mastery and exposure matrix shapes differ")
    if minimum_topic_sample_size < 1:
        raise ValueError("Minimum Topic sample size must be positive")
    if not np.isfinite(mastery).all():
        raise ValueError("Mastery has non-finite values")
    output: dict[str, Any] = {
        "unknown_semantics": "UNKNOWN means train/history exposure equals zero. Its numeric model prior is never treated as observed mastery.",
        "full_vector_compatibility": _full_vector_summary(mastery, pairwise_summary_sample_size, pairwise_seed),
        "thresholds": {},
    }
    for threshold in thresholds:
        if threshold < 1:
            raise ValueError("Exposure thresholds must be positive")
        observed = exposure >= threshold
        student_counts = observed.sum(axis=1)
        topic_counts = observed.sum(axis=0)
        topic_rows: list[dict[str, Any]] = []
        retained_stds: list[float] = []
        constant_retained_topics: list[int] = []
        for topic_id in range(mastery.shape[1]):
            values = mastery[observed[:, topic_id], topic_id]
            topic_row: dict[str, Any] = {
                "topic_id": topic_id,
                "observed_student_count": int(values.size),
                "included_in_aggregate": bool(values.size >= minimum_topic_sample_size),
            }
            if values.size >= minimum_topic_sample_size:
                stats = distribution(values)
                topic_row.update({"mean_mastery": stats["mean"], "std_mastery": stats["std"], "p05": stats["p05"], "p50": stats["median"], "p95": stats["p95"]})
                retained_stds.append(float(stats["std"]))
                if float(stats["std"]) == 0.0:
                    constant_retained_topics.append(topic_id)
            else:
                topic_row.update({"mean_mastery": None, "std_mastery": None, "p05": None, "p50": None, "p95": None})
            topic_rows.append(topic_row)
        within_student_std: list[float] = []
        within_student_range: list[float] = []
        within_student_iqr: list[float] = []
        for student_id in range(mastery.shape[0]):
            values = mastery[student_id, observed[student_id]]
            if not values.size:
                continue
            within_student_std.append(float(values.std()))
            within_student_range.append(float(values.max() - values.min()))
            within_student_iqr.append(float(np.quantile(values, 0.75) - np.quantile(values, 0.25)))
        common = observed.astype(np.int16) @ observed.astype(np.int16).T
        eligible_pair_count = int(np.count_nonzero(np.triu(common >= pairwise_common_minimum, k=1)))
        sampled_common_counts: list[int] = []
        centered_rmse: list[float] = []
        if eligible_pair_count:
            rng = np.random.default_rng(pairwise_seed + threshold)
            requested = min(pairwise_summary_sample_size, eligible_pair_count)
            seen: set[tuple[int, int]] = set()
            attempts = 0
            maximum_attempts = max(requested * 200, 1000)
            while len(seen) < requested and attempts < maximum_attempts:
                attempts += 1
                first = int(rng.integers(0, mastery.shape[0]))
                second = int(rng.integers(0, mastery.shape[0] - 1))
                if second >= first:
                    second += 1
                pair = (first, second) if first < second else (second, first)
                if pair in seen or common[pair[0], pair[1]] < pairwise_common_minimum:
                    continue
                seen.add(pair)
                shared = observed[pair[0]] & observed[pair[1]]
                first_values = mastery[pair[0], shared]
                second_values = mastery[pair[1], shared]
                difference = (first_values - first_values.mean()) - (second_values - second_values.mean())
                centered_rmse.append(float(np.sqrt(np.mean(np.square(difference)))))
                sampled_common_counts.append(int(shared.sum()))
        output["thresholds"][str(threshold)] = {
            "definition": f"OBSERVED_{threshold} means train/history exposure >= {threshold}; all other student-Topic pairs remain UNKNOWN for this threshold.",
            "student_topic_pair_coverage": int(observed.sum()),
            "student_topic_pair_total": int(observed.size),
            "coverage_fraction": float(observed.mean()),
            "unknown_pair_count": int((~observed).sum()),
            "per_student_observed_topic_count": distribution(student_counts),
            "per_topic_observed_student_count": distribution(topic_counts),
            "concept_side": {
                "minimum_topic_sample_size": minimum_topic_sample_size,
                "retained_topic_count": len(retained_stds),
                "aggregate_topic_standard_deviation": distribution(retained_stds),
                "constant_retained_topic_ids": constant_retained_topics,
                "topic_rows": topic_rows,
            },
            "student_side": {
                "students_with_observed_topic": int((student_counts > 0).sum()),
                "within_student_mastery_standard_deviation": distribution(within_student_std),
                "within_student_mastery_range": distribution(within_student_range),
                "within_student_mastery_iqr": distribution(within_student_iqr),
            },
            "pairwise_observed_only": {
                "definition": "For each sampled pair, compare only common OBSERVED Topics after centering each student over that shared Topic set. UNKNOWN values are excluded.",
                "common_observed_topic_minimum": pairwise_common_minimum,
                "eligible_pair_count": eligible_pair_count,
                "sampled_pair_count": len(centered_rmse),
                "sampled_common_observed_topic_count": distribution(sampled_common_counts),
                "centered_observed_mnd_rmse": distribution(centered_rmse),
            },
        }
    return output


def historical_signal_scores(
    train: InteractionSplit,
    evaluation: InteractionSplit,
    mastery: np.ndarray,
    student_num: int,
    exercise_num: int,
    topic_num: int,
) -> tuple[dict[str, np.ndarray], np.ndarray, np.ndarray, np.ndarray]:
    exposure, correct = exposure_matrices(train, student_num, topic_num)
    if evaluation.students.max() >= student_num or evaluation.exercises.max() >= exercise_num or evaluation.topics.max() >= topic_num:
        raise ValueError("Evaluation rows exceed frozen MODEL-1 cardinalities")
    global_rate = float(train.outcomes.mean())
    student_attempts = np.bincount(train.students, minlength=student_num)
    student_correct = np.bincount(train.students, weights=train.outcomes, minlength=student_num)
    exercise_attempts = np.bincount(train.exercises, minlength=exercise_num)
    exercise_correct = np.bincount(train.exercises, weights=train.outcomes, minlength=exercise_num)
    topic_attempts = np.bincount(train.topics, minlength=topic_num)
    topic_correct = np.bincount(train.topics, weights=train.outcomes, minlength=topic_num)
    student_global_beta = (student_correct + 1.0) / (student_attempts + 2.0)
    rule_beta = (correct + 1.0) / (exposure + 2.0)
    exercise_rate = np.divide(
        exercise_correct,
        exercise_attempts,
        out=np.full(exercise_num, global_rate, dtype=np.float64),
        where=exercise_attempts > 0,
    )
    topic_rate = np.divide(
        topic_correct,
        topic_attempts,
        out=np.full(topic_num, global_rate, dtype=np.float64),
        where=topic_attempts > 0,
    )
    row_exposure = exposure[evaluation.students, evaluation.topics]
    return (
        {
            "C40_MASTERY": mastery[evaluation.students, evaluation.topics],
            "RULE_BETA": rule_beta[evaluation.students, evaluation.topics],
            "STUDENT_GLOBAL_BETA": student_global_beta[evaluation.students],
            "TOPIC_RATE": topic_rate[evaluation.topics],
            "EXERCISE_RATE": exercise_rate[evaluation.exercises],
        },
        row_exposure,
        exposure,
        correct,
    )


def future_signal_summary(
    split: InteractionSplit,
    scores: Mapping[str, np.ndarray],
    row_exposure: np.ndarray,
    thresholds: Sequence[int],
    calibration_bin_count: int,
) -> dict[str, Any]:
    output: dict[str, Any] = {}
    for threshold in thresholds:
        selected = row_exposure >= threshold
        if not selected.any():
            output[str(threshold)] = {"row_count": 0, "score_metrics": {}, "spearman_c40_rule_beta": None}
            continue
        subset = InteractionSplit(
            students=split.students[selected],
            exercises=split.exercises[selected],
            topics=split.topics[selected],
            outcomes=split.outcomes[selected],
        )
        metrics: dict[str, Any] = {}
        for name, score in scores.items():
            selected_score = np.asarray(score)[selected]
            metrics[name] = {
                "metrics": binary_metrics(subset.outcomes, selected_score),
                "calibration_bins": calibration_bins(subset.outcomes, selected_score, calibration_bin_count),
                "within_topic_rank_agreement": within_topic_rank_agreement(subset, selected_score),
            }
        output[str(threshold)] = {
            "definition": f"Future response rows whose response Topic has train/history exposure >= {threshold} for that student. Scores are fixed from train/history and never updated online.",
            "row_count": int(subset.size),
            "student_count": int(np.unique(subset.students).size),
            "outcome_rate": float(subset.outcomes.mean()),
            "score_metrics": metrics,
            "spearman_c40_rule_beta": spearman_correlation(
                np.asarray(scores["C40_MASTERY"])[selected], np.asarray(scores["RULE_BETA"])[selected]
            ),
        }
    return output


def bytes_text(value: int | None) -> str:
    if value is None:
        return "N/A"
    return f"{value:,} bytes ({value / (1024 * 1024):.2f} MiB)"


def metric_text(value: float | int | None) -> str:
    return f"{value:.6f}" if isinstance(value, (float, int)) else "N/A"


def elapsed_seconds(started: float) -> float:
    return time.perf_counter() - started
