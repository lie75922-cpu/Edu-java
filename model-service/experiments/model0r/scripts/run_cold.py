"""Run MODEL-0R new-student cold-start calibration without global updates.

The NCDM global model is fit solely on the frozen 7,000 train students.  For a
new validation/test student, every global parameter is frozen and a standalone
student-representation tensor is the sole optimizer parameter.  ORCDF is
reported as unsupported because the current archived graph adapter has no
equivalent safe standalone path.
"""

from __future__ import annotations

import argparse
import copy
import sys
import time
from pathlib import Path
from typing import Any, Mapping

import numpy as np
import torch
from torch import nn

from model0r_common import (
    CHECKPOINT_ROOT,
    CONFIG_ROOT,
    DERIVED_ROOT,
    MANIFEST_ROOT,
    REPORT_ROOT,
    RUNTIME_ROOT,
    InteractionSplit,
    ResourceTracker,
    batched_indices,
    binary_metrics,
    bytes_text,
    choose_device,
    doa_from_mastery,
    inference_latency,
    is_better,
    load_cardinalities,
    load_interactions,
    load_q_matrix,
    make_rate_estimates,
    metric_text,
    percentile_latency,
    rate_predictions,
    read_json,
    runtime_versions,
    set_deterministic_seed,
    synchronize,
    write_json,
    write_text,
)


LEGACY_SCRIPTS = Path(__file__).resolve().parents[2] / "model0/scripts"
if str(LEGACY_SCRIPTS) not in sys.path:
    sys.path.insert(0, str(LEGACY_SCRIPTS))
from inscd_adapter import NCDMAdapter  # noqa: E402


PROTOCOL = "junyi_mid_cold_v1"
RESULT_PATH = REPORT_ROOT / "cold_result.json"
LEDGER_PATH = RUNTIME_ROOT / "cold_test_evaluation_ledger.json"


def assert_ready() -> dict[str, Any]:
    manifest = read_json(MANIFEST_ROOT / "junyi_mid_cold_v1.json")
    if manifest.get("status") != "PASS":
        raise RuntimeError(f"Cold preflight is not ready: {manifest.get('status')}")
    frozen = manifest["shared_measurements"]["frozen_data0_membership"]
    if frozen["counts"] != {"test": 2000, "train": 7000, "valid": 1000}:
        raise RuntimeError("Cold input does not have the frozen 7000/1000/2000 membership")
    if any(frozen["overlap"].values()):
        raise RuntimeError("Cold input has student membership overlap")
    return manifest


def groups_by_student(split: InteractionSplit) -> dict[int, InteractionSplit]:
    groups: dict[int, InteractionSplit] = {}
    for student in np.unique(split.students):
        indices = np.flatnonzero(split.students == student)
        groups[int(student)] = InteractionSplit(
            students=split.students[indices],
            exercises=split.exercises[indices],
            topics=split.topics[indices],
            outcomes=split.outcomes[indices],
        )
    return groups


def evaluation_split(groups: Mapping[int, InteractionSplit], k: int) -> InteractionSplit:
    eligible = [group for _, group in sorted(groups.items()) if group.size >= k + 1]
    if not eligible:
        raise ValueError(f"No eligible students for k={k}")
    return InteractionSplit(
        students=np.concatenate([group.students[k:] for group in eligible]),
        exercises=np.concatenate([group.exercises[k:] for group in eligible]),
        topics=np.concatenate([group.topics[k:] for group in eligible]),
        outcomes=np.concatenate([group.outcomes[k:] for group in eligible]),
    )


def clone_global_state(model: NCDMAdapter) -> dict[str, torch.Tensor]:
    return {name: tensor.detach().clone() for name, tensor in model.state_dict().items()}


def state_is_equal(model: NCDMAdapter, before: Mapping[str, torch.Tensor]) -> bool:
    after = model.state_dict()
    return set(after) == set(before) and all(torch.equal(after[name], before[name]) for name in before)


