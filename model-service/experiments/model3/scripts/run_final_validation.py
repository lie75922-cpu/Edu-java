"""WP-M3.3--M3.5: one-time final holdout materialisation and evaluation."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any, Mapping

import numpy as np

from model3_common import (
    CHECKPOINT_ROOT,
    CONFIG_ROOT,
    MANIFEST_ROOT,
    REPORT_ROOT,
    ResourceTracker,
    anonymized_student_cases,
    build_final_exercise_scope,
    calibration_rule_beta_scores,
    fit_personal_calibration,
    load_development_protocol,
    load_global_parameters,
    materialize_final_interactions,
    metric_text,
    metrics_with_calibration,
    personal_delta_values,
    predict_exercise_rate,
    predict_personal,
    read_json,
    runtime_versions,
    sanitized_bad_cases,
    student_cluster_paired_bootstrap_auc_deltas,
    topic_deviation_audit,
    topic_exposure_matrix,
    topic_statuses,
    write_json,
    write_text,
)
from run_development import DEVELOPMENT_FREEZE_PATH, GLOBAL_PARAMETERS_PATH


LEDGER_PATH = MANIFEST_ROOT / "junyi_final_holdout_v1_consumption.json"
RESULT_PATH = REPORT_ROOT / "final_results.json"


def _assert_ready() -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    integrity = read_json(REPORT_ROOT / "integrity_audit.json")
    if integrity.get("status") != "INTEGRITY_AUDIT_PASSED_NO_FINAL_LABEL_READ":
        raise RuntimeError("Final validation requires a passed integrity audit with final labels unread")
    if not DEVELOPMENT_FREEZE_PATH.is_file():
        raise RuntimeError("Final validation requires the frozen development selection and refit")
    freeze = read_json(DEVELOPMENT_FREEZE_PATH)
    if freeze.get("status") != "DEVELOPMENT_MODEL_AND_FINAL_PROTOCOL_FROZEN":
        raise RuntimeError("Development model form, regularization, fallback, and Gate are not frozen")
    if freeze.get("final_holdout_labels_read") is not False:
        raise RuntimeError("Development freeze contradicts the final holdout access boundary")
    final_config = read_json(CONFIG_ROOT / "final_validation.json")
    gate_config = read_json(CONFIG_ROOT / "final_gate.json")
    if int(final_config["bootstrap"]["resamples"]) < 1000:
        raise ValueError("Final validation must retain the 1000-resample target")
    if float(final_config["chronological_split"]["calibration_fraction"]) != 0.6:
        raise ValueError("Final validation calibration fraction differs from the frozen 60% protocol")
    if not GLOBAL_PARAMETERS_PATH.is_file():
        raise FileNotFoundError("Development global parameter artifact is absent")
    ledger = read_json(LEDGER_PATH)
    if ledger.get("status") != "FROZEN_UNEVALUATED":
        raise RuntimeError("Final evaluation ledger is already claimed or completed and refuses a second run")
    if ledger.get("labels_read") is not False or ledger.get("metrics_generated") is not False:
        raise RuntimeError("Final evaluation ledger is inconsistent with an unevaluated holdout")
    existing = [path for path in (RESULT_PATH, REPORT_ROOT / "final_results.md") if path.exists()]
    if existing:
        raise RuntimeError("Final result evidence already exists and final validation cannot be repeated")
    return freeze, final_config, gate_config


def _claim_final_evaluation() -> None:
    ledger = read_json(LEDGER_PATH)
    if ledger.get("status") != "FROZEN_UNEVALUATED":
        raise RuntimeError("Final evaluation ledger rejects a second claim")
    claimed = dict(ledger)
    claimed.update({
        "status": "FINAL_EVALUATION_CLAIMED",
        "labels_read": False,
        "metrics_generated": False,
        "label_read_state": "PENDING_OR_UNCERTAIN",
        "claim_policy": "Fail closed: this claim precedes opening final correctness labels, and any later retry is prohibited.",
    })
    write_json(LEDGER_PATH, claimed)


def _complete_final_evaluation(result: Mapping[str, Any]) -> None:
    ledger = read_json(LEDGER_PATH)
    if ledger.get("status") != "FINAL_EVALUATION_CLAIMED":
        raise RuntimeError("Final evaluation completion requires its own prior claim")
    completed = dict(ledger)
    completed.update({
        "status": "FINAL_EVALUATION_COMPLETED",
        "labels_read": True,
        "metrics_generated": True,
        "label_read_state": "COMPLETED",
        "final_student_count": result["input_counts"]["student_count"],
        "final_calibration_rows": result["input_counts"]["calibration_rows"],
        "final_evaluation_rows": result["input_counts"]["evaluation_rows"],
        "primary_bootstrap_resamples": result["bootstrap"]["rasch_minus_exercise_rate"]["resamples_requested"],
    })
    write_json(LEDGER_PATH, completed)


def _coverage(labels: np.ndarray, prediction: np.ndarray, known: np.ndarray, threshold: float, bins: int) -> dict[str, Any]:
    fallback = ~known
    return {
        "known_item_rows": int(known.sum()),
        "fallback_item_rows": int(fallback.sum()),
        "known_item_fraction": float(known.mean()),
        "fallback_item_fraction": float(fallback.mean()),
        "known_item_metrics": metrics_with_calibration(labels[known], prediction[known], threshold, bins)
        if known.any()
        else None,
        "fallback_item_metrics": metrics_with_calibration(labels[fallback], prediction[fallback], threshold, bins)
        if fallback.any()
        else None,
    }


def _model_result(
    labels: np.ndarray,
    prediction: np.ndarray,
    known: np.ndarray,
    *,
    threshold: float,
    bins: int,
    calibration: Mapping[str, Any],
    inference: Mapping[str, Any],
) -> dict[str, Any]:
    return {
        "overall": metrics_with_calibration(labels, prediction, threshold, bins),
        "coverage": _coverage(labels, prediction, known, threshold, bins),
        "calibration_latency": dict(calibration),
        "inference": dict(inference),
    }


def run(
    model1_asset_root: Path,
    model2_asset_root: Path,
    medium_root: Path,
    problem_log: Path,
) -> dict[str, Any]:
    freeze, config, gate_config = _assert_ready()
    development = load_development_protocol(model1_asset_root)
    final_scope = build_final_exercise_scope(development, medium_root)
    membership_path = model2_asset_root / "data/junyi_final_holdout_v1/frozen_membership.json"
    parameters = load_global_parameters(GLOBAL_PARAMETERS_PATH)
    if parameters.rasch_difficulty.shape != parameters.hier_difficulty.shape:
        raise ValueError("Frozen global Rasch and hierarchical Exercise cardinalities differ")
    if len(parameters.rasch_difficulty) != development.exercise_count:
        raise ValueError("Frozen global Exercise cardinality differs from development identity")
    if parameters.selected_lambda_delta != float(freeze["selected_hierarchical_lambda_delta"]):
        raise RuntimeError("Global parameter artifact lambda_delta differs from frozen development selection")

    # The ledger is claimed only after every non-label precondition has passed.
    _claim_final_evaluation()
    chronology = config["chronological_split"]
    materialization = materialize_final_interactions(
        problem_log,
        membership_path,
        final_scope,
        expected_students=int(config["target_students"]),
        minimum_interactions=int(chronology["minimum_eligible_interactions"]),
        calibration_fraction=float(chronology["calibration_fraction"]),
    )
    calibration = materialization.calibration
    evaluation = materialization.evaluation
    tracker = ResourceTracker.start()
    metrics_config = config["metrics"]
    threshold = float(metrics_config["threshold"])
    bin_count = int(metrics_config["calibration_bin_count"])
    personal_config = config["personal_calibration"]
    student_count = int(config["target_students"])

    exercise_prediction, exercise_known = predict_exercise_rate(parameters.exercise_rate, evaluation.exercises)
    exercise_result = _model_result(
        evaluation.outcomes,
        exercise_prediction,
        exercise_known,
        threshold=threshold,
        bins=bin_count,
        calibration={"total_seconds": 0.0, "milliseconds_per_student": 0.0, "mode": "no per-student fit"},
        inference={"total_seconds": 0.0, "milliseconds_per_interaction": 0.0, "evaluation_updated_personal_parameters": False},
    )

    rasch_model, rasch_calibration = fit_personal_calibration(
        calibration,
        student_count,
        development.topic_count,
        parameters.rasch_mu,
        parameters.rasch_difficulty,
        hierarchical=False,
        delta_lambda=None,
        learning_rate=float(personal_config["learning_rate"]),
        batch_size=int(personal_config["batch_size"]),
        epochs=int(personal_config["epochs"]),
        theta_l2=float(personal_config["theta_l2"]),
        seed=2026090809,
        tracker=tracker,
    )
    rasch_prediction, rasch_known, rasch_inference = predict_personal(
        rasch_model,
        evaluation,
        parameters.exercise_rate.known,
        parameters.exercise_rate.global_rate,
        int(personal_config["batch_size"]),
        tracker,
    )
    rasch_result = _model_result(
        evaluation.outcomes,
        rasch_prediction,
        rasch_known,
        threshold=threshold,
        bins=bin_count,
        calibration=rasch_calibration,
        inference=rasch_inference,
    )

    hierarchical_model, hierarchical_calibration = fit_personal_calibration(
        calibration,
        student_count,
        development.topic_count,
        parameters.hier_mu,
        parameters.hier_difficulty,
        hierarchical=True,
        delta_lambda=parameters.selected_lambda_delta,
        learning_rate=float(personal_config["learning_rate"]),
        batch_size=int(personal_config["batch_size"]),
        epochs=int(personal_config["epochs"]),
        theta_l2=float(personal_config["theta_l2"]),
        seed=2026090810,
        tracker=tracker,
    )
    hierarchical_prediction, hierarchical_known, hierarchical_inference = predict_personal(
        hierarchical_model,
        evaluation,
        parameters.exercise_rate.known,
        parameters.exercise_rate.global_rate,
        int(personal_config["batch_size"]),
        tracker,
    )
    hierarchical_result = _model_result(
        evaluation.outcomes,
        hierarchical_prediction,
        hierarchical_known,
        threshold=threshold,
        bins=bin_count,
        calibration=hierarchical_calibration,
        inference=hierarchical_inference,
    )

    bootstrap = student_cluster_paired_bootstrap_auc_deltas(
        evaluation.outcomes,
        {
            "EXERCISE_RATE_DEV": exercise_prediction,
            "RASCH_1PL_V1": rasch_prediction,
            "HIER_RASCH_TOPIC_V1": hierarchical_prediction,
        },
        {
            "rasch_minus_exercise_rate": ("RASCH_1PL_V1", "EXERCISE_RATE_DEV"),
            "hierarchical_minus_rasch": ("HIER_RASCH_TOPIC_V1", "RASCH_1PL_V1"),
        },
        evaluation.students,
        int(config["bootstrap"]["resamples"]),
        float(config["bootstrap"]["confidence_level"]),
        int(config["bootstrap"]["seed"]),
    )
    exposure = topic_exposure_matrix(calibration, student_count, development.topic_count)
    statuses = topic_statuses(exposure, evaluation.students, evaluation.topics)
    delta_audit = topic_deviation_audit(
        calibration,
        evaluation,
        student_count,
        development.topic_count,
        hierarchical_model,
        hierarchical_prediction,
        rasch_prediction,
    )
    cases = anonymized_student_cases(materialization, exposure, rasch_model, hierarchical_model, count=20)
    bad_cases = sanitized_bad_cases(evaluation, hierarchical_prediction, statuses)
    _, rule_beta_counts, _ = calibration_rule_beta_scores(
        calibration, evaluation, student_count, development.topic_count
    )
    delta_values = personal_delta_values(hierarchical_model, evaluation)
    if not np.array_equal(delta_values[rule_beta_counts == 0], np.zeros(int((rule_beta_counts == 0).sum()))):
        raise AssertionError("No-history final Topic deviation is not neutral zero")

    result = {
        "status": "FINAL_EVALUATION_COMPLETED_ONCE",
        "dataset_name": config["dataset_name"],
        "input_counts": {
            "student_count": student_count,
            "source_rows_scanned": materialization.source_rows_read,
            "eligible_rows_materialized": materialization.eligible_rows_materialized,
            "calibration_rows": calibration.size,
            "evaluation_rows": evaluation.size,
            "minimum_student_interactions": int(materialization.total_interactions_by_student.min()),
            "maximum_student_interactions": int(materialization.total_interactions_by_student.max()),
        },
        "protocol": {
            "chronological_ordering": chronology["ordering"],
            "calibration_fraction": chronology["calibration_fraction"],
            "evaluation_fraction": chronology["evaluation_fraction"],
            "global_parameters_frozen": True,
            "evaluation_updates_personal_parameters": False,
            "no_history_topic_policy": config["no_history_topic_policy"],
            "selected_lambda_delta": parameters.selected_lambda_delta,
        },
        "models": {
            "EXERCISE_RATE_DEV": exercise_result,
            "RASCH_1PL_V1": rasch_result,
            "HIER_RASCH_TOPIC_V1": hierarchical_result,
        },
        "bootstrap": bootstrap,
        "topic_deviation_audit": delta_audit,
        "anonymized_student_cases": cases,
        "bad_cases": bad_cases,
        "resources": tracker.report(),
        "runtime_versions": runtime_versions(),
        "product_boundaries": {
            "java_mastery_provider_changed": False,
            "recommendation_changed": False,
            "published_graph_changed": False,
            "rule_beta_changed": False,
        },
        "final_gate_config": gate_config,
    }
    write_json(RESULT_PATH, result)
    write_text(REPORT_ROOT / "final_holdout_protocol.md", make_protocol_report(result))
    write_text(REPORT_ROOT / "final_results.md", make_results_report(result))
    write_text(REPORT_ROOT / "topic_deviation_audit.md", make_topic_report(result))
    write_text(REPORT_ROOT / "statistical_report.md", make_statistical_report(result))
    write_text(REPORT_ROOT / "calibration.md", make_calibration_report(result))
    write_text(REPORT_ROOT / "bad_cases.md", make_bad_cases_report(result))
    write_text(REPORT_ROOT / "cost.md", make_cost_report(result))
    _complete_final_evaluation(result)
    return result


def make_protocol_report(result: Mapping[str, Any]) -> str:
    counts = result["input_counts"]
    protocol = result["protocol"]
    return f"""# MODEL-3 Final Holdout Protocol

