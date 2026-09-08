"""Build and audit the non-production JUNYI_MID_MODEL_V1 derivative.

The implementation uses exact field-wise comparisons for duplicate rows and
exact member-list comparisons for split integrity. It never creates or records
a content digest.
"""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable, Mapping


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
EXPECTED_RELATIONSHIP_FIELDS = {
    "relation_type",
    "score",
    "source",
    "source_external_id",
    "target_external_id",
    "verified",
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


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2, sort_keys=True)
        handle.write("\n")


def iter_jsonl(path: Path) -> Iterable[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                raise ValueError(f"Blank JSONL row at {path}:{line_number}")
            payload = json.loads(line)
            if not isinstance(payload, dict):
                raise ValueError(f"Non-object JSONL row at {path}:{line_number}")
            yield payload


def assert_schema(record: Mapping[str, Any], expected: set[str], label: str) -> None:
    actual = set(record)
    if actual != expected:
        raise ValueError(
            f"Unexpected {label} schema: missing={sorted(expected - actual)}, "
            f"extra={sorted(actual - expected)}"
        )


def normalise_topic(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    cleaned = value.strip()
    return None if cleaned.casefold() in MISSING_TOPIC_VALUES else cleaned


def exact_interaction_key(record: Mapping[str, Any]) -> tuple[Any, ...]:
    """Return a complete ordered row value tuple without using a digest."""

    return tuple(record[field] for field in INTERACTION_FIELD_ORDER)


def load_frozen_split(
    membership_path: Path, expected_counts: Mapping[str, int]
) -> tuple[dict[str, list[str]], dict[str, str], dict[str, Any]]:
    membership = read_json(membership_path)
    if set(membership) != set(expected_counts):
        raise ValueError(
            f"Split keys must be {sorted(expected_counts)}, got {sorted(membership)}"
        )
    split_sets: dict[str, set[str]] = {}
    for split_name, expected_count in expected_counts.items():
        members = membership[split_name]
        if not isinstance(members, list) or not all(isinstance(member, str) for member in members):
            raise ValueError(f"{split_name} membership must be a string list")
        if len(members) != expected_count:
            raise ValueError(
                f"{split_name} member count changed: expected {expected_count}, got {len(members)}"
            )
        member_set = set(members)
        if len(member_set) != len(members):
            raise ValueError(f"{split_name} membership contains duplicate students")
        split_sets[split_name] = member_set
    overlaps = {
        "train_valid": len(split_sets["train"] & split_sets["valid"]),
        "train_test": len(split_sets["train"] & split_sets["test"]),
        "valid_test": len(split_sets["valid"] & split_sets["test"]),
    }
    if any(overlaps.values()):
        raise ValueError(f"Frozen split overlap detected: {overlaps}")
    student_to_split = {
        student: split_name
        for split_name, members in split_sets.items()
        for student in members
    }
    evidence = {
        "counts": {name: len(membership[name]) for name in expected_counts},
        "overlap": overlaps,
        "student_count": len(student_to_split),
        "exact_member_list_comparison": "PASS",
    }
    return membership, student_to_split, evidence


def load_exercise_mapping(
    exercises_path: Path,
) -> tuple[dict[str, str], set[str], list[str], dict[str, Any]]:
    by_external_id: dict[str, list[dict[str, Any]]] = defaultdict(list)
    record_count = 0
    for record in iter_jsonl(exercises_path):
        assert_schema(record, EXPECTED_EXERCISE_FIELDS, "exercise")
        external_id = record["exercise_external_id"]
        if not isinstance(external_id, str) or not external_id:
            raise ValueError("Exercise external ID must be a non-empty string")
        by_external_id[external_id].append(record)
        record_count += 1
    all_topics = sorted(
        {
            topic
            for records in by_external_id.values()
            for record in records
            if (topic := normalise_topic(record["topic"])) is not None
        }
    )
    topic_by_exercise: dict[str, str] = {}
    unmapped_ids: set[str] = set()
    conflicting_ids: set[str] = set()
    for external_id, records in by_external_id.items():
        candidate_topics = {
            topic
            for record in records
            if (topic := normalise_topic(record["topic"])) is not None
        }
        if len(candidate_topics) == 1:
            topic_by_exercise[external_id] = next(iter(candidate_topics))
        else:
            unmapped_ids.add(external_id)
            if len(candidate_topics) > 1:
                conflicting_ids.add(external_id)
    evidence = {
        "metadata_rows": record_count,
        "distinct_external_ids": len(by_external_id),
        "duplicate_external_id_count": sum(
            1 for records in by_external_id.values() if len(records) > 1
        ),
        "topic_count": len(all_topics),
        "mapped_external_ids": len(topic_by_exercise),
        "unmapped_external_ids": len(unmapped_ids),
        "conflicting_topic_external_ids": len(conflicting_ids),
        "mapping_rule": (
            "Map only an external ID with exactly one non-missing Topic across its "
            "metadata records; leave missing or conflicting IDs unmapped."
        ),
    }
    return topic_by_exercise, unmapped_ids, all_topics, evidence


def induced_graph_metrics(
    nodes: set[str], relationships: Iterable[Mapping[str, Any]]
) -> dict[str, Any]:
    """Measure raw-prerequisite evidence induced by an explicit node set."""

    raw_rows = 0
    edges: set[tuple[str, str]] = set()
    for relation in relationships:
        source = relation["source_external_id"]
        target = relation["target_external_id"]
        if source in nodes and target in nodes:
            raw_rows += 1
            edges.add((source, target))
    directed = {node: set() for node in nodes}
    undirected = {node: set() for node in nodes}
    for source, target in edges:
        directed[source].add(target)
        undirected[source].add(target)
        undirected[target].add(source)
    visited: set[str] = set()
    component_sizes: list[int] = []
    for node in nodes:
        if node in visited:
            continue
        stack = [node]
        visited.add(node)
        size = 0
        while stack:
            current = stack.pop()
            size += 1
            for neighbor in undirected[current]:
                if neighbor not in visited:
                    visited.add(neighbor)
                    stack.append(neighbor)
        component_sizes.append(size)

    index = 0
    indices: dict[str, int] = {}
    low_links: dict[str, int] = {}
    stack: list[str] = []
    on_stack: set[str] = set()
    strongly_connected_components: list[list[str]] = []

    def visit(node: str) -> None:
        nonlocal index
        indices[node] = index
        low_links[node] = index
        index += 1
        stack.append(node)
        on_stack.add(node)
        for neighbor in directed[node]:
            if neighbor not in indices:
                visit(neighbor)
                low_links[node] = min(low_links[node], low_links[neighbor])
            elif neighbor in on_stack:
                low_links[node] = min(low_links[node], indices[neighbor])
        if low_links[node] == indices[node]:
            component: list[str] = []
            while True:
                child = stack.pop()
                on_stack.remove(child)
                component.append(child)
                if child == node:
                    break
            strongly_connected_components.append(component)

    for node in nodes:
        if node not in indices:
            visit(node)
    self_loops = sum(1 for source, target in edges if source == target)
    cyclic_sccs = sum(
        1
        for component in strongly_connected_components
        if len(component) > 1
        or (len(component) == 1 and (component[0], component[0]) in edges)
    )
    largest_component = max(component_sizes, default=0)
    return {
        "nodes": len(nodes),
        "raw_relation_rows_in_projection": raw_rows,
        "edges": len(edges),
        "duplicate_relation_rows_in_projection": raw_rows - len(edges),
        "components": len(component_sizes),
        "largest_component": largest_component,
        "largest_component_ratio": largest_component / len(nodes) if nodes else 0.0,
        "self_loops": self_loops,
        "cyclic_sccs": cyclic_sccs,
        "edge_source": "PREREQUISITE_RAW_UNVERIFIED / JUNYI_EXERCISE_METADATA only",
        "publication_status": "RAW_EVIDENCE_ONLY_NOT_PUBLISHED_GRAPH",
    }


def correct_rate(summary: Mapping[str, int]) -> float | None:
    count = summary["interactions"]
    return summary["correct"] / count if count else None


def make_markdown(measurements: Mapping[str, Any]) -> str:
    split_lines = "\n".join(
        "| {split} | {students} | {interactions} | {rate:.6f} |".format(
            split=split_name,
            students=values["students"],
            interactions=values["interactions"],
            rate=values["correct_rate"] if values["correct_rate"] is not None else 0.0,
        )
        for split_name, values in measurements["retained_split_statistics"].items()
    )
    interaction = measurements["interactions"]
    exercise = measurements["exercise_topic_mapping"]
    q_matrix = measurements["q_matrix"]
    graph = measurements["medium_observed_exercise_induced_graph"]
    eligible_graph = measurements["q_eligible_observed_exercise_induced_graph"]
    fixed_scope = measurements["fixed_data0_graph_scope"]
    return f"""# MODEL-0 Medium Preflight

## Status

**PASS_WITH_COLD_START_LIMITATION**. `JUNYI_MID_MODEL_V1` was derived without
changing the frozen 10,000-student membership or the 7,000 / 1,000 / 2,000
student split. The derivative removes only later full-record-equal interactions
and excludes unknown or Topic-unmapped exercises from Q-matrix model input.

This is not a production dataset and no research student is imported into a
platform database.

## Immutable split evidence

| Check | Value |
| --- | ---: |
| Frozen train students | {measurements['frozen_split']['counts']['train']} |
| Frozen valid students | {measurements['frozen_split']['counts']['valid']} |
| Frozen test students | {measurements['frozen_split']['counts']['test']} |
| Train/valid overlap | {measurements['frozen_split']['overlap']['train_valid']} |
| Train/test overlap | {measurements['frozen_split']['overlap']['train_test']} |
| Valid/test overlap | {measurements['frozen_split']['overlap']['valid_test']} |
| Member-list comparison | {measurements['frozen_split']['exact_member_list_comparison']} |

## Interaction filtering

| Measurement | Count |
| --- | ---: |
| Source Medium interactions | {interaction['source_rows']} |
| Exact duplicate interactions | {interaction['exact_duplicate_rows']} |
| Rows after exact-duplicate removal | {interaction['rows_after_exact_duplicate_removal']} |
| Unknown-exercise interactions before duplicate removal | {interaction['unknown_exercise_rows_source']} |
| Unknown-exercise interactions after duplicate removal | {interaction['unknown_exercise_rows_after_dedup']} |
| Known but Topic-unmapped interactions after duplicate removal | {interaction['topic_unmapped_rows_after_dedup']} |
| Retained Q-matrix-eligible interactions | {interaction['retained_q_eligible_rows']} |
| Observed Exercise IDs before filtering | {interaction['observed_exercise_count_before_filtering']} |
| Q-eligible observed Exercise IDs after filtering | {interaction['observed_exercise_count_after_filtering']} |

Exact duplicates are determined by equality of every documented interaction
field, not by a shortened business key. The first source-order occurrence is
retained in the local derivative; all original DATA-0 artifacts remain intact.

## Exercise to Topic mapping and Q-matrix

| Measurement | Count / value |
| --- | --- |
| Medium metadata rows | {exercise['metadata_rows']} |
| Medium metadata distinct external IDs | {exercise['distinct_external_ids']} |
| Metadata duplicate external IDs | {exercise['duplicate_external_id_count']} |
| Metadata Topic columns | {exercise['topic_count']} |
| Mapped metadata external IDs | {exercise['mapped_external_ids']} |
| Unmapped metadata external IDs | {exercise['unmapped_external_ids']} |
| Conflicting-Topic external IDs | {exercise['conflicting_topic_external_ids']} |
| Q-matrix shape | {q_matrix['rows']} × {q_matrix['columns']} |
| Q-matrix non-zero entries | {q_matrix['nonzero_entries']} |
| Q-matrix density | {q_matrix['density']:.6f} |
| Observed Exercise IDs excluded as unmapped | {q_matrix['unmapped_observed_exercise_ids']} |

The 40 columns are the non-missing Topics found in the Medium metadata. An
external ID is mapped only if all of its metadata rows yield one unique
non-missing Topic. Missing or conflicting identity evidence stays `UNMAPPED`.

## Retained split statistics

| Split | Students | Interactions | Correct rate |
| --- | ---: | ---: | ---: |
{split_lines}

## Candidate-specific raw-prerequisite projection

The table below is an induced projection of raw prerequisite evidence, not a
Published Graph and not a production relationship import.

| Measurement | All Medium observed exercises | Q-eligible observed exercises |
| --- | ---: | ---: |
| Nodes | {graph['nodes']} | {eligible_graph['nodes']} |
| Raw relation rows in projection | {graph['raw_relation_rows_in_projection']} | {eligible_graph['raw_relation_rows_in_projection']} |
| Unique directed edges | {graph['edges']} | {eligible_graph['edges']} |
| Weak components | {graph['components']} | {eligible_graph['components']} |
| Largest-component ratio | {graph['largest_component_ratio']:.6f} | {eligible_graph['largest_component_ratio']:.6f} |
| Self-loops | {graph['self_loops']} | {eligible_graph['self_loops']} |
| Cyclic SCCs | {graph['cyclic_sccs']} | {eligible_graph['cyclic_sccs']} |

For comparison only, DATA-0's fixed scope contains {fixed_scope['nodes']} nodes
and {fixed_scope['edges']} raw prerequisite edges. Those values are not used to
describe the Medium observed-exercise projection above.

## Evaluation limitation carried into NCDM and ORCDF

The split is deliberately student-disjoint. Standard NCDM and ORCDF contain
per-student parameters, so validation and test students have no training-row
signal. This derivative does **not** create calibration rows from held-out
students. Later model runs must therefore use a documented neutral,
zero-history fallback for held-out student embeddings. Their validation/test
metrics measure cold-start response prediction, not recovery of an individually
fitted held-out student's mastery state. A different temporal-calibration
protocol would require Owner approval and a new gate.

## Output boundaries

- Tracked evidence: this report, the manifest, fixed configuration, scripts,
  and tests.
- Ignored local data: numeric model tables, exact member lists, and index maps
  under `model-service/experiments/model0/data/`.
- No content digest is generated or recorded.
"""


def run_preflight(
    medium_root: Path,
    derived_root: Path,
    manifest_path: Path,
    report_path: Path,
    config_path: Path,
    expected_split_counts: Mapping[str, int] | None = None,
) -> dict[str, Any]:
    """Materialise the derived model input and return auditable measurements."""

    expected_split_counts = dict(expected_split_counts or FROZEN_SPLIT_COUNTS)
    medium_root = medium_root.resolve()
    derived_root = derived_root.resolve()
    config = read_json(config_path)
    if config["dataset_name"] != "JUNYI_MID_MODEL_V1":
        raise ValueError("Preflight config dataset name must be JUNYI_MID_MODEL_V1")
    membership, student_to_split, split_evidence = load_frozen_split(
        medium_root / "split_membership.json", expected_split_counts
    )
    topic_by_exercise, unmapped_metadata_ids, topics, mapping_evidence = load_exercise_mapping(
        medium_root / "exercises.jsonl"
    )
    if len(topics) != config["topic_columns"]:
        raise ValueError(
            f"Expected {config['topic_columns']} Topic columns, found {len(topics)}"
        )

    relationships: list[dict[str, Any]] = []
    relation_source_counts: Counter[str] = Counter()
    for relation in iter_jsonl(medium_root / "relationships.jsonl"):
        assert_schema(relation, EXPECTED_RELATIONSHIP_FIELDS, "relationship")
        relation_source_counts[
            f"{relation['source']}|{relation['relation_type']}|{relation['verified']}"
        ] += 1
        if (
            relation["source"] == "JUNYI_EXERCISE_METADATA"
            and relation["relation_type"] == "PREREQUISITE_RAW_UNVERIFIED"
            and relation["verified"] is False
        ):
            relationships.append(relation)
    if not relationships:
        raise ValueError("No raw prerequisite evidence was available for the required projection")

    derived_root.mkdir(parents=True, exist_ok=True)
    raw_eligible_path = derived_root / "eligible_interactions.jsonl"
    retained_by_split = {
        split_name: {
            "students": expected_split_counts[split_name],
            "interactions": 0,
            "correct": 0,
        }
        for split_name in expected_split_counts
    }
    source_rows = 0
    exact_duplicate_rows = 0
    unknown_source_rows = 0
    unknown_after_dedup = 0
    topic_unmapped_after_dedup = 0
    seen_exact_rows: set[tuple[Any, ...]] = set()
    observed_before: set[str] = set()
    observed_after: set[str] = set()

    with raw_eligible_path.open("w", encoding="utf-8", newline="\n") as output:
        for source_row_number, record in enumerate(
            iter_jsonl(medium_root / "interactions.jsonl"), start=1
        ):
            assert_schema(record, EXPECTED_INTERACTION_FIELDS, "interaction")
            source_rows += 1
            student = record["student_external_id"]
            exercise = record["exercise_external_id"]
            correct = record["correct"]
            if not isinstance(student, str) or not student:
                raise ValueError(f"Empty student ID at interaction row {source_row_number}")
            if student not in student_to_split:
                raise ValueError(
                    f"Interaction row {source_row_number} belongs to a student outside the frozen membership"
                )
            if not isinstance(exercise, str) or not exercise:
                raise ValueError(f"Empty exercise ID at interaction row {source_row_number}")
            if not isinstance(correct, bool):
                raise ValueError(f"Non-binary correct value at interaction row {source_row_number}")
            observed_before.add(exercise)
            if exercise not in topic_by_exercise and exercise not in unmapped_metadata_ids:
                unknown_source_rows += 1
            if exact_interaction_key(record) in seen_exact_rows:
                exact_duplicate_rows += 1
                continue
            seen_exact_rows.add(exact_interaction_key(record))
            if exercise not in topic_by_exercise and exercise not in unmapped_metadata_ids:
                unknown_after_dedup += 1
                continue
            if exercise not in topic_by_exercise:
                topic_unmapped_after_dedup += 1
                continue
            split_name = student_to_split[student]
            retained_by_split[split_name]["interactions"] += 1
            retained_by_split[split_name]["correct"] += int(correct)
            observed_after.add(exercise)
            output.write(
                json.dumps(
                    {
                        "student_external_id": student,
                        "exercise_external_id": exercise,
                        "correct": int(correct),
                    },
                    ensure_ascii=False,
                    separators=(",", ":"),
                )
                + "\n"
            )

    student_index = {student: index for index, student in enumerate(sorted(student_to_split))}
    exercise_index = {exercise: index for index, exercise in enumerate(sorted(observed_after))}
    topic_index = {topic: index for index, topic in enumerate(topics)}
    if not exercise_index:
        raise ValueError("No Q-matrix-eligible interactions remain after filtering")
    q_matrix_path = derived_root / "q_matrix.csv"
    with q_matrix_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        for exercise in sorted(exercise_index, key=exercise_index.get):
            row = [0] * len(topics)
            row[topic_index[topic_by_exercise[exercise]]] = 1
            writer.writerow(row)

    split_paths = {
        split_name: derived_root / f"interactions_{split_name}.csv"
        for split_name in expected_split_counts
    }
    split_handles = {
        split_name: path.open("w", encoding="utf-8", newline="")
        for split_name, path in split_paths.items()
    }
    try:
        writers = {
            split_name: csv.writer(handle, lineterminator="\n")
            for split_name, handle in split_handles.items()
        }
        for writer in writers.values():
            writer.writerow(["student_id", "exercise_id", "correct"])
        for record in iter_jsonl(raw_eligible_path):
            student = record["student_external_id"]
            exercise = record["exercise_external_id"]
            writers[student_to_split[student]].writerow(
                [student_index[student], exercise_index[exercise], record["correct"]]
            )
    finally:
        for handle in split_handles.values():
            handle.close()

    copied_membership_path = derived_root / "split_membership.json"
    write_json(copied_membership_path, membership)
    if read_json(copied_membership_path) != membership:
        raise AssertionError("Derived member list differs from the frozen source member list")
    write_json(derived_root / "student_index_map.json", student_index)
    write_json(derived_root / "exercise_index_map.json", exercise_index)
    write_json(derived_root / "topic_index_map.json", topic_index)

    retained_split_statistics = {
        split_name: {**values, "correct_rate": correct_rate(values)}
        for split_name, values in retained_by_split.items()
    }
    q_matrix_nonzero = len(exercise_index)
    measurements: dict[str, Any] = {
        "frozen_split": split_evidence,
        "interactions": {
            "source_rows": source_rows,
            "exact_duplicate_rows": exact_duplicate_rows,
            "rows_after_exact_duplicate_removal": source_rows - exact_duplicate_rows,
            "unknown_exercise_rows_source": unknown_source_rows,
            "unknown_exercise_rows_after_dedup": unknown_after_dedup,
            "topic_unmapped_rows_after_dedup": topic_unmapped_after_dedup,
            "retained_q_eligible_rows": sum(
                values["interactions"] for values in retained_by_split.values()
            ),
            "observed_exercise_count_before_filtering": len(observed_before),
            "observed_exercise_count_after_filtering": len(observed_after),
            "exact_duplicate_definition": list(INTERACTION_FIELD_ORDER),
        },
        "exercise_topic_mapping": mapping_evidence,
        "retained_split_statistics": retained_split_statistics,
        "q_matrix": {
            "rows": len(exercise_index),
            "columns": len(topics),
            "nonzero_entries": q_matrix_nonzero,
            "density": q_matrix_nonzero / (len(exercise_index) * len(topics)),
            "unmapped_observed_exercise_ids": len(observed_before - observed_after),
            "semantics": "one Topic column per uniquely mapped observed Exercise external ID",
        },
        "medium_observed_exercise_induced_graph": induced_graph_metrics(
            observed_before, relationships
        ),
        "q_eligible_observed_exercise_induced_graph": induced_graph_metrics(
            observed_after, relationships
        ),
        "fixed_data0_graph_scope": {
            "nodes": 815,
            "edges": 979,
            "scope_note": (
                "DATA-0 fixed graph scope; it is only a comparison and is not the "
                "Medium observed-exercise induced graph."
            ),
        },
        "relationship_source_counts": dict(sorted(relation_source_counts.items())),
        "cold_start_evaluation_constraint": {
            "status": "REQUIRED",
            "reason": (
                "Student-disjoint validation/test memberships provide no response rows for "
                "fitting per-student NCDM/ORCDF embeddings."
            ),
            "allowed_policy": (
                "Neutral zero-history held-out embeddings only; do not use validation/test "
                "responses for per-student calibration without a new Owner-approved protocol."
            ),
        },
    }
    manifest = {
        "dataset_name": "JUNYI_MID_MODEL_V1",
        "version": "1.0.0-model0",
        "parent_dataset": "JUNYI_MID",
        "source_type": "THIRD_PARTY_PROCESSED",
        "content_integrity_evidence": (
            "No content digest is present because the governing repository rule forbids it. "
            "Review uses fixed paths, exact schemas, exact member-list comparison, and measured counts."
        ),
        "source_artifacts": {
            "medium_root": "data-pipeline/.data/generated/medium",
            "parent_manifest": "data-pipeline/manifests/junyi_mid_v1.json",
        },
        "frozen_split": split_evidence,
        "derivation_rules": {
            "exact_duplicate": config["exact_duplicate_policy"],
            "unknown_exercise": config["unknown_exercise_policy"],
            "unmapped_topic": config["unmapped_topic_policy"],
            "test_membership_changed": False,
        },
        "derived_local_artifacts": {
            "root": "model-service/experiments/model0/data/junyi_mid_model_v1",
            "split_tables": {name: path.name for name, path in split_paths.items()},
            "q_matrix": q_matrix_path.name,
            "student_index_map": "student_index_map.json",
            "exercise_index_map": "exercise_index_map.json",
            "topic_index_map": "topic_index_map.json",
            "split_membership_copy": copied_membership_path.name,
            "eligible_interactions": raw_eligible_path.name,
            "git_tracking": "ignored",
        },
        "measurements": measurements,
        "model_evaluation_boundary": measurements["cold_start_evaluation_constraint"],
        "status": "PASS_WITH_COLD_START_LIMITATION",
    }
    write_json(manifest_path, manifest)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(make_markdown(measurements), encoding="utf-8", newline="\n")
    return manifest


def parse_args() -> argparse.Namespace:
    repository_root = Path(__file__).resolve().parents[4]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--medium-root",
        type=Path,
        default=repository_root / "data-pipeline/.data/generated/medium",
    )
    parser.add_argument(
        "--derived-root",
        type=Path,
        default=repository_root / "model-service/experiments/model0/data/junyi_mid_model_v1",
    )
    parser.add_argument(
        "--manifest-path",
        type=Path,
        default=repository_root / "model-service/experiments/model0/manifests/junyi_mid_model_v1.json",
    )
    parser.add_argument(
        "--report-path",
        type=Path,
        default=repository_root / "model-service/experiments/model0/reports/preflight.md",
    )
    parser.add_argument(
        "--config-path",
        type=Path,
        default=repository_root / "model-service/experiments/model0/configs/preflight.json",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    manifest = run_preflight(
        medium_root=args.medium_root,
        derived_root=args.derived_root,
        manifest_path=args.manifest_path,
        report_path=args.report_path,
        config_path=args.config_path,
    )
    print(
        "Preflight completed: "
        f"{manifest['dataset_name']} / {manifest['status']} / "
        f"retained={manifest['measurements']['interactions']['retained_q_eligible_rows']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

