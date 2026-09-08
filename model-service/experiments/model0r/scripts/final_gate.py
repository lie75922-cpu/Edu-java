"""Apply the pre-registered MODEL-0R terminal gate without changing product code."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any, Mapping

from model0r_common import CONFIG_ROOT, REPORT_ROOT, bytes_text, metric_text, read_json, write_json, write_text


WARM_PATH = REPORT_ROOT / "warm_result.json"
COLD_PATH = REPORT_ROOT / "cold_result.json"


def best_warm_candidates(warm: Mapping[str, Any], config: Mapping[str, Any]) -> tuple[list[str], dict[str, list[str]]]:
    passing: list[str] = []
    failures: dict[str, list[str]] = {}
    for name in ("NCDM", "ORCDF-NCD"):
        model = warm["models"][name]
        reasons: list[str] = []
        metric = model["test_metrics"]
        bootstrap = model["test_auc_vs_selected_nonpersonalized_baseline"]
        sanity = warm["mastery_sanity"][name]["oversmoothing_check"]
        if metric["auc"] is None or metric["auc"] <= config["warm"]["minimum_auc"]:
            reasons.append("test AUC is not strictly above the pre-registered minimum")
        if bootstrap["observed_auc_delta"] <= config["warm"]["minimum_auc_margin_over_best_nonpersonalized_baseline"]:
            reasons.append("test AUC does not strictly exceed the validation-selected non-personalized baseline")
        if config["warm"]["require_bootstrap_lower_bound_above_zero"] and (
            bootstrap["lower_bound"] is None or bootstrap["lower_bound"] <= 0.0
        ):
            reasons.append("paired-bootstrap lower confidence bound is not above zero")
        if config["warm"]["require_mastery_sanity_pass"] and sanity["status"] != "PASS":
            reasons.append("mastery collapse/oversmoothing sanity check did not pass")
        if model["resources"]["peak_process_rss_bytes"] > config["warm"]["maximum_peak_process_rss_bytes"]:
            reasons.append("peak process RSS exceeds the pre-registered engineering bound")
        if reasons:
            failures[name] = reasons
        else:
            passing.append(name)
    return passing, failures


def cold_readiness(cold: Mapping[str, Any], config: Mapping[str, Any]) -> tuple[bool, list[str], dict[str, Any]]:
    k = str(config["cold"]["integration_ready_k"])
    candidate = cold["test"][k]["NCDM"]
    validation = cold["validation"][k]
    selected_baseline = max(
        validation["baselines"],
        key=lambda name: (
            validation["baselines"][name]["metrics"]["auc"]
            if validation["baselines"][name]["metrics"]["auc"] is not None
            else float("-inf"),
            -(validation["baselines"][name]["metrics"]["rmse"] or float("inf")),
        ),
    )
    baseline = cold["test"][k]["baselines"][selected_baseline]
    reasons: list[str] = []
    if config["cold"]["require_global_state_unchanged"] and not candidate["global_state_unchanged"]:
        reasons.append("global state changed during new-student calibration")
    if config["cold"]["require_evaluation_representation_unchanged"] and not candidate["evaluation_representation_unchanged"]:
        reasons.append("student representation changed during evaluation")
    if candidate["metrics"]["auc"] is None or baseline["metrics"]["auc"] is None:
        reasons.append("k=5 AUC is unavailable")
    elif candidate["metrics"]["auc"] - baseline["metrics"]["auc"] <= config["cold"]["require_test_auc_margin_over_best_baseline"]:
        reasons.append("k=5 NCDM does not strictly exceed the validation-selected cold statistical baseline")
    if candidate["calibration_latency"]["p95_ms"] is None or candidate["calibration_latency"]["p95_ms"] > config["cold"]["maximum_calibration_p95_ms"]:
        reasons.append("k=5 calibration P95 exceeds the pre-registered bound")
    if candidate["eligible_students"] == 0:
        reasons.append("k=5 has no eligible frozen test students")
    return not reasons, reasons, {
        "k": int(k),
        "validation_selected_baseline": selected_baseline,
        "test_ncdm_auc": candidate["metrics"]["auc"],
        "test_baseline_auc": baseline["metrics"]["auc"],
        "test_auc_delta": candidate["metrics"]["auc"] - baseline["metrics"]["auc"],
        "calibration_p95_ms": candidate["calibration_latency"]["p95_ms"],
        "global_state_unchanged": candidate["global_state_unchanged"],
        "evaluation_representation_unchanged": candidate["evaluation_representation_unchanged"],
    }


def decide(warm: Mapping[str, Any], cold: Mapping[str, Any], config: Mapping[str, Any]) -> dict[str, Any]:
    candidates, warm_failures = best_warm_candidates(warm, config)
    ready, cold_failures, cold_evidence = cold_readiness(cold, config)
    if not candidates:
        status = "NO_GO"
        reasons = ["No warm candidate cleared all pre-registered response-prediction and mastery-sanity criteria."]
    elif ready:
        status = "GO_MODEL_INTEGRATION"
        reasons = [
            "At least one warm candidate strictly exceeded the validation-selected non-personalized baseline with paired-bootstrap support and passed mastery sanity.",
            "The frozen k=5 new-student NCDM path preserved global state and evaluation representation, stayed within calibration cost, and strictly exceeded its validation-selected cold baseline.",
        ]
    else:
        status = "CONDITIONAL_GO"
        reasons = [
            "At least one warm candidate cleared the pre-registered response-prediction and mastery-sanity criteria.",
            "A production cold-start diagnosis claim is not ready under the frozen k=5 audit; use the declared statistical/insufficient-evidence fallback until a new authorized gate closes the listed condition.",
        ]
    return {
        "status": status,
        "reasons": reasons,
        "warm_passing_candidates": candidates,
        "warm_failures": warm_failures,
        "cold_ready": ready,
        "cold_failures": cold_failures,
        "cold_evidence": cold_evidence,
        "production_integration_changed": False,
        "java_ai_gateway_changed": False,
        "mastery_outbox_consumed": False,
        "recommendation_started": False,
        "website_work_started": False,
    }


def make_final_report(gate: Mapping[str, Any], warm: Mapping[str, Any], cold: Mapping[str, Any]) -> str:
    warm_models = warm["models"]
    return f"""# MODEL-0R Final Gate

