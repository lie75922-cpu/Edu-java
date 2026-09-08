"""Run the frozen MODEL-1 NCDM concept-granularity ablation exactly once.

Both candidates share the same NCDM equations, hidden dimensions, optimizer,
seed, epoch budget, and validation policy. They differ only in their frozen
Q-matrix / ModelConcept representation. Final-test rows are not opened until
the candidates and rate comparator have been selected on validation evidence.
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

from model1_common import (
    CHECKPOINT_ROOT,
    CONFIG_ROOT,
    DERIVED_ROOT,
    MANIFEST_ROOT,
    REPORT_ROOT,
    RUNTIME_ROOT,
    InteractionSplit,
    ResourceTracker,
    aggregate_fine_mastery_to_topics,
    batched_indices,
    binary_metrics,
    bootstrap_auc_delta,
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
from inscd_adapter import NCDMAdapter  # noqa: E402


PROTOCOL_ROOT = DERIVED_ROOT / "junyi_external_holdout_v1"
RESULT_PATH = REPORT_ROOT / "ncdm_concept_ablation.json"
LEDGER_PATH = RUNTIME_ROOT / "final_test_evaluation_ledger.json"


def onehot_ncdm_forward(
    model: NCDMAdapter,
    student_ids: torch.Tensor,
    exercise_ids: torch.Tensor,
    concept_for_exercise: torch.Tensor,
) -> torch.Tensor:
    """Evaluate the unchanged NCDM interaction for a one-hot Q row.

    For a one-hot Q mask, every input to the first interaction linear layer
    except the Exercise-selected concept is exactly zero. Selecting that one
    column is algebraically equivalent to dense masked multiplication while
    avoiding repeated CPU work over known-zero dimensions.
    """

    concept_ids = concept_for_exercise.index_select(0, exercise_ids)
    student = torch.sigmoid(model.student_embedding.weight[student_ids, concept_ids])
    difficulty = torch.sigmoid(model.difficulty_embedding.weight[exercise_ids, concept_ids])
    discrimination = torch.sigmoid(model.discrimination_embedding(exercise_ids)).view(-1)
    feature = discrimination * (student - difficulty)
    layers = list(model.interaction.mlp)
    first = layers[0]
    if not isinstance(first, nn.Linear):
        raise TypeError("NCDM interaction first layer must be linear")
    hidden = feature.unsqueeze(1) * first.weight.index_select(1, concept_ids).transpose(0, 1)
    hidden = hidden + first.bias
    for layer in layers[1:]:
        hidden = layer(hidden)
    return hidden.view(-1)


def assert_ready() -> tuple[dict[str, Any], dict[str, Any]]:
    manifest = read_json(MANIFEST_ROOT / "junyi_external_holdout_v1.json")
    catalog = read_json(MANIFEST_ROOT / "model_concept_catalog_v1.json")
    if manifest.get("status") != "FROZEN_BEFORE_MODEL_TRAINING":
        raise RuntimeError(f"MODEL-1 holdout is not frozen: {manifest.get('status')}")
    if manifest["student_membership"]["student_count"] < 1:
        raise RuntimeError("MODEL-1 holdout has no frozen members")
    if manifest["student_membership"]["original_medium_overlap"] != 0:
        raise RuntimeError("MODEL-1 external holdout overlaps original Medium membership")
    if manifest["selection"]["correct_label_used"] is not False:
        raise RuntimeError("MODEL-1 holdout selection was not label-independent")
    if catalog["candidate_model_concept_count"] != manifest["concept_catalog"]["candidate_model_concept_count"]:
        raise RuntimeError("Catalog and frozen holdout disagree on fine ModelConcept cardinality")
    return manifest, catalog


def predict_ncdm(
    model: NCDMAdapter,
    split: InteractionSplit,
    concept_for_exercise: torch.Tensor,
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
            predictions.append(onehot_ncdm_forward(model, students, exercises, concept_for_exercise).detach().cpu().numpy())
            tracker.sample()
    synchronize(device)
    return np.concatenate(predictions), inference_latency(started, time.perf_counter(), split.size)


def train_candidate(
    name: str,
    config: Mapping[str, Any],
    train: InteractionSplit,
    valid: InteractionSplit,
    concept_for_exercise_cpu: torch.Tensor,
    cardinalities: tuple[int, int, int],
    device: torch.device,
) -> tuple[NCDMAdapter, dict[str, Any], ResourceTracker]:
    training = config["ncdm"]
    student_num, exercise_num, concept_num = cardinalities
    set_deterministic_seed(int(config["seed"]))
    tracker = ResourceTracker.start(device)
    model = NCDMAdapter(
        student_num,
        exercise_num,
        concept_num,
        list(training["hidden_dims"]),
        float(training["dropout"]),
    ).to(device)
    concept_for_exercise = concept_for_exercise_cpu.to(device)
    optimizer = torch.optim.Adam(
        model.parameters(),
        lr=float(training["learning_rate"]),
        weight_decay=float(training["weight_decay"]),
    )
    loss_function = nn.BCELoss()
    checkpoint_path = CHECKPOINT_ROOT / f"{name.lower()}_best.pt"
    checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(int(config["seed"]))
    best_metrics: dict[str, float | None] | None = None
    best_epoch: int | None = None
    best_validation_latency: dict[str, float] | None = None
    no_improvement = 0
    epoch_records: list[dict[str, Any]] = []
    started = time.perf_counter()
    for epoch in range(1, int(training["max_epochs"]) + 1):
        model.train()
        losses: list[float] = []
        for batch in batched_indices(train.size, int(training["batch_size"]), shuffle=True, rng=rng):
            students = torch.as_tensor(train.students[batch], dtype=torch.long, device=device)
            exercises = torch.as_tensor(train.exercises[batch], dtype=torch.long, device=device)
            labels = torch.as_tensor(train.outcomes[batch], dtype=torch.float32, device=device)
            prediction = onehot_ncdm_forward(model, students, exercises, concept_for_exercise)
            loss = loss_function(prediction, labels)
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            optimizer.step()
            model.enforce_monotonicity()
            losses.append(float(loss.detach().cpu()))
            tracker.sample()
        valid_prediction, valid_latency = predict_ncdm(
            model, valid, concept_for_exercise, int(training["batch_size"]), device, tracker
        )
        valid_metrics = binary_metrics(valid.outcomes, valid_prediction)
        epoch_records.append(
            {
                "epoch": epoch,
                "mean_train_bce": float(np.mean(losses)),
                "validation_metrics": valid_metrics,
                "validation_inference": valid_latency,
            }
        )
        if is_better(valid_metrics, best_metrics):
            best_metrics = valid_metrics
            best_epoch = epoch
            best_validation_latency = valid_latency
            torch.save({"state_dict": copy.deepcopy(model.state_dict()), "selected_epoch": epoch}, checkpoint_path)
            no_improvement = 0
        else:
            no_improvement += 1
            if no_improvement >= int(training["early_stopping_patience"]):
                break
    synchronize(device)
    if best_epoch is None or best_metrics is None or best_validation_latency is None:
        raise RuntimeError(f"{name} produced no validation-selected checkpoint")
    checkpoint = torch.load(checkpoint_path, map_location=device)
    model.load_state_dict(checkpoint["state_dict"])
    return model, {
        "status": "SELECTED_VALIDATION_ONLY",
        "model": "NCDM",
        "candidate": name,
        "reference_component": training["reference_component"],
        "hyperparameters": dict(training),
        "selected_epoch": best_epoch,
        "selection_metric": training["selection_metric"],
        "validation_metrics": best_metrics,
        "validation_inference": best_validation_latency,
        "epoch_records": epoch_records,
        "training_wall_seconds": time.perf_counter() - started,
        "checkpoint": {
            "path": f"model-service/experiments/model1/checkpoints/{checkpoint_path.name}",
            "size_bytes": checkpoint_path.stat().st_size,
        },
    }, tracker


def select_validation_baseline(
    estimates: Mapping[str, Any], valid: InteractionSplit
) -> tuple[str, dict[str, dict[str, float | None]]]:
    metrics = {
        kind: binary_metrics(valid.outcomes, rate_predictions(estimates, valid, kind))
        for kind in ("global", "exercise", "topic")
    }
    selected = max(
        metrics,
        key=lambda kind: (
            metrics[kind]["auc"] if metrics[kind]["auc"] is not None else float("-inf"),
            -(metrics[kind]["rmse"] if metrics[kind]["rmse"] is not None else float("inf")),
        ),
    )
    return selected, metrics


def claim_final_test_evaluation(candidate_names: list[str]) -> None:
    ledger = read_json(LEDGER_PATH) if LEDGER_PATH.exists() else {}
    duplicate = [name for name in candidate_names if name in ledger]
    if duplicate:
        raise RuntimeError(f"MODEL-1 final test evaluation already recorded for: {duplicate}")
    for name in candidate_names:
        ledger[name] = {
            "status": "STARTED",
            "policy": "One final test evaluation after fixed configuration and validation-only candidate/comparator selection.",
        }
    write_json(LEDGER_PATH, ledger)


def complete_final_test_evaluation(results: Mapping[str, Mapping[str, Any]]) -> None:
    ledger = read_json(LEDGER_PATH)
    for name, result in results.items():
        ledger[name]["status"] = "COMPLETED"
        ledger[name]["test_metrics"] = dict(result["test_metrics"])
    write_json(LEDGER_PATH, ledger)


def baseline_results(
    estimates: Mapping[str, Any], test: InteractionSplit, tracker: ResourceTracker
) -> tuple[dict[str, dict[str, Any]], dict[str, np.ndarray]]:
    entries: dict[str, dict[str, Any]] = {}
    predictions: dict[str, np.ndarray] = {}
    labels = {
        "global": "Global correct-rate baseline",
        "exercise": "Exercise historical-rate baseline",
        "topic": "Topic historical-rate baseline",
    }
    for kind, label in labels.items():
        started = time.perf_counter()
        prediction = rate_predictions(estimates, test, kind)
        tracker.sample()
        predictions[kind] = prediction
        entries[kind] = {
            "model": label,
            "test_metrics": binary_metrics(test.outcomes, prediction),
            "test_inference": inference_latency(started, time.perf_counter(), test.size),
            "checkpoint": {"path": None, "size_bytes": 0},
        }
    return entries, predictions


def model_bad_cases(split: InteractionSplit, predictions: np.ndarray, train: InteractionSplit) -> dict[str, Any]:
    binary = predictions >= 0.5
    unseen_exercise = ~np.isin(split.exercises, np.unique(train.exercises))
    return {
        "false_positive_count": int(np.logical_and(binary, split.outcomes == 0).sum()),
        "false_negative_count": int(np.logical_and(~binary, split.outcomes == 1).sum()),
        "test_rows_on_exercises_unseen_in_train_history": int(unseen_exercise.sum()),
        "unseen_exercise_metrics": binary_metrics(split.outcomes[unseen_exercise], predictions[unseen_exercise])
        if unseen_exercise.any()
        else None,
    }


def run(config_path: Path = CONFIG_ROOT / "ncdm_ablation.json") -> dict[str, Any]:
    if RESULT_PATH.exists() or LEDGER_PATH.exists():
        raise RuntimeError("MODEL-1 NCDM ablation already has a result or final-test ledger and must not be rerun")
    config = read_json(config_path)
    if config.get("dataset_name") != "JUNYI_EXTERNAL_HOLDOUT_V1":
        raise ValueError("MODEL-1 NCDM ablation requires JUNYI_EXTERNAL_HOLDOUT_V1")
    manifest, catalog = assert_ready()
    set_deterministic_seed(int(config["seed"]))
    device = choose_device(config["device_preference"])
    train = load_interactions(PROTOCOL_ROOT / "interactions_train.csv")
    valid = load_interactions(PROTOCOL_ROOT / "interactions_valid.csv")
    student_num, exercise_num, topic_num = load_cardinalities(PROTOCOL_ROOT)
    fine_num = int(catalog["candidate_model_concept_count"])
    if (student_num, exercise_num, topic_num) != (
        manifest["student_membership"]["student_count"],
        fine_num,
        catalog["topic_count"],
    ):
        raise RuntimeError("Frozen local cardinalities disagree with the tracked manifest/catalog")
    q_c40 = torch.as_tensor(
        load_q_matrix(PROTOCOL_ROOT / "q_c40_topic.csv", exercise_num, topic_num), dtype=torch.float32
    )
    q_fine = torch.as_tensor(
        load_q_matrix(PROTOCOL_ROOT / "q_fine_exercise.csv", exercise_num, fine_num), dtype=torch.float32
    )
    c40_concept_for_exercise = torch.argmax(q_c40, dim=1).to(dtype=torch.long)
    fine_concept_for_exercise = torch.argmax(q_fine, dim=1).to(dtype=torch.long)
    estimates_started = time.perf_counter()
    estimates = make_rate_estimates(train, exercise_num, topic_num)
    baseline_fit_seconds = time.perf_counter() - estimates_started
    selected_baseline, validation_baselines = select_validation_baseline(estimates, valid)
    c40_model, c40_result, c40_tracker = train_candidate(
        "C40_TOPIC", config, train, valid, c40_concept_for_exercise, (student_num, exercise_num, topic_num), device
    )
    fine_model, fine_result, fine_tracker = train_candidate(
        "C_FINE_EXERCISE", config, train, valid, fine_concept_for_exercise, (student_num, exercise_num, fine_num), device
    )

    # Final test becomes visible only after both candidates and the comparator are selected.
    test = load_interactions(PROTOCOL_ROOT / "interactions_test.csv")
    claim_final_test_evaluation(["GLOBAL_RATE", "EXERCISE_RATE", "TOPIC_RATE", "C40_TOPIC", "C_FINE_EXERCISE"])
    baseline_tracker = ResourceTracker.start(device)
    baseline_entries, baseline_predictions = baseline_results(estimates, test, baseline_tracker)
    c40_prediction, c40_latency = predict_ncdm(
        c40_model, test, c40_concept_for_exercise.to(device), int(config["ncdm"]["batch_size"]), device, c40_tracker
    )
    fine_prediction, fine_latency = predict_ncdm(
        fine_model, test, fine_concept_for_exercise.to(device), int(config["ncdm"]["batch_size"]), device, fine_tracker
    )
    sanity_config = config["mastery_sanity"]
    c40_mastery = c40_model.mastery().detach().cpu().numpy()
    fine_mastery = fine_model.mastery().detach().cpu().numpy()
    c40_sanity = mastery_sanity(
        "C40_TOPIC", c40_mastery, q_c40.numpy(), train,
        int(sanity_config["anonymous_case_count"]), int(sanity_config["pairwise_cosine_sample_size"]),
        float(sanity_config["constant_column_standard_deviation_tolerance"]),
        float(sanity_config["minimum_median_concept_standard_deviation"]),
        float(sanity_config["maximum_mean_pairwise_cosine"]),
    )
    fine_sanity = mastery_sanity(
        "C_FINE_EXERCISE", fine_mastery, q_fine.numpy(), train,
        int(sanity_config["anonymous_case_count"]), int(sanity_config["pairwise_cosine_sample_size"]),
        float(sanity_config["constant_column_standard_deviation_tolerance"]),
        float(sanity_config["minimum_median_concept_standard_deviation"]),
        float(sanity_config["maximum_mean_pairwise_cosine"]),
    )
    topic_labels = catalog["topic_column_order"]
    fine_topic_indices = [
        int(concept["topic_id"])
        for concept in sorted(catalog["model_concepts"], key=lambda entry: int(entry["model_concept_id"]))
    ]
    fine_topic_aggregation = aggregate_fine_mastery_to_topics(
        fine_mastery, fine_topic_indices, topic_labels,
        int(sanity_config["pairwise_cosine_sample_size"]),
        float(sanity_config["constant_column_standard_deviation_tolerance"]),
        float(sanity_config["topic_aggregation_minimum_median_standard_deviation"]),
        float(sanity_config["topic_aggregation_maximum_mean_pairwise_cosine"]),
    )
    bootstrap = config["bootstrap"]
    selected_prediction = baseline_predictions[selected_baseline]
    c40_result.update({
        "test_metrics": binary_metrics(test.outcomes, c40_prediction),
        "test_inference": c40_latency,
        "test_auc_vs_selected_nonpersonalized_baseline": bootstrap_auc_delta(
            test.outcomes, c40_prediction, selected_prediction, int(bootstrap["resamples"]),
            float(bootstrap["confidence_level"]), int(config["seed"]) + int(bootstrap["seed_offset"]),
        ),
        "doa": doa_from_mastery(test, c40_mastery, q_c40.numpy()),
        "mastery_sanity_status": c40_sanity["oversmoothing_check"]["status"],
        "resources": c40_tracker.report(),
        "bad_cases": model_bad_cases(test, c40_prediction, train),
    })
    fine_result.update({
        "test_metrics": binary_metrics(test.outcomes, fine_prediction),
        "test_inference": fine_latency,
        "test_auc_vs_selected_nonpersonalized_baseline": bootstrap_auc_delta(
            test.outcomes, fine_prediction, selected_prediction, int(bootstrap["resamples"]),
            float(bootstrap["confidence_level"]), int(config["seed"]) + int(bootstrap["seed_offset"]) + 1,
        ),
        "doa": doa_from_mastery(test, fine_mastery, q_fine.numpy()),
        "mastery_sanity_status": fine_sanity["oversmoothing_check"]["status"],
        "resources": fine_tracker.report(),
        "bad_cases": model_bad_cases(test, fine_prediction, train),
    })
    for entry in baseline_entries.values():
        entry["training_wall_seconds"] = baseline_fit_seconds
        baseline_key = {
            "Global correct-rate baseline": "global",
            "Exercise historical-rate baseline": "exercise",
            "Topic historical-rate baseline": "topic",
        }[entry["model"]]
        entry["validation_metrics"] = validation_baselines[baseline_key]
        entry["resources"] = baseline_tracker.report()
        entry["doa"] = {"doa": None, "status": "NOT_APPLICABLE_NO_STUDENT_MASTERY_VECTOR"}
    complete_final_test_evaluation({
        "GLOBAL_RATE": baseline_entries["global"],
        "EXERCISE_RATE": baseline_entries["exercise"],
        "TOPIC_RATE": baseline_entries["topic"],
        "C40_TOPIC": c40_result,
        "C_FINE_EXERCISE": fine_result,
    })
    result = {
        "status": "COMPLETED",
        "dataset_name": config["dataset_name"],
        "preflight_status": manifest["status"],
        "device": str(device),
        "runtime_versions": runtime_versions(),
        "seed": config["seed"],
        "data_protocol": config["data_protocol"],
        "test_read_before_all_model_selection": False,
        "selected_nonpersonalized_baseline_by_validation": selected_baseline,
        "rate_estimate_evidence": {
            "train_history_global_correct_rate": estimates["global_rate"],
            "train_history_exercises_seen": estimates["exercise_seen_count"],
            "train_history_topics_seen": estimates["topic_seen_count"],
            "baseline_fit_seconds": baseline_fit_seconds,
        },
        "baselines": baseline_entries,
        "models": {"C40_TOPIC": c40_result, "C_FINE_EXERCISE": fine_result},
        "mastery_sanity": {"C40_TOPIC": c40_sanity, "C_FINE_EXERCISE": fine_sanity},
        "fine_concept_to_topic_aggregation": fine_topic_aggregation,
        "test_evaluation_ledger": "model-service/experiments/model1/runtime/final_test_evaluation_ledger.json",
        "product_boundaries": manifest["product_boundaries"],
    }
    write_json(RESULT_PATH, result)
    write_text(REPORT_ROOT / "ncdm_concept_ablation.md", make_ablation_report(result, catalog))
    write_text(REPORT_ROOT / "mastery_sanity.md", make_mastery_report(result, catalog))
    write_text(REPORT_ROOT / "bad_cases.md", make_bad_cases_report(result))
    write_text(REPORT_ROOT / "cost.md", make_cost_report(result))
    write_text(
        REPORT_ROOT / "graph_readiness.md",
        "# MODEL-1 Graph Readiness\n\nPending the recorded fine ModelConcept Final Gate. No graph model has been started.\n",
    )
    return result


def make_ablation_report(result: Mapping[str, Any], catalog: Mapping[str, Any]) -> str:
    baseline_rows = []
    for key in ("global", "exercise", "topic"):
        entry = result["baselines"][key]
        metric = entry["test_metrics"]
        baseline_rows.append(
            f"| {entry['model']} | {metric_text(entry['validation_metrics']['auc'])} | {metric_text(metric['auc'])} | {metric_text(metric['acc'])} | {metric_text(metric['rmse'])} | {metric_text(metric['log_loss'])} |"
        )
    model_rows = []
    for name in ("C40_TOPIC", "C_FINE_EXERCISE"):
        entry = result["models"][name]
        metric = entry["test_metrics"]
        bootstrap = entry["test_auc_vs_selected_nonpersonalized_baseline"]
        model_rows.append(
            f"| {name} / NCDM | {entry['selected_epoch']} | {metric_text(entry['validation_metrics']['auc'])} | {metric_text(metric['auc'])} | {metric_text(metric['acc'])} | {metric_text(metric['rmse'])} | {metric_text(metric['log_loss'])} | {metric_text(bootstrap['observed_auc_delta'])} | {metric_text(bootstrap['lower_bound'])} | {metric_text(entry['doa']['doa'])} |"
        )
    return f"""# MODEL-1 NCDM Concept-Granularity Ablation

