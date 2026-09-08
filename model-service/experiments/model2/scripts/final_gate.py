"""Apply the three-outcome MODEL-2 stop gate without product changes."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any, Mapping

from model2_common import CONFIG_ROOT, REPORT_ROOT, metric_text, read_json, write_json, write_text


RESULT_PATH = REPORT_ROOT / "model2_diagnostic_results.json"
GATE_PATH = REPORT_ROOT / "final_gate.json"


def positive_lower_bound(entry: Mapping[str, Any]) -> bool:
    return entry.get("lower_bound") is not None and float(entry["lower_bound"]) > 0.0


def assess(result: Mapping[str, Any]) -> dict[str, Any]:
    identity = result["bootstrap"]["student_identity_ablation"]
    identity_pop = positive_lower_bound(identity["ORIGINAL_MINUS_POP_MEAN"])
    identity_permuted = positive_lower_bound(identity["ORIGINAL_MINUS_PERMUTED"])
    observed = result["observability"]["thresholds"]["3"]["concept_side"]
    observed_std = observed["aggregate_topic_standard_deviation"]["median"]
    observed_nonconstant = (
        observed["retained_topic_count"] > 0
        and observed_std is not None
        and float(observed_std) > 0.0
        and not observed["constant_retained_topic_ids"]
    )
    c40_rule = result["bootstrap"]["c40_vs_rule_beta_observed_3"]
    c40_future_incremental = positive_lower_bound(c40_rule)
    final = result["final_holdout"]
    final_holdout_ok = (
        final["dataset_name"] == "JUNYI_FINAL_HOLDOUT_V1"
        and final["status"] == "FROZEN_UNEVALUATED"
        and final["labels_read_for_model2_diagnostics"] is False
        and final["metrics_generated_in_model2"] is False
    )
    c40_conditions = {
        "identity_signal_vs_pop_mean_or_permuted": identity_pop or identity_permuted,
        "identity_signal_vs_pop_mean": identity_pop,
        "identity_signal_vs_permuted": identity_permuted,
        "observed_exposure_3_nonconstant": observed_nonconstant,
        "observed_c40_incremental_over_rule_beta": c40_future_incremental,
        "final_holdout_frozen_and_unused": final_holdout_ok,
    }
    simple = result["bootstrap"]["simple_signal_vs_exercise"]
    simple_conditions = {
        "student_global_beta_over_exercise": positive_lower_bound(simple["STUDENT_GLOBAL_MINUS_EXERCISE"]),
        "rasch_1pl_over_exercise": positive_lower_bound(simple["RASCH_MINUS_EXERCISE"]),
    }
    return {"c40_conditions": c40_conditions, "simple_conditions": simple_conditions}


def decide(result: Mapping[str, Any], config: Mapping[str, Any]) -> dict[str, Any]:
    assessment = assess(result)
    c40_conditions = assessment["c40_conditions"]
    simple_conditions = assessment["simple_conditions"]
    c40_passed = all(
        c40_conditions[name]
        for name in (
            "identity_signal_vs_pop_mean_or_permuted",
            "observed_exposure_3_nonconstant",
            "observed_c40_incremental_over_rule_beta",
            "final_holdout_frozen_and_unused",
        )
    )
    simple_signal = any(simple_conditions.values())
    if c40_passed:
        status = "GO_C40_FINAL_VALIDATION"
        reasons = [config["interpretation"]["go_c40"]]
    elif simple_signal:
        status = "GO_SIMPLE_HIERARCHICAL_ROUTE"
        reasons = [config["interpretation"]["go_simple"]]
    else:
        status = "STOP_ML_DIAGNOSIS_RULE_ONLY"
        reasons = [config["interpretation"]["stop"]]
    failures = [name for name, passed in c40_conditions.items() if not passed]
    return {
        "status": status,
        "reasons": reasons,
        "c40_requirements_passed": c40_passed,
        "simple_student_signal_present": simple_signal,
        "c40_conditions": c40_conditions,
        "simple_conditions": simple_conditions,
        "c40_failures": failures,
        "final_holdout": result["final_holdout"],
        "product_boundaries": result["product_boundaries"],
    }


def make_report(gate: Mapping[str, Any], result: Mapping[str, Any]) -> str:
    identity = result["bootstrap"]["student_identity_ablation"]
    c40_rule = result["bootstrap"]["c40_vs_rule_beta_observed_3"]
    simple = result["bootstrap"]["simple_signal_vs_exercise"]
    c40_rows = "\n".join(
        f"| {name} | {'PASS' if passed else 'FAIL'} |" for name, passed in gate["c40_conditions"].items()
    )
    simple_rows = "\n".join(
        f"| {name} | {'PASS' if passed else 'FAIL'} |" for name, passed in gate["simple_conditions"].items()
    )
    failures = "\n".join(f"- {failure}" for failure in gate["c40_failures"]) or "- none"
    return f"""# MODEL-2 Final Gate