def calibrate_representation(
    model: NCDMAdapter,
    calibration: InteractionSplit,
    q_matrix: torch.Tensor,
    calibration_config: Mapping[str, Any],
    device: torch.device,
) -> tuple[torch.Tensor, int, float]:
    """Fit only one standalone new-student representation from calibration rows."""

    started = time.perf_counter()
    topic_num = q_matrix.shape[1]
    representation = nn.Parameter(torch.zeros(topic_num, dtype=torch.float32, device=device))
    if calibration.size == 0:
        return representation.detach(), 0, (time.perf_counter() - started) * 1000
    optimizer = torch.optim.Adam([representation], lr=float(calibration_config["learning_rate"]))
    loss_function = nn.BCELoss()
    exercises = torch.as_tensor(calibration.exercises, dtype=torch.long, device=device)
    labels = torch.as_tensor(calibration.outcomes, dtype=torch.float32, device=device)
    q_masks = q_matrix.index_select(0, exercises)
    previous_loss: float | None = None
    stagnant = 0
    steps = 0
    model.eval()
    for _ in range(int(calibration_config["max_steps"])):
        prediction = model.interaction(
            representation.unsqueeze(0).expand(calibration.size, -1),
            model.difficulty_embedding(exercises),
            model.discrimination_embedding(exercises),
            q_masks,
        )
        loss = loss_function(prediction, labels)
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        optimizer.step()
        current_loss = float(loss.detach().cpu())
        steps += 1
        if previous_loss is not None:
            if previous_loss - current_loss > 0.000001:
                stagnant = 0
            else:
                stagnant += 1
        previous_loss = current_loss
        if stagnant >= 3:
            break
    return representation.detach(), steps, (time.perf_counter() - started) * 1000


def predict_representation(
    model: NCDMAdapter,
    representation: torch.Tensor,
    evaluation: InteractionSplit,
    q_matrix: torch.Tensor,
    device: torch.device,
) -> tuple[np.ndarray, float, bool]:
    before = representation.detach().clone()
    synchronize(device)
    started = time.perf_counter()
    model.eval()
    with torch.no_grad():
        exercises = torch.as_tensor(evaluation.exercises, dtype=torch.long, device=device)
        prediction = model.interaction(
            representation.unsqueeze(0).expand(evaluation.size, -1),
            model.difficulty_embedding(exercises),
            model.discrimination_embedding(exercises),
            q_matrix.index_select(0, exercises),
        ).detach().cpu().numpy()
    synchronize(device)
    unchanged = bool(torch.equal(before, representation))
    return prediction, (time.perf_counter() - started), unchanged


