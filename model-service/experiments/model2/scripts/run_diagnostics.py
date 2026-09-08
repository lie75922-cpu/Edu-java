"""Run the fixed MODEL-2 observability and personalization diagnostics once.

This script uses a read-only MODEL-1 C40 checkpoint for inference-only
ablations. Its only fitted model is the pre-registered simple Rasch/IRT-1PL
baseline. JUNYI_FINAL_HOLDOUT_V1 is never opened by this module.
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

from model2_common import (
    CHECKPOINT_ROOT,
    CONFIG_ROOT,
    MANIFEST_ROOT,
    REPORT_ROOT,
    RUNTIME_ROOT,
    InteractionSplit,
    ResourceTracker,
    batched_indices,
    binary_metrics,
    bytes_text,
    choose_device,
    distribution,
    exposure_matrices,
    future_signal_summary,
    historical_signal_scores,
    load_cardinalities,
    load_interactions,
    load_q_matrix,
    metric_text,
    observability_summary,
    read_json,
    runtime_versions,
    set_deterministic_seed,
    student_cluster_paired_bootstrap_auc_deltas,
    synchronize,
    write_json,
    write_text,
)


LEGACY_SCRIPTS = Path(__file__).resolve().parents[2] / "model0/scripts"
if str(LEGACY_SCRIPTS) not in sys.path:
    sys.path.insert(0, str(LEGACY_SCRIPTS))
from inscd_adapter import NCDMAdapter  # noqa: E402


RESULT_PATH = REPORT_ROOT / "model2_diagnostic_results.json"
LEDGER_PATH = RUNTIME_ROOT / "model2_diagnostic_execution_ledger.json"


class Rasch1PL(nn.Module):
    """Simple fixed-form logit P(correct) = ability - difficulty baseline."""

    def __init__(self, student_num: int, exercise_num: int) -> None:
        super().__init__()
        self.ability = nn.Embedding(student_num, 1)
        self.difficulty = nn.Embedding(exercise_num, 1)
        nn.init.zeros_(self.ability.weight)
        nn.init.zeros_(self.difficulty.weight)

    def forward(self, student_ids: torch.Tensor, exercise_ids: torch.Tensor) -> torch.Tensor:
        logits = self.ability(student_ids).view(-1) - self.difficulty(exercise_ids).view(-1)
        return torch.sigmoid(logits)

    def center_parameters(self) -> None:
        """Fix location without changing the ability-minus-difficulty logits."""

        with torch.no_grad():
            offset = self.ability.weight.mean()
            self.ability.weight.sub_(offset)
            self.difficulty.weight.sub_(offset)


def onehot_ncdm_forward_with_student_logits(
    model: NCDMAdapter,
    student_ids: torch.Tensor,
    exercise_ids: torch.Tensor,
    concept_for_exercise: torch.Tensor,
    student_logits: torch.Tensor,
) -> torch.Tensor:
    """Evaluate unchanged C40 NCDM parameters with an explicit student table."""

    concept_ids = concept_for_exercise.index_select(0, exercise_ids)
    student = torch.sigmoid(student_logits[student_ids, concept_ids])
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


def predict_c40(
    model: NCDMAdapter,
    split: InteractionSplit,
    concept_for_exercise: torch.Tensor,
    student_logits: torch.Tensor,
    batch_size: int,
    device: torch.device,
    tracker: ResourceTracker,
) -> tuple[np.ndarray, dict[str, float]]:
    model.eval()
    synchronize(device)
    started = time.perf_counter()
    prediction: list[np.ndarray] = []
    with torch.no_grad():
        for batch in batched_indices(split.size, batch_size):
            students = torch.as_tensor(split.students[batch], dtype=torch.long, device=device)
            exercises = torch.as_tensor(split.exercises[batch], dtype=torch.long, device=device)
            values = onehot_ncdm_forward_with_student_logits(
                model, students, exercises, concept_for_exercise, student_logits
            )
            prediction.append(values.detach().cpu().numpy())
            tracker.sample()
    synchronize(device)
    total_seconds = time.perf_counter() - started
    return np.concatenate(prediction), {
        "total_seconds": total_seconds,
        "milliseconds_per_interaction": total_seconds * 1000 / split.size,
    }


def deterministic_derangement(student_num: int, seed: int) -> np.ndarray:
    if student_num < 2:
        raise ValueError("A student-identity permutation requires at least two students")
    order = np.random.default_rng(seed).permutation(student_num)
    mapping = np.empty(student_num, dtype=np.int64)
    mapping[order] = np.roll(order, -1)
    if np.any(mapping == np.arange(student_num)):
        raise AssertionError("Deterministic permutation must give every student another embedding row")
    return mapping


def c40_embedding_variants(original_logits: torch.Tensor, permutation_seed: int) -> tuple[dict[str, torch.Tensor], np.ndarray]:
    if original_logits.ndim != 2:
        raise ValueError("C40 student embedding must be a two-dimensional table")
    with torch.no_grad():
        population_mean = original_logits.mean(dim=0, keepdim=True).expand_as(original_logits).clone()
        zero = torch.zeros_like(original_logits)
        permutation = deterministic_derangement(original_logits.shape[0], permutation_seed)
        permuted = original_logits.index_select(
            0, torch.as_tensor(permutation, dtype=torch.long, device=original_logits.device)
        ).clone()
    return {
        "ORIGINAL": original_logits.detach().clone(),
        "POP_MEAN": population_mean,
        "ZERO": zero,
        "PERMUTED": permuted,
    }, permutation


def load_c40_model(
    checkpoint_path: Path,
    student_num: int,
    exercise_num: int,
    topic_num: int,
    config: Mapping[str, Any],
    device: torch.device,
) -> tuple[NCDMAdapter, dict[str, Any]]:
    model = NCDMAdapter(
        student_num,
        exercise_num,
        topic_num,
        [int(value) for value in config["hidden_dims"]],
        float(config["dropout"]),
    ).to(device)
    checkpoint = torch.load(checkpoint_path, map_location=device)
    if not isinstance(checkpoint, dict) or "state_dict" not in checkpoint:
        raise ValueError("MODEL-1 C40 checkpoint lacks the expected state_dict")
    model.load_state_dict(checkpoint["state_dict"], strict=True)
    model.eval()
    return model, {
        "checkpoint_name": checkpoint_path.name,
        "checkpoint_size_bytes": checkpoint_path.stat().st_size,
        "selected_epoch_recorded_by_model1": checkpoint.get("selected_epoch"),
        "mode": "INFERENCE_ONLY_REUSED_MODEL1_C40",
    }


def is_better_rasch(candidate: Mapping[str, float | None], current: Mapping[str, float | None] | None) -> bool:
    if current is None:
        return True
    candidate_auc = candidate["auc"] if candidate["auc"] is not None else float("-inf")
    current_auc = current["auc"] if current["auc"] is not None else float("-inf")
    if candidate_auc != current_auc:
        return bool(candidate_auc > current_auc)
    candidate_loss = candidate["log_loss"] if candidate["log_loss"] is not None else float("inf")
    current_loss = current["log_loss"] if current["log_loss"] is not None else float("inf")
    return bool(candidate_loss < current_loss)


def predict_rasch(
    model: Rasch1PL,
    split: InteractionSplit,
    batch_size: int,
    device: torch.device,
    tracker: ResourceTracker,
) -> tuple[np.ndarray, dict[str, float]]:
    model.eval()
    synchronize(device)
    started = time.perf_counter()
    prediction: list[np.ndarray] = []
    with torch.no_grad():
        for batch in batched_indices(split.size, batch_size):
            students = torch.as_tensor(split.students[batch], dtype=torch.long, device=device)
            exercises = torch.as_tensor(split.exercises[batch], dtype=torch.long, device=device)
            prediction.append(model(students, exercises).detach().cpu().numpy())
            tracker.sample()
    synchronize(device)
    total_seconds = time.perf_counter() - started
    return np.concatenate(prediction), {
        "total_seconds": total_seconds,
        "milliseconds_per_interaction": total_seconds * 1000 / split.size,
    }


def fit_rasch(
    train: InteractionSplit,
    valid: InteractionSplit,
    test: InteractionSplit,
    student_num: int,
    exercise_num: int,
    config: Mapping[str, Any],
    device: torch.device,
    seed: int,
) -> tuple[Rasch1PL, dict[str, Any], np.ndarray, np.ndarray]:
    set_deterministic_seed(seed)
    model = Rasch1PL(student_num, exercise_num).to(device)
    optimizer = torch.optim.Adam(
        model.parameters(), lr=float(config["learning_rate"]), weight_decay=float(config["weight_decay"])
    )
    loss_function = nn.BCELoss()
    tracker = ResourceTracker.start(device)
    checkpoint_path = CHECKPOINT_ROOT / "rasch_1pl_best.pt"
    checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
    best_metrics: dict[str, float | None] | None = None
    best_epoch: int | None = None
    best_latency: dict[str, float] | None = None
    epoch_records: list[dict[str, Any]] = []
    no_improvement = 0
    rng = np.random.default_rng(seed)
    started = time.perf_counter()
    for epoch in range(1, int(config["max_epochs"]) + 1):
        model.train()
        indices = np.arange(train.size, dtype=np.int64)
        rng.shuffle(indices)
        losses: list[float] = []
        for start in range(0, train.size, int(config["batch_size"])):
            batch = indices[start : start + int(config["batch_size"])]
            students = torch.as_tensor(train.students[batch], dtype=torch.long, device=device)
            exercises = torch.as_tensor(train.exercises[batch], dtype=torch.long, device=device)
            outcomes = torch.as_tensor(train.outcomes[batch], dtype=torch.float32, device=device)
            prediction = model(students, exercises)
            loss = loss_function(prediction, outcomes)
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            optimizer.step()
            model.center_parameters()
            losses.append(float(loss.detach().cpu()))
            tracker.sample()
        valid_prediction, valid_latency = predict_rasch(
            model, valid, int(config["batch_size"]), device, tracker
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
        if is_better_rasch(valid_metrics, best_metrics):
            best_metrics = valid_metrics
            best_epoch = epoch
            best_latency = valid_latency
            torch.save({"state_dict": copy.deepcopy(model.state_dict()), "selected_epoch": epoch}, checkpoint_path)
            no_improvement = 0
        else:
            no_improvement += 1
            if no_improvement >= int(config["early_stopping_patience"]):
                break
    if best_metrics is None or best_epoch is None or best_latency is None:
        raise RuntimeError("Rasch/IRT-1PL did not produce a validation-selected checkpoint")
    checkpoint = torch.load(checkpoint_path, map_location=device)
    model.load_state_dict(checkpoint["state_dict"], strict=True)
    valid_prediction, _ = predict_rasch(model, valid, int(config["batch_size"]), device, tracker)
    test_prediction, test_latency = predict_rasch(model, test, int(config["batch_size"]), device, tracker)
    return model, {
        "model": "Rasch/IRT-1PL",
        "equation": "logit P(correct) = student_ability - exercise_difficulty",
        "training_scope": "MODEL-1 train/history only",
        "selection_scope": "MODEL-1 validation only",
        "selected_epoch": best_epoch,
        "selection_metric": config["selection_metric"],
        "hyperparameters": dict(config),
        "validation_metrics": binary_metrics(valid.outcomes, valid_prediction),
        "validation_inference": best_latency,
        "diagnostic_test_metrics": binary_metrics(test.outcomes, test_prediction),
        "diagnostic_test_inference": test_latency,
        "epoch_records": epoch_records,
        "training_wall_seconds": time.perf_counter() - started,
        "resources": tracker.report(),
        "checkpoint": {"path": "model-service/experiments/model2/checkpoints/rasch_1pl_best.pt", "size_bytes": checkpoint_path.stat().st_size},
        "student_ability_distribution": distribution(model.ability.weight.detach().cpu().numpy().reshape(-1)),
        "exercise_difficulty_distribution": distribution(model.difficulty.weight.detach().cpu().numpy().reshape(-1)),
    }, valid_prediction, test_prediction


def error_summary(labels: np.ndarray, prediction: np.ndarray) -> dict[str, int]:
    binary = np.asarray(prediction) >= 0.5
    return {
        "false_positive_count": int(np.logical_and(binary, labels == 0).sum()),
        "false_negative_count": int(np.logical_and(~binary, labels == 1).sum()),
    }


def sanitized_bad_cases(
    test: InteractionSplit,
    exposure: np.ndarray,
    original_prediction: np.ndarray,
    rule_beta: np.ndarray,
) -> dict[str, Any]:
    false_positive = np.flatnonzero(np.logical_and(original_prediction >= 0.5, test.outcomes == 0))
    examples = []
    for case_index, position in enumerate(false_positive[:10], start=1):
        examples.append(
            {
                "anonymous_case": f"false-positive-{case_index:03d}",
                "topic_id": int(test.topics[position]),
                "train_history_topic_exposure": int(exposure[test.students[position], test.topics[position]]),
                "c40_prediction": round(float(original_prediction[position]), 6),
                "rule_beta": round(float(rule_beta[position]), 6),
                "future_outcome": int(test.outcomes[position]),
            }
        )
    row_exposure = exposure[test.students, test.topics]
    return {
        "privacy": "No external student ID, Exercise external ID, timestamp, or raw response is included.",
        "original_c40_errors": error_summary(test.outcomes, original_prediction),
        "unknown_topic_rows_in_diagnostic_test": int((row_exposure == 0).sum()),
        "observed_1_topic_rows_in_diagnostic_test": int((row_exposure >= 1).sum()),
        "observed_3_topic_rows_in_diagnostic_test": int((row_exposure >= 3).sum()),
        "observed_5_topic_rows_in_diagnostic_test": int((row_exposure >= 5).sum()),
        "anonymous_original_c40_false_positive_cases": examples,
    }


def claim_execution() -> None:
    if RESULT_PATH.exists() or LEDGER_PATH.exists():
        raise RuntimeError("MODEL-2 diagnostic result or execution ledger already exists; a completed diagnostic must not be rerun")
    write_json(
        LEDGER_PATH,
        {
            "status": "STARTED",
            "policy": "One controlled MODEL-2 diagnostic execution. JUNYI_FINAL_HOLDOUT_V1 labels and metrics are prohibited.",
        },
    )


def complete_execution(result: Mapping[str, Any]) -> None:
    ledger = read_json(LEDGER_PATH)
    ledger["status"] = "COMPLETED"
    ledger["diagnostic_test_rows"] = result["input_counts"]["diagnostic_test_rows"]
    ledger["final_holdout_labels_read"] = False
    ledger["final_holdout_metrics_generated"] = False
    write_json(LEDGER_PATH, ledger)


def make_observability_report(result: Mapping[str, Any]) -> str:
    audit = result["observability"]
    full = audit["full_vector_compatibility"]
    rows = []
    for threshold, evidence in audit["thresholds"].items():
        concept = evidence["concept_side"]
        pairwise = evidence["pairwise_observed_only"]
        std = concept["aggregate_topic_standard_deviation"]
        centered = pairwise["centered_observed_mnd_rmse"]
        rows.append(
            f"| >= {threshold} | {evidence['coverage_fraction']:.6f} | {evidence['unknown_pair_count']:,} | {concept['retained_topic_count']} | {metric_text(std['median'])} | {len(concept['constant_retained_topic_ids'])} | {pairwise['eligible_pair_count']:,} | {metric_text(centered['median'])} |"
        )
    return f"""# MODEL-2 Observability-aware Mastery Audit

