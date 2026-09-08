"""Apply the pre-registered MODEL-1 Concept Gate without product changes."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any, Mapping

from model1_common import CONFIG_ROOT, REPORT_ROOT, metric_text, read_json, write_json, write_text


RESULT_PATH = REPORT_ROOT / "ncdm_concept_ablation.json"
GATE_PATH = REPORT_ROOT / "final_gate.json"


def assess_fine_concept(result: Mapping[str, Any], config: Mapping[str, Any]) -> tuple[bool, bool, list[str], dict[str, Any]]:
    gate_config = config["concept_gate"]
    model = result["models"]["C_FINE_EXERCISE"]
    sanity = result["mastery_sanity"]["C_FINE_EXERCISE"]
    aggregation = result["fine_concept_to_topic_aggregation"]
    metric = model["test_metrics"]
    bootstrap = model["test_auc_vs_selected_nonpersonalized_baseline"]
    failures: list[str] = []
    prediction_pass = True
    if metric["auc"] is None or bootstrap["observed_auc_delta"] <= 0.0:
        prediction_pass = False
        failures.append("fine final-test AUC does not strictly exceed the validation-selected non-personalized baseline")
    if gate_config["require_paired_bootstrap_lower_bound_above_zero"] and (
        bootstrap["lower_bound"] is None or bootstrap["lower_bound"] <= 0.0
    ):
        prediction_pass = False
        failures.append("fine paired-bootstrap AUC-delta lower bound is not above zero")
    median_std = sanity["student_to_student_concept_variance"]["median_concept_standard_deviation"]
    if median_std is None or median_std < gate_config["minimum_median_concept_standard_deviation"]:
        failures.append("fine median per-concept student standard deviation is below the pre-registered minimum")
    cosine = sanity["oversmoothing_check"]["mean_pairwise_cosine"]
    if cosine is None or cosine > gate_config["maximum_mean_pairwise_cosine"]:
        failures.append("fine mean pairwise mastery cosine exceeds the pre-registered maximum")
    coverage = sanity["concept_coverage"]["train_coverage_fraction"]
    if coverage < gate_config["minimum_train_coverage_fraction"]:
        failures.append("fine train coverage is below the pre-registered eligible ModelConcept fraction")
    nonfinite = sanity["nonfinite_values"]
    if gate_config["require_no_nonfinite_mastery_values"] and (
        nonfinite["nan_count"] != 0 or nonfinite["infinite_count"] != 0
    ):
        failures.append("fine mastery has non-finite values")
    constant_fraction = sanity["constant_column_check"]["constant_column_fraction"]
    if constant_fraction > gate_config["maximum_constant_concept_fraction"]:
        failures.append("fine mastery has a large-scale constant-column fraction")
    if gate_config["require_topic_aggregation_stable"] and aggregation["status"] != "PASS":
        failures.append("fine-to-Topic aggregation is not stable and non-collapsed")
    return not failures, prediction_pass, failures, {
        "selected_baseline": result["selected_nonpersonalized_baseline_by_validation"],
        "test_auc": metric["auc"],
        "test_acc": metric["acc"],
        "test_rmse": metric["rmse"],
        "test_log_loss": metric["log_loss"],
        "test_auc_delta": bootstrap["observed_auc_delta"],
        "bootstrap_lower_bound": bootstrap["lower_bound"],
        "median_concept_standard_deviation": median_std,
        "mean_pairwise_cosine": cosine,
        "train_coverage_fraction": coverage,
        "constant_column_fraction": constant_fraction,
        "nan_count": nonfinite["nan_count"],
        "infinite_count": nonfinite["infinite_count"],
        "topic_aggregation_status": aggregation["status"],
    }


def decide(result: Mapping[str, Any], config: Mapping[str, Any]) -> dict[str, Any]:
    passed, prediction_pass, failures, evidence = assess_fine_concept(result, config)
    if passed:
        status = "GO_FINE_CONCEPT_GRAPH_MODELS"
        reasons = [config["interpretation"]["go"]]
    elif prediction_pass:
        status = "CONDITIONAL_GO"
        reasons = [config["interpretation"]["conditional"]]
    else:
        status = "NO_GO_CONCEPT_LAYER"
        reasons = [config["interpretation"]["no_go"]]
    return {
        "status": status,
        "reasons": reasons,
        "concept_gate_passed": passed,
        "prediction_evidence_passed": prediction_pass,
        "failures": failures,
        "evidence": evidence,
        "product_boundaries": {
            "java_mastery_provider_changed": False,
            "rule_mastery_changed": False,
            "recommendation_changed": False,
            "published_graph_changed": False,
        },
    }


def graph_readiness(gate: Mapping[str, Any]) -> str:
    if gate["status"] != "GO_FINE_CONCEPT_GRAPH_MODELS":
        return f"""# MODEL-1 Graph Readiness