def evaluate_new_students(
    model: NCDMAdapter,
    groups: Mapping[int, InteractionSplit],
    k: int,
    q_matrix: torch.Tensor,
    calibration_config: Mapping[str, Any],
    device: torch.device,
    tracker: ResourceTracker,
    include_doa: bool,
    student_num: int,
) -> dict[str, Any]:
    """Calibrate and score each frozen new student without mutating global state."""

    original_flags = [parameter.requires_grad for parameter in model.parameters()]
    for parameter in model.parameters():
        parameter.requires_grad_(False)
    model.zero_grad(set_to_none=True)
    state_before = clone_global_state(model)
    predictions: list[np.ndarray] = []
    labels: list[np.ndarray] = []
    students: list[np.ndarray] = []
    exercises: list[np.ndarray] = []
    topics: list[np.ndarray] = []
    latencies: list[float] = []
    steps: list[int] = []
    inference_seconds = 0.0
    representation_unchanged = True
    mastery = np.zeros((student_num, q_matrix.shape[1]), dtype=np.float32)
    eligible = 0
    try:
        for student, group in sorted(groups.items()):
            if group.size < k + 1:
                continue
            eligible += 1
            calibration = InteractionSplit(
                students=group.students[:k],
                exercises=group.exercises[:k],
                topics=group.topics[:k],
                outcomes=group.outcomes[:k],
            )
            evaluation = InteractionSplit(
                students=group.students[k:],
                exercises=group.exercises[k:],
                topics=group.topics[k:],
                outcomes=group.outcomes[k:],
            )
            representation, calibration_steps, latency_ms = calibrate_representation(
                model, calibration, q_matrix, calibration_config, device
            )
            prediction, elapsed, unchanged = predict_representation(
                model, representation, evaluation, q_matrix, device
            )
            predictions.append(prediction)
            labels.append(evaluation.outcomes)
            students.append(evaluation.students)
            exercises.append(evaluation.exercises)
            topics.append(evaluation.topics)
            latencies.append(latency_ms)
            steps.append(calibration_steps)
            inference_seconds += elapsed
            representation_unchanged = representation_unchanged and unchanged
            mastery[student] = torch.sigmoid(representation).detach().cpu().numpy()
            tracker.sample()
    finally:
        global_state_unchanged = state_is_equal(model, state_before)
        model.zero_grad(set_to_none=True)
        for parameter, original_flag in zip(model.parameters(), original_flags):
            parameter.requires_grad_(original_flag)
    if eligible == 0:
        raise ValueError(f"No eligible frozen students for k={k}")
    split = InteractionSplit(
        students=np.concatenate(students),
        exercises=np.concatenate(exercises),
        topics=np.concatenate(topics),
        outcomes=np.concatenate(labels),
    )
    if split.size != sum(group.size - k for group in groups.values() if group.size >= k + 1):
        raise AssertionError("Cold evaluation split lost or reused an interaction")
    doa = (
        doa_from_mastery(split, mastery, q_matrix.detach().cpu().numpy())
        if include_doa and k > 0
        else {
            "doa": None,
            "status": "NOT_APPLICABLE_ZERO_HISTORY" if k == 0 else "NOT_REQUESTED",
            "pair_count": 0,
            "eligible_exercises": 0,
        }
    )
    return {
        "eligible_students": eligible,
        "ineligible_students": len(groups) - eligible,
        "coverage_percent": 100.0 * eligible / len(groups),
        "evaluation_interactions": split.size,
        "split": split,
        "predictions": np.concatenate(predictions),
        "metrics": binary_metrics(split.outcomes, np.concatenate(predictions)),
        "doa": doa,
        "calibration_latency": percentile_latency(latencies),
        "calibration_steps": {
            "mean": float(np.mean(steps)),
            "p50": float(np.quantile(steps, 0.50)),
            "p95": float(np.quantile(steps, 0.95)),
            "minimum": int(np.min(steps)),
            "maximum": int(np.max(steps)),
        },
        "inference": inference_latency(0.0, inference_seconds, split.size),
        "global_parameters_frozen_during_calibration": True,
        "global_state_unchanged": global_state_unchanged,
        "evaluation_representation_unchanged": representation_unchanged,
        "optimizer_scope": "standalone_current_student_representation_only",
        "optimizer_parameter_count": int(q_matrix.shape[1]),
    }


