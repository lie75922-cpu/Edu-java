"""Shared data, metric, resource, and gate helpers for MODEL-0 runners."""

from __future__ import annotations

import csv
import importlib.metadata
import json
import os
import random
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

import numpy as np
import psutil
import torch
from sklearn.metrics import accuracy_score, mean_squared_error, roc_auc_score


REPOSITORY_ROOT = Path(__file__).resolve().parents[4]
MODEL0_ROOT = REPOSITORY_ROOT / "model-service/experiments/model0"
DERIVED_ROOT = MODEL0_ROOT / "data/junyi_mid_model_v1"
RUNTIME_ROOT = MODEL0_ROOT / "runtime"
CHECKPOINT_ROOT = MODEL0_ROOT / "checkpoints"
REPORT_ROOT = MODEL0_ROOT / "reports"


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2, sort_keys=True)
        handle.write("\n")


def load_config(path: Path) -> dict[str, Any]:
    config = read_json(path)
    if config.get("dataset_name") != "JUNYI_MID_MODEL_V1":
        raise ValueError("MODEL-0 runner requires JUNYI_MID_MODEL_V1")
    return config


def assert_preflight_ready() -> dict[str, Any]:
    manifest_path = MODEL0_ROOT / "manifests/junyi_mid_model_v1.json"
    manifest = read_json(manifest_path)
    if manifest.get("status") != "PASS_WITH_COLD_START_LIMITATION":
        raise RuntimeError(f"Preflight is not ready: {manifest.get('status')}")
    if manifest["frozen_split"]["counts"] != {"test": 2000, "train": 7000, "valid": 1000}:
        raise RuntimeError("The frozen student split does not have the required 7000/1000/2000 counts")
    if any(manifest["frozen_split"]["overlap"].values()):
        raise RuntimeError("The frozen student split has overlap")
    return manifest


@dataclass(frozen=True)
class InteractionSplit:
    students: np.ndarray
    exercises: np.ndarray
    outcomes: np.ndarray

    @property
    def size(self) -> int:
        return int(self.outcomes.size)


def load_split(split_name: str, derived_root: Path = DERIVED_ROOT) -> InteractionSplit:
    path = derived_root / f"interactions_{split_name}.csv"
    students: list[int] = []
    exercises: list[int] = []
    outcomes: list[float] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.reader(handle)
        header = next(reader, None)
        if header != ["student_id", "exercise_id", "correct"]:
            raise ValueError(f"Unexpected split header in {path}: {header}")
        for row_number, row in enumerate(reader, start=2):
            if len(row) != 3:
                raise ValueError(f"Malformed split row at {path}:{row_number}")
            student, exercise, correct = (int(value) for value in row)
            if correct not in (0, 1):
                raise ValueError(f"Non-binary model label at {path}:{row_number}")
            students.append(student)
            exercises.append(exercise)
            outcomes.append(float(correct))
    if not outcomes:
        raise ValueError(f"No rows in derived {split_name} input")
    return InteractionSplit(
        students=np.asarray(students, dtype=np.int64),
        exercises=np.asarray(exercises, dtype=np.int64),
        outcomes=np.asarray(outcomes, dtype=np.float32),
    )


def load_q_matrix(derived_root: Path = DERIVED_ROOT) -> np.ndarray:
    rows: list[list[float]] = []
    with (derived_root / "q_matrix.csv").open("r", encoding="utf-8", newline="") as handle:
        reader = csv.reader(handle)
        for row_number, row in enumerate(reader, start=1):
            values = [float(value) for value in row]
            if not values or any(value not in (0.0, 1.0) for value in values):
                raise ValueError(f"Invalid Q-matrix row at row {row_number}")
            if sum(values) != 1.0:
                raise ValueError(f"Q-matrix row {row_number} is not one-Topic-per-Exercise")
            rows.append(values)
    matrix = np.asarray(rows, dtype=np.float32)
    if matrix.shape != (624, 40):
        raise ValueError(f"Unexpected Q-matrix shape: {matrix.shape}")
    return matrix


def load_cardinalities(derived_root: Path = DERIVED_ROOT) -> tuple[int, int, int]:
    students = read_json(derived_root / "student_index_map.json")
    exercises = read_json(derived_root / "exercise_index_map.json")
    topics = read_json(derived_root / "topic_index_map.json")
    return len(students), len(exercises), len(topics)