## Status

BLOCKED_BY_CONCEPT_GATE — final status is **{gate['status']}**. No RCD,
ORCDF, or GEAR-CD training or graph adaptation was started.

The frozen fine-catalog mapping and raw prerequisite research graph audit are
retained for review only. They are not promoted to Published Graph data.
"""
    return """# MODEL-1 Graph Readiness

## Status

READY_FOR_SEPARATE_CONTROLLED_GRAPH_STAGE — the fine ModelConcept NCDM
cleared every pre-registered Concept Gate condition. The tracked catalog
provides the required auditable Exercise external ID -> model_concept_id
mapping, and raw prerequisite edges remain explicitly research-only.

RCD, ORCDF, and GEAR-CD are not silently run as part of this final test result.
Their next protocol must preserve the frozen catalog, prevent reuse of this
final test as a new selection signal, and keep product graph storage unchanged.
"""


def make_report(gate: Mapping[str, Any], result: Mapping[str, Any]) -> str:
    evidence = gate["evidence"]
    c40 = result["models"]["C40_TOPIC"]
    fine = result["models"]["C_FINE_EXERCISE"]
    failures = "\n".join(f"- {failure}" for failure in gate["failures"]) or "- none"
    return f"""# MODEL-1 Final Gate

## Decision

**{gate['status']}**

## Fine ModelConcept evidence

| Check | Value |
| --- | --- |
| Validation-selected non-personalized baseline | {evidence['selected_baseline']} |
| Fine final-test AUC / ACC / RMSE / Log Loss | {metric_text(evidence['test_auc'])} / {metric_text(evidence['test_acc'])} / {metric_text(evidence['test_rmse'])} / {metric_text(evidence['test_log_loss'])} |
| Fine final-test AUC delta / bootstrap lower bound | {metric_text(evidence['test_auc_delta'])} / {metric_text(evidence['bootstrap_lower_bound'])} |
| Fine median concept standard deviation | {metric_text(evidence['median_concept_standard_deviation'])} |
| Fine mean pairwise cosine | {metric_text(evidence['mean_pairwise_cosine'])} |
| Fine train coverage fraction | {evidence['train_coverage_fraction']:.6f} |
| Fine constant-column fraction | {evidence['constant_column_fraction']:.6f} |
| Fine NaN / infinite values | {evidence['nan_count']} / {evidence['infinite_count']} |
| Fine-to-Topic aggregation | {evidence['topic_aggregation_status']} |

| Candidate | Test AUC | Test ACC | Test RMSE | Test Log Loss | Bootstrap lower bound | Mastery sanity |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| C40_TOPIC / NCDM | {metric_text(c40['test_metrics']['auc'])} | {metric_text(c40['test_metrics']['acc'])} | {metric_text(c40['test_metrics']['rmse'])} | {metric_text(c40['test_metrics']['log_loss'])} | {metric_text(c40['test_auc_vs_selected_nonpersonalized_baseline']['lower_bound'])} | {c40['mastery_sanity_status']} |
| C_FINE_EXERCISE / NCDM | {metric_text(fine['test_metrics']['auc'])} | {metric_text(fine['test_metrics']['acc'])} | {metric_text(fine['test_metrics']['rmse'])} | {metric_text(fine['test_metrics']['log_loss'])} | {metric_text(fine['test_auc_vs_selected_nonpersonalized_baseline']['lower_bound'])} | {fine['mastery_sanity_status']} |

## Gate failures retained

{failures}

## Stop boundary

No Java MasteryProvider, V0.4 rule mastery, recommendation, Published Graph,
or production model routing changed. MODEL-0A / COLD_ZERO_HISTORY and
MODEL-0R remain untouched historical evidence. If this gate does not fully
pass, no complex graph model is run.
"""


def run(config_path: Path = CONFIG_ROOT / "final_gate.json") -> dict[str, Any]:
    if not RESULT_PATH.exists():
        raise FileNotFoundError("MODEL-1 NCDM ablation result is required before the final gate")
    result = read_json(RESULT_PATH)
    if result.get("status") != "COMPLETED":
        raise RuntimeError("MODEL-1 cannot gate an incomplete NCDM ablation")
    config = read_json(config_path)
    gate = decide(result, config)
    if gate["status"] not in config["allowed_statuses"]:
        raise AssertionError("Final gate returned an unauthorized status")
    write_json(GATE_PATH, gate)
    write_text(REPORT_ROOT / "final_gate.md", make_report(gate, result))
    write_text(REPORT_ROOT / "graph_readiness.md", graph_readiness(gate))
    return gate


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=CONFIG_ROOT / "final_gate.json")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    gate = run(args.config)
    print(gate["status"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