## Decision

**{gate['status']}**

{gate['reasons'][0]}

## C40 conditions

| Condition | Result |
| --- | --- |
{c40_rows}

| C40 comparison | AUC delta | Student-cluster 95% CI |
| --- | ---: | --- |
| ORIGINAL - POP_MEAN | {metric_text(identity['ORIGINAL_MINUS_POP_MEAN']['observed_auc_delta'])} | [{metric_text(identity['ORIGINAL_MINUS_POP_MEAN']['lower_bound'])}, {metric_text(identity['ORIGINAL_MINUS_POP_MEAN']['upper_bound'])}] |
| ORIGINAL - PERMUTED | {metric_text(identity['ORIGINAL_MINUS_PERMUTED']['observed_auc_delta'])} | [{metric_text(identity['ORIGINAL_MINUS_PERMUTED']['lower_bound'])}, {metric_text(identity['ORIGINAL_MINUS_PERMUTED']['upper_bound'])}] |
| C40 observed >=3 - RuleBeta | {metric_text(c40_rule['observed_auc_delta'])} | [{metric_text(c40_rule['lower_bound'])}, {metric_text(c40_rule['upper_bound'])}] |

## Simple student-signal conditions

| Condition | Result |
| --- | --- |
{simple_rows}

| Simple comparison | AUC delta | Student-cluster 95% CI |
| --- | ---: | --- |
| StudentGlobalBeta - ExerciseRate | {metric_text(simple['STUDENT_GLOBAL_MINUS_EXERCISE']['observed_auc_delta'])} | [{metric_text(simple['STUDENT_GLOBAL_MINUS_EXERCISE']['lower_bound'])}, {metric_text(simple['STUDENT_GLOBAL_MINUS_EXERCISE']['upper_bound'])}] |
| Rasch/IRT-1PL - ExerciseRate | {metric_text(simple['RASCH_MINUS_EXERCISE']['observed_auc_delta'])} | [{metric_text(simple['RASCH_MINUS_EXERCISE']['lower_bound'])}, {metric_text(simple['RASCH_MINUS_EXERCISE']['upper_bound'])}] |

## C40 requirements not met

{failures}

## Stop boundary

The final holdout remains `{gate['final_holdout']['status']}` with labels read:
`{gate['final_holdout']['labels_read_for_model2_diagnostics']}` and metrics
generated: `{gate['final_holdout']['metrics_generated_in_model2']}`. No Java
MasteryProvider, recommendation, Published Graph, RCD, ORCDF, or GEAR-CD work
was added. This gate is terminal for this Issue; do not start another ML model
from this result.
"""


def run(config_path: Path = CONFIG_ROOT / "final_gate.json") -> dict[str, Any]:
    if not RESULT_PATH.is_file():
        raise FileNotFoundError("MODEL-2 diagnostic result is required before final gating")
    result = read_json(RESULT_PATH)
    if result.get("status") != "DIAGNOSTIC_COMPLETED":
        raise RuntimeError("MODEL-2 cannot gate an incomplete diagnostic")
    config = read_json(config_path)
    gate = decide(result, config)
    if gate["status"] not in config["allowed_statuses"]:
        raise AssertionError("MODEL-2 final gate returned an unauthorized status")
    write_json(GATE_PATH, gate)
    write_text(REPORT_ROOT / "final_gate.md", make_report(gate, result))
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
