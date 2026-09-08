"""Run the frozen MODEL-0R warm/transductive diagnosis protocol once.

No test rows are loaded until both NCDM and ORCDF have been selected using
validation only.  The local ignored ledger rejects an accidental second final
test evaluation for any reported candidate.
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
from sklearn.metrics import roc_auc_score
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
    mastery_sanity,
    metric_text,
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
from inscd_adapter import ORCDFNCDAdapter, ResponseGraph, NCDMAdapter, build_response_graph  # noqa: E402


PROTOCOL = "junyi_mid_trans_v1"
RESULT_PATH = REPORT_ROOT / "warm_result.json"
LEDGER_PATH = RUNTIME_ROOT / "warm_test_evaluation_ledger.json"


def assert_ready() -> dict[str, Any]:
    manifest = read_json(MANIFEST_ROOT / "junyi_mid_trans_v1.json")
    if manifest.get("status") != "PASS":
        raise RuntimeError(f"Warm preflight is not ready: {manifest.get('status')}")
    if manifest["shared_measurements"]["frozen_data0_membership"]["counts"] != {
        "test": 2000,
        "train": 7000,
        "valid": 1000,
    }:
        raise RuntimeError("Warm input no longer has the frozen 10,000-member DATA-0 evidence")
    if any(manifest["shared_measurements"]["frozen_data0_membership"]["overlap"].values()):
        raise RuntimeError("Frozen DATA-0 membership overlap is nonzero")
    return manifest


def predict_ncdm(
    model: NCDMAdapter,
    split: InteractionSplit,
    q_matrix: torch.Tensor,
    batch_size: int,
    device: torch.device,
    tracker: ResourceTracker,
) -> tuple[np.ndarray, dict[str, float]]:
    model.eval()
    synchronize(device)
    started = time.perf_counter()
    predictions: list[np.ndarray] = []
    with torch.no_grad():
        for batch in batched_indices(split.size, batch_size, shuffle=False, rng=np.random.default_rng(0)):
            students = torch.as_tensor(split.students[batch], dtype=torch.long, device=device)
            exercises = torch.as_tensor(split.exercises[batch], dtype=torch.long, device=device)
            predictions.append(
                model(students, exercises, q_matrix.index_select(0, exercises)).detach().cpu().numpy()
            )
            tracker.sample()
    synchronize(device)
    return np.concatenate(predictions), inference_latency(started, time.perf_counter(), split.size)


def predict_orcdf(
    model: ORCDFNCDAdapter,
    split: InteractionSplit,
    q_matrix: torch.Tensor,
    graph: ResponseGraph,
    device: torch.device,
    tracker: ResourceTracker,
) -> tuple[np.ndarray, dict[str, float]]:
    model.eval()
    synchronize(device)
    started = time.perf_counter()
    with torch.no_grad():
        students = torch.as_tensor(split.students, dtype=torch.long, device=device)
        exercises = torch.as_tensor(split.exercises, dtype=torch.long, device=device)
        predictions, _ = model(students, exercises, q_matrix.index_select(0, exercises), graph)
        output = predictions.detach().cpu().numpy()
        tracker.sample()
    synchronize(device)
    return output, inference_latency(started, time.perf_counter(), split.size)


def make_flipped_graph(
    train: InteractionSplit,
    q_matrix_cpu: torch.Tensor,
    student_num: int,
    exercise_num: int,
    topic_num: int,
    ratio: float,
    seed: int,
    device: torch.device,
) -> tuple[ResponseGraph, int]:
    outcomes = train.outcomes.copy()
    flipped = np.random.default_rng(seed).random(outcomes.size) < ratio
    outcomes[flipped] = 1.0 - outcomes[flipped]
    graph = build_response_graph(
        student_num,
        exercise_num,
        topic_num,
        torch.as_tensor(train.students, dtype=torch.long),
        torch.as_tensor(train.exercises, dtype=torch.long),
        torch.as_tensor(outcomes, dtype=torch.long),
        q_matrix_cpu,
        device,
    )
    return graph, int(flipped.sum())


def train_ncdm(
    config: Mapping[str, Any],
    train: InteractionSplit,
    valid: InteractionSplit,
    q_matrix_cpu: torch.Tensor,
    cardinalities: tuple[int, int, int],
    device: torch.device,
) -> tuple[NCDMAdapter, dict[str, Any], ResourceTracker]:
    training = config["ncdm"]
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
    train_started = time.perf_counter()
    epoch_records: list[dict[str, Any]] = []
    best_metrics: dict[str, float | None] | None = None
    best_epoch: int | None = None
    best_validation_latency: dict[str, float] | None = None
    no_improvement = 0
    checkpoint_path = CHECKPOINT_ROOT / "warm_ncdm_best.pt"
    checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(int(config["seed"]))
    for epoch in range(1, int(training["max_epochs"]) + 1):
        model.train()
        losses: list[float] = []
        for batch in batched_indices(train.size, int(training["batch_size"]), shuffle=True, rng=rng):
            students = torch.as_tensor(train.students[batch], dtype=torch.long, device=device)
            exercises = torch.as_tensor(train.exercises[batch], dtype=torch.long, device=device)
            labels = torch.as_tensor(train.outcomes[batch], dtype=torch.float32, device=device)
            predictions = model(students, exercises, q_matrix.index_select(0, exercises))
            loss = loss_function(predictions, labels)
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            optimizer.step()
            model.enforce_monotonicity()
            losses.append(float(loss.detach().cpu()))
            tracker.sample()
        validation_predictions, validation_latency = predict_ncdm(
            model, valid, q_matrix, int(training["batch_size"]), device, tracker
        )
        validation_metrics = binary_metrics(valid.outcomes, validation_predictions)
        epoch_records.append(
            {
                "epoch": epoch,
                "mean_train_bce": float(np.mean(losses)),
                "validation": validation_metrics,
                "validation_inference": validation_latency,
            }
        )
        if is_better(validation_metrics, best_metrics):
            best_metrics = validation_metrics
            best_epoch = epoch
            best_validation_latency = validation_latency
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
    if best_epoch is None or best_metrics is None or best_validation_latency is None:
        raise RuntimeError("NCDM produced no validation-selected checkpoint")
    checkpoint = torch.load(checkpoint_path, map_location=device)
    model.load_state_dict(checkpoint["state_dict"])
    return model, {
        "status": "SELECTED_VALIDATION_ONLY",
        "model": "NCDM",
        "reference_component": training["reference_component"],
        "hyperparameters": dict(training),
        "selected_epoch": best_epoch,
        "selection_metric": training["selection_metric"],
        "validation_metrics": best_metrics,
        "validation_inference": best_validation_latency,
        "epoch_records": epoch_records,
        "training_wall_seconds": training_seconds,
        "checkpoint": {
            "path": "model-service/experiments/model0r/checkpoints/warm_ncdm_best.pt",
            "size_bytes": checkpoint_path.stat().st_size,
        },
    }, tracker


def train_orcdf(
    config: Mapping[str, Any],
    train: InteractionSplit,
    valid: InteractionSplit,
    q_matrix_cpu: torch.Tensor,
    cardinalities: tuple[int, int, int],
    device: torch.device,
) -> tuple[ORCDFNCDAdapter, ResponseGraph, dict[str, Any], ResourceTracker]:
    training = config["orcdf"]
    student_num, exercise_num, topic_num = cardinalities
    tracker = ResourceTracker.start(device)
    graph_started = time.perf_counter()
    graph = build_response_graph(
        student_num,
        exercise_num,
        topic_num,
        torch.as_tensor(train.students, dtype=torch.long),
        torch.as_tensor(train.exercises, dtype=torch.long),
        torch.as_tensor(train.outcomes, dtype=torch.long),
        q_matrix_cpu,
        device,
    )
    graph_seconds = time.perf_counter() - graph_started
    model = ORCDFNCDAdapter(
        student_num,
        exercise_num,
        topic_num,
        int(training["latent_dim"]),
        int(training["gcn_layers"]),
        float(training["ssl_temperature"]),
        float(training["ssl_weight"]),
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
    train_students = torch.as_tensor(train.students, dtype=torch.long, device=device)
    train_exercises = torch.as_tensor(train.exercises, dtype=torch.long, device=device)
    train_labels = torch.as_tensor(train.outcomes, dtype=torch.float32, device=device)
    train_q_masks = q_matrix.index_select(0, train_exercises)
    ssl_students = torch.as_tensor(np.unique(train.students), dtype=torch.long, device=device)
    train_started = time.perf_counter()
    epoch_records: list[dict[str, Any]] = []
    best_metrics: dict[str, float | None] | None = None
    best_epoch: int | None = None
    best_validation_latency: dict[str, float] | None = None
    no_improvement = 0
    flip_seconds = 0.0
    checkpoint_path = CHECKPOINT_ROOT / "warm_orcdf_ncd_best.pt"
    checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
    for epoch in range(1, int(training["max_epochs"]) + 1):
        flip_started = time.perf_counter()
        flipped_graph, flip_count = make_flipped_graph(
            train,
            q_matrix_cpu,
            student_num,
            exercise_num,
            topic_num,
            float(training["flip_ratio"]),
            int(config["seed"]) + epoch,
            device,
        )
        current_flip_seconds = time.perf_counter() - flip_started
        flip_seconds += current_flip_seconds
        model.train()
        predictions, ssl_loss = model(
            train_students, train_exercises, train_q_masks, graph, flipped_graph, ssl_students
        )
        classification_loss = loss_function(predictions, train_labels)
        total_loss = classification_loss + ssl_loss
        optimizer.zero_grad(set_to_none=True)
        total_loss.backward()
        optimizer.step()
        model.enforce_monotonicity()
        tracker.sample()
        validation_predictions, validation_latency = predict_orcdf(
            model, valid, q_matrix, graph, device, tracker
        )
        validation_metrics = binary_metrics(valid.outcomes, validation_predictions)
        epoch_records.append(
            {
                "epoch": epoch,
                "classification_bce": float(classification_loss.detach().cpu()),
                "ssl_loss": float(ssl_loss.detach().cpu()),
                "total_loss": float(total_loss.detach().cpu()),
                "flipped_train_outcome_count": flip_count,
                "flip_graph_construction_seconds": current_flip_seconds,
                "validation": validation_metrics,
                "validation_inference": validation_latency,
            }
        )
        if is_better(validation_metrics, best_metrics):
            best_metrics = validation_metrics
            best_epoch = epoch
            best_validation_latency = validation_latency
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
    if best_epoch is None or best_metrics is None or best_validation_latency is None:
        raise RuntimeError("ORCDF produced no validation-selected checkpoint")
    checkpoint = torch.load(checkpoint_path, map_location=device)
    model.load_state_dict(checkpoint["state_dict"])
    return model, graph, {
        "status": "SELECTED_VALIDATION_ONLY",
        "model": "ORCDF-NCD",
        "reference_component": training["reference_component"],
        "hyperparameters": dict(training),
        "selected_epoch": best_epoch,
        "selection_metric": training["selection_metric"],
        "validation_metrics": best_metrics,
        "validation_inference": best_validation_latency,
        "epoch_records": epoch_records,
        "training_wall_seconds": training_seconds,
        "response_graph": {
            **graph.evidence,
            "base_graph_construction_seconds": graph_seconds,
            "total_flipped_graph_construction_seconds": flip_seconds,
            "train_only_response_edges": True,
            "raw_prerequisite_edges_used": False,
        },
        "checkpoint": {
            "path": "model-service/experiments/model0r/checkpoints/warm_orcdf_ncd_best.pt",
            "size_bytes": checkpoint_path.stat().st_size,
        },
    }, tracker


def claim_final_test_evaluation(model_names: list[str]) -> None:
    ledger = read_json(LEDGER_PATH) if LEDGER_PATH.exists() else {}
    duplicate = [name for name in model_names if name in ledger]
    if duplicate:
        raise RuntimeError(f"Final warm test evaluation already recorded for: {duplicate}")
    for name in model_names:
        ledger[name] = {
            "status": "STARTED",
            "policy": "One final test evaluation after both candidate models were selected by validation only.",
        }
    write_json(LEDGER_PATH, ledger)


def complete_final_test_evaluation(results: Mapping[str, Mapping[str, Any]]) -> None:
    ledger = read_json(LEDGER_PATH)
    for name, result in results.items():
        ledger[name]["status"] = "COMPLETED"
        ledger[name]["test_metrics"] = dict(result["test_metrics"])
    write_json(LEDGER_PATH, ledger)


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
        "method": "Paired nonparametric bootstrap over final test interactions; report-only and not used for selection.",
    }


def baseline_evidence(
    estimates: Mapping[str, Any],
    split: InteractionSplit,
    tracker: ResourceTracker,
) -> tuple[dict[str, Any], dict[str, np.ndarray]]:
    result: dict[str, Any] = {}
    predictions: dict[str, np.ndarray] = {}
    labels = {
        "majority": "Majority / Always-correct-rate baseline",
        "global": "Global correct-rate baseline",
        "exercise": "Exercise historical-rate baseline",
        "topic": "Topic historical-rate baseline",
    }
    for kind, label in labels.items():
        started = time.perf_counter()
        prediction = rate_predictions(estimates, split, kind)
        tracker.sample()
        predictions[kind] = prediction
        result[kind] = {
            "model": label,
            "test_metrics": binary_metrics(split.outcomes, prediction),
            "test_inference": inference_latency(started, time.perf_counter(), split.size),
            "checkpoint": {"path": None, "size_bytes": 0},
        }
    return result, predictions


def model_bad_cases(
    split: InteractionSplit,
    predictions: np.ndarray,
    train: InteractionSplit,
) -> dict[str, Any]:
    binary = predictions >= 0.5
    unseen = ~np.isin(split.exercises, np.unique(train.exercises))
    return {
        "false_positive_count": int(np.logical_and(binary, split.outcomes == 0).sum()),
        "false_negative_count": int(np.logical_and(~binary, split.outcomes == 1).sum()),
        "test_rows_on_exercises_unseen_in_warm_train": int(unseen.sum()),
        "unseen_exercise_metrics": binary_metrics(split.outcomes[unseen], predictions[unseen])
        if unseen.any()
        else None,
    }


def run(config_path: Path = CONFIG_ROOT / "warm.json") -> dict[str, Any]:
    config = read_json(config_path)
    if config.get("dataset_name") != "JUNYI_MID_TRANS_V1":
        raise ValueError("Warm runner requires JUNYI_MID_TRANS_V1")
    preflight = assert_ready()
    set_deterministic_seed(int(config["seed"]))
    device = choose_device(config["device_preference"])
    train = load_interactions(DERIVED_ROOT / PROTOCOL / "interactions_train.csv")
    valid = load_interactions(DERIVED_ROOT / PROTOCOL / "interactions_valid.csv")
    q_matrix_cpu = torch.as_tensor(load_q_matrix(PROTOCOL), dtype=torch.float32)
    cardinalities = load_cardinalities(PROTOCOL)
    if cardinalities != (10000, 624, 40):
        raise RuntimeError(f"Unexpected warm model cardinalities: {cardinalities}")
    estimates_started = time.perf_counter()
    estimates = make_rate_estimates(train, cardinalities[1], cardinalities[2])
    baseline_train_seconds = time.perf_counter() - estimates_started
    baseline_validation: dict[str, Any] = {}
    for kind in ("majority", "global", "exercise", "topic"):
        prediction = rate_predictions(estimates, valid, kind)
        baseline_validation[kind] = binary_metrics(valid.outcomes, prediction)
    ordered_baselines = sorted(
        baseline_validation,
        key=lambda kind: (
            baseline_validation[kind]["auc"] if baseline_validation[kind]["auc"] is not None else float("-inf"),
            -(baseline_validation[kind]["rmse"] or float("inf")),
        ),
        reverse=True,
    )
    selected_nonpersonalized_baseline = ordered_baselines[0]

    ncdm, ncdm_result, ncdm_tracker = train_ncdm(
        config, train, valid, q_matrix_cpu, cardinalities, device
    )
    orcdf, response_graph, orcdf_result, orcdf_tracker = train_orcdf(
        config, train, valid, q_matrix_cpu, cardinalities, device
    )

    # Test data becomes visible only after all validation selections above are final.
    test = load_interactions(DERIVED_ROOT / PROTOCOL / "interactions_test.csv")
    claim_final_test_evaluation(
        ["MAJORITY", "GLOBAL_RATE", "EXERCISE_RATE", "TOPIC_RATE", "NCDM", "ORCDF_NCD"]
    )
    baseline_tracker = ResourceTracker.start(device)
    baseline_results, baseline_predictions = baseline_evidence(estimates, test, baseline_tracker)
    q_matrix = q_matrix_cpu.to(device)
    ncdm_predictions, ncdm_test_latency = predict_ncdm(
        ncdm, test, q_matrix, int(config["ncdm"]["batch_size"]), device, ncdm_tracker
    )
    orcdf_predictions, orcdf_test_latency = predict_orcdf(
        orcdf, test, q_matrix, response_graph, device, orcdf_tracker
    )
    ncdm_mastery = ncdm.mastery().detach().cpu().numpy()
    orcdf_mastery = orcdf.mastery(response_graph).detach().cpu().numpy()
    sanity_config = config["mastery_sanity"]
    ncdm_sanity = mastery_sanity(
        "NCDM",
        ncdm_mastery,
        q_matrix_cpu.numpy(),
        train,
        int(sanity_config["anonymous_case_count"]),
        float(sanity_config["minimum_median_topic_standard_deviation"]),
        float(sanity_config["maximum_mean_pairwise_cosine"]),
    )
    orcdf_sanity = mastery_sanity(
        "ORCDF-NCD",
        orcdf_mastery,
        q_matrix_cpu.numpy(),
        train,
        int(sanity_config["anonymous_case_count"]),
        float(sanity_config["minimum_median_topic_standard_deviation"]),
        float(sanity_config["maximum_mean_pairwise_cosine"]),
    )
    ncdm_result.update(
        {
            "test_metrics": binary_metrics(test.outcomes, ncdm_predictions),
            "test_inference": ncdm_test_latency,
            "doa": doa_from_mastery(test, ncdm_mastery, q_matrix_cpu.numpy()),
            "mastery_sanity_status": ncdm_sanity["oversmoothing_check"]["status"],
            "resources": ncdm_tracker.report(),
            "bad_cases": model_bad_cases(test, ncdm_predictions, train),
        }
    )
    orcdf_result.update(
        {
            "test_metrics": binary_metrics(test.outcomes, orcdf_predictions),
            "test_inference": orcdf_test_latency,
            "doa": doa_from_mastery(test, orcdf_mastery, q_matrix_cpu.numpy()),
            "mastery_sanity_status": orcdf_sanity["oversmoothing_check"]["status"],
            "resources": orcdf_tracker.report(),
            "bad_cases": model_bad_cases(test, orcdf_predictions, train),
        }
    )
    for result in baseline_results.values():
        result["training_wall_seconds"] = baseline_train_seconds
        result["validation_metrics"] = baseline_validation[
            {"Majority / Always-correct-rate baseline": "majority", "Global correct-rate baseline": "global", "Exercise historical-rate baseline": "exercise", "Topic historical-rate baseline": "topic"}[result["model"]]
        ]
        result["resources"] = baseline_tracker.report()
        result["doa"] = {"doa": None, "status": "NOT_APPLICABLE_NO_STUDENT_MASTERY_VECTOR"}
    bootstrap_config = config["bootstrap"]
    selected_predictions = baseline_predictions[selected_nonpersonalized_baseline]
    ncdm_result["test_auc_vs_selected_nonpersonalized_baseline"] = bootstrap_auc_delta(
        test.outcomes,
        ncdm_predictions,
        selected_predictions,
        int(bootstrap_config["resamples"]),
        float(bootstrap_config["confidence_level"]),
        int(config["seed"]) + int(bootstrap_config["seed_offset"]),
    )
    orcdf_result["test_auc_vs_selected_nonpersonalized_baseline"] = bootstrap_auc_delta(
        test.outcomes,
        orcdf_predictions,
        selected_predictions,
        int(bootstrap_config["resamples"]),
        float(bootstrap_config["confidence_level"]),
        int(config["seed"]) + int(bootstrap_config["seed_offset"]) + 1,
    )
    complete_final_test_evaluation(
        {
            "MAJORITY": baseline_results["majority"],
            "GLOBAL_RATE": baseline_results["global"],
            "EXERCISE_RATE": baseline_results["exercise"],
            "TOPIC_RATE": baseline_results["topic"],
            "NCDM": ncdm_result,
            "ORCDF_NCD": orcdf_result,
        }
    )
    result = {
        "status": "COMPLETED",
        "dataset_name": config["dataset_name"],
        "preflight_status": preflight["status"],
        "device": str(device),
        "runtime_versions": runtime_versions(),
        "seed": config["seed"],
        "data_protocol": config["data_protocol"],
        "test_read_before_all_model_selection": False,
        "selected_nonpersonalized_baseline_by_validation": selected_nonpersonalized_baseline,
        "rate_estimate_evidence": {
            "warm_train_global_correct_rate": estimates["global_rate"],
            "warm_train_exercises_seen": estimates["exercise_seen_count"],
            "warm_train_topics_seen": estimates["topic_seen_count"],
        },
        "baselines": baseline_results,
        "models": {
            "NCDM": ncdm_result,
            "ORCDF-NCD": orcdf_result,
            "RCD": read_json(CONFIG_ROOT / "optional_routes.json")["RCD"],
            "GEAR-CD": read_json(CONFIG_ROOT / "optional_routes.json")["GEAR-CD"],
        },
        "mastery_sanity": {"NCDM": ncdm_sanity, "ORCDF-NCD": orcdf_sanity},
        "test_evaluation_ledger": "model-service/experiments/model0r/runtime/warm_test_evaluation_ledger.json",
        "production_integration_changed": False,
    }
    write_json(RESULT_PATH, result)
    write_text(REPORT_ROOT / "warm_results.md", make_warm_report(result))
    write_text(REPORT_ROOT / "mastery_sanity.md", make_mastery_report(result))
    write_text(REPORT_ROOT / "bad_cases.md", make_bad_cases_report(result))
    write_text(REPORT_ROOT / "cost.md", make_cost_report(result))
    return result


def make_warm_report(result: Mapping[str, Any]) -> str:
    baselines = result["baselines"]
    models = result["models"]
    rows: list[str] = []
    for key in ("majority", "global", "exercise", "topic"):
        entry = baselines[key]
        metric = entry["test_metrics"]
        rows.append(
            f"| {entry['model']} | {metric_text(metric['auc'])} | {metric_text(metric['acc'])} | "
            f"{metric_text(metric['rmse'])} | {metric_text(metric['log_loss'])} | N/A | "
            f"{entry['training_wall_seconds']:.3f} s shared fit | {entry['test_inference']['total_seconds']:.6f} s | "
            f"{bytes_text(entry['resources']['peak_process_rss_bytes'])} | {entry['resources']['gpu']} | N/A |"
        )
    for key in ("NCDM", "ORCDF-NCD"):
        entry = models[key]
        metric = entry["test_metrics"]
        doa = entry["doa"]
        rows.append(
            f"| {key} | {metric_text(metric['auc'])} | {metric_text(metric['acc'])} | "
            f"{metric_text(metric['rmse'])} | {metric_text(metric['log_loss'])} | {metric_text(doa['doa'])} | "
            f"{entry['training_wall_seconds']:.3f} s | {entry['test_inference']['total_seconds']:.6f} s | "
            f"{bytes_text(entry['resources']['peak_process_rss_bytes'])} | {entry['resources']['gpu']} | "
            f"{bytes_text(entry['checkpoint']['size_bytes'])} |"
        )
    historical = """| Model | AUC | ACC | RMSE | Interpretation |
