"""Run WP-M3.0 without opening JUNYI_FINAL_HOLDOUT_V1 correctness labels."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any

from model3_common import (
    CONFIG_ROOT,
    MANIFEST_ROOT,
    MODEL3_ROOT,
    REPORT_ROOT,
    assert_final_membership_disjoint,
    audit_metadata_topic_scope,
    build_final_exercise_scope,
    load_development_protocol,
    load_membership,
    load_medium_topic_scope,
    load_original_medium_membership,
    read_json,
    write_json,
    write_text,
)


def _require_frozen_configs() -> dict[str, Any]:
    development = read_json(CONFIG_ROOT / "development.json")
    final = read_json(CONFIG_ROOT / "final_validation.json")
    gate = read_json(CONFIG_ROOT / "final_gate.json")
    expected_candidates = ["EXERCISE_RATE_DEV", "RASCH_1PL_V1", "HIER_RASCH_TOPIC_V1"]
    if development.get("allowed_candidates") != expected_candidates:
        raise ValueError("MODEL-3 development candidate allowlist differs from Issue #30")
    if development.get("final_holdout_labels_read") is not False or final.get("final_holdout_labels_read") is not False:
        raise RuntimeError("MODEL-3 frozen configuration already records a final-label read")
    if int(final.get("bootstrap", {}).get("resamples", 0)) < 1000:
        raise ValueError("MODEL-3 requires the registered 1000-resample bootstrap target")
    allowed_statuses = gate.get("allowed_statuses")
    if allowed_statuses != [
        "GO_HIERARCHICAL_MODEL_INTEGRATION",
        "GO_RASCH_ONLY_INTEGRATION",
        "STOP_ML_INTEGRATION_RULE_ONLY",
    ]:
        raise ValueError("MODEL-3 final gate contains an unauthorized status")
    return {"development": development, "final": final, "gate": gate}


def _no_prior_final_artifacts() -> list[str]:
    paths = [
        REPORT_ROOT / "final_results.json",
        REPORT_ROOT / "final_results.md",
        REPORT_ROOT / "final_gate.json",
        REPORT_ROOT / "final_gate.md",
    ]
    present = [str(path.relative_to(MODEL3_ROOT)) for path in paths if path.exists()]
    if present:
        raise RuntimeError(f"MODEL-3 final evidence already exists before integrity audit: {present}")
    return present


def audit(
    model1_asset_root: Path,
    model2_asset_root: Path,
    medium_root: Path,
    metadata: Path,
) -> dict[str, Any]:
    """Verify the frozen membership and identity boundary without raw labels."""

    configs = _require_frozen_configs()
    consumption = read_json(MANIFEST_ROOT / "junyi_final_holdout_v1_consumption.json")
    if consumption.get("status") != "FROZEN_UNEVALUATED":
        raise RuntimeError("Final consumption ledger is no longer eligible for the one permitted materialization")
    if consumption.get("labels_read") is not False or consumption.get("metrics_generated") is not False:
        raise RuntimeError("Final consumption ledger contradicts an unevaluated holdout")
    prior_artifacts = _no_prior_final_artifacts()

    archived_manifest = read_json(MODEL3_ROOT.parent / "model2/manifests/junyi_final_holdout_v1.json")
    usage = archived_manifest.get("holdout_usage", {})
    if archived_manifest.get("status") != "FROZEN_UNEVALUATED":
        raise RuntimeError("Archived MODEL-2 manifest does not preserve FROZEN_UNEVALUATED")
    if usage.get("labels_read_for_model2_diagnostics") is not False or usage.get("metrics_generated_in_model2") is not False:
        raise RuntimeError("Archived MODEL-2 manifest records prohibited final holdout use")

    model2_membership = model2_asset_root / "data/junyi_final_holdout_v1/frozen_membership.json"
    final_members = load_membership(model2_membership, "JUNYI_FINAL_HOLDOUT_V1", 5000)
    model1_members = load_membership(
        model1_asset_root / "data/junyi_external_holdout_v1/frozen_membership.json",
        "JUNYI_EXTERNAL_HOLDOUT_V1",
        5000,
    )
    medium_members = load_original_medium_membership(medium_root / "split_membership.json")
    overlap = assert_final_membership_disjoint(final_members, model1_members, medium_members)
    final_model1_overlap = overlap["model1_overlap"]
    final_medium_overlap = overlap["original_medium_overlap"]

    development = load_development_protocol(model1_asset_root)
    final_scope = build_final_exercise_scope(development, medium_root)
    topic_by_exercise, eligible_exercises = load_medium_topic_scope(medium_root)
    metadata_audit = audit_metadata_topic_scope(metadata, topic_by_exercise, eligible_exercises)
    active_final_topics = len({topic for _, topic in final_scope.values()})
    expected_active_topics = int(configs["final"]["topic_identity_scope"]["active_final_eligible_topics"])
    if len(final_scope) != 624 or active_final_topics != expected_active_topics:
        raise RuntimeError("Development and final Exercise/Topic identity scope differs from the frozen active-scope contract")

    result = {
        "status": "INTEGRITY_AUDIT_PASSED_NO_FINAL_LABEL_READ",
        "final_holdout": {
            "dataset_name": archived_manifest.get("dataset_name"),
            "archived_status": archived_manifest.get("status"),
            "model3_consumption_status": consumption.get("status"),
            "final_member_count": len(final_members),
            "model1_overlap": final_model1_overlap,
            "original_medium_overlap": final_medium_overlap,
            "model2_labels_read": usage.get("labels_read_for_model2_diagnostics"),
            "model2_metrics_generated": usage.get("metrics_generated_in_model2"),
            "model3_labels_read": False,
            "model3_metrics_generated": False,
        },
        "identity_scope": {
            "development_exercise_count": development.exercise_count,
            "development_topic_catalog_count": development.topic_count,
            "final_eligible_exercise_count": len(final_scope),
            "final_active_topic_count": active_final_topics,
            "raw_metadata_audit": metadata_audit,
        },
        "frozen_protocol": {
            "candidates": configs["development"]["allowed_candidates"],
            "lambda_delta_grid": configs["development"]["hierarchical_rasch_topic"]["lambda_delta_grid"],
            "bootstrap_resamples": configs["final"]["bootstrap"]["resamples"],
            "final_gate_statuses": configs["gate"]["allowed_statuses"],
        },
        "prior_model3_final_artifacts": prior_artifacts,
        "label_access": "No ProblemLog correctness field was opened by this audit.",
    }
    write_json(REPORT_ROOT / "integrity_audit.json", result)
    write_text(REPORT_ROOT / "integrity_audit.md", make_report(result))
    return result


def make_report(result: dict[str, Any]) -> str:
    final = result["final_holdout"]
    scope = result["identity_scope"]
    protocol = result["frozen_protocol"]
    return f"""# MODEL-3 Integrity Audit