## Semantic boundary

{audit['unknown_semantics']}

The C40 mastery table is reported twice: the historical full-vector summary is
retained for compatibility, while every observed-only conclusion excludes
UNKNOWN student-Topic pairs.

## Full-vector compatibility summary

| Measure | Value |
| --- | ---: |
| Median Topic standard deviation | {metric_text(full['median_concept_standard_deviation'])} |
| Mean pairwise cosine | {metric_text(full['mean_pairwise_cosine'])} |
| Pairwise mean absolute difference | {metric_text(full['pairwise_mean_absolute_difference'])} |
| Pair sample size | {full['pair_sample_size']:,} |

## Observed-only summary

| Train/history exposure | Pair coverage | UNKNOWN pairs | Retained Topics (n>=50) | Median retained Topic std | Constant retained Topics | Student pairs with >=3 common Topics | Median centered observed MND-RMSE |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
{chr(10).join(rows)}

The structured result retains per-Topic P05/P50/P95, per-student observed
Topic counts, within-student standard deviation/range/IQR, and the sampled
common-observed-pair distributions for each threshold. No numeric prior for
an UNKNOWN pair appears in any observed-only statistic.
"""


def make_future_signal_report(result: Mapping[str, Any]) -> str:
    signal = result["future_signal"]
    rows = []
    for partition in ("validation", "diagnostic_test"):
        for threshold in ("1", "3", "5"):
            evidence = signal[partition][threshold]
            for name in ("C40_MASTERY", "RULE_BETA", "STUDENT_GLOBAL_BETA", "TOPIC_RATE"):
                metric = evidence["score_metrics"].get(name, {}).get("metrics", {})
                rows.append(
                    f"| {partition} | >= {threshold} | {name} | {evidence['row_count']:,} | {metric_text(metric.get('auc'))} | {metric_text(metric.get('log_loss'))} | {metric_text(metric.get('brier'))} |"
                )
    bootstrap = result["bootstrap"]["c40_vs_rule_beta_observed_3"]
    test_three = signal["diagnostic_test"]["3"]
    return f"""# MODEL-2 Observed Mastery Future-signal Audit