| --- | ---: | ---: | ---: | --- |
| NCDM | 0.756775 | 0.820678 | 0.361951 | Historical MODEL-0A zero-history unseen-student compatibility result only. |
| ORCDF-NCD | 0.638144 | 0.813219 | 0.472493 | Historical MODEL-0A zero-history unseen-student compatibility result only. |"""
    ncdm = models["NCDM"]
    orcdf = models["ORCDF-NCD"]
    return f"""# MODEL-0R Warm Results

## Scope and leakage controls

`JUNYI_MID_TRANS_V1` uses the exact frozen 10,000 Medium students, but the
unit of split is each student's chronology.  Only earlier warm-train responses
fit models and rates; validation selected both neural candidates before the
test file was read.  The response graph contains only warm-train response
edges plus the frozen Exercise-to-Topic Q-matrix.  It contains no raw
prerequisite edge and no validation/test response edge.

The selected non-personalized comparator was **{result['selected_nonpersonalized_baseline_by_validation']}**, selected by validation AUC then lower RMSE before test evaluation.

| Model | AUC | ACC | RMSE | Log Loss | DOA | Train Time | Test Inference | Peak RAM | GPU | Checkpoint |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- |
{chr(10).join(rows)}

## Model-selection evidence

| Candidate | Selected epoch | Validation AUC | Validation RMSE | Test AUC delta versus validation-selected baseline | Paired bootstrap lower bound |
| --- | ---: | ---: | ---: | ---: | ---: |
| NCDM | {ncdm['selected_epoch']} | {metric_text(ncdm['validation_metrics']['auc'])} | {metric_text(ncdm['validation_metrics']['rmse'])} | {ncdm['test_auc_vs_selected_nonpersonalized_baseline']['observed_auc_delta']:.6f} | {metric_text(ncdm['test_auc_vs_selected_nonpersonalized_baseline']['lower_bound'])} |
| ORCDF-NCD | {orcdf['selected_epoch']} | {metric_text(orcdf['validation_metrics']['auc'])} | {metric_text(orcdf['validation_metrics']['rmse'])} | {orcdf['test_auc_vs_selected_nonpersonalized_baseline']['observed_auc_delta']:.6f} | {metric_text(orcdf['test_auc_vs_selected_nonpersonalized_baseline']['lower_bound'])} |