## Frozen comparison

The same NCDM equations, hidden layers, dropout, learning rate, batch size,
maximum epochs, early stopping, seed, fresh holdout, and chronological
protocol are used for both candidates. Only Q-matrix / ModelConcept definition
changes.

Because each frozen Q row is exactly one-hot, both candidates use an
algebraically equivalent first-layer implementation that selects the sole
nonzero concept input instead of multiplying known-zero dimensions. It changes
neither the NCDM parameters nor its interaction equation.

| Candidate | Q-matrix | ModelConcept columns |
| --- | --- | ---: |
| C40_TOPIC | eligible Exercise -> frozen business Topic | {catalog['topic_count']} |
| C_FINE_EXERCISE | identity-style eligible Exercise -> sequential ModelConcept | {catalog['candidate_model_concept_count']} |

Only train/history rows fit models and statistical rates. Validation selected
the non-personalized comparator **{result['selected_nonpersonalized_baseline_by_validation']}** before final test rows were opened. The final-test ledger records one evaluation per candidate.

## Non-personalized baselines

| Baseline | Validation AUC | Test AUC | Test ACC | Test RMSE | Test Log Loss |
| --- | ---: | ---: | ---: | ---: | ---: |
{chr(10).join(baseline_rows)}

## NCDM results

| Candidate | Selected epoch | Validation AUC | Test AUC | Test ACC | Test RMSE | Test Log Loss | Test AUC delta | Bootstrap lower bound | DOA |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
{chr(10).join(model_rows)}