Every score below is fixed from MODEL-1 train/history. No score is updated from
the validation or diagnostic-test response being predicted. The diagnostic-test
partition is historical MODEL-1 evidence, not `JUNYI_FINAL_HOLDOUT_V1`.

| Partition | Topic exposure | Score | Rows | AUC | Log Loss | Brier |
| --- | --- | --- | ---: | ---: | ---: | ---: |
{chr(10).join(rows)}

## Primary observed >=3 comparison

| Comparison | AUC delta | Student-cluster 95% CI | Valid / requested resamples |
| --- | ---: | --- | ---: |
| C40 mastery - RuleBeta | {metric_text(bootstrap['observed_auc_delta'])} | [{metric_text(bootstrap['lower_bound'])}, {metric_text(bootstrap['upper_bound'])}] | {bootstrap['resamples_valid']} / {bootstrap['resamples_requested']} |

At diagnostic-test exposure >=3, C40-versus-RuleBeta Spearman correlation is
**{metric_text(test_three['spearman_c40_rule_beta'])}**. The structured result
contains ten-bin calibration tables and within-Topic rank-agreement summaries
for every listed score and threshold.
"""


def make_identity_ablation_report(result: Mapping[str, Any]) -> str:
    ablation = result["student_identity_ablation"]
    rows = []
    for name in ("ORIGINAL", "POP_MEAN", "ZERO", "PERMUTED"):
        evidence = ablation["variants"][name]
        valid = evidence["validation_metrics"]
        test = evidence["diagnostic_test_metrics"]
        rows.append(
            f"| {name} | {metric_text(valid['auc'])} | {metric_text(test['auc'])} | {metric_text(test['acc'])} | {metric_text(test['rmse'])} | {metric_text(test['log_loss'])} |"
        )
    bootstrap_rows = []
    for key in ("ORIGINAL_MINUS_POP_MEAN", "ORIGINAL_MINUS_ZERO", "ORIGINAL_MINUS_PERMUTED"):
        evidence = result["bootstrap"]["student_identity_ablation"][key]
        bootstrap_rows.append(
            f"| {key} | {metric_text(evidence['observed_auc_delta'])} | [{metric_text(evidence['lower_bound'])}, {metric_text(evidence['upper_bound'])}] | {evidence['resamples_valid']} / {evidence['resamples_requested']} |"
        )
    return f"""# MODEL-2 Student-identity Inference Ablation