DOA is the pre-registered exercise-conditioned pairwise diagnosis agreement.
It uses later test outcomes only to score mastery vectors that were fitted from
earlier warm-train histories.

## Routes outside the fair neural table

- **RCD:** `{models['RCD']['status']}` — {models['RCD']['reason']}
- **GEAR-CD:** `{models['GEAR-CD']['status']}` — {models['GEAR-CD']['reason']}

## Historical table kept separate — MODEL-0A / COLD_ZERO_HISTORY

{historical}

The archived MODEL-0A gate remains
`NO_GO_FOR_ZERO_HISTORY_PROTOCOL_AS_MODEL_SELECTION`.  It is not part of this
warm model-selection table and is not evidence that cognitive diagnosis as a
whole failed.
"""


def make_mastery_report(result: Mapping[str, Any]) -> str:
    sections: list[str] = []
    for name in ("NCDM", "ORCDF-NCD"):
        evidence = result["mastery_sanity"][name]
        distribution = evidence["mastery_distribution"]
        variance = evidence["student_to_student_mastery_variance"]
        coverage = evidence["topic_coverage"]
        smoothing = evidence["oversmoothing_check"]
        cases = "\n".join(
            f"| {case['anonymous_case']} | {', '.join(f'{value:.6f}' for value in case['mastery_vector'])} |"
            for case in evidence["anonymous_mastery_vector_sanity_cases"]
        )
        sections.append(
            f"""## {name}