The paired bootstrap is a final-test report-only measurement against the
validation-selected comparator; it was not used for selection. DOA is the
pre-registered Exercise-conditioned future-response agreement and uses test
outcomes only to score mastery fitted from train/history.
"""


def make_mastery_summary(name: str, evidence: Mapping[str, Any]) -> str:
    distribution = evidence["mastery_distribution"]
    variance = evidence["student_to_student_concept_variance"]
    coverage = evidence["concept_coverage"]
    constant = evidence["constant_column_check"]
    smoothing = evidence["oversmoothing_check"]
    return f"""## {name}

| Measure | Value |
| --- | ---: |
| Min / P05 / median / P95 / max | {metric_text(distribution['min'])} / {metric_text(distribution['p05'])} / {metric_text(distribution['median'])} / {metric_text(distribution['p95'])} / {metric_text(distribution['max'])} |
| Mean / standard deviation | {metric_text(distribution['mean'])} / {metric_text(distribution['std'])} |
| Concept columns / train-covered concepts | {coverage['concept_columns']} / {coverage['concepts_with_train_coverage']} |
| Train coverage fraction | {coverage['train_coverage_fraction']:.6f} |
| Median per-concept student standard deviation | {metric_text(variance['median_concept_standard_deviation'])} |
| Mean pairwise cosine | {metric_text(smoothing['mean_pairwise_cosine'])} |
| NaN / infinite values | {evidence['nonfinite_values']['nan_count']} / {evidence['nonfinite_values']['infinite_count']} |
| Constant columns / fraction | {constant['constant_column_count']} / {constant['constant_column_fraction']:.6f} |
| Collapse / oversmoothing status | {'yes' if smoothing['collapsed'] else 'no'} / {'yes' if smoothing['oversmoothed'] else 'no'} ({smoothing['status']}) |
"""


def make_mastery_report(result: Mapping[str, Any], catalog: Mapping[str, Any]) -> str:
    fine = result["mastery_sanity"]["C_FINE_EXERCISE"]
    aggregation = result["fine_concept_to_topic_aggregation"]
    aggregation_rows = "\n".join(
        f"| {row['topic_id']} | {row['topic']} | {row['fine_model_concept_count']} | {metric_text(row['student_mean_mastery'])} | {metric_text(row['student_standard_deviation'])} | {row['projection_status']} |"
        for row in aggregation["topic_rows"]
    )
    cases = "\n".join(
        f"- `{case['anonymous_case']}`: {', '.join(f'{value:.6f}' for value in case['mastery_vector'])}"
        for case in fine["anonymous_mastery_vector_sanity_cases"]
    )
    return f"""# MODEL-1 Mastery Sanity