All four columns use the same selected MODEL-1 C40 NCDM checkpoint. Only the
student embedding table differs. Exercise difficulty, discrimination,
interaction MLP, Q matrix, batch order, and evaluation rows are unchanged.

| Student embedding | Validation AUC | Diagnostic-test AUC | ACC | RMSE | Log Loss |
| --- | ---: | ---: | ---: | ---: | ---: |
{chr(10).join(rows)}

The deterministic permutation uses seed {ablation['permutation_seed']} and has
{ablation['permutation_fixed_point_count']} fixed points (required: zero).

| Paired comparison | AUC delta | Student-cluster 95% CI | Valid / requested resamples |
| --- | ---: | --- | ---: |
{chr(10).join(bootstrap_rows)}
"""


def make_simple_baselines_report(result: Mapping[str, Any]) -> str:
    baselines = result["simple_signal_baselines"]
    rows = []
    for name in ("EXERCISE_RATE", "STUDENT_GLOBAL_BETA", "TOPIC_BETA", "RASCH_1PL"):
        metrics = baselines["diagnostic_test_metrics"][name]
        rows.append(
            f"| {name} | {metric_text(metrics['auc'])} | {metric_text(metrics['acc'])} | {metric_text(metrics['rmse'])} | {metric_text(metrics['log_loss'])} | {metric_text(metrics['brier'])} |"
        )
    bootstrap_rows = []
    for key in ("STUDENT_GLOBAL_MINUS_EXERCISE", "TOPIC_BETA_MINUS_EXERCISE", "RASCH_MINUS_EXERCISE"):
        evidence = result["bootstrap"]["simple_signal_vs_exercise"][key]
        bootstrap_rows.append(
            f"| {key} | {metric_text(evidence['observed_auc_delta'])} | [{metric_text(evidence['lower_bound'])}, {metric_text(evidence['upper_bound'])}] |"
        )
    rasch = baselines["rasch_1pl"]
    return f"""# MODEL-2 Simple Interpretable Personalization Baselines