| Distribution measure | Value |
| --- | ---: |
| Min / P05 / median / P95 / max | {distribution['min']:.6f} / {distribution['p05']:.6f} / {distribution['median']:.6f} / {distribution['p95']:.6f} / {distribution['max']:.6f} |
| Mean / standard deviation | {distribution['mean']:.6f} / {distribution['std']:.6f} |
| Topic columns / topics observed in warm train | {coverage['topic_columns']} / {coverage['topics_observed_in_warm_train']} |
| Student-to-student topic variance min / median / max | {variance['minimum']:.6f} / {variance['median']:.6f} / {variance['maximum']:.6f} |
| Median topic standard deviation | {variance['median_topic_standard_deviation']:.6f} |
| Mean pairwise cosine (deterministic 256-or-fewer sample) | {smoothing['mean_pairwise_cosine']:.6f} |
| Collapse / oversmoothing status | {'yes' if smoothing['collapsed'] else 'no'} / {'yes' if smoothing['oversmoothed'] else 'no'} ({smoothing['status']}) |

The 20 vectors below use anonymous case labels only.  Positions are
deterministically distributed across the frozen student index; no external
student identifier is reported.  Each vector has the 40 frozen Topic values in
the manifest's Topic-column order.

| Anonymous student | 40-topic mastery vector |
| --- | --- |
{cases}"""
        )
    return "# MODEL-0R Mastery Sanity\n\n" + "\n\n".join(sections) + "\n"


def make_bad_cases_report(result: Mapping[str, Any]) -> str:
    rows = []
    for name in ("NCDM", "ORCDF-NCD"):
        bad = result["models"][name]["bad_cases"]
        rows.append(
            f"| {name} | {bad['false_positive_count']:,} | {bad['false_negative_count']:,} | "
            f"{bad['test_rows_on_exercises_unseen_in_warm_train']:,} |"
        )
    return f"""# MODEL-0R Bad Cases