{make_mastery_summary('C40_TOPIC', result['mastery_sanity']['C40_TOPIC'])}

{make_mastery_summary('C_FINE_EXERCISE', fine)}

## Fine ModelConcept -> Topic aggregation

Fine mastery is projected by the catalog's one-Topic-per-Exercise membership;
the mean projection is for business-layer interpretation only and does not turn
fine `model_concept_id` values into platform KnowledgePoint IDs.

| Topic ID | Topic | Fine concepts | Student mean mastery | Student standard deviation | Projection status |
| ---: | --- | ---: | ---: | ---: | --- |
{aggregation_rows}

| Aggregation check | Value |
| --- | --- |
| Median Topic student standard deviation | {metric_text(aggregation['student_to_student_topic_standard_deviation']['median'])} |
| Mean pairwise cosine | {metric_text(aggregation['mean_pairwise_cosine'])} |
| Topics with fine ModelConcepts / missing Topic IDs | {aggregation['topics_with_fine_model_concepts']} / {', '.join(str(value) for value in aggregation['topics_without_fine_model_concepts']) or 'none'} |
| Constant Topic IDs | {', '.join(str(value) for value in aggregation['constant_topic_ids']) or 'none'} |
| Stable and non-collapsed | {aggregation['status']} |

## Twenty anonymized fine-mastery sanity cases

