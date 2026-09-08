"""Apply the only permitted MODEL-3 final Gate and stop."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any, Mapping

from model3_common import CONFIG_ROOT, MANIFEST_ROOT, REPORT_ROOT, metric_text, read_json, write_json, write_text


RESULT_PATH = REPORT_ROOT / "final_results.json"
GATE_PATH = REPORT_ROOT / "final_gate.json"


def _positive_lower_bound(comparison: Mapping[str, Any]) -> bool:
    lower = comparison.get("lower_bound")
    return lower is not None and float(lower) > 0.0


def _quality_ok(model: Mapping[str, Any], limits: Mapping[str, Any]) -> bool:
    return (
        float(model["overall"]["expected_calibration_error"]) <= float(limits["maximum_ece"])
        and float(model["coverage"]["fallback_item_fraction"]) <= float(limits["maximum_fallback_item_fraction"])
        and model["inference"].get("evaluation_updated_personal_parameters") is False
        and model["inference"].get("frozen_global_parameters_verified", True) is True
    )


def decide(result: Mapping[str, Any], config: Mapping[str, Any]) -> dict[str, Any]:
    models = result["models"]
    exercise = models["EXERCISE_RATE_DEV"]
    rasch = models["RASCH_1PL_V1"]
    hierarchical = models["HIER_RASCH_TOPIC_V1"]
    comparisons = result["bootstrap"]
    limits = config["quality_limits"]
    interpretability = config["hierarchical_interpretability"]
    rasch_vs_exercise = comparisons["rasch_minus_exercise_rate"]
    hierarchical_vs_rasch = comparisons["hierarchical_minus_rasch"]
    observed_threshold = str(interpretability["minimum_observed_exposure"])
    topic = result["topic_deviation_audit"]["thresholds"][observed_threshold]
    hierarchical_metrics = hierarchical["overall"]["metrics"]
    rasch_metrics = rasch["overall"]["metrics"]
    topic_distribution = topic["delta_distribution"]
    topic_association = topic["future_response_association"]["delta_rank_auc"]
    conditions = {
        "rasch_auc_above_exercise_rate": _positive_lower_bound(rasch_vs_exercise),
        "rasch_quality": _quality_ok(rasch, limits),
        "hierarchical_point_auc_above_rasch": (
            hierarchical_metrics["auc"] is not None
            and rasch_metrics["auc"] is not None
            and float(hierarchical_metrics["auc"]) > float(rasch_metrics["auc"])
        ),
        "hierarchical_bootstrap_lower_bound_above_zero": _positive_lower_bound(hierarchical_vs_rasch),
        "hierarchical_log_loss_not_materially_worse": (
            float(hierarchical_metrics["log_loss"]) - float(rasch_metrics["log_loss"])
            <= float(limits["maximum_hierarchical_log_loss_increase_over_rasch"])
        ),
        "hierarchical_brier_not_materially_worse": (
            float(hierarchical_metrics["brier"]) - float(rasch_metrics["brier"])
            <= float(limits["maximum_hierarchical_brier_increase_over_rasch"])
        ),
        "hierarchical_quality": _quality_ok(hierarchical, limits),
        "observed_topic_delta_nonempty": int(topic_distribution["count"]) > 0,
        "observed_topic_delta_future_association": (
            topic_association is not None
            and float(topic_association) >= float(interpretability["minimum_delta_future_association_auc"])
        ),
        "unknown_topic_delta_neutral": result["topic_deviation_audit"].get("unknown_delta_exactly_zero") is True,
        "twenty_anonymized_cases": len(result.get("anonymized_student_cases", [])) >= 20,
        "final_protocol_global_freeze": result["protocol"].get("global_parameters_frozen") is True,
        "evaluation_no_parameter_update": result["protocol"].get("evaluation_updates_personal_parameters") is False,
    }
    rasch_go = conditions["rasch_auc_above_exercise_rate"] and conditions["rasch_quality"]
    hierarchical_go = rasch_go and all(
        conditions[name]
        for name in (
            "hierarchical_point_auc_above_rasch",
            "hierarchical_bootstrap_lower_bound_above_zero",
            "hierarchical_log_loss_not_materially_worse",
            "hierarchical_brier_not_materially_worse",
            "hierarchical_quality",
            "observed_topic_delta_nonempty",
            "observed_topic_delta_future_association",
            "unknown_topic_delta_neutral",
            "twenty_anonymized_cases",
            "final_protocol_global_freeze",
            "evaluation_no_parameter_update",
        )
    )
    if hierarchical_go:
        status = "GO_HIERARCHICAL_MODEL_INTEGRATION"
        reason = "Hierarchical Rasch clears the frozen final comparison, quality, and interpretable Topic-deviation conditions."
    elif rasch_go:
        status = "GO_RASCH_ONLY_INTEGRATION"
        reason = "Rasch robustly clears ExerciseRate, while the hierarchical candidate lacks a frozen GO condition."
    else:
        status = "STOP_ML_INTEGRATION_RULE_ONLY"
        reason = "Rasch does not robustly clear the final ExerciseRate comparison or its frozen quality requirements."
    if status not in config["allowed_statuses"]:
        raise AssertionError("MODEL-3 Gate returned an unauthorized status")
    return {
        "status": status,
        "reason": reason,
        "conditions": conditions,
        "rasch_conditions_passed": rasch_go,
        "hierarchical_conditions_passed": hierarchical_go,
        "primary_comparisons": {
            "rasch_minus_exercise_rate": rasch_vs_exercise,
            "hierarchical_minus_rasch": hierarchical_vs_rasch,
        },
        "product_boundary": config["product_boundary"],
    }


def make_report(gate: Mapping[str, Any]) -> str:
    condition_rows = "\n".join(
        f"| {name} | {'PASS' if passed else 'FAIL'} |" for name, passed in gate["conditions"].items()
    )
    comparison_rows = "\n".join(
        f"| {name} | {metric_text(comparison['observed_auc_delta'])} | "
        f"[{metric_text(comparison['lower_bound'])}, {metric_text(comparison['upper_bound'])}] | "
        f"{comparison['resamples_valid']} / {comparison['resamples_requested']} |"
        for name, comparison in gate["primary_comparisons"].items()
    )
    return f"""# MODEL-3 Final Gate