## Decision

**{gate['status']}**

## Evidence basis

{chr(10).join(f'{index}. {reason}' for index, reason in enumerate(gate['reasons'], start=1))}

| Warm candidate | Test AUC | Test ACC | Test RMSE | Test Log Loss | Bootstrap AUC-delta lower bound | Mastery sanity |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| NCDM | {metric_text(warm_models['NCDM']['test_metrics']['auc'])} | {metric_text(warm_models['NCDM']['test_metrics']['acc'])} | {metric_text(warm_models['NCDM']['test_metrics']['rmse'])} | {metric_text(warm_models['NCDM']['test_metrics']['log_loss'])} | {metric_text(warm_models['NCDM']['test_auc_vs_selected_nonpersonalized_baseline']['lower_bound'])} | {warm_models['NCDM']['mastery_sanity_status']} |
| ORCDF-NCD | {metric_text(warm_models['ORCDF-NCD']['test_metrics']['auc'])} | {metric_text(warm_models['ORCDF-NCD']['test_metrics']['acc'])} | {metric_text(warm_models['ORCDF-NCD']['test_metrics']['rmse'])} | {metric_text(warm_models['ORCDF-NCD']['test_metrics']['log_loss'])} | {metric_text(warm_models['ORCDF-NCD']['test_auc_vs_selected_nonpersonalized_baseline']['lower_bound'])} | {warm_models['ORCDF-NCD']['mastery_sanity_status']} |

## Cold readiness at k={gate['cold_evidence']['k']}

| Check | Value |
| --- | --- |
| Validation-selected statistical baseline | {gate['cold_evidence']['validation_selected_baseline']} |
| NCDM test AUC / baseline AUC / delta | {metric_text(gate['cold_evidence']['test_ncdm_auc'])} / {metric_text(gate['cold_evidence']['test_baseline_auc'])} / {gate['cold_evidence']['test_auc_delta']:.6f} |
| Global state unchanged during calibration | {gate['cold_evidence']['global_state_unchanged']} |
| Representation unchanged during evaluation | {gate['cold_evidence']['evaluation_representation_unchanged']} |
| Calibration P95 | {gate['cold_evidence']['calibration_p95_ms']:.3f} ms |
| Cold readiness | {gate['cold_ready']} |

## Limitations retained

- RCD: `NOT_COMPARABLE_ON_COMMON_GRAPH`.
- GEAR-CD: smoke only; no full MODEL-0R training was run.
- ORCDF new-student adaptation: `NOT_SUPPORTED_BY_CURRENT_ADAPTER`; no zero-vector result was relabeled as calibration.
- ICDM: `NOT_AUDITABLY_ALIGNED_FOR_CURRENT_40_TOPIC_INPUT`; no ID mapping was invented.
- `MODEL-0A / COLD_ZERO_HISTORY` remains a separate historical negative experiment with its original `NO_GO_FOR_ZERO_HISTORY_PROTOCOL_AS_MODEL_SELECTION`.  It does not establish failure of the full cognitive-diagnosis route.

## Stop boundary