## One-time execution

**{result['status']}**

The final ledger was claimed before the final `correct` column was opened. The
completed ledger rejects every second evaluation. Global development-refit
parameters remained frozen; final fitting was limited to each final student's
personal theta and, only for the hierarchical candidate, personal Topic delta.

| Check | Value |
| --- | --- |
| Final students | {counts['student_count']} |
| Raw source rows scanned | {counts['source_rows_scanned']} |
| Eligible final rows | {counts['eligible_rows_materialized']} |
| Calibration rows | {counts['calibration_rows']} |
| Evaluation rows | {counts['evaluation_rows']} |
| Calibration fraction | {protocol['calibration_fraction']} |
| Evaluation fraction | {protocol['evaluation_fraction']} |
| Global parameters frozen | {protocol['global_parameters_frozen']} |
| Evaluation updated theta/delta | {protocol['evaluation_updates_personal_parameters']} |
| Selected lambda_delta | {protocol['selected_lambda_delta']:.2f} |

Chronology was `{protocol['chronological_ordering']}`. No-history Topic rows
retain `delta=0` with semantic status `UNKNOWN`; they are never recoded as
0.5 mastery.
"""


def make_results_report(result: Mapping[str, Any]) -> str:
    rows = []
    for name, model in result["models"].items():
        metrics = model["overall"]["metrics"]
        coverage = model["coverage"]
        rows.append(
            f"| {name} | {metric_text(metrics['auc'])} | {metric_text(metrics['acc'])} | "
            f"{metric_text(metrics['rmse'])} | {metric_text(metrics['log_loss'])} | "
            f"{metric_text(metrics['brier'])} | {model['overall']['expected_calibration_error']:.6f} | "
            f"{coverage['known_item_fraction']:.6f} | {coverage['fallback_item_fraction']:.6f} |"
        )
    bootstrap_rows = []
    for name, comparison in result["bootstrap"].items():
        bootstrap_rows.append(
            f"| {name} | {metric_text(comparison['observed_auc_delta'])} | "
            f"[{metric_text(comparison['lower_bound'])}, {metric_text(comparison['upper_bound'])}] | "
            f"{comparison['resamples_valid']} / {comparison['resamples_requested']} |"
        )
    return f"""# MODEL-3 Final Results

