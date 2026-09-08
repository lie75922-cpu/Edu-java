"""Audit MODEL-1 concepts and freeze a fresh external Junyi holdout.

The membership-selection pass deliberately reads only raw user identity,
Exercise, and timestamp fields.  Outcome labels are not parsed until the fixed
member list already exists and are then used only to validate and materialise
the selected members' model rows.
"""

from __future__ import annotations

import argparse
import csv
import json
import random
from collections import Counter, defaultdict, deque
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

from model1_common import CONFIG_ROOT, DERIVED_ROOT, MANIFEST_ROOT, REPORT_ROOT, read_json, write_json, write_text


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
MISSING_VALUES = {"", "n/a", "na", "none", "null"}
DATASET_NAME = "JUNYI_EXTERNAL_HOLDOUT_V1"
DERIVED_DATASET = "junyi_external_holdout_v1"


def normalise(value: Any) -> str:
    return value.strip() if isinstance(value, str) else ""


def is_missing(value: Any) -> bool:
    return normalise(value).casefold() in MISSING_VALUES


def parse_timestamp(value: Any) -> int | None:
    try:
        parsed = int(normalise(value))
    except ValueError:
        return None
    return parsed if parsed >= 0 else None


def parse_correct(value: Any) -> int | None:
    normalized = normalise(value).casefold()
    if normalized == "true":
        return 1
    if normalized == "false":
        return 0
    return None


