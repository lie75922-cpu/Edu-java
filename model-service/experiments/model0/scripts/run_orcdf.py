"""Run the train-response-graph ORCDF candidate after the NCDM baseline."""

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

from inscd_adapter import ORCDFNCDAdapter, ResponseGraph, build_response_graph
from model0_common import (
    CHECKPOINT_ROOT,
    DERIVED_ROOT,
    MODEL0_ROOT,
    REPORT_ROOT,
    ResourceTracker,
    assert_preflight_ready,
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
    read_json,
    runtime_versions,
    set_deterministic_seed,
    synchronize,
    write_json,
)


def predict(
    model: ORCDFNCDAdapter,
    split: Any,
    q_matrix: torch.Tensor,
    graph: ResponseGraph,
    device: torch.device,
    tracker: ResourceTracker,
) -> tuple[np.ndarray, dict[str, float]]:
    """Evaluate one split in one graph pass; no response edge comes from that split."""

    model.eval()
    synchronize(device)
    started = time.perf_counter()
    with torch.no_grad():
        students = torch.as_tensor(split.students, dtype=torch.long, device=device)
        exercises = torch.as_tensor(split.exercises, dtype=torch.long, device=device)
        predictions, _ = model(
            students,
            exercises,
            q_matrix.index_select(0, exercises),
            graph,
        )
        output = predictions.detach().cpu().numpy()
        tracker.sample()
    synchronize(device)
    return output, inference_latency_ms(started, time.perf_counter(), split.size)


def is_better(candidate: dict[str, float | None], current: dict[str, float | None] | None) -> bool:
    if current is None:
        return True
    candidate_auc = candidate["auc"] if candidate["auc"] is not None else float("-inf")
    current_auc = current["auc"] if current["auc"] is not None else float("-inf")
    if candidate_auc != current_auc:
        return candidate_auc > current_auc
    return float(candidate["rmse"] or float("inf")) < float(current["rmse"] or float("inf"))


def make_flipped_graph(
    train: Any,
    q_matrix_cpu: torch.Tensor,
    student_num: int,
    exercise_num: int,
    knowledge_num: int,
    flip_ratio: float,
    seed: int,
    device: torch.device,
) -> tuple[ResponseGraph, int]:
    outcomes = train.outcomes.copy()
    random_values = np.random.default_rng(seed).random(outcomes.size)
    flipped = random_values < flip_ratio
    outcomes[flipped] = 1.0 - outcomes[flipped]
    graph = build_response_graph(
        student_num,
        exercise_num,
        knowledge_num,
        torch.as_tensor(train.students, dtype=torch.long),
        torch.as_tensor(train.exercises, dtype=torch.long),
        torch.as_tensor(outcomes, dtype=torch.long),
        q_matrix_cpu,
        device,
    )
    return graph, int(flipped.sum())


def ncdm_comparison(result: dict[str, Any]) -> dict[str, Any]:
    ncdm_path = REPORT_ROOT / "ncdm_result.json"
    if not ncdm_path.exists():
        return {"status": "UNAVAILABLE", "reason": "NCDM result file is absent"}
    ncdm = read_json(ncdm_path)
    if ncdm.get("status") != "COMPLETED":
        return {"status": "UNAVAILABLE", "reason": "NCDM did not complete"}
    return {
        "status": "COMPARABLE_COLD_START_PROTOCOL_ONLY",
        "test_auc_delta": result["test_metrics"]["auc"] - ncdm["test_metrics"]["auc"],
        "test_acc_delta": result["test_metrics"]["acc"] - ncdm["test_metrics"]["acc"],
        "test_rmse_delta": result["test_metrics"]["rmse"] - ncdm["test_metrics"]["rmse"],
        "additional_training_wall_seconds": result["training_wall_seconds"]
        - ncdm["training_wall_seconds"],
        "additional_peak_process_rss_bytes": result["resources"]["peak_process_rss_bytes"]
        - ncdm["resources"]["peak_process_rss_bytes"],
        "additional_checkpoint_bytes": result["checkpoint"]["size_bytes"]
        - ncdm["checkpoint"]["size_bytes"],
        "interpretation_limit": (
            "The two models share the same zero-history held-out-student protocol. "
            "This is not evidence of personalized diagnostic quality for fitted test students."
        ),
    }