## Decision

**{gate['status']}**

{gate['reason']}

## Frozen conditions

| Condition | Result |
| --- | --- |
{condition_rows}

## Primary comparisons

| Comparison | AUC delta | Student-cluster 95% CI | Valid / requested resamples |
| --- | ---: | --- | ---: |
{comparison_rows}

## Stop boundary

{gate['product_boundary']}

This is the terminal output for Issue #30. It does not modify Java
`MasteryProvider`, recommendation, Published Graph, or RuleBeta.
"""


def run(config_path: Path = CONFIG_ROOT / "final_gate.json") -> dict[str, Any]:
    if GATE_PATH.exists() or (REPORT_ROOT / "final_gate.md").exists():
        raise RuntimeError("MODEL-3 final Gate already exists and must not be regenerated")
    ledger = read_json(MANIFEST_ROOT / "junyi_final_holdout_v1_consumption.json")
    if ledger.get("status") != "FINAL_EVALUATION_COMPLETED" or ledger.get("labels_read") is not True:
        raise RuntimeError("MODEL-3 final Gate requires one completed final evaluation")
    if not RESULT_PATH.is_file():
        raise FileNotFoundError("MODEL-3 final results are required before gating")
    result = read_json(RESULT_PATH)
    if result.get("status") != "FINAL_EVALUATION_COMPLETED_ONCE":
        raise RuntimeError("MODEL-3 final result is incomplete")
    gate = decide(result, read_json(config_path))
    write_json(GATE_PATH, gate)
    write_text(REPORT_ROOT / "final_gate.md", make_report(gate))
    return gate


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=CONFIG_ROOT / "final_gate.json")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    print(run(args.config)["status"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