def iter_jsonl(path: Path) -> Iterable[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                raise ValueError(f"Blank JSONL row at {path}:{line_number}")
            value = json.loads(line)
            if not isinstance(value, dict):
                raise ValueError(f"Non-object JSONL row at {path}:{line_number}")
            yield value


def read_csv_rows(path: Path, expected_columns: Sequence[str]) -> Iterable[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != list(expected_columns):
            raise ValueError(f"Unexpected CSV header at {path}: {reader.fieldnames}")
        for row_number, row in enumerate(reader, start=2):
            if None in row:
                raise ValueError(f"Malformed CSV row at {path}:{row_number}")
            yield {column: normalise(row[column]) for column in expected_columns}


def iter_membership_selection_fields(problem_log: Path) -> Iterable[tuple[str, str, str]]:
    """Yield only the three pre-registered fields used to select members."""

    with problem_log.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != RAW_LOG_COLUMNS:
            raise ValueError(f"Unexpected CSV header at {problem_log}: {reader.fieldnames}")
        for row_number, row in enumerate(reader, start=2):
            if None in row:
                raise ValueError(f"Malformed CSV row at {problem_log}:{row_number}")
            yield (
                normalise(row["user_id"]),
                normalise(row["exercise"]),
                normalise(row["time_done"]),
            )


def load_original_medium_membership(path: Path) -> set[str]:
    payload = read_json(path)
    if set(payload) != {"train", "valid", "test"}:
        raise ValueError("Original Medium split membership must contain train, valid, and test")
    expected = {"train": 7000, "valid": 1000, "test": 2000}
    all_members: list[str] = []
    for split, required_count in expected.items():
        members = payload[split]
        if not isinstance(members, list) or len(members) != required_count:
            raise ValueError(f"Original Medium {split} membership has an unexpected count")
        if not all(isinstance(member, str) and member for member in members):
            raise ValueError(f"Original Medium {split} membership contains an invalid member")
        all_members.extend(members)
    if len(set(all_members)) != 10000:
        raise ValueError("Original Medium membership has an overlap or duplicate")
    return set(all_members)


def audit_raw_metadata(path: Path) -> tuple[dict[str, list[dict[str, str]]], dict[str, Any], list[tuple[str, str]]]:
    grouped: dict[str, list[dict[str, str]]] = defaultdict(list)
    records = list(read_csv_rows(path, RAW_METADATA_COLUMNS))
    for record in records:
        identifier = record["name"]
        if identifier:
            grouped[identifier].append(record)
    duplicate_ids = sorted(identifier for identifier, rows in grouped.items() if len(rows) > 1)
    unique_ids = {identifier for identifier, rows in grouped.items() if len(rows) == 1}
    parsed_edges: list[tuple[str, str]] = []
    raw_edge_rows = 0
    duplicate_token_rows = 0
    dangling_tokens = 0
    ambiguous_tokens = 0
    for identifier in sorted(unique_ids):
        record = grouped[identifier][0]
        tokens = [token.strip() for token in record["prerequisites"].split(",") if token.strip()]
        raw_edge_rows += len(tokens)
        counts = Counter(tokens)
        duplicate_token_rows += sum(count > 1 for count in counts.values())
        for token in tokens:
            if token not in grouped:
                dangling_tokens += 1
            elif token not in unique_ids:
                ambiguous_tokens += 1
            else:
                parsed_edges.append((token, identifier))
    topics = sorted({record["topic"] for record in records if not is_missing(record["topic"])})
    audit = {
        "metadata_rows": len(records),
        "raw_unique_exercise_external_ids": len(grouped),
        "identity_unambiguous_exercise_external_ids": len(unique_ids),
        "duplicate_external_id_count": len(duplicate_ids),
        "duplicate_external_ids": duplicate_ids,
        "missing_topic_rows": sum(is_missing(record["topic"]) for record in records),
        "missing_area_rows": sum(is_missing(record["area"]) for record in records),
        "topic_count_excluding_missing": len(topics),
        "topics": topics,
        "prerequisite_raw_edge_rows": raw_edge_rows,
        "duplicate_prerequisite_token_rows": duplicate_token_rows,
        "dangling_prerequisite_tokens": dangling_tokens,
        "ambiguous_prerequisite_tokens": ambiguous_tokens,
    }
    return grouped, audit, parsed_edges


def graph_statistics(nodes: set[str], edges: Sequence[tuple[str, str]]) -> dict[str, Any]:
    edge_set = set(edges)
    directed: dict[str, set[str]] = {node: set() for node in nodes}
    reverse: dict[str, set[str]] = {node: set() for node in nodes}
    undirected: dict[str, set[str]] = {node: set() for node in nodes}
    for source, target in edge_set:
        if source not in nodes or target not in nodes:
            continue
        directed[source].add(target)
        reverse[target].add(source)
        undirected[source].add(target)
        undirected[target].add(source)
    components: list[set[str]] = []
    remaining = set(nodes)
    while remaining:
        start = min(remaining)
        component = {start}
        queue: deque[str] = deque([start])
        remaining.remove(start)
        while queue:
            current = queue.popleft()
            for neighbor in undirected[current]:
                if neighbor in remaining:
                    remaining.remove(neighbor)
                    component.add(neighbor)
                    queue.append(neighbor)
        components.append(component)

    visited: set[str] = set()
    finish: list[str] = []
    for start in sorted(nodes):
        if start in visited:
            continue
        stack: list[tuple[str, bool]] = [(start, False)]
        while stack:
            current, exiting = stack.pop()
            if exiting:
                finish.append(current)
                continue
            if current in visited:
                continue
            visited.add(current)
            stack.append((current, True))
            for neighbor in sorted(directed[current], reverse=True):
                if neighbor not in visited:
                    stack.append((neighbor, False))
    reverse_visited: set[str] = set()
    strongly_connected: list[set[str]] = []
    for start in reversed(finish):
        if start in reverse_visited:
            continue
        component: set[str] = set()
        stack = [start]
        reverse_visited.add(start)
        while stack:
            current = stack.pop()
            component.add(current)
            for neighbor in reverse[current]:
                if neighbor not in reverse_visited:
                    reverse_visited.add(neighbor)
                    stack.append(neighbor)
        strongly_connected.append(component)
    cyclic_components = [
        component
        for component in strongly_connected
        if len(component) > 1 or any((node, node) in edge_set for node in component)
    ]
    largest_component = max((len(component) for component in components), default=0)
    return {
        "nodes": len(nodes),
        "raw_edge_rows": len(edges),
        "unique_edges": len(edge_set),
        "duplicate_edge_rows": len(edges) - len(edge_set),
        "self_loop_count": sum(source == target for source, target in edge_set),
        "connected_components": len(components),
        "largest_component_size": largest_component,
        "largest_component_ratio": largest_component / len(nodes) if nodes else 0.0,
        "isolated_nodes": sum(not undirected[node] for node in nodes),
        "cyclic_strongly_connected_components": len(cyclic_components),
    }


def load_medium_topic_scope(medium_root: Path) -> tuple[dict[str, str], set[str], dict[str, Any]]:
    topic_by_exercise: dict[str, str] = {}
    for record in iter_jsonl(medium_root / "exercises.jsonl"):
        if set(record) != MEDIUM_EXERCISE_FIELDS:
            raise ValueError("Frozen Medium exercise schema differs from the audited MODEL-0 input")
        exercise = record["exercise_external_id"]
        topic = record["topic"]
        if not isinstance(exercise, str) or not exercise or not isinstance(topic, str) or is_missing(topic):
            raise ValueError("Frozen Medium has an invalid Exercise-to-Topic record")
        if exercise in topic_by_exercise:
            raise ValueError("Frozen Medium has duplicate Exercise metadata identity")
        topic_by_exercise[exercise] = topic.strip()
    observed: set[str] = set()
    source_rows = 0
    for record in iter_jsonl(medium_root / "interactions.jsonl"):
        if set(record) != MEDIUM_INTERACTION_FIELDS:
            raise ValueError("Frozen Medium interaction schema differs from the audited MODEL-0 input")
        source_rows += 1
        exercise = record["exercise_external_id"]
        if not isinstance(exercise, str) or not exercise:
            raise ValueError("Frozen Medium has an invalid interaction Exercise")
        if exercise in topic_by_exercise:
            observed.add(exercise)
    topics = sorted(set(topic_by_exercise.values()))
    observed_topics = sorted({topic_by_exercise[exercise] for exercise in observed})
    evidence = {
        "medium_exercise_metadata_rows": len(topic_by_exercise),
        "medium_source_interaction_rows": source_rows,
        "medium_observed_exercise_external_ids": len(observed),
        "q_eligible_exercise_external_ids": len(observed),
        "one_to_one_exercise_topic_coverage": len(observed),
        "topic_count": len(topics),
        "topics": topics,
        "topics_represented_by_current_eligible_exercises": len(observed_topics),
        "topics_not_represented_by_current_eligible_exercises": sorted(set(topics) - set(observed_topics)),
    }
    return topic_by_exercise, observed, evidence


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
    raise ValueError(f"Length below eligibility minimum cannot be stratified: {length}")


def select_stratified_members(counts: Mapping[str, int], target: int, seed: int) -> tuple[list[str], list[dict[str, Any]]]:
    if not counts:
        return [], []
    strata: dict[str, list[str]] = defaultdict(list)
    for student, count in counts.items():
        strata[stratum_name(count)].append(student)
    order = ["20-49", "50-99", "100-199", "200-499", "500-plus"]
    population = len(counts)
    selected_target = min(target, population)
    quotas: dict[str, int] = {}
    remainders: list[tuple[float, int, str]] = []
    allocated = 0
    for index, name in enumerate(order):
        members = strata[name]
        desired = selected_target * len(members) / population
        quota = min(len(members), int(desired))
        quotas[name] = quota
        allocated += quota
        remainders.append((desired - quota, index, name))
    for _, _, name in sorted(remainders, key=lambda item: (-item[0], item[1])):
        if allocated >= selected_target:
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
                "minimum_sequence_length": min((counts[item] for item in candidates), default=None),
                "maximum_sequence_length": max((counts[item] for item in candidates), default=None),
            }
        )
    if len(selected) != selected_target or len(set(selected)) != selected_target:
        raise AssertionError("Stratified selection did not produce the required unique target count")
    return sorted(selected), evidence