No Java AI Gateway was changed, no Mastery Outbox was consumed, and no
recommendation or website work was started.  This PR contains experiment
evidence only; any product integration remains a separately authorized action.
"""


def make_cost_report(warm: Mapping[str, Any], cold: Mapping[str, Any]) -> str:
    warm_rows = []
    for name in ("NCDM", "ORCDF-NCD"):
        model = warm["models"][name]
        warm_rows.append(
            f"| Warm | {name} | {warm['device']} | {model['training_wall_seconds']:.3f} s | "
            f"{model['validation_inference']['milliseconds_per_interaction']:.6f} ms/row | "
            f"{model['test_inference']['milliseconds_per_interaction']:.6f} ms/row | "
            f"{bytes_text(model['resources']['peak_process_rss_bytes'])} | {model['resources']['gpu']} | "
            f"{bytes_text(model['checkpoint']['size_bytes'])} |"
        )
    global_model = cold["global_ncdm"]
    k5 = cold["test"]["5"]["NCDM"]
    cold_row = (
        f"| Cold | NCDM global + k=5 representation-only adaptation | {cold['device']} | "
        f"{global_model['training_wall_seconds']:.3f} s | "
        f"{global_model['validation_k5_calibration_latency']['mean_ms']:.3f} ms/student calibration mean | "
        f"{k5['inference']['milliseconds_per_interaction']:.6f} ms/row | "
        f"{bytes_text(cold['resources']['NCDM']['peak_process_rss_bytes'])} | "
        f"{cold['resources']['NCDM']['gpu']} | {bytes_text(global_model['checkpoint']['size_bytes'])} |"
    )
    return f"""# MODEL-0R Cost

| Protocol | Model | Device | Train time | Validation / calibration | Test inference | Peak process RAM | Peak GPU | Checkpoint |
| --- | --- | --- | ---: | --- | ---: | --- | --- | --- |
{chr(10).join(warm_rows)}
{cold_row}

`GPU=none` means no CUDA model runtime was used.  Process RSS is sampled while
the process runs.  Large checkpoints, numeric derivatives, Torch runtime,
virtual environments, logs, and source data remain ignored and are not
uploaded.
"""


def make_bad_cases_report(warm: Mapping[str, Any], cold: Mapping[str, Any]) -> str:
    warm_rows = []
    for name in ("NCDM", "ORCDF-NCD"):
        bad = warm["models"][name]["bad_cases"]
        warm_rows.append(
            f"| {name} | {bad['false_positive_count']:,} | {bad['false_negative_count']:,} | "
            f"{bad['test_rows_on_exercises_unseen_in_warm_train']:,} |"
        )
    cold_rows = []
    for k in ("0", "3", "5", "10"):
        entry = cold["test"][k]["NCDM"]
        cold_rows.append(
            f"| {k} | {entry['ineligible_students']} | {entry['eligible_students']} | "
            f"{entry['evaluation_interactions']:,} | {entry['global_state_unchanged']} | {entry['evaluation_representation_unchanged']} |"
        )
    return f"""# MODEL-0R Bad Cases and Boundaries

## Warm errors retained

| Model | False positives | False negatives | Test rows on Exercises unseen in warm train |
| --- | ---: | ---: | ---: |
{chr(10).join(warm_rows)}

## Cold eligibility and invariant checks retained

| k | `INELIGIBLE_FOR_K` test students | Eligible test students | Evaluation interactions | Global state unchanged | Evaluation representation unchanged |
| ---: | ---: | ---: | ---: | --- | --- |
{chr(10).join(cold_rows)}

No bad case was removed by changing membership, chronology, Topic semantics,
configuration, seed, or test membership.  RCD is not fairly comparable,
GEAR-CD is smoke only, ORCDF cold adaptation is unsupported by the current
adapter, and ICDM lacks audited input compatibility.
"""


def run(config_path: Path = CONFIG_ROOT / "final_gate.json") -> dict[str, Any]:
    if not WARM_PATH.exists() or not COLD_PATH.exists():
        raise FileNotFoundError("Warm and cold completed result files are both required for final gate")
    config = read_json(config_path)
    warm = read_json(WARM_PATH)
    cold = read_json(COLD_PATH)
    if warm.get("status") != "COMPLETED" or cold.get("status") != "COMPLETED":
        raise RuntimeError("MODEL-0R cannot gate incomplete protocol results")
    gate = decide(warm, cold, config)
    if gate["status"] not in config["allowed_statuses"]:
        raise AssertionError("Gate returned a status outside the allowed terminal set")
    write_json(REPORT_ROOT / "final_gate.json", gate)
    write_text(REPORT_ROOT / "final_gate.md", make_final_report(gate, warm, cold))
    write_text(REPORT_ROOT / "cost.md", make_cost_report(warm, cold))
    write_text(REPORT_ROOT / "bad_cases.md", make_bad_cases_report(warm, cold))
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
