"""Run the frozen-split NCDM baseline after MODEL-0 preflight."""

from __future__ import annotations

import argparse
import copy
import time
import traceback
from pathlib import Path
from typing import Any

import numpy as np
import torch
from torch import nn

from inscd_adapter import NCDMAdapter
from model0_common import (
    CHECKPOINT_ROOT,
    DERIVED_ROOT,
    MODEL0_ROOT,
    REPORT_ROOT,
    ResourceTracker,
    assert_preflight_ready,
    batched_indices,
    binary_metrics,
    choose_device,
    claim_final_test_evaluation,
    complete_final_test_evaluation,
    inference_latency_ms,
    load_cardinalities,
    load_config,
    load_held_out_student_ids,
    load_q_matrix,
    load_split,
    prediction_bad_cases,
    runtime_versions,
    set_deterministic_seed,
    synchronize,
    write_json,
)


def predict(
    model: NCDMAdapter,
    split: Any,
    q_matrix: torch.Tensor,
    batch_size: int,
    device: torch.device,
    tracker: ResourceTracker,
) -> tuple[np.ndarray, dict[str, float]]:
    model.eval()
    predictions: list[np.ndarray] = []
    started = time.perf_counter()
    synchronize(device)
    with torch.no_grad():
        for indices in batched_indices(split.size, batch_size, False, np.random.default_rng(0)):
            students = torch.as_tensor(split.students[indices], dtype=torch.long, device=device)
            exercises = torch.as_tensor(split.exercises[indices], dtype=torch.long, device=device)
            q_mask = q_matrix.index_select(0, exercises)
            predictions.append(model(students, exercises, q_mask).detach().cpu().numpy())
            tracker.sample()
    synchronize(device)
    completed = time.perf_counter()
    return np.concatenate(predictions), inference_latency_ms(started, completed, split.size)


def is_better(candidate: dict[str, float | None], current: dict[str, float | None] | None) -> bool:
    if current is None:
        return True
    candidate_auc = candidate["auc"] if candidate["auc"] is not None else float("-inf")
    current_auc = current["auc"] if current["auc"] is not None else float("-inf")
    if candidate_auc != current_auc:
        return candidate_auc > current_auc
    return float(candidate["rmse"] or float("inf")) < float(current["rmse"] or float("inf"))