## Status

**{result['status']}**

This audit reads frozen membership, archived manifest, development identity, and
raw Exercise metadata only. It does **not** open the final ProblemLog
`correct` column or materialize final response rows.

| Check | Value |
| --- | --- |
| Dataset | {final['dataset_name']} |
| Archived status | {final['archived_status']} |
| MODEL-3 ledger status | {final['model3_consumption_status']} |
| Frozen final students | {final['final_member_count']} |
| Final / MODEL-1 overlap | {final['model1_overlap']} |
| Final / original Medium overlap | {final['original_medium_overlap']} |
| MODEL-2 labels read | {final['model2_labels_read']} |
| MODEL-2 metrics generated | {final['model2_metrics_generated']} |
| MODEL-3 labels read | {final['model3_labels_read']} |
| MODEL-3 metrics generated | {final['model3_metrics_generated']} |

## Identity consistency

| Check | Value |
| --- | ---: |
| Development Exercises | {scope['development_exercise_count']} |
| Development Topic catalog | {scope['development_topic_catalog_count']} |
| Final eligible Exercises | {scope['final_eligible_exercise_count']} |
| Active final eligible Topics | {scope['final_active_topic_count']} |
| Metadata identity/Topic mismatches | {scope['raw_metadata_audit']['identity_topic_mismatch_count']} |

## Frozen protocol before final labels

- Candidates: `{', '.join(protocol['candidates'])}`
- Pre-registered `lambda_delta`: `{protocol['lambda_delta_grid']}`
- Student-cluster bootstrap resamples: `{protocol['bootstrap_resamples']}`
- Allowed final Gate statuses: `{', '.join(protocol['final_gate_statuses'])}`

No final label or metric artifact existed when this audit completed. The next
allowed phase is development-only model selection and global refit.
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model1-asset-root", type=Path, required=True)
    parser.add_argument("--model2-asset-root", type=Path, required=True)
    parser.add_argument("--medium-root", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = audit(args.model1_asset_root, args.model2_asset_root, args.medium_root, args.metadata)
    print(result["status"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
