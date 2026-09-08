"""Audit reusable MODEL-1 assets and freeze JUNYI_FINAL_HOLDOUT_V1.

The final-holdout selection pass reads only anonymous user identity, Exercise,
and timestamp. It does not parse correctness labels, materialise response rows,
or score the resulting members.
"""

from __future__ import annotations

import argparse
import csv
import json
import random
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

from model2_common import CONFIG_ROOT, DERIVED_ROOT, MANIFEST_ROOT, REPORT_ROOT, read_json, write_json, write_text


RAW_METADATA_COLUMNS = [
    "name",
    "live",
    "prerequisites",
    "h_position",
    "v_position",
    "creation_date",
    "seconds_per_fast_problem",
    "pretty_display_name",
    "short_display_name",
    "topic",
    "area",
]
RAW_LOG_COLUMNS = [
    "user_id",
    "exercise",
    "problem_type",
    "problem_number",
    "topic_mode",
    "suggested",
    "review_mode",
    "time_done",
    "time_taken",
    "time_taken_attempts",
    "correct",
    "count_attempts",
    "hint_used",
    "count_hints",
    "hint_time_taken_list",
    "earned_proficiency",
    "points_earned",
]
MEDIUM_EXERCISE_FIELDS = {
    "area",
    "display_name",
    "exercise_external_id",
    "live",
    "prerequisite_raw",
    "topic",
}
MEDIUM_INTERACTION_FIELDS = {
    "attempts",
    "correct",
    "count_hints",
    "duration_seconds",
    "earned_proficiency",
    "exercise_external_id",
    "hint_used",
    "occurred_at",
    "student_external_id",
}
DATASET_NAME = "JUNYI_FINAL_HOLDOUT_V1"
DERIVED_DATASET = "junyi_final_holdout_v1"


def normalise(value: Any) -> str:
    return value.strip() if isinstance(value, str) else ""


def parse_timestamp(value: str) -> int | None:
    try:
        parsed = int(value.strip())
    except ValueError:
        return None
    return parsed if parsed >= 0 else None