def run(config_path: Path) -> dict[str, Any]:
    config = load_config(config_path)
    preflight = assert_preflight_ready()
    set_deterministic_seed(config["seed"])
    device = choose_device(config["device_preference"])
    training = config["training"]
    student_num, exercise_num, knowledge_num = load_cardinalities(DERIVED_ROOT)
    q_matrix_cpu = torch.as_tensor(load_q_matrix(DERIVED_ROOT), dtype=torch.float32)
    q_matrix = q_matrix_cpu.to(device)
    train = load_split("train", DERIVED_ROOT)
    valid = load_split("valid", DERIVED_ROOT)
    held_out = torch.as_tensor(
        load_held_out_student_ids(DERIVED_ROOT), dtype=torch.long, device=device
    )
    train_students_cpu = torch.as_tensor(train.students, dtype=torch.long)
    train_exercises_cpu = torch.as_tensor(train.exercises, dtype=torch.long)
    train_outcomes_cpu = torch.as_tensor(train.outcomes, dtype=torch.long)
    graph_started = time.perf_counter()
    response_graph = build_response_graph(
        student_num,
        exercise_num,
        knowledge_num,
        train_students_cpu,
        train_exercises_cpu,
        train_outcomes_cpu,
        q_matrix_cpu,
        device,
    )
    base_graph_construction_seconds = time.perf_counter() - graph_started
    model = ORCDFNCDAdapter(
        student_num,
        exercise_num,
        knowledge_num,
        int(training["latent_dim"]),
        int(training["gcn_layers"]),
        float(training["ssl_temperature"]),
        float(training["ssl_weight"]),
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
    checkpoint_path = CHECKPOINT_ROOT / "orcdf_ncd_best.pt"
    checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
    train_students = train_students_cpu.to(device)
    train_exercises = train_exercises_cpu.to(device)
    train_labels = torch.as_tensor(train.outcomes, dtype=torch.float32, device=device)
    train_q_masks = q_matrix.index_select(0, train_exercises)
    ssl_student_ids = torch.as_tensor(
        np.unique(train.students), dtype=torch.long, device=device
    )
    best_metrics: dict[str, float | None] | None = None
    best_epoch: int | None = None
    best_latency: dict[str, float] | None = None
    epoch_records: list[dict[str, Any]] = []
    no_improvement = 0
    flip_construction_seconds = 0.0
    training_started = time.perf_counter()

    for epoch in range(1, int(training["max_epochs"]) + 1):
        flip_started = time.perf_counter()
        flipped_graph, flip_count = make_flipped_graph(
            train,
            q_matrix_cpu,
            student_num,
            exercise_num,
            knowledge_num,
            float(training["flip_ratio"]),
            int(config["seed"]) + epoch,
            device,
        )
        current_flip_construction_seconds = time.perf_counter() - flip_started
        flip_construction_seconds += current_flip_construction_seconds
        model.train()
        predictions, extra_loss = model(
            train_students,
            train_exercises,
            train_q_masks,
            response_graph,
            flipped_graph,
            ssl_student_ids,
        )
        classification_loss = loss_function(predictions, train_labels)
        total_loss = classification_loss + extra_loss
        optimizer.zero_grad(set_to_none=True)
        total_loss.backward()
        optimizer.step()
        model.enforce_monotonicity()
        model.neutralize_students(held_out)
        tracker.sample()

        model.neutralize_students(held_out)
        valid_predictions, valid_latency = predict(
            model, valid, q_matrix, response_graph, device, tracker
        )
        valid_metrics = binary_metrics(valid.outcomes, valid_predictions)
        epoch_records.append(
            {
                "epoch": epoch,
                "classification_bce": float(classification_loss.detach().cpu()),
                "ssl_loss": float(extra_loss.detach().cpu()),
                "total_loss": float(total_loss.detach().cpu()),
                "flipped_train_outcome_count": flip_count,
                "flip_graph_construction_seconds": current_flip_construction_seconds,
                "validation": valid_metrics,
                "validation_inference": valid_latency,
            }
        )
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
        raise RuntimeError("ORCDF produced no validation-selected checkpoint")
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
    ledger_path = claim_final_test_evaluation("ORCDF_NCD", selection)
    test = load_split("test", DERIVED_ROOT)
    test_predictions, test_latency = predict(
        model, test, q_matrix, response_graph, device, tracker
    )
    test_metrics = binary_metrics(test.outcomes, test_predictions)
    complete_final_test_evaluation(ledger_path, "ORCDF_NCD", test_metrics)
    result: dict[str, Any] = {
        "status": "COMPLETED",
        "model": "ORCDF-NCD",
        "dataset_name": "JUNYI_MID_MODEL_V1",
        "preflight_status": preflight["status"],
        "implementation": {
            **config["implementation"],
            "runtime_adapter": "model-service/experiments/model0/scripts/inscd_adapter.py",
            "runtime_adapter_reason": (
                "The local inscdkit 1.3.1 distribution pinned torch 2.4.0, whose Windows "
                "fbgemm dependency could not load because libomp140.x86_64.dll is unavailable. "
                "The adapter preserves the referenced ORCDF response-graph and NCD_IF equations, "
                "uses sparse adjacency rather than the reference dense temporary matrix, and avoids "
                "the toolkit's incompatible random interaction split path."
            ),
        },
        "runtime_versions": runtime_versions(),
        "device": str(device),
        "seed": config["seed"],
        "hyperparameters": training,
        "student_split_protocol": config["data_protocol"],
        "response_graph": {
            **config["response_graph"],
            **response_graph.evidence,
            "base_graph_construction_seconds": base_graph_construction_seconds,
            "total_flip_graph_construction_seconds": flip_construction_seconds,
            "ssl_student_node_scope": "train students only; held-out students are excluded from the SSL term",
        },
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
        "resources": tracker.report(),
        "checkpoint": {
            "path": "model-service/experiments/model0/checkpoints/orcdf_ncd_best.pt",
            "size_bytes": checkpoint_path.stat().st_size,
        },
        "test_evaluation_count": 1,
        "bad_cases": prediction_bad_cases(
            test.outcomes, test_predictions, train.exercises, test.exercises
        ),
        "production_integration_changed": False,
    }
    result["comparison_to_ncdm"] = ncdm_comparison(result)
    return result


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--config-path",
        type=Path,
        default=MODEL0_ROOT / "configs/orcdf.json",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result_path = REPORT_ROOT / "orcdf_result.json"
    try:
        result = run(args.config_path)
    except Exception as exc:  # Preserve the failure as evidence; never invent a metric.
        result = {
            "status": "FAILED",
            "model": "ORCDF-NCD",
            "failure_type": type(exc).__name__,
            "failure_message": str(exc),
            "traceback": traceback.format_exc(),
            "production_integration_changed": False,
        }
        write_json(result_path, result)
        print(f"ORCDF failed: {type(exc).__name__}: {exc}")
        return 1
    write_json(result_path, result)
    print(
        "ORCDF completed: "
        f"test_auc={result['test_metrics']['auc']} test_acc={result['test_metrics']['acc']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