def scan_external_candidate_counts(
    problem_log: Path,
    original_medium_members: set[str],
    eligible_exercises: set[str],
) -> tuple[Counter[str], dict[str, int]]:
    counts: Counter[str] = Counter()
    source_rows = 0
    scope_rows = 0
    excluded_medium_rows = 0
    invalid_timestamp_rows = 0
    empty_student_rows = 0
    # Deliberate selection boundary: this loop receives no outcome field.
    for student, exercise, time_done in iter_membership_selection_fields(problem_log):
        source_rows += 1
        if exercise not in eligible_exercises:
            continue
        scope_rows += 1
        if not student:
            empty_student_rows += 1
            continue
        if student in original_medium_members:
            excluded_medium_rows += 1
            continue
        if parse_timestamp(time_done) is None:
            invalid_timestamp_rows += 1
            continue
        counts[student] += 1
    return counts, {
        "raw_problem_log_rows_scanned": source_rows,
        "current_eligible_exercise_scope_rows": scope_rows,
        "rows_excluded_for_original_medium_membership": excluded_medium_rows,
        "external_scope_rows_with_invalid_timestamp": invalid_timestamp_rows,
        "external_scope_rows_with_empty_student": empty_student_rows,
    }


def write_q_matrix(path: Path, exercise_count: int, concept_for_exercise: Mapping[int, int], concept_count: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        for exercise_id in range(exercise_count):
            row = [0] * concept_count
            row[concept_for_exercise[exercise_id]] = 1
            writer.writerow(row)


def write_interactions(path: Path, rows: Iterable[tuple[int, int, int, int]]) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(["student_id", "exercise_id", "topic_id", "correct"])
        for row in rows:
            writer.writerow(row)
            count += 1
    return count


def materialise_selected_rows(
    problem_log: Path,
    selected_members: set[str],
    eligible_exercises: set[str],
    student_index: Mapping[str, int],
    exercise_index: Mapping[str, int],
    topic_index: Mapping[str, int],
    topic_by_exercise: Mapping[str, str],
) -> tuple[dict[str, list[tuple[int, int, int, int]]], dict[str, Any]]:
    per_student: dict[str, list[tuple[int, int, int, int]]] = defaultdict(list)
    selected_scope_rows = 0
    invalid_selected_correct = 0
    invalid_selected_timestamp = 0
    for source_order, row in enumerate(read_csv_rows(problem_log, RAW_LOG_COLUMNS), start=1):
        student = row["user_id"]
        exercise = row["exercise"]
        if student not in selected_members or exercise not in eligible_exercises:
            continue
        selected_scope_rows += 1
        timestamp = parse_timestamp(row["time_done"])
        if timestamp is None:
            invalid_selected_timestamp += 1
            continue
        correct = parse_correct(row["correct"])
        if correct is None:
            invalid_selected_correct += 1
            continue
        per_student[student].append(
            (
                timestamp,
                source_order,
                exercise_index[exercise],
                topic_index[topic_by_exercise[exercise]],
                correct,
            )
        )
    if invalid_selected_timestamp or invalid_selected_correct:
        raise ValueError(
            "Selected membership has invalid timestamp or correct values; membership is not replaced or filtered. "
            f"invalid_timestamp={invalid_selected_timestamp}, invalid_correct={invalid_selected_correct}"
        )
    partitions: dict[str, list[tuple[int, int, int, int]]] = {"train": [], "valid": [], "test": []}
    sequence_lengths: list[int] = []
    for student in sorted(selected_members):
        sequence = sorted(per_student[student], key=lambda item: (item[0], item[1]))
        if len(sequence) < 20:
            raise ValueError(
                "Membership was selected with at least 20 timestamp-valid rows but cannot be materialised without label filtering; "
                "no replacement is permitted."
            )
        sequence_lengths.append(len(sequence))
        train_end = int(len(sequence) * 0.60)
        valid_end = train_end + int(len(sequence) * 0.20)
        if train_end < 1 or valid_end <= train_end or valid_end >= len(sequence):
            raise AssertionError("A >=20-row sequence did not yield three chronological partitions")
        student_id = student_index[student]
        for name, section in (
            ("train", sequence[:train_end]),
            ("valid", sequence[train_end:valid_end]),
            ("test", sequence[valid_end:]),
        ):
            partitions[name].extend(
                (student_id, exercise_id, topic_id, correct)
                for _, _, exercise_id, topic_id, correct in section
            )
    return partitions, {
        "selected_scope_rows": selected_scope_rows,
        "valid_binary_label_rows": sum(sequence_lengths),
        "sequence_length_minimum": min(sequence_lengths),
        "sequence_length_median": float(sorted(sequence_lengths)[len(sequence_lengths) // 2]),
        "sequence_length_maximum": max(sequence_lengths),
    }


def numeric_pair_evidence(path: Path) -> dict[str, Any]:
    pairs: list[tuple[str, str]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            values = line.strip().split()
            if len(values) != 2:
                raise ValueError(f"Unexpected numeric-pair schema at {path}:{line_number}")
            pairs.append((values[0], values[1]))
    return {
        "rows": len(pairs),
        "first_column_distinct_numeric_ids": len({source for source, _ in pairs}),
        "second_column_distinct_numeric_ids": len({target for _, target in pairs}),
    }


def audit_reference_routes(reference_root: Path) -> dict[str, Any]:
    rcd_config = reference_root / "RCD/RCD/config.txt"
    rcd_values = []
    for line in rcd_config.read_text(encoding="utf-8").splitlines():
        parts = [part.strip() for part in line.split(",")]
        if len(parts) == 3 and all(part.isdigit() for part in parts):
            rcd_values.append(parts)
    if len(rcd_values) != 1:
        raise ValueError("RCD Junyi config is not a single cardinality line")
    rcd_cardinalities = [int(value) for value in rcd_values[0]]
    inscd_source = (reference_root / "InsCD/inscd/datahub/junyi734.py").read_text(encoding="utf-8")
    if "class Junyi734" not in inscd_source or "config.json" not in inscd_source:
        raise ValueError("InsCD Junyi734 adapter evidence is incomplete")
    gear_root = reference_root / "GEAR-CD/junyi/junyi"
    gear_q = numeric_pair_evidence(gear_root / "graph/k_from_e.txt")
    gear_directed = numeric_pair_evidence(gear_root / "graph/K_Directed.txt")
    return {
        "RCD": {
            "source": "local RCD/RCD/config.txt",
            "junyi_cardinalities": {
                "students": rcd_cardinalities[0],
                "exercises": rcd_cardinalities[1],
                "knowledge_concepts": rcd_cardinalities[2],
            },
            "external_id_mapping": "NOT_PRESENT_IN_LOCAL_REFERENCE; opaque numeric IDs are not mapped by numeric coincidence.",
            "status": "NOT_COMPARABLE_UNTIL_PROJECT_COMMON_GRAPH_IS_BUILT",
        },
        "InsCD": {
            "source": "local InsCD inscd/datahub/junyi734.py",
            "dataset_identifier": "Junyi734",
            "input_semantics": "The adapter downloads an archive, reads config.json, then loads response/Q arrays and archive-provided info fields.",
            "local_external_id_mapping": "NOT_PRESENT; the data archive is not materialised locally and no mapping is inferred.",
            "status": "REFERENCE_NCDM_SEMANTICS_AVAILABLE_BUT_JUNYI734_IDS_NOT_ALIGNED_TO_LOCAL_SCOPE",
        },
        "GEAR-CD": {
            "source": "local GEAR-CD preprocessed Junyi graph files",
            "exercise_concept_pair_file": gear_q,
            "directed_concept_pair_file": gear_directed,
            "external_id_mapping": "NOT_PRESENT_IN_PREPROCESSED_NUMERIC_FILES; no numeric-ID alignment is inferred.",
            "status": "SMOKE_OR_COMPATIBILITY_ONLY",
        },
    }


def make_catalog(
    eligible_exercises: set[str], topic_by_exercise: Mapping[str, str], topics: Sequence[str]
) -> dict[str, Any]:
    topic_index = {topic: index for index, topic in enumerate(topics)}
    concepts = [
        {
            "model_concept_id": concept_id,
            "exercise_external_id": exercise,
            "topic": topic_by_exercise[exercise],
            "topic_id": topic_index[topic_by_exercise[exercise]],
        }
        for concept_id, exercise in enumerate(sorted(eligible_exercises))
    ]
    return {
        "catalog_name": "JUNYI_MODEL_CONCEPT_CATALOG_V1",
        "model_concept_definition": "One current Q-eligible Exercise external ID maps to one sequential algorithm-layer ModelConcept. This does not change business KnowledgePoint or Question identities.",
        "candidate_model_concept_count": len(concepts),
        "topic_count": len(topics),
        "topic_column_order": list(topics),
        "model_concepts": concepts,
    }


def make_concept_audit_report(audit: Mapping[str, Any]) -> str:
    raw = audit["raw_metadata"]
    medium = audit["medium_scope"]
    full_graph = audit["raw_prerequisite_full_graph"]
    induced = audit["raw_prerequisite_induced_research_graph"]
    routes = audit["reference_routes"]
    return f"""# MODEL-1 Concept Provenance Audit

## Local Exercise identity and current scope

| Check | Value |
| --- | ---: |
| Raw metadata rows | {raw['metadata_rows']:,} |
| Raw unique Exercise external IDs | {raw['raw_unique_exercise_external_ids']:,} |
| Identity-unambiguous raw Exercises | {raw['identity_unambiguous_exercise_external_ids']:,} |
| Duplicate external IDs | {raw['duplicate_external_id_count']:,} |
| Missing Topic rows | {raw['missing_topic_rows']:,} |
| Raw non-missing Topics | {raw['topic_count_excluding_missing']:,} |
| Frozen Medium Exercise metadata rows | {medium['medium_exercise_metadata_rows']:,} |
| Medium observed Exercises | {medium['medium_observed_exercise_external_ids']:,} |
| Current Q-eligible Exercises | {medium['q_eligible_exercise_external_ids']:,} |
| One-to-one Exercise-to-Topic coverage | {medium['one_to_one_exercise_topic_coverage']:,} |
| Frozen Topic display columns | {medium['topic_count']:,} |
| Topics represented by current eligible Exercises | {medium['topics_represented_by_current_eligible_exercises']:,} |
| Fine ModelConcept candidates | {audit['catalog']['candidate_model_concept_count']:,} |

The tracked catalog is [`model_concept_catalog_v1.json`](../manifests/model_concept_catalog_v1.json).  It supplies the local, auditable `Exercise external ID -> model_concept_id` relation.  A fine ModelConcept remains an algorithm-layer identity and does not overwrite a business Topic KnowledgePoint.

## Raw prerequisite research graph

| Graph | Nodes | Unique edges | Raw edge rows | Self loops | Components | Cyclic SCCs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Identity-unambiguous raw Exercise view | {full_graph['nodes']:,} | {full_graph['unique_edges']:,} | {full_graph['raw_edge_rows']:,} | {full_graph['self_loop_count']:,} | {full_graph['connected_components']:,} | {full_graph['cyclic_strongly_connected_components']:,} |
| Current Q-eligible induced view | {induced['nodes']:,} | {induced['unique_edges']:,} | {induced['raw_edge_rows']:,} | {induced['self_loop_count']:,} | {induced['connected_components']:,} | {induced['cyclic_strongly_connected_components']:,} |

Prerequisite text is retained only as `RESEARCH_MODEL_GRAPH` evidence.  Self loops and cycles are reported rather than repaired, and this audit does not publish or modify any product graph.

## Reference Junyi concept semantics

| Route | Verified local evidence | Alignment result |
| --- | --- | --- |
| RCD | config declares {routes['RCD']['junyi_cardinalities']['students']} students / {routes['RCD']['junyi_cardinalities']['exercises']} exercises / {routes['RCD']['junyi_cardinalities']['knowledge_concepts']} knowledge concepts | `{routes['RCD']['status']}` |
| InsCD | `{routes['InsCD']['dataset_identifier']}` adapter loads archive-defined response/Q arrays | `{routes['InsCD']['status']}` |
| GEAR-CD | numeric Exercise-concept pair file: {routes['GEAR-CD']['exercise_concept_pair_file']['rows']} rows; distinct numeric IDs by file column: {routes['GEAR-CD']['exercise_concept_pair_file']['first_column_distinct_numeric_ids']} / {routes['GEAR-CD']['exercise_concept_pair_file']['second_column_distinct_numeric_ids']} | `{routes['GEAR-CD']['status']}` |

RCD and GEAR-CD local files use opaque numeric identifiers with no supplied external-ID relation to this repository's current Q-eligible Exercise scope.  No mapping is invented.  InsCD provides the NCDM reference semantics but its `Junyi734` archive identifiers are not locally auditable against the present 624-Exercise catalog.
"""


def make_holdout_report(manifest: Mapping[str, Any]) -> str:
    selection = manifest["selection"]
    rows = "\n".join(
        f"| {entry['stratum']} | {entry['eligible_students']:,} | {entry['selected_students']:,} | {entry['minimum_sequence_length'] if entry['minimum_sequence_length'] is not None else 'N/A'} | {entry['maximum_sequence_length'] if entry['maximum_sequence_length'] is not None else 'N/A'} |"
        for entry in selection["strata"]
    )
    split = manifest["chronological_partitions"]
    return f"""# MODEL-1 Fresh External Holdout Freeze

## Status

**{manifest['status']}** — membership was selected and frozen before any MODEL-1 model training.

## Membership boundary

| Check | Value |
| --- | --- |
| Target / frozen students | {selection['target_students']:,} / {manifest['student_membership']['student_count']:,} |
| Random seed | {selection['seed']} |
| Original Medium member overlap | {manifest['student_membership']['original_medium_overlap']} |
| Minimum eligible chronological interactions | {selection['minimum_eligible_chronological_interactions']} |
| `correct` used to select membership | {selection['correct_label_used']} |
| Local member list | `{manifest['student_membership']['local_member_list_path']}` (ignored; not uploaded) |

The selection pass used only anonymous user identity, the frozen eligible Exercise scope, and valid `time_done`; it did not inspect `correct`.  Binary correctness was checked only after the frozen list existed to materialise model rows.  An invalid selected label would fail preflight rather than replace a member.

## Sequence-length strata

| Eligible length stratum | Eligible students outside Medium | Frozen students | Min length | Max length |
| --- | ---: | ---: | ---: | ---: |
{rows}

## Chronological partitions

| Partition | Interactions |
| --- | ---: |
| train/history | {split['interaction_counts']['train']:,} |
| validation | {split['interaction_counts']['valid']:,} |
| final test | {split['interaction_counts']['test']:,} |

Each selected student sequence is ordered by `time_done`, with original source row as the tie-breaker: first 60% is train/history, next 20% is validation, and remaining 20% is final test.  The final test is not used to fit rates, models, mastery, or validation selection.
"""


def preflight(
    medium_root: Path,
    metadata: Path,
    problem_log: Path,
    reference_root: Path,
    config_path: Path = CONFIG_ROOT / "preflight.json",
    derived_root: Path = DERIVED_ROOT / DERIVED_DATASET,
    catalog_path: Path = MANIFEST_ROOT / "model_concept_catalog_v1.json",
    manifest_path: Path = MANIFEST_ROOT / "junyi_external_holdout_v1.json",
    concept_report_path: Path = REPORT_ROOT / "concept_audit.md",
    holdout_report_path: Path = REPORT_ROOT / "holdout_freeze.md",
) -> tuple[dict[str, Any], dict[str, Any]]:
    config = read_json(config_path)
    if config.get("dataset_name") != DATASET_NAME:
        raise ValueError("MODEL-1 preflight requires JUNYI_EXTERNAL_HOLDOUT_V1")
    if manifest_path.exists() or (derived_root / "frozen_membership.json").exists():
        raise RuntimeError("MODEL-1 external holdout already exists and must not be replaced")
    raw_grouped, raw_audit, raw_edges = audit_raw_metadata(metadata)
    medium_topic_map, eligible_exercises, medium_evidence = load_medium_topic_scope(medium_root)
    topics = medium_evidence["topics"]
    if len(topics) != 40:
        raise ValueError(f"Expected the frozen 40 Topic display layer, found {len(topics)}")
    if not eligible_exercises:
        raise ValueError("No observed Q-eligible Exercise is available")
    for exercise in eligible_exercises:
        raw_rows = raw_grouped.get(exercise, [])
        if len(raw_rows) != 1 or is_missing(raw_rows[0]["topic"]):
            raise ValueError("Current Q-eligible Exercise does not retain an unambiguous raw identity and Topic")
        if raw_rows[0]["topic"] != medium_topic_map[exercise]:
            raise ValueError("Current Medium Exercise-to-Topic mapping differs from raw metadata")
    catalog = make_catalog(eligible_exercises, medium_topic_map, topics)
    unambiguous_raw_nodes = {
        identifier for identifier, rows in raw_grouped.items() if len(rows) == 1
    }
    full_graph = graph_statistics(unambiguous_raw_nodes, raw_edges)
    induced_edges = [
        (source, target)
        for source, target in raw_edges
        if source in eligible_exercises and target in eligible_exercises
    ]
    induced_graph = graph_statistics(set(eligible_exercises), induced_edges)
    reference_routes = audit_reference_routes(reference_root)
    concept_audit = {
        "status": "PASS",
        "raw_metadata": raw_audit,
        "medium_scope": medium_evidence,
        "catalog": {
            "candidate_model_concept_count": catalog["candidate_model_concept_count"],
            "topic_count": catalog["topic_count"],
        },
        "raw_prerequisite_full_graph": full_graph,
        "raw_prerequisite_induced_research_graph": induced_graph,
        "reference_routes": reference_routes,
        "published_graph_changed": False,
    }
    write_json(catalog_path, catalog)
    write_text(concept_report_path, make_concept_audit_report({**concept_audit, "catalog": catalog}))

    original_medium_members = load_original_medium_membership(medium_root / "split_membership.json")
    candidate_counts, source_scan = scan_external_candidate_counts(
        problem_log, original_medium_members, eligible_exercises
    )
    minimum = int(config["minimum_eligible_chronological_interactions"])
    eligible_counts = {
        student: count for student, count in candidate_counts.items() if count >= minimum
    }
    selected_members, strata = select_stratified_members(
        eligible_counts, int(config["target_students"]), int(config["seed"])
    )
    if not selected_members:
        raise ValueError("No external student satisfies the frozen eligibility rule")
    student_index = {student: index for index, student in enumerate(selected_members)}
    exercise_index = {
        concept["exercise_external_id"]: int(concept["model_concept_id"])
        for concept in catalog["model_concepts"]
    }
    topic_index = {topic: index for index, topic in enumerate(topics)}
    membership_payload = {
        "dataset_name": DATASET_NAME,
        "members": selected_members,
        "selection_seed": int(config["seed"]),
        "selected_before_model_training": True,
    }
    derived_root.mkdir(parents=True, exist_ok=True)
    write_json(derived_root / "frozen_membership.json", membership_payload)
    if read_json(derived_root / "frozen_membership.json") != membership_payload:
        raise AssertionError("Local frozen member list differs after write")
    write_json(derived_root / "student_index_map.json", student_index)
    write_json(derived_root / "exercise_index_map.json", exercise_index)
    write_json(derived_root / "topic_index_map.json", topic_index)
    write_q_matrix(
        derived_root / "q_c40_topic.csv",
        len(exercise_index),
        {exercise_index[exercise]: topic_index[medium_topic_map[exercise]] for exercise in exercise_index},
        len(topic_index),
    )
    write_q_matrix(
        derived_root / "q_fine_exercise.csv",
        len(exercise_index),
        {exercise_id: exercise_id for exercise_id in range(len(exercise_index))},
        len(exercise_index),
    )
    partitions, materialisation = materialise_selected_rows(
        problem_log,
        set(selected_members),
        eligible_exercises,
        student_index,
        exercise_index,
        topic_index,
        medium_topic_map,
    )
    partition_counts = {
        partition: write_interactions(derived_root / f"interactions_{partition}.csv", rows)
        for partition, rows in partitions.items()
    }
    if sum(partition_counts.values()) != materialisation["valid_binary_label_rows"]:
        raise AssertionError("Chronological partitions do not cover all selected materialised rows")
    if set(selected_members) & original_medium_members:
        raise AssertionError("Frozen external membership overlaps the original Medium members")
    manifest = {
        "dataset_name": DATASET_NAME,
        "version": "1.0.0-model1",
        "status": "FROZEN_BEFORE_MODEL_TRAINING",
        "source_boundary": config["source_boundary"],
        "concept_catalog": {
            "path": "model-service/experiments/model1/manifests/model_concept_catalog_v1.json",
            "candidate_model_concept_count": len(exercise_index),
            "topic_count": len(topic_index),
        },
        "student_membership": {
            "student_count": len(selected_members),
            "original_medium_student_count": len(original_medium_members),
            "original_medium_overlap": len(set(selected_members) & original_medium_members),
            "local_member_list_path": "model-service/experiments/model1/data/junyi_external_holdout_v1/frozen_membership.json",
            "tracked_by_git": False,
            "selected_before_model_training": True,
        },
        "selection": {
            "seed": int(config["seed"]),
            "target_students": int(config["target_students"]),
            "minimum_eligible_chronological_interactions": minimum,
            "correct_label_used": False,
            "fields_used": config["membership_selection"]["fields_used"],
            "eligible_external_students": len(eligible_counts),
            "candidate_students_with_any_eligible_timestamp": len(candidate_counts),
            "strata": strata,
            "policy": config["membership_selection"],
            "source_scan": source_scan,
        },
        "chronological_partitions": {
            "ordering": config["chronological_protocol"]["ordering"],
            "split": config["chronological_protocol"]["split"],
            "future_leakage": config["chronological_protocol"]["future_leakage"],
            "interaction_counts": partition_counts,
            "materialisation": materialisation,
        },
        "derived_local_artifacts": {
            "root": "model-service/experiments/model1/data/junyi_external_holdout_v1",
            "tracked_by_git": False,
            "files": [
                "frozen_membership.json",
                "student_index_map.json",
                "exercise_index_map.json",
                "topic_index_map.json",
                "q_c40_topic.csv",
                "q_fine_exercise.csv",
                "interactions_train.csv",
                "interactions_valid.csv",
                "interactions_test.csv",
            ],
        },
        "product_boundaries": {
            "java_mastery_provider_changed": False,
            "rule_mastery_changed": False,
            "recommendation_changed": False,
            "published_graph_changed": False,
        },
    }
    write_json(manifest_path, manifest)
    write_text(holdout_report_path, make_holdout_report(manifest))
    return concept_audit, manifest


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--medium-root", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--problem-log", required=True, type=Path)
    parser.add_argument("--reference-root", required=True, type=Path)
    parser.add_argument("--config", type=Path, default=CONFIG_ROOT / "preflight.json")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    concept_audit, manifest = preflight(
        args.medium_root,
        args.metadata,
        args.problem_log,
        args.reference_root,
        args.config,
    )
    print(
        "MODEL-1 preflight completed: "
        f"concept_audit={concept_audit['status']}; "
        f"holdout={manifest['status']}; "
        f"students={manifest['student_membership']['student_count']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