def iter_jsonl(path: Path) -> Iterable[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                raise ValueError(f"Blank JSONL row at {path}:{line_number}")
            value = json.loads(line)
            if not isinstance(value, dict):
                raise ValueError(f"Non-object JSONL row at {path}:{line_number}")
            yield value


def load_original_medium_membership(path: Path) -> set[str]:
    payload = read_json(path)
    if set(payload) != {"train", "valid", "test"}:
        raise ValueError("Original Medium membership must contain train, valid, and test")
    expected = {"train": 7000, "valid": 1000, "test": 2000}
    members: list[str] = []
    for split, required_count in expected.items():
        values = payload[split]
        if not isinstance(values, list) or len(values) != required_count:
            raise ValueError(f"Original Medium {split} membership has an unexpected count")
        if not all(isinstance(value, str) and value for value in values):
            raise ValueError(f"Original Medium {split} membership contains an invalid member")
        members.extend(values)
    if len(set(members)) != 10000:
        raise ValueError("Original Medium membership has an overlap or duplicate")
    return set(members)


def load_model1_members(model1_asset_root: Path) -> set[str]:
    membership_path = model1_asset_root / "data/junyi_external_holdout_v1/frozen_membership.json"
    payload = read_json(membership_path)
    members = payload.get("members")
    if payload.get("dataset_name") != "JUNYI_EXTERNAL_HOLDOUT_V1" or not isinstance(members, list):
        raise ValueError("MODEL-1 frozen membership has an unexpected schema")
    if len(members) != 5000 or len(set(members)) != 5000:
        raise ValueError("MODEL-1 frozen membership must contain exactly 5,000 unique students")
    if not all(isinstance(member, str) and member for member in members):
        raise ValueError("MODEL-1 frozen membership contains an invalid member")
    return set(members)


def load_medium_topic_scope(medium_root: Path) -> tuple[dict[str, str], set[str]]:
    topic_by_exercise: dict[str, str] = {}
    for record in iter_jsonl(medium_root / "exercises.jsonl"):
        if set(record) != MEDIUM_EXERCISE_FIELDS:
            raise ValueError("Frozen Medium exercise schema differs from the audited MODEL-0 input")
        exercise = record["exercise_external_id"]
        topic = record["topic"]
        if not isinstance(exercise, str) or not exercise or not isinstance(topic, str) or not topic.strip():
            raise ValueError("Frozen Medium contains an invalid Exercise-to-Topic record")
        if exercise in topic_by_exercise:
            raise ValueError("Frozen Medium has duplicate Exercise metadata identity")
        topic_by_exercise[exercise] = topic.strip()
    observed: set[str] = set()
    for record in iter_jsonl(medium_root / "interactions.jsonl"):
        if set(record) != MEDIUM_INTERACTION_FIELDS:
            raise ValueError("Frozen Medium interaction schema differs from the audited MODEL-0 input")
        exercise = record["exercise_external_id"]
        if not isinstance(exercise, str) or not exercise:
            raise ValueError("Frozen Medium has an invalid interaction Exercise")
        if exercise in topic_by_exercise:
            observed.add(exercise)
    if len(topic_by_exercise) != 815 or len(observed) != 624:
        raise ValueError("Frozen Medium exercise scope does not match the MODEL-0/MODEL-1 audit")
    if len(set(topic_by_exercise.values())) != 40:
        raise ValueError("Frozen Medium does not retain the 40-Topic display layer")
    return topic_by_exercise, observed


def audit_raw_metadata(path: Path, topic_by_exercise: Mapping[str, str], eligible_exercises: set[str]) -> dict[str, Any]:
    grouped: dict[str, list[str]] = defaultdict(list)
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != RAW_METADATA_COLUMNS:
            raise ValueError(f"Unexpected metadata header at {path}: {reader.fieldnames}")
        for row_number, row in enumerate(reader, start=2):
            if None in row:
                raise ValueError(f"Malformed metadata row at {path}:{row_number}")
            grouped[normalise(row["name"])].append(normalise(row["topic"]))
    mismatches: list[str] = []
    for exercise in sorted(eligible_exercises):
        values = grouped.get(exercise, [])
        if len(values) != 1 or values[0] != topic_by_exercise[exercise]:
            mismatches.append(exercise)
    if mismatches:
        raise ValueError("Frozen eligible Exercise identity or Topic differs from raw metadata")
    return {
        "metadata_rows": sum(len(values) for values in grouped.values()),
        "distinct_nonempty_exercise_ids": len([name for name in grouped if name]),
        "eligible_exercises_checked": len(eligible_exercises),
        "identity_topic_mismatch_count": len(mismatches),
    }


def iter_membership_selection_fields(problem_log: Path) -> Iterable[tuple[str, str, str]]:
    """Yield precisely the three registered selection fields, never `correct`."""

    with problem_log.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.reader(handle)
        try:
            header = next(reader)
        except StopIteration as exc:
            raise ValueError(f"Empty ProblemLog file: {problem_log}") from exc
        if header != RAW_LOG_COLUMNS:
            raise ValueError(f"Unexpected ProblemLog header at {problem_log}: {header}")
        user_index = header.index("user_id")
        exercise_index = header.index("exercise")
        timestamp_index = header.index("time_done")
        expected_width = len(header)
        for row_number, row in enumerate(reader, start=2):
            if len(row) != expected_width:
                raise ValueError(f"Malformed ProblemLog row at {problem_log}:{row_number}")
            yield normalise(row[user_index]), normalise(row[exercise_index]), normalise(row[timestamp_index])


def stratum_name(length: int) -> str:
    if 20 <= length <= 49:
        return "20-49"
    if 50 <= length <= 99:
        return "50-99"
    if 100 <= length <= 199:
        return "100-199"
    if 200 <= length <= 499:
        return "200-499"
    if length >= 500:
        return "500-plus"
    raise ValueError(f"Ineligible sequence length cannot be stratified: {length}")


def select_stratified_members(counts: Mapping[str, int], target: int, seed: int) -> tuple[list[str], list[dict[str, Any]]]:
    if len(counts) < target:
        raise ValueError(f"Only {len(counts)} eligible external students are available; required {target}")
    strata: dict[str, list[str]] = defaultdict(list)
    for student, count in counts.items():
        strata[stratum_name(count)].append(student)
    order = ["20-49", "50-99", "100-199", "200-499", "500-plus"]
    quotas: dict[str, int] = {}
    remainders: list[tuple[float, int, str]] = []
    allocated = 0
    for index, name in enumerate(order):
        desired = target * len(strata[name]) / len(counts)
        quota = min(len(strata[name]), int(desired))
        quotas[name] = quota
        allocated += quota
        remainders.append((desired - quota, index, name))
    for _, _, name in sorted(remainders, key=lambda item: (-item[0], item[1])):
        if allocated >= target:
            break
        if quotas[name] < len(strata[name]):
            quotas[name] += 1
            allocated += 1
    selected: list[str] = []
    evidence: list[dict[str, Any]] = []
    for index, name in enumerate(order):
        candidates = sorted(strata[name])
        random.Random(seed + index).shuffle(candidates)
        selected.extend(candidates[: quotas[name]])
        evidence.append(
            {
                "stratum": name,
                "eligible_students": len(candidates),
                "selected_students": quotas[name],
                "minimum_sequence_length": min((counts[student] for student in candidates), default=None),
                "maximum_sequence_length": max((counts[student] for student in candidates), default=None),
            }
        )
    if len(selected) != target or len(set(selected)) != target:
        raise AssertionError("Final-holdout selection did not produce the required unique target count")
    return sorted(selected), evidence


def scan_final_candidates(
    problem_log: Path,
    original_medium_members: set[str],
    model1_members: set[str],
    eligible_exercises: set[str],
) -> tuple[Counter[str], dict[str, int]]:
    counts: Counter[str] = Counter()
    evidence = {
        "raw_problem_log_rows_scanned": 0,
        "q_eligible_scope_rows": 0,
        "rows_excluded_original_medium": 0,
        "rows_excluded_model1": 0,
        "rows_with_empty_student": 0,
        "rows_with_invalid_timestamp": 0,
    }
    for student, exercise, timestamp in iter_membership_selection_fields(problem_log):
        evidence["raw_problem_log_rows_scanned"] += 1
        if exercise not in eligible_exercises:
            continue
        evidence["q_eligible_scope_rows"] += 1
        if not student:
            evidence["rows_with_empty_student"] += 1
            continue
        if student in original_medium_members:
            evidence["rows_excluded_original_medium"] += 1
            continue
        if student in model1_members:
            evidence["rows_excluded_model1"] += 1
            continue
        if parse_timestamp(timestamp) is None:
            evidence["rows_with_invalid_timestamp"] += 1
            continue
        counts[student] += 1
    return counts, evidence


def audit_model1_assets(model1_asset_root: Path) -> dict[str, Any]:
    expected = {
        "manifest": model1_asset_root / "manifests/junyi_external_holdout_v1.json",
        "membership": model1_asset_root / "data/junyi_external_holdout_v1/frozen_membership.json",
        "student_index": model1_asset_root / "data/junyi_external_holdout_v1/student_index_map.json",
        "exercise_index": model1_asset_root / "data/junyi_external_holdout_v1/exercise_index_map.json",
        "topic_index": model1_asset_root / "data/junyi_external_holdout_v1/topic_index_map.json",
        "q_c40": model1_asset_root / "data/junyi_external_holdout_v1/q_c40_topic.csv",
        "train": model1_asset_root / "data/junyi_external_holdout_v1/interactions_train.csv",
        "valid": model1_asset_root / "data/junyi_external_holdout_v1/interactions_valid.csv",
        "test": model1_asset_root / "data/junyi_external_holdout_v1/interactions_test.csv",
        "checkpoint": model1_asset_root / "checkpoints/c40_topic_best.pt",
        "historical_result": model1_asset_root / "reports/ncdm_concept_ablation.json",
    }
    missing = [name for name, path in expected.items() if not path.is_file()]
    manifest = read_json(expected["manifest"]) if expected["manifest"].is_file() else {}
    return {
        "status": "REUSE_LOCAL_MODEL1_C40" if not missing else "DIAGNOSTIC_REPRODUCTION_REQUIRED",
        "model1_asset_root_supplied": str(model1_asset_root),
        "missing_assets": missing,
        "assets": {
            name: {"present": path.is_file(), "size_bytes": path.stat().st_size if path.is_file() else None}
            for name, path in expected.items()
        },
        "model1_manifest_status": manifest.get("status"),
        "model1_student_count": manifest.get("student_membership", {}).get("student_count"),
        "model1_original_medium_overlap": manifest.get("student_membership", {}).get("original_medium_overlap"),
        "model1_selection_correct_label_used": manifest.get("selection", {}).get("correct_label_used"),
        "historical_test_role_in_model2": "Diagnostic source only. MODEL-2 does not rerun or rewrite the MODEL-1 historical result, and it does not treat this partition as a new final holdout.",
        "final_holdout_role_in_model2": "JUNYI_FINAL_HOLDOUT_V1 is frozen only. Its labels and metrics are prohibited in MODEL-2.",
    }


def make_asset_audit_report(audit: Mapping[str, Any]) -> str:
    asset_rows = "\n".join(
        f"| {name} | {entry['present']} | {entry['size_bytes'] if entry['size_bytes'] is not None else 'N/A'} |"
        for name, entry in audit["assets"].items()
    )
    missing = ", ".join(audit["missing_assets"]) or "none"
    return f"""# MODEL-2 Asset Audit

## Status

**{audit['status']}**

The local asset location was supplied explicitly for this diagnostic run. The
checkpoint and derivatives remain external ignored artifacts; MODEL-2 neither
copies nor modifies them.

| Asset | Present | Size bytes |
| --- | --- | ---: |
{asset_rows}

| Check | Value |
| --- | --- |
| Missing assets | {missing} |
| MODEL-1 manifest status | {audit['model1_manifest_status']} |
| MODEL-1 frozen students | {audit['model1_student_count']} |
| MODEL-1 / original Medium overlap | {audit['model1_original_medium_overlap']} |
| MODEL-1 selection read `correct` | {audit['model1_selection_correct_label_used']} |
| Historical MODEL-1 test role | {audit['historical_test_role_in_model2']} |
| New final holdout role | {audit['final_holdout_role_in_model2']} |

No new C40 training, RCD, ORCDF, GEAR-CD, Java MasteryProvider,
recommendation, or Published Graph work is authorized by this audit.
"""


def make_freeze_report(manifest: Mapping[str, Any]) -> str:
    rows = "\n".join(
        f"| {entry['stratum']} | {entry['eligible_students']:,} | {entry['selected_students']:,} | {entry['minimum_sequence_length'] if entry['minimum_sequence_length'] is not None else 'N/A'} | {entry['maximum_sequence_length'] if entry['maximum_sequence_length'] is not None else 'N/A'} |"
        for entry in manifest["selection"]["strata"]
    )
    scan = manifest["selection"]["source_scan"]
    return f"""# JUNYI_FINAL_HOLDOUT_V1 Freeze

## Status

**{manifest['status']}** — membership was frozen before any MODEL-2
diagnostic metric was generated.

| Check | Value |
| --- | --- |
| Target / frozen students | {manifest['selection']['target_students']:,} / {manifest['student_membership']['student_count']:,} |
| Seed | {manifest['selection']['seed']} |
| Minimum eligible interactions | {manifest['selection']['minimum_eligible_chronological_interactions']} |
| Original Medium overlap | {manifest['student_membership']['original_medium_overlap']} |
| MODEL-1 external-holdout overlap | {manifest['student_membership']['model1_overlap']} |
| `correct` used to select membership | {manifest['selection']['correct_label_used']} |
| Local member list | `{manifest['student_membership']['local_member_list_path']}` (ignored; not uploaded) |
| MODEL-2 labels read from this holdout | {manifest['holdout_usage']['labels_read_for_model2_diagnostics']} |
| MODEL-2 metrics from this holdout | {manifest['holdout_usage']['metrics_generated_in_model2']} |

The selection stream read only `user_id`, `exercise`, and `time_done`. It did
not parse `correct`, materialise response rows, or perform any result-driven
substitution.

## Sequence-length strata

| Eligible length stratum | Eligible students outside both prior cohorts | Frozen students | Min length | Max length |
| --- | ---: | ---: | ---: | ---: |
{rows}

## Source scan

| Measurement | Count |
| --- | ---: |
| Raw ProblemLog rows scanned | {scan['raw_problem_log_rows_scanned']:,} |
| Q-eligible scope rows | {scan['q_eligible_scope_rows']:,} |
| Rows excluded for original Medium membership | {scan['rows_excluded_original_medium']:,} |
| Rows excluded for MODEL-1 membership | {scan['rows_excluded_model1']:,} |
| Rows with empty student | {scan['rows_with_empty_student']:,} |
| Rows with invalid timestamp | {scan['rows_with_invalid_timestamp']:,} |
"""


def preflight(
    medium_root: Path,
    model1_asset_root: Path,
    metadata: Path,
    problem_log: Path,
    config_path: Path = CONFIG_ROOT / "preflight.json",
    manifest_path: Path = MANIFEST_ROOT / "junyi_final_holdout_v1.json",
    derived_root: Path = DERIVED_ROOT / DERIVED_DATASET,
    asset_report_path: Path = REPORT_ROOT / "asset_audit.md",
    freeze_report_path: Path = REPORT_ROOT / "final_holdout_freeze.md",
) -> tuple[dict[str, Any], dict[str, Any]]:
    if manifest_path.exists() or (derived_root / "frozen_membership.json").exists():
        raise RuntimeError("JUNYI_FINAL_HOLDOUT_V1 already exists and must not be replaced")
    config = read_json(config_path)
    if config.get("dataset_name") != DATASET_NAME:
        raise ValueError("MODEL-2 preflight requires JUNYI_FINAL_HOLDOUT_V1")
    if int(config["seed"]) != 2026090802 or int(config["target_students"]) != 5000:
        raise ValueError("MODEL-2 final-holdout seed and target are frozen by Issue #24")
    if config["membership_selection"].get("correct_label_used") is not False:
        raise ValueError("MODEL-2 final-holdout selection must not use correct labels")
    asset_audit = audit_model1_assets(model1_asset_root)
    write_text(asset_report_path, make_asset_audit_report(asset_audit))
    if asset_audit["status"] != "REUSE_LOCAL_MODEL1_C40":
        raise RuntimeError("Required MODEL-1 C40 assets are unavailable for this diagnostic implementation")
    if asset_audit["model1_manifest_status"] != "FROZEN_BEFORE_MODEL_TRAINING":
        raise ValueError("MODEL-1 source is not a frozen external-holdout derivative")
    if asset_audit["model1_student_count"] != 5000 or asset_audit["model1_original_medium_overlap"] != 0:
        raise ValueError("MODEL-1 source membership does not satisfy the archived boundary")
    if asset_audit["model1_selection_correct_label_used"] is not False:
        raise ValueError("MODEL-1 source membership was not label-independent")

    topic_by_exercise, eligible_exercises = load_medium_topic_scope(medium_root)
    metadata_audit = audit_raw_metadata(metadata, topic_by_exercise, eligible_exercises)
    original_medium_members = load_original_medium_membership(medium_root / "split_membership.json")
    model1_members = load_model1_members(model1_asset_root)
    if original_medium_members & model1_members:
        raise ValueError("MODEL-1 membership unexpectedly overlaps original Medium membership")
    candidate_counts, source_scan = scan_final_candidates(
        problem_log, original_medium_members, model1_members, eligible_exercises
    )
    minimum = int(config["minimum_eligible_chronological_interactions"])
    eligible_counts = {student: count for student, count in candidate_counts.items() if count >= minimum}
    selected_members, strata = select_stratified_members(eligible_counts, int(config["target_students"]), int(config["seed"]))
    selected_set = set(selected_members)
    if selected_set & original_medium_members or selected_set & model1_members:
        raise AssertionError("New final holdout overlaps a prior research cohort")
    derived_root.mkdir(parents=True, exist_ok=True)
    frozen_membership = {
        "dataset_name": DATASET_NAME,
        "members": selected_members,
        "selection_seed": int(config["seed"]),
        "selected_before_model2_diagnostics": True,
        "correct_label_used": False,
    }
    write_json(derived_root / "frozen_membership.json", frozen_membership)
    if read_json(derived_root / "frozen_membership.json") != frozen_membership:
        raise AssertionError("Local final-holdout membership differs after write")
    manifest = {
        "dataset_name": DATASET_NAME,
        "version": "1.0.0-model2",
        "status": "FROZEN_UNEVALUATED",
        "selection": {
            "seed": int(config["seed"]),
            "target_students": int(config["target_students"]),
            "minimum_eligible_chronological_interactions": minimum,
            "correct_label_used": False,
            "fields_used": config["membership_selection"]["fields_used"],
            "policy": config["membership_selection"],
            "candidate_students_with_any_eligible_timestamp_outside_prior_cohorts": len(candidate_counts),
            "eligible_students_outside_prior_cohorts": len(eligible_counts),
            "strata": strata,
            "source_scan": source_scan,
        },
        "student_membership": {
            "student_count": len(selected_members),
            "original_medium_student_count": len(original_medium_members),
            "model1_student_count": len(model1_members),
            "original_medium_overlap": len(selected_set & original_medium_members),
            "model1_overlap": len(selected_set & model1_members),
            "local_member_list_path": "model-service/experiments/model2/data/junyi_final_holdout_v1/frozen_membership.json",
            "tracked_by_git": False,
            "selected_before_model2_diagnostics": True,
        },
        "eligible_scope": {
            "frozen_medium_q_eligible_exercises": len(eligible_exercises),
            "frozen_medium_topic_count": len(set(topic_by_exercise.values())),
            "raw_metadata_audit": metadata_audit,
        },
        "holdout_usage": config["holdout_usage"],
        "product_boundaries": {
            "java_mastery_provider_changed": False,
            "rule_mastery_changed": False,
            "recommendation_changed": False,
            "published_graph_changed": False,
        },
    }
    write_json(manifest_path, manifest)
    write_text(freeze_report_path, make_freeze_report(manifest))
    return asset_audit, manifest


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--medium-root", required=True, type=Path)
    parser.add_argument("--model1-asset-root", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--problem-log", required=True, type=Path)
    parser.add_argument("--config", type=Path, default=CONFIG_ROOT / "preflight.json")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    _, manifest = preflight(
        args.medium_root,
        args.model1_asset_root,
        args.metadata,
        args.problem_log,
        args.config,
    )
    print(
        "MODEL-2 preflight completed: "
        f"holdout={manifest['status']}; students={manifest['student_membership']['student_count']}; "
        f"original_medium_overlap={manifest['student_membership']['original_medium_overlap']}; "
        f"model1_overlap={manifest['student_membership']['model1_overlap']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