## One-time final evaluation

**{result['status']}**

| Candidate | AUC | ACC | RMSE | Log Loss | Brier | ECE | Known-item fraction | Fallback-item fraction |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
{chr(10).join(rows)}

## Primary student-cluster paired bootstrap

| Comparison | AUC delta | 95% CI | Valid / requested resamples |
| --- | ---: | --- | ---: |
{chr(10).join(bootstrap_rows)}

All evaluation rows were retained. Rows whose Exercise lacked the frozen
development parameter coverage used the pre-registered global ExerciseRate
fallback and are reported separately above.
"""


def make_topic_report(result: Mapping[str, Any]) -> str:
    audit = result["topic_deviation_audit"]
    coverage_rows = []
    threshold_rows = []
    for threshold in ("1", "3", "5"):
        coverage = audit["exposure_coverage"][threshold]
        details = audit["thresholds"][threshold]
        future = details["future_response_association"]
        residual = details["incremental_signal_over_rasch_residual"]
        rule = details["rule_beta_relation"]
        coverage_rows.append(
            f"| >= {threshold} | {coverage['student_topic_pairs']} | {coverage['fraction_of_all_student_topic_pairs']:.6f} | {coverage['evaluation_rows']} |"
        )
        threshold_rows.append(
            f"| >= {threshold} | {details['delta_distribution']['count']} | {metric_text(future['delta_rank_auc'])} | "
            f"{metric_text(future['delta_outcome_pearson_correlation'])} | {metric_text(residual['auc_delta'])} | "
            f"{metric_text(rule['spearman_delta_vs_rule_beta'])} |"
        )
    case_rows = "\n".join(
        f"| {case['anonymous_case']} | {case['calibration_interactions']} | {case['evaluation_interactions']} | "
        f"{case['observed_topic_count']} | {case['unknown_topic_count']} | {case['hierarchical_theta']:.6f} | "
        f"{metric_text(case['observed_delta_distribution']['median'])} |"
        for case in result["anonymized_student_cases"]
    )
    distribution = audit["observed_delta_distribution"]
    return f"""# MODEL-3 Topic-deviation Audit