def train_global_ncdm(
    config: Mapping[str, Any],
    train: InteractionSplit,
    validation_groups: Mapping[int, InteractionSplit],
    q_matrix_cpu: torch.Tensor,
    cardinalities: tuple[int, int, int],
    device: torch.device,
) -> tuple[NCDMAdapter, dict[str, Any], ResourceTracker]:
    training = config["global_training"]
    calibration = config["calibration"]
    student_num, exercise_num, topic_num = cardinalities
    tracker = ResourceTracker.start(device)
    model = NCDMAdapter(
        student_num,
        exercise_num,
        topic_num,
        list(training["hidden_dims"]),
        float(training["dropout"]),
    ).to(device)
    q_matrix = q_matrix_cpu.to(device)
    optimizer = torch.optim.Adam(
        model.parameters(),
        lr=float(training["learning_rate"]),
        weight_decay=float(training["weight_decay"]),
    )
    loss_function = nn.BCELoss()
    checkpoint_path = CHECKPOINT_ROOT / "cold_ncdm_global_best.pt"
    checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
    train_started = time.perf_counter()
    rng = np.random.default_rng(int(config["seed"]))
    best_metrics: dict[str, float | None] | None = None
    best_epoch: int | None = None
    best_validation: dict[str, Any] | None = None
    no_improvement = 0
    epoch_records: list[dict[str, Any]] = []
    for epoch in range(1, int(training["max_epochs"]) + 1):
        model.train()
        losses: list[float] = []
        for batch in batched_indices(train.size, int(training["batch_size"]), shuffle=True, rng=rng):
            students = torch.as_tensor(train.students[batch], dtype=torch.long, device=device)
            exercises = torch.as_tensor(train.exercises[batch], dtype=torch.long, device=device)
            labels = torch.as_tensor(train.outcomes[batch], dtype=torch.float32, device=device)
            prediction = model(students, exercises, q_matrix.index_select(0, exercises))
            loss = loss_function(prediction, labels)
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            optimizer.step()
            model.enforce_monotonicity()
            losses.append(float(loss.detach().cpu()))
            tracker.sample()
        validation = evaluate_new_students(
            model,
            validation_groups,
            5,
            q_matrix,
            calibration,
            device,
            tracker,
            include_doa=False,
            student_num=student_num,
        )
        validation_metrics = validation["metrics"]
        epoch_records.append(
            {
                "epoch": epoch,
                "mean_global_train_bce": float(np.mean(losses)),
                "validation_k": 5,
                "validation_metrics": validation_metrics,
                "validation_calibration_latency": validation["calibration_latency"],
                "global_state_unchanged": validation["global_state_unchanged"],
                "evaluation_representation_unchanged": validation["evaluation_representation_unchanged"],
            }
        )
        if not validation["global_state_unchanged"] or not validation["evaluation_representation_unchanged"]:
            raise RuntimeError("Validation calibration violated the global or representation immutability audit")
        if is_better(validation_metrics, best_metrics):
            best_metrics = validation_metrics
            best_epoch = epoch
            best_validation = validation
            torch.save(
                {"state_dict": copy.deepcopy(model.state_dict()), "selected_epoch": epoch}, checkpoint_path
            )
            no_improvement = 0
        else:
            no_improvement += 1
            if no_improvement >= int(training["early_stopping_patience"]):
                break
    synchronize(device)
    training_seconds = time.perf_counter() - train_started
    if best_epoch is None or best_metrics is None or best_validation is None:
        raise RuntimeError("Cold NCDM produced no validation-selected global checkpoint")
    checkpoint = torch.load(checkpoint_path, map_location=device)
    model.load_state_dict(checkpoint["state_dict"])
    return model, {
        "status": "SELECTED_VALIDATION_ONLY",
        "model": "NCDM",
        "reference_component": training["reference_component"],
        "hyperparameters": dict(training),
        "selected_epoch": best_epoch,
        "selection_metric": training["selection_metric"],
        "selection_boundary": training["selection_boundary"],
        "validation_k5_metrics": best_metrics,
        "validation_k5_calibration_latency": best_validation["calibration_latency"],
        "epoch_records": epoch_records,
        "training_wall_seconds": training_seconds,
        "checkpoint": {
            "path": "model-service/experiments/model0r/checkpoints/cold_ncdm_global_best.pt",
            "size_bytes": checkpoint_path.stat().st_size,
        },
    }, tracker


def rate_baseline_result(
    estimates: Mapping[str, Any], split: InteractionSplit, kind: str, tracker: ResourceTracker
) -> dict[str, Any]:
    started = time.perf_counter()
    predictions = rate_predictions(estimates, split, kind)
    tracker.sample()
    return {
        "status": "COMPLETED_STATIC_TRAIN_ONLY",
        "metrics": binary_metrics(split.outcomes, predictions),
        "inference": inference_latency(started, time.perf_counter(), split.size),
        "calibration_latency": {"mean_ms": 0.0, "p50_ms": 0.0, "p95_ms": 0.0},
        "calibration_steps": {"mean": 0.0, "p50": 0.0, "p95": 0.0, "minimum": 0, "maximum": 0},
        "prediction": predictions,
    }