The vector position order is the sequential `model_concept_id` order in
[`model_concept_catalog_v1.json`](../manifests/model_concept_catalog_v1.json).
Case labels are deterministic positions in the frozen student index and do not
expose external student identifiers.

{cases}
"""


def make_bad_cases_report(result: Mapping[str, Any]) -> str:
    rows = []
    for name in ("C40_TOPIC", "C_FINE_EXERCISE"):
        bad = result["models"][name]["bad_cases"]
        rows.append(
            f"| {name} | {bad['false_positive_count']:,} | {bad['false_negative_count']:,} | {bad['test_rows_on_exercises_unseen_in_train_history']:,} |"
        )
    fine = result["mastery_sanity"]["C_FINE_EXERCISE"]
    return f"""# MODEL-1 Bad Cases and Boundaries

| Candidate | False positives | False negatives | Final-test rows on Exercises unseen in train/history |
| --- | ---: | ---: | ---: |
{chr(10).join(rows)}

## Fine diagnosis exceptions retained

| Check | Value |
| --- | --- |
| Missing train-covered fine ModelConcept IDs | {', '.join(str(value) for value in fine['concept_coverage']['missing_model_concept_ids']) or 'none'} |
| Constant fine ModelConcept IDs | {', '.join(str(value) for value in fine['constant_column_check']['constant_model_concept_ids']) or 'none'} |
| NaN / infinite mastery values | {fine['nonfinite_values']['nan_count']} / {fine['nonfinite_values']['infinite_count']} |