## Semantic boundary

{audit['semantic_boundary']}

Observed delta distribution: count={distribution['count']},
P05={metric_text(distribution['p05'])}, P50={metric_text(distribution['median'])},
P95={metric_text(distribution['p95'])}, std={metric_text(distribution['std'])}.
Unknown delta exactly zero: `{audit['unknown_delta_exactly_zero']}`.

| Calibration Topic exposure | Student-Topic pairs | Fraction of all pairs | Evaluation rows |
| --- | ---: | ---: | ---: |
{chr(10).join(coverage_rows)}

| Calibration Topic exposure | Delta count | Future delta rank AUC | Delta/outcome correlation | Hierarchical minus Rasch AUC | Delta/RuleBeta Spearman |
| --- | ---: | ---: | ---: | ---: | ---: |
{chr(10).join(threshold_rows)}

The RuleBeta relationship is an offline calibration-only Beta(1,1) comparator,
not a Java provider output. UNKNOWN rows are excluded rather than assigned a
numeric mastery value.

## 20 anonymized student cases

| Case | Calibration rows | Evaluation rows | Observed Topics | UNKNOWN Topics | Hierarchical theta | Observed delta median |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
{case_rows}
"""


def make_statistical_report(result: Mapping[str, Any]) -> str:
    bootstrap = result["bootstrap"]
    comparison_rows = "\n".join(
        f"| {name} | {comparison['student_cluster_count']} | {comparison['resamples_valid']} / {comparison['resamples_requested']} | "
        f"{metric_text(comparison['observed_auc_delta'])} | [{metric_text(comparison['lower_bound'])}, {metric_text(comparison['upper_bound'])}] |"
        for name, comparison in bootstrap.items()
    )
    fallacies = [
        ("Simpson's paradox", "Checked with exposure-stratified Topic reporting; no aggregate-only Gate claim is used."),
        ("Ecological fallacy", "Student-cluster resampling keeps the student as the dependence unit; no individual causal claim is made."),
        ("Berkson's paradox", "CAUTION: the frozen cohort requires at least 20 eligible interactions, so claims are limited to this observed population."),
        ("Collider bias", "No post-outcome covariate adjustment is performed."),
        ("Base-rate neglect", "AUC is accompanied by ACC, RMSE, Log Loss, Brier, and calibration."),
        ("Regression to the mean", "No selected-extreme pre/post intervention claim is made."),
        ("Survivorship bias", "CAUTION: excluded students are outside the frozen cohort claim."),
        ("Look-elsewhere effect", "Only the pre-registered three candidates and four-value lambda grid were considered."),
        ("Garden of forking paths", "Chronology, fallback, metric set, bootstrap, Gate, and lambda grid were frozen before final labels."),
        ("Correlation is not causation", "Topic-deviation future association is descriptive and non-causal."),
        ("Reverse causality", "Calibration precedes evaluation chronologically, but no causal interpretation is asserted."),
    ]
    fallacy_rows = "\n".join(f"| {name} | {finding} |" for name, finding in fallacies)
    return f"""# MODEL-3 Statistical Report