| Score | Diagnostic-test AUC | ACC | RMSE | Log Loss | Brier |
| --- | ---: | ---: | ---: | ---: | ---: |
{chr(10).join(rows)}

| Paired comparison | AUC delta | Student-cluster 95% CI |
| --- | ---: | --- |
{chr(10).join(bootstrap_rows)}

## Rasch / IRT-1PL

`logit P(correct) = student_ability - exercise_difficulty`. It was fitted on
train/history only, with validation-only epoch selection. Selected epoch:
{rasch['selected_epoch']}. Student ability distribution median / standard
deviation: {metric_text(rasch['student_ability_distribution']['median'])} /
{metric_text(rasch['student_ability_distribution']['std'])}. Exercise
difficulty distribution median / standard deviation:
{metric_text(rasch['exercise_difficulty_distribution']['median'])} /
{metric_text(rasch['exercise_difficulty_distribution']['std'])}.
"""


def make_statistical_report(result: Mapping[str, Any]) -> str:
    bootstrap = result["bootstrap"]
    method = bootstrap["student_identity_ablation"]["ORIGINAL_MINUS_POP_MEAN"]["method"]
    resamples = bootstrap["student_identity_ablation"]["ORIGINAL_MINUS_POP_MEAN"]["resamples_requested"]
    confidence = bootstrap["student_identity_ablation"]["ORIGINAL_MINUS_POP_MEAN"]["confidence_level"]
    fallacies = [
        ("Simpson's paradox", "Checked by exposure and within-Topic reporting; no aggregate-only gate claim is used."),
        ("Ecological fallacy", "Student-cluster resampling preserves the individual student as the dependence unit; aggregate metrics are not interpreted as individual causal effects."),
        ("Berkson's paradox", "CAUTION: eligibility requires at least 20 Q-eligible interactions, so conclusions are limited to this observed-student population."),
        ("Collider bias", "No post-outcome covariate adjustment or collider control is used."),
        ("Base-rate neglect", "Outcome rates, Log Loss, Brier, and calibration bins accompany AUC."),
        ("Regression to the mean", "No intervention or selected-extreme pre/post claim is made."),
        ("Survivorship bias", "CAUTION: the external-cohort and >=20-interaction boundary is documented; no claim is extended to excluded students."),
        ("Look-elsewhere effect", "The four ablations and named simple baselines are fixed in the Issue/plan; no unlisted model search occurred."),
        ("Garden of forking paths", "Seeds, membership rule, thresholds, C40 checkpoint, and bootstrap method are fixed before diagnostic outputs."),
        ("Correlation is not causation", "Future-response associations are reported without causal language."),
        ("Reverse causality", "Train/history temporally precedes evaluation rows, but unmeasured ability/confounding remains possible and no causal claim is made."),
    ]
    fallacy_rows = "\n".join(f"| {name} | {finding} |" for name, finding in fallacies)
    return f"""# MODEL-2 Statistical Report

## Primary confidence intervals