## Warm test prediction errors

| Model | False positives | False negatives | Test rows on Exercises unseen in warm train |
| --- | ---: | ---: | ---: |
{chr(10).join(rows)}

These counts are preserved as failure evidence.  They are not used to change
the frozen student set, Topic mapping, sequence split, seed, or configuration.

## Protocol limitations

- RCD remains `NOT_COMPARABLE_ON_COMMON_GRAPH`; no graph or identity mapping was invented.
- GEAR-CD remains smoke-only; no full run was started.
- The historical `MODEL-0A / COLD_ZERO_HISTORY` `NO_GO` remains intact and
  separate from the warm protocol.
"""


def make_cost_report(result: Mapping[str, Any]) -> str:
    rows = []
    for name in ("NCDM", "ORCDF-NCD"):
        model = result["models"][name]
        rows.append(
            f"| {name} | {result['device']} | {model['training_wall_seconds']:.3f} s | "
            f"{model['validation_inference']['milliseconds_per_interaction']:.6f} ms/row | "
            f"{model['test_inference']['milliseconds_per_interaction']:.6f} ms/row | "
            f"{bytes_text(model['resources']['peak_process_rss_bytes'])} | {model['resources']['gpu']} | "
            f"{bytes_text(model['checkpoint']['size_bytes'])} |"
        )
    return f"""# MODEL-0R Cost

| Model | Device | Train time | Validation inference | Test inference | Peak process RAM | Peak GPU | Checkpoint |
| --- | --- | ---: | ---: | ---: | --- | --- | --- |
{chr(10).join(rows)}

The measured device was `{result['device']}`; `GPU=none` means no CUDA model
runtime was used.  Peak RAM is sampled process RSS during the run.  Checkpoints
and runtime ledgers are ignored locally and are not uploaded.
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=CONFIG_ROOT / "warm.json")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = run(args.config)
    print(
        "Warm protocol completed: "
        f"NCDM test AUC={result['models']['NCDM']['test_metrics']['auc']:.6f}; "
        f"ORCDF test AUC={result['models']['ORCDF-NCD']['test_metrics']['auc']:.6f}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