## Primary comparisons

Method: student-cluster paired bootstrap. Each resample draws whole students
with replacement and gives all of each sampled student's evaluation rows the
same multiplicity. Interaction-level bootstrap is not used as the primary
interval.

| Comparison | Student clusters | Valid / requested resamples | AUC delta | 95% CI |
| --- | ---: | ---: | ---: | --- |
{comparison_rows}

## Statistical fallacy scan (11/11 checked)

| Check | Finding |
| --- | --- |
{fallacy_rows}
"""


def make_calibration_report(result: Mapping[str, Any]) -> str:
    sections = []
    for name, model in result["models"].items():
        rows = "\n".join(
            f"| {row['bin']} | {row['lower']:.1f}-{row['upper']:.1f} | {row['count']} | "
            f"{metric_text(row['mean_prediction'])} | {metric_text(row['outcome_rate'])} | {metric_text(row['absolute_gap'])} |"
            for row in model["overall"]["calibration_bins"]
        )
        sections.append(
            f"""## {name}

ECE: `{model['overall']['expected_calibration_error']:.6f}`

| Bin | Range | Rows | Mean prediction | Outcome rate | Absolute gap |
| ---: | --- | ---: | ---: | ---: | ---: |
{rows}
"""
        )
    return "# MODEL-3 Calibration\n\n" + "\n".join(sections)


def make_bad_cases_report(result: Mapping[str, Any]) -> str:
    cases = result["bad_cases"]
    def rows(values: list[Mapping[str, Any]]) -> str:
        return "\n".join(
            f"| {row['anonymous_case']} | {row['topic_index']} | {row['topic_status']} | {row['prediction']:.6f} | {row['outcome']} |"
            for row in values
        ) or "| none | N/A | N/A | N/A | N/A |"
    return f"""# MODEL-3 Bad Cases and Boundaries