- Method: {method}
- Confidence level: {confidence:.0%}
- Requested resamples: {resamples}
- Interaction-level bootstrap is intentionally not used as a primary MODEL-2
  confidence interval.

The primary paired comparisons cover C40 identity ablation, observed C40 versus
RuleBeta at exposure >=3, and each simple student signal versus ExerciseRate.
All preserve complete within-student evaluation clusters on each resample.

## Statistical fallacy scan (11/11 checked)

| Check | Finding |
| --- | --- |
{fallacy_rows}

No p-values, causal effect sizes, or intervention effects are claimed. The
reported intervals quantify diagnostic prediction/ranking differences under the
frozen cohort and chronology only.
"""


def make_bad_cases_report(result: Mapping[str, Any]) -> str:
    cases = result["bad_cases"]
    error = cases["original_c40_errors"]
    rows = "\n".join(
        f"| {case['anonymous_case']} | {case['topic_id']} | {case['train_history_topic_exposure']} | {case['c40_prediction']:.6f} | {case['rule_beta']:.6f} | {case['future_outcome']} |"
        for case in cases["anonymous_original_c40_false_positive_cases"]
    ) or "| none retained | N/A | N/A | N/A | N/A | N/A |"
    return f"""# MODEL-2 Bad Cases and Boundaries

| C40 diagnostic-test error type | Count |
| --- | ---: |
| False positives | {error['false_positive_count']:,} |
| False negatives | {error['false_negative_count']:,} |
| UNKNOWN Topic rows (exposure=0) | {cases['unknown_topic_rows_in_diagnostic_test']:,} |
| OBSERVED_1 Topic rows | {cases['observed_1_topic_rows_in_diagnostic_test']:,} |
| OBSERVED_3 Topic rows | {cases['observed_3_topic_rows_in_diagnostic_test']:,} |
| OBSERVED_5 Topic rows | {cases['observed_5_topic_rows_in_diagnostic_test']:,} |

UNKNOWN rows remain excluded from observed-only mastery claims; a model value
near 0.5 is not recoded as observed mastery.

## Anonymized original-C40 false-positive cases

| Case | Topic ID | Train/history Topic exposure | C40 prediction | RuleBeta | Future outcome |
| --- | ---: | ---: | ---: | ---: | ---: |
{rows}

{cases['privacy']}
"""


def make_cost_report(result: Mapping[str, Any]) -> str:
    cost = result["cost"]
    rasch = result["simple_signal_baselines"]["rasch_1pl"]
    return f"""# MODEL-2 Cost and Runtime

| Phase | Device | Wall time | Peak process RAM | GPU |
| --- | --- | ---: | --- | --- |
| C40 inference-only diagnostics | {cost['device']} | {cost['c40_inference_seconds']:.3f} s | {bytes_text(cost['c40_resources']['peak_process_rss_bytes'])} | {cost['c40_resources']['gpu']} |
| Rasch / IRT-1PL | {cost['device']} | {rasch['training_wall_seconds']:.3f} s | {bytes_text(rasch['resources']['peak_process_rss_bytes'])} | {rasch['resources']['gpu']} |
| Total controlled diagnostic | {cost['device']} | {cost['total_seconds']:.3f} s | N/A | N/A |