No bad case is removed by changing frozen external membership, chronology,
Topic semantics, seed, configuration, or final-test membership. No RCD, ORCDF,
or GEAR-CD run occurs unless the separately recorded Concept Gate passes.
"""


def make_cost_report(result: Mapping[str, Any]) -> str:
    rows = []
    for name in ("C40_TOPIC", "C_FINE_EXERCISE"):
        model = result["models"][name]
        rows.append(
            f"| {name} / NCDM | {result['device']} | {model['training_wall_seconds']:.3f} s | {model['validation_inference']['milliseconds_per_interaction']:.6f} ms/row | {model['test_inference']['milliseconds_per_interaction']:.6f} ms/row | {bytes_text(model['resources']['peak_process_rss_bytes'])} | {model['resources']['gpu']} | {bytes_text(model['checkpoint']['size_bytes'])} |"
        )
    return f"""# MODEL-1 Cost

| Candidate | Device | Train time | Validation inference | Final-test inference | Peak process RAM | Peak GPU | Checkpoint |
| --- | --- | ---: | ---: | ---: | --- | --- | --- |
{chr(10).join(rows)}

`GPU=none` means no CUDA model runtime was used. Peak RAM is sampled process RSS. Checkpoints, local numeric derivatives, runtime ledgers, logs, virtual environments, raw data, and secrets remain ignored and are not uploaded.
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=CONFIG_ROOT / "ncdm_ablation.json")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = run(args.config)
    print(
        "MODEL-1 NCDM ablation completed: "
        f"C40 test AUC={result['models']['C40_TOPIC']['test_metrics']['auc']:.6f}; "
        f"fine test AUC={result['models']['C_FINE_EXERCISE']['test_metrics']['auc']:.6f}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