| Error type | Count |
| --- | ---: |
| Hierarchical false positives | {cases['false_positive_count']} |
| Hierarchical false negatives | {cases['false_negative_count']} |
| UNKNOWN Topic evaluation rows | {cases['unknown_topic_evaluation_rows']} |

## False positives (anonymized)

| Case | Topic index | Topic status | Prediction | Outcome |
| --- | ---: | --- | ---: | ---: |
{rows(cases['false_positive_examples'])}

## False negatives (anonymized)

| Case | Topic index | Topic status | Prediction | Outcome |
| --- | ---: | --- | ---: | ---: |
{rows(cases['false_negative_examples'])}

{cases['privacy_boundary']}
"""


def make_cost_report(result: Mapping[str, Any]) -> str:
    resources = result["resources"]
    rows = []
    for name, model in result["models"].items():
        calibration = model["calibration_latency"]
        inference = model["inference"]
        rows.append(
            f"| {name} | {calibration['total_seconds']:.3f} s | "
            f"{calibration['milliseconds_per_student']:.6f} ms | {inference['total_seconds']:.3f} s | "
            f"{inference['milliseconds_per_interaction']:.6f} ms |"
        )
    return f"""# MODEL-3 Cost and Runtime

| Candidate | Calibration wall time | Mean calibration ms/student | Evaluation inference wall time | Mean inference ms/interaction |
| --- | ---: | ---: | ---: | ---: |
{chr(10).join(rows)}

| Resource | Value |
| --- | --- |
| Device | {resources['gpu']} |
| Peak process RAM bytes | {resources['peak_process_rss_bytes']} |
| Peak GPU allocated bytes | {resources['peak_gpu_allocated_bytes']} |
| Peak GPU reserved bytes | {resources['peak_gpu_reserved_bytes']} |

Runtime versions: {', '.join(f'{name}={value}' for name, value in result['runtime_versions'].items())}.
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model1-asset-root", type=Path, required=True)
    parser.add_argument("--model2-asset-root", type=Path, required=True)
    parser.add_argument("--medium-root", type=Path, required=True)
    parser.add_argument("--problem-log", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = run(args.model1_asset_root, args.model2_asset_root, args.medium_root, args.problem_log)
    print(result["status"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
