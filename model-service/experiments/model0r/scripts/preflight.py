"""Materialise the two MODEL-0R local derivatives without changing DATA-0.

The warm and cold derivatives are intentionally distinct.  Their numeric
tables and member lists stay under an ignored directory; the checked-in
manifests and reports retain the audit evidence needed to reproduce the split.
"""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable, Mapping

from model0r_common import (
    CONFIG_ROOT,
    DERIVED_ROOT,
    MANIFEST_ROOT,
    REPORT_ROOT,
    read_json,
    write_json,
    write_text,
)


FROZEN_SPLIT_COUNTS = {"train": 7000, "valid": 1000, "test": 2000}
EXPECTED_EXERCISE_FIELDS = {
    "area",
    "display_name",
    "exercise_external_id",
    "live",
    "prerequisite_raw",
    "topic",
}
EXPECTED_INTERACTION_FIELDS = {
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
INTERACTION_FIELD_ORDER = (
    "student_external_id",
    "exercise_external_id",
    "correct",
    "attempts",
    "count_hints",
    "duration_seconds",
    "earned_proficiency",
    "hint_used",
    "occurred_at",
)
MISSING_TOPIC_VALUES = {"", "n/a", "na", "none", "null"}


def iter_jsonl(path: Path) -> Iterable[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                raise ValueError(f"Blank JSONL row at {path}:{line_number}")
            value = json.loads(line)
            if not isinstance(value, dict):
                raise ValueError(f"Non-object JSONL row at {path}:{line_number}")
            yield value


def assert_schema(record: Mapping[str, Any], expected: set[str], label: str) -> None:
    if set(record) != expected:
        raise ValueError(
            f"Unexpected {label} schema: missing={sorted(expected - set(record))}, "
            f"extra={sorted(set(record) - expected)}"
        )


def exact_interaction_key(record: Mapping[str, Any]) -> tuple[Any, ...]:
    return tuple(record[field] for field in INTERACTION_FIELD_ORDER)


def normalise_topic(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    value = value.strip()
    return None if value.casefold() in MISSING_TOPIC_VALUES else value


def load_frozen_split(
    membership_path: Path, expected_counts: Mapping[str, int] = FROZEN_SPLIT_COUNTS
) -> tuple[dict[str, list[str]], dict[str, str], dict[str, Any]]:
    membership = read_json(membership_path)
    if set(membership) != set(expected_counts):
        raise ValueError("Frozen membership does not have the required train/valid/test keys")
    sets: dict[str, set[str]] = {}
    for split_name, expected_count in expected_counts.items():
        members = membership[split_name]
        if not isinstance(members, list) or not all(isinstance(member, str) and member for member in members):
            raise ValueError(f"Invalid {split_name} member list")
        if len(members) != expected_count or len(set(members)) != expected_count:
            raise ValueError(f"Frozen {split_name} membership differs from its required count")
        sets[split_name] = set(members)
    overlap = {
        "train_valid": len(sets["train"] & sets["valid"]),
        "train_test": len(sets["train"] & sets["test"]),
        "valid_test": len(sets["valid"] & sets["test"]),
    }
    if any(overlap.values()):
        raise ValueError(f"Frozen membership overlap: {overlap}")
    student_to_split = {
        student: split_name
        for split_name, students in sets.items()
        for student in students
    }
    return membership, student_to_split, {
        "counts": {name: len(membership[name]) for name in expected_counts},
        "overlap": overlap,
        "student_count": len(student_to_split),
        "exact_member_list_comparison": "PASS",
    }


def load_topic_mapping(exercises_path: Path) -> tuple[dict[str, str], set[str], list[str], dict[str, Any]]:
    by_exercise: dict[str, list[dict[str, Any]]] = defaultdict(list)
    source_rows = 0
    for record in iter_jsonl(exercises_path):
        assert_schema(record, EXPECTED_EXERCISE_FIELDS, "exercise")
        external_id = record["exercise_external_id"]
        if not isinstance(external_id, str) or not external_id:
            raise ValueError("Empty exercise external ID")
        by_exercise[external_id].append(record)
        source_rows += 1
    topics = sorted(
        {
            topic
            for records in by_exercise.values()
            for record in records
            if (topic := normalise_topic(record["topic"])) is not None
        }
    )
    mapping: dict[str, str] = {}
    unmapped: set[str] = set()
    conflicting = 0
    for external_id, records in by_exercise.items():
        candidates = {
            topic for record in records if (topic := normalise_topic(record["topic"])) is not None
        }
        if len(candidates) == 1:
            mapping[external_id] = next(iter(candidates))
        else:
            unmapped.add(external_id)
            if len(candidates) > 1:
                conflicting += 1
    return mapping, unmapped, topics, {
        "metadata_rows": source_rows,
        "distinct_external_ids": len(by_exercise),
        "duplicate_external_id_count": sum(len(rows) > 1 for rows in by_exercise.values()),
        "mapped_external_ids": len(mapping),
        "unmapped_external_ids": len(unmapped),
        "conflicting_topic_external_ids": conflicting,
        "topic_count": len(topics),
        "mapping_rule": "Only an Exercise with exactly one non-missing Topic across metadata records is Q-matrix eligible.",
    }


def split_warm_sequence(length: int) -> tuple[int, int, int]:
    """Return deterministic chronological train/validation/test counts.

    For normal sequences (length >= 5), the documented floor/remaining rule is
    used directly.  Short sequences remain members: one-row sequences are test
    only, two-row sequences are train/test, and three/four-row sequences retain
    at least one validation and one test row when possible.
    """

    if length < 1:
        return 0, 0, 0
    if length == 1:
        return 0, 0, 1
    if length == 2:
        return 1, 0, 1
    train_count = int(length * 0.60)
    valid_count = int(length * 0.20)
    if length < 5:
        train_count = max(1, train_count)
        valid_count = max(1, valid_count)
    test_count = length - train_count - valid_count
    if test_count < 1:
        if train_count > 1:
            train_count -= 1
        elif valid_count > 0:
            valid_count -= 1
        test_count = length - train_count - valid_count
    if test_count < 1:
        raise AssertionError("Warm split did not retain a test interaction")
    return train_count, valid_count, test_count


def chronological_records(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return sorted(records, key=lambda record: (record["occurred_at"], record["source_order"]))


def write_q_and_maps(
    root: Path,
    membership: Mapping[str, list[str]],
    student_index: Mapping[str, int],
    exercise_index: Mapping[str, int],
    topic_index: Mapping[str, int],
    topic_by_exercise: Mapping[str, str],
) -> None:
    root.mkdir(parents=True, exist_ok=True)
    with (root / "q_matrix.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        for exercise in sorted(exercise_index, key=exercise_index.get):
            row = [0] * len(topic_index)
            row[topic_index[topic_by_exercise[exercise]]] = 1
            writer.writerow(row)
    write_json(root / "split_membership.json", dict(membership))
    if read_json(root / "split_membership.json") != dict(membership):
        raise AssertionError("Copied frozen member list differs from source")
    write_json(root / "student_index_map.json", dict(student_index))
    write_json(root / "exercise_index_map.json", dict(exercise_index))
    write_json(root / "topic_index_map.json", dict(topic_index))


def write_rows(path: Path, rows: Iterable[dict[str, Any]], indexes: Mapping[str, Mapping[str, int]]) -> int:
    count = 0
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(["student_id", "exercise_id", "topic_id", "correct"])
        for record in rows:
            writer.writerow(
                [
                    indexes["student"][record["student_external_id"]],
                    indexes["exercise"][record["exercise_external_id"]],
                    indexes["topic"][record["topic"]],
                    int(record["correct"]),
                ]
            )
            count += 1
    return count


def preflight(
    medium_root: Path,
    warm_root: Path,
    cold_root: Path,
    warm_manifest_path: Path,
    cold_manifest_path: Path,
    warm_report_path: Path,
    cold_report_path: Path,
    config_path: Path,
    expected_split_counts: Mapping[str, int] = FROZEN_SPLIT_COUNTS,
) -> tuple[dict[str, Any], dict[str, Any]]:
    config = read_json(config_path)
    if config.get("dataset_family") != "JUNYI_MID":
        raise ValueError("MODEL-0R preflight requires the JUNYI_MID dataset family")
    membership, student_to_split, split_evidence = load_frozen_split(
        medium_root / "split_membership.json", expected_split_counts
    )
    topic_by_exercise, unmapped_exercises, topics, mapping_evidence = load_topic_mapping(
        medium_root / "exercises.jsonl"
    )
    if len(topics) != 40:
        raise ValueError(f"Expected 40 frozen Topic columns, found {len(topics)}")

    source_rows = 0
    duplicate_rows = 0
    unknown_rows = 0
    topic_unmapped_rows = 0
    retained_by_student: dict[str, list[dict[str, Any]]] = defaultdict(list)
    seen_exact: set[tuple[Any, ...]] = set()
    observed_exercises: set[str] = set()
    retained_exercises: set[str] = set()
    for source_order, record in enumerate(iter_jsonl(medium_root / "interactions.jsonl"), start=1):
        assert_schema(record, EXPECTED_INTERACTION_FIELDS, "interaction")
        source_rows += 1
        student = record["student_external_id"]
        exercise = record["exercise_external_id"]
        if not isinstance(student, str) or student not in student_to_split:
            raise ValueError(f"Interaction {source_order} does not belong to frozen Medium membership")
        if not isinstance(exercise, str) or not exercise:
            raise ValueError(f"Invalid Exercise at interaction {source_order}")
        if not isinstance(record["correct"], bool):
            raise ValueError(f"Non-binary correct value at interaction {source_order}")
        if not isinstance(record["occurred_at"], str) or not record["occurred_at"]:
            raise ValueError(f"Missing occurred_at value at interaction {source_order}")
        observed_exercises.add(exercise)
        key = exact_interaction_key(record)
        if key in seen_exact:
            duplicate_rows += 1
            continue
        seen_exact.add(key)
        if exercise not in topic_by_exercise and exercise not in unmapped_exercises:
            unknown_rows += 1
            continue
        if exercise not in topic_by_exercise:
            topic_unmapped_rows += 1
            continue
        retained_exercises.add(exercise)
        retained_by_student[student].append(
            {
                "student_external_id": student,
                "exercise_external_id": exercise,
                "topic": topic_by_exercise[exercise],
                "correct": int(record["correct"]),
                "occurred_at": record["occurred_at"],
                "source_order": source_order,
            }
        )

    missing_students = [student for student in student_to_split if not retained_by_student[student]]
    if missing_students:
        raise ValueError(
            f"Frozen Medium members have no Q-matrix-eligible interactions: {len(missing_students)}"
        )
    student_index = {student: index for index, student in enumerate(sorted(student_to_split))}
    exercise_index = {exercise: index for index, exercise in enumerate(sorted(retained_exercises))}
    topic_index = {topic: index for index, topic in enumerate(topics)}
    if (len(student_index), len(exercise_index), len(topic_index)) != (10000, 624, 40):
        raise ValueError(
            "Unexpected frozen cardinalities: "
            f"students={len(student_index)}, exercises={len(exercise_index)}, topics={len(topic_index)}"
        )
    indexes = {"student": student_index, "exercise": exercise_index, "topic": topic_index}
    write_q_and_maps(warm_root, membership, student_index, exercise_index, topic_index, topic_by_exercise)
    write_q_and_maps(cold_root, membership, student_index, exercise_index, topic_index, topic_by_exercise)

    warm_partitions: dict[str, list[dict[str, Any]]] = {"train": [], "valid": [], "test": []}
    warm_distribution: Counter[tuple[int, int, int]] = Counter()
    short_sequence_distribution: Counter[tuple[int, int, int, int]] = Counter()
    for student in sorted(student_to_split):
        sequence = chronological_records(retained_by_student[student])
        train_count, valid_count, test_count = split_warm_sequence(len(sequence))
        if train_count + valid_count + test_count != len(sequence):
            raise AssertionError("Warm chronological partition lost an interaction")
        warm_distribution[(train_count, valid_count, test_count)] += 1
        if len(sequence) < 5:
            short_sequence_distribution[(len(sequence), train_count, valid_count, test_count)] += 1
        warm_partitions["train"].extend(sequence[:train_count])
        warm_partitions["valid"].extend(sequence[train_count : train_count + valid_count])
        warm_partitions["test"].extend(sequence[train_count + valid_count :])
    warm_counts = {
        split_name: write_rows(warm_root / f"interactions_{split_name}.csv", rows, indexes)
        for split_name, rows in warm_partitions.items()
    }
    if sum(warm_counts.values()) != sum(len(records) for records in retained_by_student.values()):
        raise AssertionError("Warm split counts do not cover all retained interactions")

    cold_train_rows: list[dict[str, Any]] = []
    cold_sequence_rows: dict[str, list[dict[str, Any]]] = {"valid": [], "test": []}
    cold_lengths: dict[str, list[int]] = {"valid": [], "test": []}
    for student in sorted(student_to_split):
        sequence = chronological_records(retained_by_student[student])
        split_name = student_to_split[student]
        if split_name == "train":
            cold_train_rows.extend(sequence)
        else:
            cold_sequence_rows[split_name].extend(sequence)
            cold_lengths[split_name].append(len(sequence))
    cold_counts = {
        "train": write_rows(cold_root / "interactions_train.csv", cold_train_rows, indexes),
        "valid": write_rows(cold_root / "interactions_valid_sequence.csv", cold_sequence_rows["valid"], indexes),
        "test": write_rows(cold_root / "interactions_test_sequence.csv", cold_sequence_rows["test"], indexes),
    }
    cold_eligibility: dict[str, dict[str, dict[str, Any]]] = {}
    for split_name, lengths in cold_lengths.items():
        by_k: dict[str, dict[str, Any]] = {}
        for k in config["cold_k_values"]:
            eligible_lengths = [length for length in lengths if length >= k + 1]
            by_k[str(k)] = {
                "frozen_members": len(lengths),
                "eligible_students": len(eligible_lengths),
                "ineligible_students": len(lengths) - len(eligible_lengths),
                "coverage_percent": 100.0 * len(eligible_lengths) / len(lengths),
                "evaluation_interactions": int(sum(length - k for length in eligible_lengths)),
                "eligibility_rule": f"At least {k + 1} retained chronological rows: first {k} calibration rows and at least one disjoint evaluation row.",
            }
        cold_eligibility[split_name] = by_k

    shared_measurements = {
        "frozen_data0_membership": split_evidence,
        "source_interactions": {
            "source_rows": source_rows,
            "exact_duplicate_rows": duplicate_rows,
            "rows_after_exact_duplicate_removal": source_rows - duplicate_rows,
            "unknown_exercise_rows_after_dedup": unknown_rows,
            "topic_unmapped_rows_after_dedup": topic_unmapped_rows,
            "retained_q_eligible_rows": sum(len(records) for records in retained_by_student.values()),
            "exact_duplicate_definition": list(INTERACTION_FIELD_ORDER),
        },
        "exercise_topic_mapping": mapping_evidence,
        "q_matrix": {
            "rows": len(exercise_index),
            "columns": len(topic_index),
            "nonzero_entries": len(exercise_index),
            "semantics": "DATA-0 / ADR-0002 frozen one-Topic-per-Exercise initial concept layer.",
        },
        "student_with_retained_interactions": len(retained_by_student),
        "source_artifacts_tracked": False,
    }
    warm_manifest = {
        "dataset_name": "JUNYI_MID_TRANS_V1",
        "version": "1.0.0-model0r",
        "parent_dataset": "JUNYI_MID",
        "source_artifacts": {
            "logical_medium_root": "data-pipeline/.data/generated/medium",
            "parent_manifest": "data-pipeline/manifests/junyi_mid_v1.json",
        },
        "shared_measurements": shared_measurements,
        "protocol": {
            "name": "WARM_TRANSDUCTIVE_DIAGNOSIS",
            "student_membership": "Exactly the frozen 10,000 DATA-0 Medium students; no resampling.",
            "ordering": "Per student: occurred_at ascending, with source-order as a stable tie-breaker.",
            "split_rule": "For length >= 5, floor(0.60*n), floor(0.20*n), and remaining rows for test. Short sequences use the documented retained-member exception.",
            "future_leakage_control": "Training uses only each student's earlier partition; validation and test labels do not enter training, rate estimates, response graphs, selection, or mastery fitting.",
            "short_sequence_rule": "n=1 -> 0/0/1; n=2 -> 1/0/1; n=3 or 4 -> at least 1/1/1 while retaining all rows.",
            "short_sequence_distribution": [
                {"sequence_length": n, "train": tr, "valid": va, "test": te, "students": count}
                for (n, tr, va, te), count in sorted(short_sequence_distribution.items())
            ],
            "students_by_partition_pattern": [
                {"train": tr, "valid": va, "test": te, "students": count}
                for (tr, va, te), count in sorted(warm_distribution.items())
            ],
            "interaction_counts": warm_counts,
        },
        "derived_local_artifacts": {
            "root": "model-service/experiments/model0r/data/junyi_mid_trans_v1",
            "tracked_by_git": False,
            "files": ["interactions_train.csv", "interactions_valid.csv", "interactions_test.csv", "q_matrix.csv", "student_index_map.json", "exercise_index_map.json", "topic_index_map.json", "split_membership.json"],
        },
        "status": "PASS",
    }
    cold_manifest = {
        "dataset_name": "JUNYI_MID_COLD_V1",
        "version": "1.0.0-model0r",
        "parent_dataset": "JUNYI_MID",
        "source_artifacts": {
            "logical_medium_root": "data-pipeline/.data/generated/medium",
            "parent_manifest": "data-pipeline/manifests/junyi_mid_v1.json",
        },
        "shared_measurements": shared_measurements,
        "protocol": {
            "name": "NEW_STUDENT_COLD_START",
            "student_membership": "Exactly DATA-0 frozen 7000 train / 1000 validation / 2000 test students; no substitutions.",
            "ordering": "Per student: occurred_at ascending, with source-order as a stable tie-breaker.",
            "calibration_evaluation_boundary": "For each k, first k chronological retained rows are calibration only; later rows are evaluation only. Members with fewer than k+1 retained rows remain in the frozen split and are marked INELIGIBLE_FOR_K.",
            "k_values": config["cold_k_values"],
            "interaction_counts": cold_counts,
            "eligibility": cold_eligibility,
        },
        "derived_local_artifacts": {
            "root": "model-service/experiments/model0r/data/junyi_mid_cold_v1",
            "tracked_by_git": False,
            "files": ["interactions_train.csv", "interactions_valid_sequence.csv", "interactions_test_sequence.csv", "q_matrix.csv", "student_index_map.json", "exercise_index_map.json", "topic_index_map.json", "split_membership.json"],
        },
        "status": "PASS",
    }
    write_json(warm_manifest_path, warm_manifest)
    write_json(cold_manifest_path, cold_manifest)
    write_text(warm_report_path, make_warm_report(warm_manifest))
    write_text(cold_report_path, make_cold_report(cold_manifest))
    return warm_manifest, cold_manifest


def make_warm_report(manifest: Mapping[str, Any]) -> str:
    shared = manifest["shared_measurements"]
    protocol = manifest["protocol"]
    split_rows = "\n".join(
        f"| {name} | {count:,} |" for name, count in protocol["interaction_counts"].items()
    )
    short_rows = "\n".join(
        f"| {entry['sequence_length']} | {entry['train']} / {entry['valid']} / {entry['test']} | {entry['students']} |"
        for entry in protocol["short_sequence_distribution"]
    ) or "| none | N/A | 0 |"
    return f"""# MODEL-0R Warm Preflight

## Status

**{manifest['status']}** — `JUNYI_MID_TRANS_V1` retains exactly the frozen
10,000 Medium members and creates a separate chronological interaction split.
It does not overwrite the DATA-0 student-level 7,000 / 1,000 / 2,000 split.

## Frozen input and Q-matrix

| Check | Value |
| --- | --- |
| Frozen train / validation / test members | 7,000 / 1,000 / 2,000 |
| Membership overlap | all zero |
| Exact member-list comparison | {shared['frozen_data0_membership']['exact_member_list_comparison']} |
| Retained Q-eligible interactions | {shared['source_interactions']['retained_q_eligible_rows']:,} |
| Exact full-record duplicates removed locally | {shared['source_interactions']['exact_duplicate_rows']:,} |
| Q-matrix | {shared['q_matrix']['rows']} Exercises × {shared['q_matrix']['columns']} Topics |

The first source-order occurrence of an exact full-record duplicate is retained.
Unknown or Topic-unmapped exercises are excluded under the unchanged MODEL-0A
derivation rule.  Source data and local numeric derivatives are not tracked.

## Chronological rule

{protocol['ordering']}

{protocol['split_rule']}

{protocol['future_leakage_control']}

| Warm partition | Interactions |
| --- | ---: |
{split_rows}

## Short sequences

{protocol['short_sequence_rule']}

| Sequence length | train / validation / test | Students |
| ---: | --- | ---: |
{short_rows}

No student is silently deleted because a sequence is short.  Every retained
student has at least one final test interaction under this protocol.
"""


def make_cold_report(manifest: Mapping[str, Any]) -> str:
    shared = manifest["shared_measurements"]
    protocol = manifest["protocol"]
    rows: list[str] = []
    for split_name in ("valid", "test"):
        for k, entry in protocol["eligibility"][split_name].items():
            rows.append(
                f"| {split_name} | {k} | {entry['eligible_students']} | {entry['ineligible_students']} | "
                f"{entry['coverage_percent']:.2f}% | {entry['evaluation_interactions']:,} |"
            )
    return f"""# MODEL-0R Cold Preflight

## Status

**{manifest['status']}** — `JUNYI_MID_COLD_V1` preserves DATA-0's exact
student membership: 7,000 train, 1,000 validation, and 2,000 test.  No member
is exchanged, resampled, or moved between groups.

## Input boundary

| Check | Value |
| --- | --- |
| Exact member-list comparison | {shared['frozen_data0_membership']['exact_member_list_comparison']} |
| Membership overlap | all zero |
| Train / valid / test source-role rows | {protocol['interaction_counts']['train']:,} / {protocol['interaction_counts']['valid']:,} / {protocol['interaction_counts']['test']:,} |
| Q-matrix | {shared['q_matrix']['rows']} Exercises × {shared['q_matrix']['columns']} Topics |

## Calibration and evaluation boundary

{protocol['ordering']}

{protocol['calibration_evaluation_boundary']}

| Frozen split | k | Eligible students | `INELIGIBLE_FOR_K` | Coverage | Evaluation interactions |
| --- | ---: | ---: | ---: | ---: | ---: |
{chr(10).join(rows)}

The first k responses and all later evaluation responses are disjoint by
construction.  No evaluation label can update a student representation.
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--medium-root", required=True, type=Path)
    parser.add_argument("--warm-root", type=Path, default=DERIVED_ROOT / "junyi_mid_trans_v1")
    parser.add_argument("--cold-root", type=Path, default=DERIVED_ROOT / "junyi_mid_cold_v1")
    parser.add_argument("--warm-manifest", type=Path, default=MANIFEST_ROOT / "junyi_mid_trans_v1.json")
    parser.add_argument("--cold-manifest", type=Path, default=MANIFEST_ROOT / "junyi_mid_cold_v1.json")
    parser.add_argument("--warm-report", type=Path, default=REPORT_ROOT / "warm_preflight.md")
    parser.add_argument("--cold-report", type=Path, default=REPORT_ROOT / "cold_preflight.md")
    parser.add_argument("--config", type=Path, default=CONFIG_ROOT / "preflight.json")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    warm, cold = preflight(
        args.medium_root,
        args.warm_root,
        args.cold_root,
        args.warm_manifest,
        args.cold_manifest,
        args.warm_report,
        args.cold_report,
        args.config,
    )
    print(
        "MODEL-0R preflight completed: "
        f"{warm['dataset_name']}={warm['status']}; {cold['dataset_name']}={cold['status']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