def claim_final_test_evaluation(names: list[str]) -> None:
    ledger = read_json(LEDGER_PATH) if LEDGER_PATH.exists() else {}
    duplicate = [name for name in names if name in ledger]
    if duplicate:
        raise RuntimeError(f"Final cold test evaluation already recorded for: {duplicate}")
    for name in names:
        ledger[name] = {
            "status": "STARTED",
            "policy": "One final frozen-test evaluation per model and k after validation-only global checkpoint selection.",
        }
    write_json(LEDGER_PATH, ledger)


def complete_final_test_evaluation(entries: Mapping[str, Mapping[str, Any]]) -> None:
    ledger = read_json(LEDGER_PATH)
    for name, entry in entries.items():
        ledger[name]["status"] = "COMPLETED"
        ledger[name]["metrics"] = dict(entry["metrics"])
    write_json(LEDGER_PATH, ledger)


def public_entry(entry: Mapping[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in entry.items() if key not in {"prediction", "split", "predictions"}}


def run(config_path: Path = CONFIG_ROOT / "cold.json") -> dict[str, Any]:
    config = read_json(config_path)
    if config.get("dataset_name") != "JUNYI_MID_COLD_V1":
        raise ValueError("Cold runner requires JUNYI_MID_COLD_V1")
    preflight = assert_ready()
    set_deterministic_seed(int(config["seed"]))
    device = choose_device(config["device_preference"])
    train = load_interactions(DERIVED_ROOT / PROTOCOL / "interactions_train.csv")
    valid_groups = groups_by_student(
        load_interactions(DERIVED_ROOT / PROTOCOL / "interactions_valid_sequence.csv")
    )
    q_matrix_cpu = torch.as_tensor(load_q_matrix(PROTOCOL), dtype=torch.float32)
    cardinalities = load_cardinalities(PROTOCOL)
    if cardinalities != (10000, 624, 40):
        raise RuntimeError(f"Unexpected cold model cardinalities: {cardinalities}")
    estimates_started = time.perf_counter()
    estimates = make_rate_estimates(train, cardinalities[1], cardinalities[2])
    baseline_training_seconds = time.perf_counter() - estimates_started
    ncdm, global_result, ncdm_tracker = train_global_ncdm(
        config, train, valid_groups, q_matrix_cpu, cardinalities, device
    )
    q_matrix = q_matrix_cpu.to(device)
    baseline_tracker = ResourceTracker.start(device)
    validation_results: dict[str, Any] = {}
    for k in config["k_values"]:
        validation_ncdm = evaluate_new_students(
            ncdm,
            valid_groups,
            int(k),
            q_matrix,
            config["calibration"],
            device,
            ncdm_tracker,
            include_doa=True,
            student_num=cardinalities[0],
        )
        prepared = evaluation_split(valid_groups, int(k))
        if prepared.size != validation_ncdm["evaluation_interactions"]:
            raise AssertionError("Validation calibration/evaluation boundary differs from precomputed split")
        baseline_entries = {
            kind: rate_baseline_result(estimates, prepared, kind, baseline_tracker)
            for kind in ("global", "exercise", "topic")
        }
        validation_results[str(k)] = {
            "eligibility": {
                key: validation_ncdm[key]
                for key in ("eligible_students", "ineligible_students", "coverage_percent", "evaluation_interactions")
            },
            "baselines": {kind: public_entry(entry) for kind, entry in baseline_entries.items()},
            "NCDM": public_entry(validation_ncdm),
        }

    # Test sequences are loaded only after the global model and all validation evidence are final.
    test_groups = groups_by_student(
        load_interactions(DERIVED_ROOT / PROTOCOL / "interactions_test_sequence.csv")
    )
    ledger_names = [
        f"{model.upper()}_K{k}"
        for k in config["k_values"]
        for model in ("GLOBAL", "EXERCISE", "TOPIC", "NCDM")
    ]
    claim_final_test_evaluation(ledger_names)
    test_results: dict[str, Any] = {}
    ledger_entries: dict[str, Mapping[str, Any]] = {}
    for k in config["k_values"]:
        ncdm_entry = evaluate_new_students(
            ncdm,
            test_groups,
            int(k),
            q_matrix,
            config["calibration"],
            device,
            ncdm_tracker,
            include_doa=True,
            student_num=cardinalities[0],
        )
        prepared = evaluation_split(test_groups, int(k))
        if prepared.size != ncdm_entry["evaluation_interactions"]:
            raise AssertionError("Test calibration/evaluation boundary differs from precomputed split")
        baseline_entries = {
            kind: rate_baseline_result(estimates, prepared, kind, baseline_tracker)
            for kind in ("global", "exercise", "topic")
        }
        for kind, entry in baseline_entries.items():
            ledger_entries[f"{kind.upper()}_K{k}"] = entry
        ledger_entries[f"NCDM_K{k}"] = ncdm_entry
        test_results[str(k)] = {
            "eligibility": {
                key: ncdm_entry[key]
                for key in ("eligible_students", "ineligible_students", "coverage_percent", "evaluation_interactions")
            },
            "baselines": {kind: public_entry(entry) for kind, entry in baseline_entries.items()},
            "NCDM": public_entry(ncdm_entry),
            "ORCDF-NCD": {
                "status": config["orcdf_route"]["status_when_no_safe_adapter"],
                "reason": config["orcdf_route"]["reason"],
                "calibration_used": False,
            },
            "ICDM": read_json(CONFIG_ROOT / "optional_routes.json")["ICDM"],
        }
    complete_final_test_evaluation(ledger_entries)
    result = {
        "status": "COMPLETED",
        "dataset_name": config["dataset_name"],
        "preflight_status": preflight["status"],
        "device": str(device),
        "runtime_versions": runtime_versions(),
        "seed": config["seed"],
        "frozen_student_membership": "7000 train / 1000 validation / 2000 test retained exactly",
        "global_ncdm": global_result,
        "calibration_protocol": config["calibration"],
        "rate_estimate_evidence": {
            "global_train_correct_rate": estimates["global_rate"],
            "global_train_exercises_seen": estimates["exercise_seen_count"],
            "global_train_topics_seen": estimates["topic_seen_count"],
            "baseline_train_fit_seconds": baseline_training_seconds,
        },
        "validation": validation_results,
        "test": test_results,
        "resources": {
            "NCDM": ncdm_tracker.report(),
            "baselines": baseline_tracker.report(),
        },
        "test_read_before_global_selection": False,
        "test_evaluation_ledger": "model-service/experiments/model0r/runtime/cold_test_evaluation_ledger.json",
        "production_integration_changed": False,
    }
    write_json(RESULT_PATH, result)
    write_text(REPORT_ROOT / "cold_results.md", make_cold_report(result))
    return result


def table_rows(result: Mapping[str, Any], section: str) -> str:
    rows: list[str] = []
    for k in ("0", "3", "5", "10"):
        entry = result[section][k]
        eligibility = entry["eligibility"]
        for name, baseline in entry["baselines"].items():
            metrics = baseline["metrics"]
            rows.append(
                f"| {name} historical rate | {k} | {eligibility['eligible_students']} | "
                f"{eligibility['coverage_percent']:.2f}% | {eligibility['evaluation_interactions']:,} | "
                f"{metric_text(metrics['auc'])} | {metric_text(metrics['acc'])} | {metric_text(metrics['rmse'])} | "
                f"{metric_text(metrics['log_loss'])} | N/A | 0.000 / 0.000 / 0.000 | "
                f"{baseline['inference']['milliseconds_per_interaction']:.6f} ms/row |"
            )
        ncdm = entry["NCDM"]
        metrics = ncdm["metrics"]
        latency = ncdm["calibration_latency"]
        rows.append(
            f"| NCDM (representation-only adaptation) | {k} | {eligibility['eligible_students']} | "
            f"{eligibility['coverage_percent']:.2f}% | {eligibility['evaluation_interactions']:,} | "
            f"{metric_text(metrics['auc'])} | {metric_text(metrics['acc'])} | {metric_text(metrics['rmse'])} | "
            f"{metric_text(metrics['log_loss'])} | {metric_text(ncdm['doa']['doa'])} | "
            f"{latency['mean_ms']:.3f} / {latency['p50_ms']:.3f} / {latency['p95_ms']:.3f} | "
            f"{ncdm['inference']['milliseconds_per_interaction']:.6f} ms/row |"
        )
        if section == "test":
            rows.append(
                f"| ORCDF-NCD | {k} | {eligibility['eligible_students']} | {eligibility['coverage_percent']:.2f}% | "
                f"{eligibility['evaluation_interactions']:,} | {entry['ORCDF-NCD']['status']} | N/A | N/A | N/A | N/A | N/A | N/A |"
            )
            rows.append(
                f"| ICDM | {k} | {eligibility['eligible_students']} | {eligibility['coverage_percent']:.2f}% | "
                f"{eligibility['evaluation_interactions']:,} | {entry['ICDM']['status']} | N/A | N/A | N/A | N/A | N/A | N/A |"
            )
    return "\n".join(rows)


def make_cold_report(result: Mapping[str, Any]) -> str:
    global_model = result["global_ncdm"]
    calibration = result["calibration_protocol"]
    return f"""# MODEL-0R New-Student Cold Results

## Protocol boundary

The global NCDM model was trained only with the frozen 7,000 train students.
Validation and test students remain unseen during global fitting.  For every
eligible new student, the first k chronological responses are calibration only
and all later responses are evaluation only.  Students with fewer than k+1
rows remain frozen members and are reported as `INELIGIBLE_FOR_K`.

NCDM adaptation uses a standalone 40-dimensional student representation as the
only optimizer parameter.  All global parameters are set `requires_grad=False`
during calibration, their complete tensors are compared before and after each
evaluation, and evaluation runs with `torch.no_grad`.

| Global model selection | Value |
| --- | --- |
| Selected epoch | {global_model['selected_epoch']} |
| Validation k=5 AUC / RMSE | {metric_text(global_model['validation_k5_metrics']['auc'])} / {metric_text(global_model['validation_k5_metrics']['rmse'])} |
| Global train time | {global_model['training_wall_seconds']:.3f} s |
| Checkpoint size | {bytes_text(global_model['checkpoint']['size_bytes'])} |
| Calibration learning rate / max steps | {calibration['learning_rate']} / {calibration['max_steps']} |
| Calibration stopping rule | {calibration['early_stopping']} |

## Validation (selection evidence; k=5 was used only for global checkpoint selection)

| Model | k | Eligible | Coverage | Evaluation rows | AUC | ACC | RMSE | Log Loss | DOA | Calibration mean / P50 / P95 (ms) | Inference |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
{table_rows(result, 'validation')}

## Final test

| Model | k | Eligible | Coverage | Evaluation rows | AUC | ACC | RMSE | Log Loss | DOA | Calibration mean / P50 / P95 (ms) | Inference |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
{table_rows(result, 'test')}

For k=0, NCDM is explicitly the `ZERO_HISTORY_BASELINE`: its standalone
representation is zero and it performs zero optimization steps.  It is not
called calibrated adaptation.  For k>0, DOA is reported only as an
exercise-conditioned score of representations fitted from calibration rows.

## Unsupported or blocked routes

- **ORCDF-NCD:** `{result['test']['0']['ORCDF-NCD']['status']}` — {result['test']['0']['ORCDF-NCD']['reason']}
- **ICDM:** `{result['test']['0']['ICDM']['status']}` — {result['test']['0']['ICDM']['reason']}

No ID mapping was invented for either route.
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=CONFIG_ROOT / "cold.json")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = run(args.config)
    k5 = result["test"]["5"]["NCDM"]
    print(f"Cold protocol completed: k=5 NCDM test AUC={k5['metrics']['auc']:.6f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