Runtime versions: {', '.join(f'{name}={value}' for name, value in result['runtime_versions'].items())}.
The C40 checkpoint and MODEL-1 derivatives were reused read-only. The Rasch
checkpoint, runtime ledger, and all numeric derivatives remain ignored.
"""


def run(model1_asset_root: Path, config_path: Path = CONFIG_ROOT / "diagnostics.json") -> dict[str, Any]:
    if RESULT_PATH.exists() or LEDGER_PATH.exists():
        raise RuntimeError("MODEL-2 diagnostic result or execution ledger already exists and must not be rerun")
    config = read_json(config_path)
    if config.get("dataset_name") != "JUNYI_EXTERNAL_HOLDOUT_V1":
        raise ValueError("MODEL-2 diagnostics require the frozen MODEL-1 external holdout derivative")
    final_holdout = read_json(MANIFEST_ROOT / "junyi_final_holdout_v1.json")
    if final_holdout.get("status") != "FROZEN_UNEVALUATED":
        raise RuntimeError("JUNYI_FINAL_HOLDOUT_V1 must be frozen and unevaluated before diagnostics")
    if final_holdout["holdout_usage"].get("labels_read_for_model2_diagnostics") is not False:
        raise RuntimeError("Final holdout labels are prohibited in MODEL-2")
    if final_holdout["holdout_usage"].get("metrics_generated_in_model2") is not False:
        raise RuntimeError("Final holdout metrics are prohibited in MODEL-2")

    protocol_root = model1_asset_root / "data/junyi_external_holdout_v1"
    checkpoint_path = model1_asset_root / "checkpoints" / config["c40"]["checkpoint_name"]
    required = [
        protocol_root / "student_index_map.json",
        protocol_root / "exercise_index_map.json",
        protocol_root / "topic_index_map.json",
        protocol_root / "q_c40_topic.csv",
        protocol_root / "interactions_train.csv",
        protocol_root / "interactions_valid.csv",
        protocol_root / "interactions_test.csv",
        checkpoint_path,
    ]
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        raise FileNotFoundError(f"Missing audited MODEL-1 local assets: {missing}")

    claim_execution()
    started = time.perf_counter()
    set_deterministic_seed(int(config["bootstrap"]["seed"]))
    device = choose_device("cpu")
    tracker = ResourceTracker.start(device)
    train = load_interactions(protocol_root / "interactions_train.csv")
    valid = load_interactions(protocol_root / "interactions_valid.csv")
    test = load_interactions(protocol_root / "interactions_test.csv")
    student_num, exercise_num, topic_num = load_cardinalities(protocol_root)
    if (student_num, exercise_num, topic_num) != (5000, 624, 40):
        raise ValueError("MODEL-1 C40 cardinalities differ from the archived C40 contract")
    q_matrix = load_q_matrix(protocol_root / "q_c40_topic.csv", exercise_num, topic_num)
    concept_for_exercise = torch.as_tensor(np.argmax(q_matrix, axis=1), dtype=torch.long, device=device)
    model, checkpoint_evidence = load_c40_model(
        checkpoint_path, student_num, exercise_num, topic_num, config["c40"], device
    )
    original_logits = model.student_embedding.weight.detach().clone()
    variants, permutation = c40_embedding_variants(
        original_logits, int(config["student_identity_ablation"]["permutation_seed"])
    )
    batch_size = int(config["rasch_1pl"]["batch_size"])
    variant_results: dict[str, Any] = {}
    variant_test_predictions: dict[str, np.ndarray] = {}
    for name in config["student_identity_ablation"]["variants"]:
        valid_prediction, valid_latency = predict_c40(
            model, valid, concept_for_exercise, variants[name], batch_size, device, tracker
        )
        test_prediction, test_latency = predict_c40(
            model, test, concept_for_exercise, variants[name], batch_size, device, tracker
        )
        variant_test_predictions[name] = test_prediction
        variant_results[name] = {
            "validation_metrics": binary_metrics(valid.outcomes, valid_prediction),
            "validation_inference": valid_latency,
            "diagnostic_test_metrics": binary_metrics(test.outcomes, test_prediction),
            "diagnostic_test_inference": test_latency,
        }
    c40_inference_seconds = time.perf_counter() - started

    exposure, _ = exposure_matrices(train, student_num, topic_num)
    mastery = torch.sigmoid(original_logits).detach().cpu().numpy()
    observability = observability_summary(
        mastery,
        exposure,
        [int(value) for value in config["observability"]["exposure_thresholds"]],
        int(config["observability"]["minimum_topic_sample_size"]),
        int(config["observability"]["pairwise_common_observed_minimum"]),
        int(config["observability"]["pairwise_summary_sample_size"]),
        int(config["observability"]["pairwise_seed"]),
    )
    valid_scores, valid_exposure, _, _ = historical_signal_scores(
        train, valid, mastery, student_num, exercise_num, topic_num
    )
    test_scores, test_exposure, _, _ = historical_signal_scores(
        train, test, mastery, student_num, exercise_num, topic_num
    )
    thresholds = [int(value) for value in config["observability"]["exposure_thresholds"]]
    future_signal = {
        "validation": future_signal_summary(
            valid, valid_scores, valid_exposure, thresholds, int(config["calibration"]["bin_count"])
        ),
        "diagnostic_test": future_signal_summary(
            test, test_scores, test_exposure, thresholds, int(config["calibration"]["bin_count"])
        ),
    }

    rasch_model, rasch_result, _, rasch_test_prediction = fit_rasch(
        train,
        valid,
        test,
        student_num,
        exercise_num,
        config["rasch_1pl"],
        device,
        int(config["bootstrap"]["seed"]) + 17,
    )
    del rasch_model
    bootstrap_config = config["bootstrap"]
    identity_bootstrap = student_cluster_paired_bootstrap_auc_deltas(
        test.outcomes,
        variant_test_predictions,
        {
            "ORIGINAL_MINUS_POP_MEAN": ("ORIGINAL", "POP_MEAN"),
            "ORIGINAL_MINUS_ZERO": ("ORIGINAL", "ZERO"),
            "ORIGINAL_MINUS_PERMUTED": ("ORIGINAL", "PERMUTED"),
        },
        test.students,
        int(bootstrap_config["resamples"]),
        float(bootstrap_config["confidence_level"]),
        int(bootstrap_config["seed"]),
    )
    observed_three = test_exposure >= 3
    c40_rule_bootstrap = student_cluster_paired_bootstrap_auc_deltas(
        test.outcomes[observed_three],
        {"C40_MASTERY": test_scores["C40_MASTERY"][observed_three], "RULE_BETA": test_scores["RULE_BETA"][observed_three]},
        {"C40_MINUS_RULE_BETA": ("C40_MASTERY", "RULE_BETA")},
        test.students[observed_three],
        int(bootstrap_config["resamples"]),
        float(bootstrap_config["confidence_level"]),
        int(bootstrap_config["seed"]) + 1,
    )["C40_MINUS_RULE_BETA"]
    simple_scores = {
        "EXERCISE_RATE": test_scores["EXERCISE_RATE"],
        "STUDENT_GLOBAL_BETA": test_scores["STUDENT_GLOBAL_BETA"],
        "TOPIC_BETA": test_scores["RULE_BETA"],
        "RASCH_1PL": rasch_test_prediction,
    }
    simple_bootstrap = student_cluster_paired_bootstrap_auc_deltas(
        test.outcomes,
        simple_scores,
        {
            "STUDENT_GLOBAL_MINUS_EXERCISE": ("STUDENT_GLOBAL_BETA", "EXERCISE_RATE"),
            "TOPIC_BETA_MINUS_EXERCISE": ("TOPIC_BETA", "EXERCISE_RATE"),
            "RASCH_MINUS_EXERCISE": ("RASCH_1PL", "EXERCISE_RATE"),
        },
        test.students,
        int(bootstrap_config["resamples"]),
        float(bootstrap_config["confidence_level"]),
        int(bootstrap_config["seed"]) + 2,
    )
    tracker.sample()
    bad_cases = sanitized_bad_cases(test, exposure, variant_test_predictions["ORIGINAL"], test_scores["RULE_BETA"])
    total_seconds = time.perf_counter() - started
    result = {
        "status": "DIAGNOSTIC_COMPLETED",
        "dataset_name": config["dataset_name"],
        "data_roles": config["diagnostic_data_roles"],
        "runtime_versions": runtime_versions(),
        "device": str(device),
        "input_counts": {
            "students": student_num,
            "exercises": exercise_num,
            "topics": topic_num,
            "train_history_rows": train.size,
            "validation_rows": valid.size,
            "diagnostic_test_rows": test.size,
        },
        "final_holdout": {
            "dataset_name": final_holdout["dataset_name"],
            "status": final_holdout["status"],
            "student_count": final_holdout["student_membership"]["student_count"],
            "labels_read_for_model2_diagnostics": False,
            "metrics_generated_in_model2": False,
        },
        "checkpoint": checkpoint_evidence,
        "student_identity_ablation": {
            "mode": "INFERENCE_ONLY_SAME_TRAINED_C40",
            "permutation_seed": int(config["student_identity_ablation"]["permutation_seed"]),
            "permutation_fixed_point_count": int((permutation == np.arange(student_num)).sum()),
            "fixed_parameters": config["student_identity_ablation"]["fixed_parameters"],
            "variants": variant_results,
        },
        "observability": observability,
        "future_signal": future_signal,
        "simple_signal_baselines": {
            "definition": "All scores are fixed from MODEL-1 train/history. Rasch uses train/history fit and validation-only epoch selection.",
            "diagnostic_test_metrics": {name: binary_metrics(test.outcomes, score) for name, score in simple_scores.items()},
            "rasch_1pl": rasch_result,
        },
        "bootstrap": {
            "student_identity_ablation": identity_bootstrap,
            "c40_vs_rule_beta_observed_3": c40_rule_bootstrap,
            "simple_signal_vs_exercise": simple_bootstrap,
        },
        "bad_cases": bad_cases,
        "product_boundaries": {
            "java_mastery_provider_changed": False,
            "rule_mastery_changed": False,
            "recommendation_changed": False,
            "published_graph_changed": False,
            "rcd_trained": False,
            "orcdf_trained": False,
            "gear_cd_trained": False,
        },
        "cost": {
            "device": str(device),
            "c40_inference_seconds": c40_inference_seconds,
            "c40_resources": tracker.report(),
            "total_seconds": total_seconds,
        },
    }
    write_json(RESULT_PATH, result)
    write_text(REPORT_ROOT / "observability.md", make_observability_report(result))
    write_text(REPORT_ROOT / "observed_mastery_future_signal.md", make_future_signal_report(result))
    write_text(REPORT_ROOT / "student_identity_ablation.md", make_identity_ablation_report(result))
    write_text(REPORT_ROOT / "simple_signal_baselines.md", make_simple_baselines_report(result))
    write_text(REPORT_ROOT / "statistical_report.md", make_statistical_report(result))
    write_text(REPORT_ROOT / "bad_cases.md", make_bad_cases_report(result))
    write_text(REPORT_ROOT / "cost.md", make_cost_report(result))
    complete_execution(result)
    return result


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model1-asset-root", required=True, type=Path)
    parser.add_argument("--config", type=Path, default=CONFIG_ROOT / "diagnostics.json")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = run(args.model1_asset_root, args.config)
    print(
        "MODEL-2 diagnostics completed: "
        f"C40 diagnostic-test AUC={result['student_identity_ablation']['variants']['ORIGINAL']['diagnostic_test_metrics']['auc']:.6f}; "
        f"Rasch diagnostic-test AUC={result['simple_signal_baselines']['diagnostic_test_metrics']['RASCH_1PL']['auc']:.6f}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
