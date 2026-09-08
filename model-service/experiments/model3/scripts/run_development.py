"""WP-M3.1 and WP-M3.2: development-only model selection and global refit."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any, Mapping

from model3_common import (
    CHECKPOINT_ROOT,
    CONFIG_ROOT,
    MANIFEST_ROOT,
    REPORT_ROOT,
    ResourceTracker,
    combine_splits,
    fit_exercise_rate,
    fit_joint_model,
    global_parameters_from_refits,
    load_development_protocol,
    metric_text,
    metrics_with_calibration,
    predict_exercise_rate,
    read_json,
    runtime_versions,
    save_global_parameters,
    write_json,
    write_text,
)


DEVELOPMENT_FREEZE_PATH = MANIFEST_ROOT / "development_freeze.json"
GLOBAL_PARAMETERS_PATH = CHECKPOINT_ROOT / "development_refit_global_parameters.npz"


def _assert_final_is_unread() -> None:
    integrity_path = REPORT_ROOT / "integrity_audit.json"
    if not integrity_path.is_file():
        raise RuntimeError("MODEL-3 development requires the passed integrity audit before model selection")
    integrity = read_json(integrity_path)
    if integrity.get("status") != "INTEGRITY_AUDIT_PASSED_NO_FINAL_LABEL_READ":
        raise RuntimeError("MODEL-3 integrity audit did not pass with final labels unread")
    ledger = read_json(MANIFEST_ROOT / "junyi_final_holdout_v1_consumption.json")
    if ledger.get("status") != "FROZEN_UNEVALUATED" or ledger.get("labels_read") is not False:
        raise RuntimeError("Final holdout ledger is not eligible for development-before-final execution")


def _candidate_row(name: str, metrics: Mapping[str, float | None], **extra: Any) -> dict[str, Any]:
    return {"candidate": name, "validation_metrics": dict(metrics), **extra}


def _better_hierarchical(candidate: Mapping[str, Any], current: Mapping[str, Any] | None) -> bool:
    if current is None:
        return True
    candidate_metrics = candidate["validation_metrics"]
    current_metrics = current["validation_metrics"]
    for metric, descending in (("auc", True), ("log_loss", False), ("brier", False)):
        left = candidate_metrics[metric]
        right = current_metrics[metric]
        if left == right:
            continue
        if left is None:
            return False
        if right is None:
            return True
        return bool(left > right) if descending else bool(left < right)
    if candidate["lambda_delta"] != current["lambda_delta"]:
        return bool(candidate["lambda_delta"] < current["lambda_delta"])
    return bool(candidate["selected_epoch"] < current["selected_epoch"])


def run(model1_asset_root: Path) -> dict[str, Any]:
    if DEVELOPMENT_FREEZE_PATH.exists() or GLOBAL_PARAMETERS_PATH.exists():
        raise RuntimeError("MODEL-3 development selection/refit already exists and must not be rerun")
    _assert_final_is_unread()
    config = read_json(CONFIG_ROOT / "development.json")
    final_config = read_json(CONFIG_ROOT / "final_validation.json")
    allowed = config.get("allowed_candidates")
    if allowed != ["EXERCISE_RATE_DEV", "RASCH_1PL_V1", "HIER_RASCH_TOPIC_V1"]:
        raise ValueError("MODEL-3 candidate allowlist differs from the frozen protocol")
    protocol = load_development_protocol(model1_asset_root)
    cardinalities = config["cardinality_contract"]
    if (protocol.student_count, protocol.exercise_count, protocol.topic_count) != (
        int(cardinalities["students"]),
        int(cardinalities["exercises"]),
        int(cardinalities["topic_catalog"]),
    ):
        raise ValueError("MODEL-1 development cardinalities differ from the frozen MODEL-3 contract")

    rate_config = config["exercise_rate"]
    selection_rate = fit_exercise_rate(
        protocol.train,
        protocol.exercise_count,
        float(rate_config["alpha"]),
        float(rate_config["beta"]),
        int(rate_config["minimum_development_exercise_exposure"]),
    )
    rate_prediction, _ = predict_exercise_rate(selection_rate, protocol.valid.exercises)
    rate_metrics = metrics_with_calibration(
        protocol.valid.outcomes,
        rate_prediction,
        float(final_config["metrics"]["threshold"]),
        int(final_config["metrics"]["calibration_bin_count"]),
    )["metrics"]
    rate_result = _candidate_row("EXERCISE_RATE_DEV", rate_metrics, fallback=rate_config["fallback"])

    rasch_config = config["rasch_1pl"]
    rasch_selection = fit_joint_model(
        protocol.train,
        protocol.valid,
        protocol.student_count,
        protocol.exercise_count,
        protocol.topic_count,
        "RASCH_1PL_V1",
        rasch_config,
        None,
    )
    if rasch_selection.validation_metrics is None:
        raise AssertionError("Rasch selection omitted validation metrics")
    rasch_result = _candidate_row(
        "RASCH_1PL_V1",
        rasch_selection.validation_metrics,
        selected_epoch=rasch_selection.selected_epoch,
        training_wall_seconds=rasch_selection.training_wall_seconds,
        resources=rasch_selection.resources,
    )

    hierarchical_config = config["hierarchical_rasch_topic"]
    grid_results: list[dict[str, Any]] = []
    selected_hierarchical: dict[str, Any] | None = None
    for lambda_delta in hierarchical_config["lambda_delta_grid"]:
        fitted = fit_joint_model(
            protocol.train,
            protocol.valid,
            protocol.student_count,
            protocol.exercise_count,
            protocol.topic_count,
            "HIER_RASCH_TOPIC_V1",
            hierarchical_config,
            float(lambda_delta),
        )
        if fitted.validation_metrics is None:
            raise AssertionError("Hierarchical selection omitted validation metrics")
        result = _candidate_row(
            "HIER_RASCH_TOPIC_V1",
            fitted.validation_metrics,
            lambda_delta=float(lambda_delta),
            selected_epoch=fitted.selected_epoch,
            training_wall_seconds=fitted.training_wall_seconds,
            resources=fitted.resources,
        )
        grid_results.append(result)
        if _better_hierarchical(result, selected_hierarchical):
            selected_hierarchical = result
    if selected_hierarchical is None:
        raise RuntimeError("No pre-registered hierarchical candidate was selected")

    all_development = combine_splits(protocol.train, protocol.valid, protocol.diagnostic)
    refit_rate = fit_exercise_rate(
        all_development,
        protocol.exercise_count,
        float(rate_config["alpha"]),
        float(rate_config["beta"]),
        int(rate_config["minimum_development_exercise_exposure"]),
    )
    rasch_refit = fit_joint_model(
        all_development,
        protocol.valid,
        protocol.student_count,
        protocol.exercise_count,
        protocol.topic_count,
        "RASCH_1PL_V1",
        rasch_config,
        None,
        fixed_epochs=int(rasch_result["selected_epoch"]),
    )
    hierarchical_refit = fit_joint_model(
        all_development,
        protocol.valid,
        protocol.student_count,
        protocol.exercise_count,
        protocol.topic_count,
        "HIER_RASCH_TOPIC_V1",
        hierarchical_config,
        float(selected_hierarchical["lambda_delta"]),
        fixed_epochs=int(selected_hierarchical["selected_epoch"]),
    )
    parameters = global_parameters_from_refits(
        refit_rate,
        rasch_refit,
        hierarchical_refit,
        float(selected_hierarchical["lambda_delta"]),
    )
    save_global_parameters(GLOBAL_PARAMETERS_PATH, parameters)

    selection = {
        "status": "DEVELOPMENT_SELECTION_COMPLETED_FINAL_LABELS_UNREAD",
        "development_data_role": "MODEL-1 train/history and validation only for selection; historical diagnostic partition is not scored here.",
        "candidates": [rate_result, rasch_result],
        "hierarchical_lambda_grid": grid_results,
        "selected_hierarchical": selected_hierarchical,
        "final_holdout_labels_read": False,
    }
    refit = {
        "status": "DEVELOPMENT_GLOBAL_REFIT_COMPLETED_FINAL_LABELS_UNREAD",
        "scope": config["development_refit"]["scope"],
        "interaction_rows": all_development.size,
        "global_parameter_artifact": "model-service/experiments/model3/checkpoints/development_refit_global_parameters.npz (ignored local artifact)",
        "exercise_rate": {
            "known_exercise_count": int(parameters.exercise_rate.known.sum()),
            "fallback_exercise_count": int((~parameters.exercise_rate.known).sum()),
            "global_rate": parameters.exercise_rate.global_rate,
        },
        "rasch": {
            "refit_epochs": rasch_refit.selected_epoch,
            "training_wall_seconds": rasch_refit.training_wall_seconds,
            "resources": rasch_refit.resources,
        },
        "hierarchical": {
            "selected_lambda_delta": parameters.selected_lambda_delta,
            "refit_epochs": hierarchical_refit.selected_epoch,
            "training_wall_seconds": hierarchical_refit.training_wall_seconds,
            "resources": hierarchical_refit.resources,
        },
        "global_parameters_frozen_before_final": config["development_refit"]["global_parameters_frozen_before_final"],
        "final_holdout_labels_read": False,
        "runtime_versions": runtime_versions(),
    }
    freeze = {
        "status": "DEVELOPMENT_MODEL_AND_FINAL_PROTOCOL_FROZEN",
        "final_holdout_labels_read": False,
        "model_form": {
            "exercise_rate": "EXERCISE_RATE_DEV",
            "rasch": config["rasch_1pl"]["equation"],
            "hierarchical": config["hierarchical_rasch_topic"]["equation"],
        },
        "selected_hierarchical_lambda_delta": parameters.selected_lambda_delta,
        "rasch_selected_epoch": rasch_refit.selected_epoch,
        "hierarchical_selected_epoch": hierarchical_refit.selected_epoch,
        "fallback_policy": final_config["fallback_policy"],
        "final_protocol": {
            "calibration_fraction": final_config["chronological_split"]["calibration_fraction"],
            "evaluation_fraction": final_config["chronological_split"]["evaluation_fraction"],
            "global_parameter_policy": final_config["global_parameter_policy"],
            "no_history_topic_policy": final_config["no_history_topic_policy"],
            "bootstrap": final_config["bootstrap"],
        },
        "gate_config": read_json(CONFIG_ROOT / "final_gate.json"),
        "global_parameter_artifact": "model-service/experiments/model3/checkpoints/development_refit_global_parameters.npz",
        "product_boundary": "No Java MasteryProvider, RuleBeta, recommendation, or Published Graph change is authorized.",
    }
    write_json(REPORT_ROOT / "development_model_selection.json", selection)
    write_text(REPORT_ROOT / "development_model_selection.md", make_selection_report(selection))
    write_json(REPORT_ROOT / "development_refit.json", refit)
    write_text(REPORT_ROOT / "development_refit.md", make_refit_report(refit))
    write_json(DEVELOPMENT_FREEZE_PATH, freeze)
    return {"selection": selection, "refit": refit, "freeze": freeze}


def make_selection_report(result: Mapping[str, Any]) -> str:
    rows = []
    for candidate in result["candidates"]:
        metrics = candidate["validation_metrics"]
        rows.append(
            f"| {candidate['candidate']} | N/A | {candidate.get('selected_epoch', 'N/A')} | "
            f"{metric_text(metrics['auc'])} | {metric_text(metrics['acc'])} | {metric_text(metrics['rmse'])} | "
            f"{metric_text(metrics['log_loss'])} | {metric_text(metrics['brier'])} |"
        )
    for candidate in result["hierarchical_lambda_grid"]:
        metrics = candidate["validation_metrics"]
        selected = " **selected**" if candidate == result["selected_hierarchical"] else ""
        rows.append(
            f"| HIER_RASCH_TOPIC_V1{selected} | {candidate['lambda_delta']:.2f} | {candidate['selected_epoch']} | "
            f"{metric_text(metrics['auc'])} | {metric_text(metrics['acc'])} | {metric_text(metrics['rmse'])} | "
            f"{metric_text(metrics['log_loss'])} | {metric_text(metrics['brier'])} |"
        )
    return f"""# MODEL-3 Development-only Model Selection