def run(config_path: Path) -> dict[str, Any]:
    config = load_config(config_path)
    preflight = assert_preflight_ready()
    set_deterministic_seed(config["seed"])
    device = choose_device(config["device_preference"])
    student_num, exercise_num, knowledge_num = load_cardinalities(DERIVED_ROOT)
    q_matrix = torch.as_tensor(load_q_matrix(DERIVED_ROOT), dtype=torch.float32, device=device)
    train = load_split("train", DERIVED_ROOT)
    valid = load_split("valid", DERIVED_ROOT)
    held_out = torch.as_tensor(
        load_held_out_student_ids(DERIVED_ROOT), dtype=torch.long, device=device
    )
    training = config["training"]
    model = NCDMAdapter(
        student_num,
        exercise_num,
        knowledge_num,
        list(training["hidden_dims"]),
        float(training["dropout"]),
    ).to(device)
    model.neutralize_students(held_out)
    optimizer = torch.optim.Adam(
        model.parameters(),
        lr=float(training["learning_rate"]),
        weight_decay=float(training["weight_decay"]),
    )
    loss_function = nn.BCELoss()
    tracker = ResourceTracker.start(device)
    rng = np.random.default_rng(config["seed"])
    checkpoint_path = CHECKPOINT_ROOT / "ncdm_best.pt"
    checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
    best_metrics: dict[str, float | None] | None = None
    best_epoch: int | None = None
    best_latency: dict[str, float] | None = None
    epoch_records: list[dict[str, Any]] = []
    no_improvement = 0
    training_started = time.perf_counter()

    for epoch in range(1, int(training["max_epochs"]) + 1):
        model.train()
        losses: list[float] = []
        for indices in batched_indices(train.size, int(training["batch_size"]), True, rng):
            students = torch.as_tensor(train.students[indices], dtype=torch.long, device=device)
            exercises = torch.as_tensor(train.exercises[indices], dtype=torch.long, device=device)
            labels = torch.as_tensor(train.outcomes[indices], dtype=torch.float32, device=device)
            predictions = model(students, exercises, q_matrix.index_select(0, exercises))
            loss = loss_function(predictions, labels)
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            optimizer.step()
            model.enforce_monotonicity()
            model.neutralize_students(held_out)
            losses.append(float(loss.detach().cpu()))
            tracker.sample()

        # Neutralisation is applied before every held-out prediction, not only once.
        model.neutralize_students(held_out)
        valid_predictions, valid_latency = predict(
            model, valid, q_matrix, int(training["batch_size"]), device, tracker
        )
        valid_metrics = binary_metrics(valid.outcomes, valid_predictions)
        epoch_record = {
            "epoch": epoch,
            "mean_train_bce": float(np.mean(losses)),
            "validation": valid_metrics,
            "validation_inference": valid_latency,
        }
        epoch_records.append(epoch_record)
        if is_better(valid_metrics, best_metrics):
            best_metrics = valid_metrics
            best_epoch = epoch
            best_latency = valid_latency
            torch.save(
                {
                    "state_dict": copy.deepcopy(model.state_dict()),
                    "selected_epoch": best_epoch,
                    "selection_validation_metrics": best_metrics,
                },
                checkpoint_path,
            )
            no_improvement = 0
        else:
            no_improvement += 1
            if no_improvement >= int(training["early_stopping_patience"]):
                break

    synchronize(device)
    training_completed = time.perf_counter()
    if best_epoch is None or best_metrics is None or best_latency is None:
        raise RuntimeError("NCDM produced no validation-selected checkpoint")
    checkpoint = torch.load(checkpoint_path, map_location=device)
    model.load_state_dict(checkpoint["state_dict"])
    model.neutralize_students(held_out)
    selection = {
        "selected_epoch": best_epoch,
        "selection_metric": training["selection_metric"],
        "validation_metrics": best_metrics,
        "test_read_before_selection": False,
        "test_membership_changed": False,
    }
    ledger_path = claim_final_test_evaluation("NCDM", selection)
    test = load_split("test", DERIVED_ROOT)
    test_predictions, test_latency = predict(
        model, test, q_matrix, int(training["batch_size"]), device, tracker
    )
    test_metrics = binary_metrics(test.outcomes, test_predictions)
    complete_final_test_evaluation(ledger_path, "NCDM", test_metrics)
    resource_report = tracker.report()
    return {
        "status": "COMPLETED",
        "model": "NCDM",
        "dataset_name": "JUNYI_MID_MODEL_V1",
        "preflight_status": preflight["status"],
        "implementation": {
            **config["implementation"],
            "runtime_adapter": "model-service/experiments/model0/scripts/inscd_adapter.py",
            "runtime_adapter_reason": (
                "The local inscdkit 1.3.1 distribution pinned torch 2.4.0, whose Windows "
                "fbgemm dependency could not load because libomp140.x86_64.dll is unavailable. "
                "The adapter preserves the referenced NCDM/Default/NCD_IF equations and avoids "
                "the toolkit's incompatible random interaction split path."
            ),
        },
        "runtime_versions": runtime_versions(),
        "device": str(device),
        "seed": config["seed"],
        "hyperparameters": training,
        "student_split_protocol": config["data_protocol"],
        "selection": selection,
        "epoch_records": epoch_records,
        "training_wall_seconds": training_completed - training_started,
        "validation_inference": best_latency,
        "test_inference": test_latency,
        "test_metrics": {
            **test_metrics,
            "doa": None,
            "doa_status": (
                "NOT_APPLICABLE: InsCD's pairwise mastery agreement definition is available, "
                "but all held-out student embeddings are intentionally neutral zero-history rows, "
                "so test-set mastery ordering is not an individually fitted diagnosis."
            ),
        },
        "resources": resource_report,
        "checkpoint": {
            "path": "model-service/experiments/model0/checkpoints/ncdm_best.pt",
            "size_bytes": checkpoint_path.stat().st_size,
        },
        "test_evaluation_count": 1,
        "bad_cases": prediction_bad_cases(
            test.outcomes, test_predictions, train.exercises, test.exercises
        ),
        "production_integration_changed": False,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--config-path",
        type=Path,
        default=MODEL0_ROOT / "configs/ncdm.json",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result_path = REPORT_ROOT / "ncdm_result.json"
    try:
        result = run(args.config_path)
    except Exception as exc:  # A failed experiment is a result, not a fabricated metric.
        result = {
            "status": "FAILED",
            "model": "NCDM",
            "failure_type": type(exc).__name__,
            "failure_message": str(exc),
            "traceback": traceback.format_exc(),
            "production_integration_changed": False,
        }
        write_json(result_path, result)
        print(f"NCDM failed: {type(exc).__name__}: {exc}")
        return 1
    write_json(result_path, result)
    print(
        "NCDM completed: "
        f"test_auc={result['test_metrics']['auc']} test_acc={result['test_metrics']['acc']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