def load_held_out_student_ids(derived_root: Path = DERIVED_ROOT) -> np.ndarray:
    membership = read_json(derived_root / "split_membership.json")
    index_by_external_id = read_json(derived_root / "student_index_map.json")
    held_out = [
        index_by_external_id[student]
        for split_name in ("valid", "test")
        for student in membership[split_name]
    ]
    if len(held_out) != 3000 or len(set(held_out)) != 3000:
        raise RuntimeError("Held-out student IDs do not match the frozen 1000/2000 membership")
    return np.asarray(sorted(held_out), dtype=np.int64)


def choose_device(preference: str) -> torch.device:
    if preference.startswith("cuda") and torch.cuda.is_available():
        return torch.device(preference)
    return torch.device("cpu")


def set_deterministic_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)
    torch.backends.cudnn.benchmark = False
    torch.backends.cudnn.deterministic = True
    torch.use_deterministic_algorithms(True, warn_only=True)


def runtime_versions() -> dict[str, str]:
    result = {"python": os.sys.version.split()[0], "torch": torch.__version__}
    for distribution in ("numpy", "scipy", "scikit-learn", "psutil"):
        try:
            result[distribution] = importlib.metadata.version(distribution)
        except importlib.metadata.PackageNotFoundError:
            result[distribution] = "UNAVAILABLE"
    return result


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
        torch.cuda.synchronize(self.device)
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
    return {
        "auc": float(roc_auc_score(labels, predictions)) if len(np.unique(labels)) > 1 else None,
        "acc": float(accuracy_score(labels, predictions >= 0.5)),
        "rmse": float(np.sqrt(mean_squared_error(labels, predictions))),
    }


def batched_indices(size: int, batch_size: int, shuffle: bool, rng: np.random.Generator) -> list[np.ndarray]:
    indices = np.arange(size, dtype=np.int64)
    if shuffle:
        rng.shuffle(indices)
    return [indices[start : start + batch_size] for start in range(0, size, batch_size)]


def claim_final_test_evaluation(model_name: str, selection: Mapping[str, Any]) -> Path:
    """Write an immutable-in-practice local ledger before labels are evaluated."""

    ledger_path = RUNTIME_ROOT / "test_evaluation_ledger.json"
    ledger = read_json(ledger_path) if ledger_path.exists() else {}
    if model_name in ledger:
        raise RuntimeError(
            f"A final test evaluation is already recorded for {model_name}; refusing a second test evaluation."
        )
    ledger[model_name] = {
        "status": "STARTED",
        "selection": dict(selection),
        "policy": "one final test evaluation after validation selection",
    }
    write_json(ledger_path, ledger)
    return ledger_path


def complete_final_test_evaluation(
    ledger_path: Path, model_name: str, metrics: Mapping[str, Any]
) -> None:
    ledger = read_json(ledger_path)
    ledger[model_name]["status"] = "COMPLETED"
    ledger[model_name]["metrics"] = dict(metrics)
    write_json(ledger_path, ledger)


def synchronize(device: torch.device) -> None:
    if device.type == "cuda":
        torch.cuda.synchronize(device)


def rounded(value: float | None, digits: int = 6) -> float | None:
    return round(value, digits) if value is not None else None


def inference_latency_ms(started: float, completed: float, rows: int) -> dict[str, float]:
    total_seconds = completed - started
    return {
        "total_seconds": total_seconds,
        "milliseconds_per_interaction": (total_seconds * 1000 / rows) if rows else 0.0,
    }


def prediction_bad_cases(labels: np.ndarray, predictions: np.ndarray, train_exercises: np.ndarray, test_exercises: np.ndarray) -> dict[str, Any]:
    binary = predictions >= 0.5
    unseen_exercise_mask = ~np.isin(test_exercises, np.unique(train_exercises))
    unseen_count = int(unseen_exercise_mask.sum())
    unseen_metrics = (
        binary_metrics(labels[unseen_exercise_mask], predictions[unseen_exercise_mask])
        if unseen_count and len(np.unique(labels[unseen_exercise_mask])) > 1
        else None
    )
    return {
        "false_positive_count": int(np.logical_and(binary, labels == 0).sum()),
        "false_negative_count": int(np.logical_and(~binary, labels == 1).sum()),
        "test_rows_on_exercises_unseen_in_train": unseen_count,
        "unseen_exercise_metrics": unseen_metrics,
        "held_out_student_protocol": "all validation/test students use neutral zero-history embeddings",
    }