## Status

**{result['status']}**

Only MODEL-1 train/history fitted candidates and MODEL-1 validation selected
the Rasch epoch and the small pre-registered `lambda_delta` grid. The historical
MODEL-1 diagnostic partition was not scored in this selection. Final holdout
labels remain unread: `{result['final_holdout_labels_read']}`.

| Candidate | lambda_delta | Selected epoch | AUC | ACC | RMSE | Log Loss | Brier |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
{chr(10).join(rows)}

The frozen hierarchical selection is `lambda_delta={result['selected_hierarchical']['lambda_delta']:.2f}`.
No architecture search, graph model, neural hidden layer, or final-holdout
metric informed this choice.
"""


def make_refit_report(result: Mapping[str, Any]) -> str:
    rate = result["exercise_rate"]
    rasch = result["rasch"]
    hierarchical = result["hierarchical"]
    return f"""# MODEL-3 Development Global Refit

## Status

**{result['status']}**

The refit used the historical MODEL-1 development cohort only after model form,
the selected `lambda_delta`, fallback policy, and final Gate were frozen. It
did not open any `JUNYI_FINAL_HOLDOUT_V1` label.

| Check | Value |
| --- | --- |
| Development interaction rows | {result['interaction_rows']} |
| Known Exercises | {rate['known_exercise_count']} |
| Fallback Exercises | {rate['fallback_exercise_count']} |
| Rasch refit epochs | {rasch['refit_epochs']} |
| Hierarchical refit epochs | {hierarchical['refit_epochs']} |
| Selected lambda_delta | {hierarchical['selected_lambda_delta']:.2f} |
| Final labels read | {result['final_holdout_labels_read']} |

The local parameter artifact is ignored by Git and contains only development
global parameters. The final runner may optimize only per-final-student theta
and, for the hierarchical candidate, per-final-student Topic deviation.
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model1-asset-root", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = run(args.model1_asset_root)
    print(result["freeze"]["status"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
