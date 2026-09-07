"""DATA-0 evidence audit for the Junyi research-data domain.

The audit uses only the standard library. It deliberately does not create a
content digest; provenance is represented with explicit locations, sizes,
schemas, deterministic selection controls, and retained member lists.
"""

from __future__ import annotations

import argparse
import csv
import ctypes
import json
import math
import os
import platform
import random
import sqlite3
import statistics
import sys
import time
from array import array
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, Iterable, Iterator, Mapping, Sequence


MISSING_MARKERS = {"", "n/a", "na", "null", "none"}
BOOLEAN_VALUES = {"true": True, "false": False}
EXERCISE_COLUMNS = [
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
RELATION_COLUMNS = [
    "Exercise_A",
    "Exercise_B",
    "Similarity_avg",
    "Similarity_raw",
    "Difficulty_avg",
    "Difficulty_raw",
    "Prerequisite_avg",
    "Prerequisite_raw",
]
LOG_COLUMNS = [
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
EXERCISE_PARSE_TYPES = {
    "name": "text identifier",
    "live": "boolean",
    "prerequisites": "raw comma-delimited text",
    "h_position": "integer",
    "v_position": "integer",
    "creation_date": "timestamp text",
    "seconds_per_fast_problem": "integer",
    "pretty_display_name": "text",
    "short_display_name": "text",
    "topic": "text category",
    "area": "text category",
}
RELATION_PARSE_TYPES = {
    "Exercise_A": "text identifier",
    "Exercise_B": "text identifier",
    "Similarity_avg": "floating-point score",
    "Similarity_raw": "underscore-delimited score text",
    "Difficulty_avg": "floating-point score",
    "Difficulty_raw": "underscore-delimited score text",
    "Prerequisite_avg": "floating-point score",
    "Prerequisite_raw": "underscore-delimited score text",
}
LOG_PARSE_TYPES = {
    "user_id": "integer identifier",
    "exercise": "text identifier",
    "problem_type": "text category",
    "problem_number": "integer",
    "topic_mode": "boolean",
    "suggested": "boolean",
    "review_mode": "boolean",
    "time_done": "microsecond timestamp",
    "time_taken": "floating-point seconds",
    "time_taken_attempts": "raw attempt-duration text",
    "correct": "boolean",
    "count_attempts": "integer",
    "hint_used": "boolean",
    "count_hints": "integer",
    "hint_time_taken_list": "raw hint-duration text",
    "earned_proficiency": "boolean",
    "points_earned": "floating-point score",
}


def normalise(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, list):
        return "|".join(str(item) for item in value)
    return str(value).strip()


def is_missing(value: Any) -> bool:
    return normalise(value).casefold() in MISSING_MARKERS


def parse_bool(value: Any) -> bool | None:
    return BOOLEAN_VALUES.get(normalise(value).casefold())


def parse_int(value: Any) -> int | None:
    text = normalise(value)
    if not text or "." in text:
        return None
    try:
        return int(text)
    except ValueError:
        return None


def parse_float(value: Any) -> float | None:
    text = normalise(value)
    if not text:
        return None
    try:
        result = float(text)
    except ValueError:
        return None
    return result if math.isfinite(result) else None


def parse_timestamp_us(value: Any) -> int | None:
    result = parse_int(value)
    if result is None:
        return None
    if result < 946684800000000 or result > 4102444800000000:
        return None
    return result


def timestamp_to_iso(value: int) -> str:
    return datetime.fromtimestamp(value / 1_000_000, tz=UTC).isoformat().replace(
        "+00:00", "Z"
    )


def quantile(values: Sequence[int | float], probability: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * probability
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return float(ordered[lower])
    return float(
        ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)
    )


def distribution(values: Iterable[int | float]) -> dict[str, float | int | None]:
    collected = list(values)
    if not collected:
        return {
            "count": 0,
            "min": None,
            "p10": None,
            "p25": None,
            "p50": None,
            "p75": None,
            "p90": None,
            "p95": None,
            "p99": None,
            "max": None,
            "mean": None,
        }
    return {
        "count": len(collected),
        "min": min(collected),
        "p10": quantile(collected, 0.10),
        "p25": quantile(collected, 0.25),
        "p50": quantile(collected, 0.50),
        "p75": quantile(collected, 0.75),
        "p90": quantile(collected, 0.90),
        "p95": quantile(collected, 0.95),
        "p99": quantile(collected, 0.99),
        "max": max(collected),
        "mean": statistics.fmean(collected),
    }


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.rstrip() + "\n", encoding="utf-8")


def file_info(path: Path) -> dict[str, Any]:
    stat = path.stat()
    return {
        "path": str(path),
        "size_bytes": stat.st_size,
        "last_modified_utc": datetime.fromtimestamp(stat.st_mtime, tz=UTC)
        .isoformat()
        .replace("+00:00", "Z"),
    }


def directory_size(path: Path) -> int:
    return sum(item.stat().st_size for item in path.rglob("*") if item.is_file())


def peak_working_set_bytes() -> int | None:
    if os.name != "nt":
        return None

    class MemoryCounters(ctypes.Structure):
        _fields_ = [
            ("cb", ctypes.c_ulong),
            ("page_fault_count", ctypes.c_ulong),
            ("peak_working_set", ctypes.c_size_t),
            ("working_set", ctypes.c_size_t),
            ("quota_peak_paged", ctypes.c_size_t),
            ("quota_paged", ctypes.c_size_t),
            ("quota_peak_nonpaged", ctypes.c_size_t),
            ("quota_nonpaged", ctypes.c_size_t),
            ("pagefile", ctypes.c_size_t),
            ("peak_pagefile", ctypes.c_size_t),
            ("private_usage", ctypes.c_size_t),
        ]

    # Declare the Win32 signatures explicitly.  ctypes otherwise assumes a
    # 32-bit return value, which silently loses the process handle on 64-bit
    # Windows and turns an observable peak into an unhelpful N/A report.
    from ctypes import wintypes

    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    psapi = ctypes.WinDLL("psapi", use_last_error=True)
    kernel32.GetCurrentProcess.argtypes = []
    kernel32.GetCurrentProcess.restype = wintypes.HANDLE
    psapi.GetProcessMemoryInfo.argtypes = [
        wintypes.HANDLE,
        ctypes.POINTER(MemoryCounters),
        wintypes.DWORD,
    ]
    psapi.GetProcessMemoryInfo.restype = wintypes.BOOL

    counters = MemoryCounters()
    counters.cb = ctypes.sizeof(counters)
    success = psapi.GetProcessMemoryInfo(
        kernel32.GetCurrentProcess(), ctypes.byref(counters), counters.cb
    )
    return int(counters.peak_working_set) if success else None


@dataclass
class CardinalityTracker:
    limit: int = 2_000_000
    values: set[str] | None = None
    capped: bool = False

    def __post_init__(self) -> None:
        self.values = set()

    def add(self, value: Any) -> None:
        if self.capped:
            return
        assert self.values is not None
        self.values.add(normalise(value))
        if len(self.values) >= self.limit:
            self.values = None
            self.capped = True

    def report(self) -> dict[str, Any]:
        if self.capped:
            return {
                "status": "LOWER_BOUND",
                "lower_bound": self.limit,
                "reason": "memory-safe cardinality cap reached",
            }
        assert self.values is not None
        return {"status": "EXACT", "count": len(self.values)}


def read_csv(path: Path) -> tuple[list[str], Iterator[dict[str | None, Any]]]:
    handle = path.open("r", encoding="utf-8-sig", newline="")
    reader = csv.DictReader(handle)
    if reader.fieldnames is None:
        handle.close()
        raise ValueError(f"CSV has no header: {path}")

    def iterator() -> Iterator[dict[str | None, Any]]:
        try:
            yield from reader
        finally:
            handle.close()

    return list(reader.fieldnames), iterator()


def scan_small_csv(
    path: Path, expected_columns: Sequence[str], sample_path: Path
) -> dict[str, Any]:
    headers, rows = read_csv(path)
    records: list[dict[str, str]] = []
    missing = Counter()
    unique: dict[str, set[str]] = {column: set() for column in headers}
    malformed = 0
    for raw_row in rows:
        if None in raw_row:
            malformed += 1
        row = {column: normalise(raw_row.get(column)) for column in headers}
        records.append(row)
        for column, value in row.items():
            if is_missing(value):
                missing[column] += 1
            unique[column].add(value)
    write_json(sample_path, records[:10])
    return {
        "file": file_info(path),
        "headers": headers,
        "expected_columns_match": headers == list(expected_columns),
        "row_count": len(records),
        "column_count": len(headers),
        "missing": dict(missing),
        "unique_counts": {column: len(values) for column, values in unique.items()},
        "malformed_row_count": malformed,
        "samples_path": str(sample_path),
        "records": records,
    }


def find_cycle(component: set[str], adjacency: Mapping[str, set[str]]) -> list[str]:
    active: set[str] = set()
    visited: set[str] = set()
    stack: list[str] = []

    def visit(node: str) -> list[str] | None:
        visited.add(node)
        active.add(node)
        stack.append(node)
        for neighbour in sorted(adjacency[node]):
            if neighbour not in component:
                continue
            if neighbour in active:
                return stack[stack.index(neighbour) :] + [neighbour]
            if neighbour not in visited:
                result = visit(neighbour)
                if result:
                    return result
        stack.pop()
        active.remove(node)
        return None

    for node in sorted(component):
        if node not in visited:
            result = visit(node)
            if result:
                return result
    return []


def graph_metrics(
    nodes: Iterable[str], edges: Iterable[tuple[str, str]], directed: bool
) -> dict[str, Any]:
    node_set = {str(node) for node in nodes}
    raw_edges = [(str(source), str(target)) for source, target in edges]
    edge_set = set(raw_edges)
    for source, target in edge_set:
        node_set.add(source)
        node_set.add(target)
    adjacency = {node: set() for node in node_set}
    reverse = {node: set() for node in node_set}
    undirected = {node: set() for node in node_set}
    for source, target in edge_set:
        adjacency[source].add(target)
        reverse[target].add(source)
        undirected[source].add(target)
        undirected[target].add(source)
        if not directed:
            adjacency[target].add(source)
            reverse[source].add(target)

    components: list[set[str]] = []
    unseen = set(node_set)
    while unseen:
        root = min(unseen)
        unseen.remove(root)
        component: set[str] = set()
        stack = [root]
        while stack:
            current = stack.pop()
            component.add(current)
            for neighbour in undirected[current]:
                if neighbour in unseen:
                    unseen.remove(neighbour)
                    stack.append(neighbour)
        components.append(component)

    cycle_components: list[dict[str, Any]] = []
    if directed:
        index = 0
        stack: list[str] = []
        on_stack: set[str] = set()
        indices: dict[str, int] = {}
        low_links: dict[str, int] = {}

        def connect(node: str) -> None:
            nonlocal index
            indices[node] = index
            low_links[node] = index
            index += 1
            stack.append(node)
            on_stack.add(node)
            for neighbour in adjacency[node]:
                if neighbour not in indices:
                    connect(neighbour)
                    low_links[node] = min(low_links[node], low_links[neighbour])
                elif neighbour in on_stack:
                    low_links[node] = min(low_links[node], indices[neighbour])
            if low_links[node] != indices[node]:
                return
            component: set[str] = set()
            while True:
                current = stack.pop()
                on_stack.remove(current)
                component.add(current)
                if current == node:
                    break
            if len(component) > 1 or any(item in adjacency[item] for item in component):
                cycle_components.append(
                    {
                        "node_count": len(component),
                        "nodes": sorted(component),
                        "representative_cycle": find_cycle(component, adjacency),
                    }
                )

        for node in sorted(node_set):
            if node not in indices:
                connect(node)

    largest = max(components, key=len) if components else set()
    in_degrees = [len(reverse[node]) for node in node_set]
    out_degrees = [len(adjacency[node]) for node in node_set]
    loops = sorted(source for source, target in edge_set if source == target)
    isolated = sorted(
        node for node in node_set if not adjacency[node] and not reverse[node]
    )
    return {
        "directed": directed,
        "nodes": len(node_set),
        "edges": len(edge_set),
        "raw_edge_rows": len(raw_edges),
        "self_loop_count": len(loops),
        "self_loops": loops,
        "duplicate_edge_count": len(raw_edges) - len(edge_set),
        "components": len(components),
        "largest_component_size": len(largest),
        "largest_component_ratio": len(largest) / len(node_set) if node_set else 0.0,
        "isolated_nodes": len(isolated),
        "isolated_node_examples": isolated[:20],
        "in_degree": distribution(in_degrees),
        "out_degree": distribution(out_degrees),
        "cycle_component_count": len(cycle_components) if directed else None,
        "cycle_components": cycle_components[:20] if directed else [],
    }


def audit_exercise(scan: Mapping[str, Any]) -> dict[str, Any]:
    records: list[dict[str, str]] = list(scan["records"])
    names = Counter(
        record["name"] for record in records if not is_missing(record["name"])
    )
    unique_names = {name for name, count in names.items() if count == 1}
    duplicate_names = sorted(name for name, count in names.items() if count > 1)
    topics = Counter(
        record["topic"] for record in records if not is_missing(record["topic"])
    )
    areas = Counter(
        record["area"] for record in records if not is_missing(record["area"])
    )
    live = Counter(record["live"] for record in records)
    parsed_edges: list[tuple[str, str]] = []
    duplicate_tokens: list[dict[str, Any]] = []
    dangling_tokens: list[dict[str, Any]] = []
    ambiguous_tokens: list[dict[str, Any]] = []
    raw_edge_rows = 0
    source_ambiguous_rows = 0

    for row_number, record in enumerate(records, start=1):
        name = record["name"]
        raw_value = record["prerequisites"]
        if is_missing(raw_value):
            continue
        if name not in unique_names:
            source_ambiguous_rows += 1
            continue
        tokens = [part.strip() for part in raw_value.split(",") if part.strip()]
        token_counts = Counter(tokens)
        for token, count in token_counts.items():
            if count > 1:
                duplicate_tokens.append(
                    {"row_number": row_number, "exercise": name, "token": token, "count": count}
                )
        for token in tokens:
            raw_edge_rows += 1
            if token not in names:
                dangling_tokens.append(
                    {"row_number": row_number, "exercise": name, "token": token}
                )
            elif token not in unique_names:
                ambiguous_tokens.append(
                    {"row_number": row_number, "exercise": name, "token": token}
                )
            else:
                parsed_edges.append((token, name))

    return {
        "metadata_rows": len(records),
        "distinct_external_ids": len(names),
        "duplicate_external_id_count": len(duplicate_names),
        "duplicate_external_ids": duplicate_names,
        "unique_external_ids": len(unique_names),
        "live_distribution": dict(live),
        "topic_count_excluding_missing": len(topics),
        "area_count_excluding_missing": len(areas),
        "topic_missing_count": sum(is_missing(record["topic"]) for record in records),
        "area_missing_count": sum(is_missing(record["area"]) for record in records),
        "prerequisite_missing_count": sum(
            is_missing(record["prerequisites"]) for record in records
        ),
        "exercise_per_topic": dict(sorted(topics.items())),
        "exercise_per_area": dict(sorted(areas.items())),
        "prerequisite_raw_edge_rows": raw_edge_rows,
        "source_ambiguous_rows": source_ambiguous_rows,
        "duplicate_token_rows": duplicate_tokens,
        "dangling_tokens": dangling_tokens,
        "ambiguous_tokens": ambiguous_tokens,
        "parsed_edges": parsed_edges,
        "graph": graph_metrics(unique_names, parsed_edges, directed=True),
        "unique_names": sorted(unique_names),
        "records_by_name": {
            record["name"]: record
            for record in records
            if record["name"] in unique_names
        },
    }


def make_duplicate_store(work_dir: Path) -> sqlite3.Connection:
    work_dir.mkdir(parents=True, exist_ok=True)
    database = work_dir / "duplicate-audit.sqlite"
    if database.exists():
        database.unlink()
    connection = sqlite3.connect(database)
    connection.execute("PRAGMA journal_mode=OFF")
    connection.execute("PRAGMA synchronous=OFF")
    connection.execute("PRAGMA temp_store=MEMORY")
    connection.execute("PRAGMA cache_size=-262144")
    connection.execute("PRAGMA locking_mode=EXCLUSIVE")
    connection.execute("CREATE TABLE exact_row (value TEXT PRIMARY KEY)")
    connection.execute("CREATE TABLE business_key (value TEXT PRIMARY KEY)")
    return connection


def flush_duplicate_batches(
    connection: sqlite3.Connection,
    exact_batch: list[tuple[str]],
    business_batch: list[tuple[str]],
) -> tuple[int, int]:
    before = connection.total_changes
    connection.executemany("INSERT OR IGNORE INTO exact_row VALUES (?)", exact_batch)
    exact_added = connection.total_changes - before
    before = connection.total_changes
    connection.executemany(
        "INSERT OR IGNORE INTO business_key VALUES (?)", business_batch
    )
    business_added = connection.total_changes - before
    connection.commit()
    exact_batch.clear()
    business_batch.clear()
    return exact_added, business_added


def audit_problem_log(
    path: Path,
    sample_path: Path,
    known_exercises: set[str],
    scope_exercises: set[str],
    work_dir: Path,
) -> dict[str, Any]:
    headers, rows = read_csv(path)
    if headers != LOG_COLUMNS:
        raise ValueError(f"Unexpected ProblemLog header: {headers!r}")
    trackers = {column: CardinalityTracker() for column in headers}
    missing = Counter()
    invalid = Counter()
    samples: list[dict[str, str]] = []
    student_counts: Counter[str] = Counter()
    scope_student_counts: Counter[str] = Counter()
    exercise_counts: Counter[str] = Counter()
    referenced_exercises: set[str] = set()
    correct_counts = Counter()
    hint_counts = Counter()
    proficiency_counts = Counter()
    attempt_values = array("q")
    duration_values = array("d")
    malformed = 0
    unknown_exercise_rows = 0
    timestamp_min: int | None = None
    timestamp_max: int | None = None
    valid_timestamp_rows = 0
    exact_batch: list[tuple[str]] = []
    business_batch: list[tuple[str]] = []
    exact_unique = 0
    business_unique = 0
    connection = make_duplicate_store(work_dir)
    started = time.monotonic()
    row_count = 0
    try:
        for row_count, raw_row in enumerate(rows, start=1):
            if None in raw_row:
                malformed += 1
            row = {column: normalise(raw_row.get(column)) for column in headers}
            if len(samples) < 10:
                samples.append(row)
            for column, value in row.items():
                if is_missing(value):
                    missing[column] += 1
                trackers[column].add(value)

            exact_batch.append(
                (
                    json.dumps(
                        [row[column] for column in headers],
                        ensure_ascii=False,
                        separators=(",", ":"),
                    ),
                )
            )
            business_batch.append(
                (
                    json.dumps(
                        [
                            row["user_id"],
                            row["exercise"],
                            row["problem_number"],
                            row["time_done"],
                        ],
                        ensure_ascii=False,
                        separators=(",", ":"),
                    ),
                )
            )

            student = row["user_id"]
            exercise = row["exercise"]
            correct = parse_bool(row["correct"])
            timestamp = parse_timestamp_us(row["time_done"])
            attempts = parse_int(row["count_attempts"])
            duration = parse_float(row["time_taken"])
            hint_used = parse_bool(row["hint_used"])
            proficiency = parse_bool(row["earned_proficiency"])

            if student and exercise:
                student_counts[student] += 1
                exercise_counts[exercise] += 1
                referenced_exercises.add(exercise)
            if exercise and exercise not in known_exercises:
                unknown_exercise_rows += 1
            if correct is None:
                if not is_missing(row["correct"]):
                    invalid["correct"] += 1
            else:
                correct_counts[str(correct).lower()] += 1
            if timestamp is None:
                if not is_missing(row["time_done"]):
                    invalid["time_done"] += 1
            else:
                valid_timestamp_rows += 1
                timestamp_min = timestamp if timestamp_min is None else min(timestamp_min, timestamp)
                timestamp_max = timestamp if timestamp_max is None else max(timestamp_max, timestamp)
            if attempts is None:
                if not is_missing(row["count_attempts"]):
                    invalid["count_attempts"] += 1
            else:
                attempt_values.append(attempts)
                if attempts < 0:
                    invalid["negative_count_attempts"] += 1
            if duration is None:
                if not is_missing(row["time_taken"]):
                    invalid["time_taken"] += 1
            else:
                duration_values.append(duration)
                if duration < 0:
                    invalid["negative_time_taken"] += 1
            if hint_used is None:
                if not is_missing(row["hint_used"]):
                    invalid["hint_used"] += 1
            else:
                hint_counts[str(hint_used).lower()] += 1
            if proficiency is None:
                if not is_missing(row["earned_proficiency"]):
                    invalid["earned_proficiency"] += 1
            else:
                proficiency_counts[str(proficiency).lower()] += 1

            if student and exercise in scope_exercises and correct is not None and timestamp is not None:
                scope_student_counts[student] += 1

            if len(exact_batch) >= 100_000:
                exact_added, business_added = flush_duplicate_batches(
                    connection, exact_batch, business_batch
                )
                exact_unique += exact_added
                business_unique += business_added
            if row_count % 500_000 == 0:
                write_json(
                    work_dir / "progress.json",
                    {
                        "phase": "full_problem_log_audit",
                        "rows_completed": row_count,
                        "elapsed_seconds": round(time.monotonic() - started, 3),
                    },
                )
        if exact_batch:
            exact_added, business_added = flush_duplicate_batches(
                connection, exact_batch, business_batch
            )
            exact_unique += exact_added
            business_unique += business_added
    finally:
        connection.close()

    write_json(sample_path, samples)
    sequence_lengths = list(student_counts.values())
    scope_lengths = list(scope_student_counts.values())
    return {
        "file": file_info(path),
        "headers": headers,
        "expected_columns_match": headers == LOG_COLUMNS,
        "row_count": row_count,
        "column_count": len(headers),
        "missing": dict(missing),
        "unique_counts": {column: tracker.report() for column, tracker in trackers.items()},
        "malformed_row_count": malformed,
        "invalid_values": dict(invalid),
        "exact_duplicate_rows": row_count - exact_unique,
        "business_key_duplicate_rows": row_count - business_unique,
        "business_key_definition": ["user_id", "exercise", "problem_number", "time_done"],
        "student_count": len(student_counts),
        "referenced_exercise_count": len(referenced_exercises),
        "known_exercise_reference_rate": (
            (row_count - unknown_exercise_rows) / row_count if row_count else 0.0
        ),
        "unknown_exercise_rows": unknown_exercise_rows,
        "correct_distribution": dict(correct_counts),
        "attempt_distribution": distribution(attempt_values),
        "duration_distribution": distribution(duration_values),
        "hint_distribution": dict(hint_counts),
        "proficiency_distribution": dict(proficiency_counts),
        "timestamp_valid_rows": valid_timestamp_rows,
        "timestamp_invalid_rows": invalid.get("time_done", 0),
        "timestamp_min_utc": timestamp_to_iso(timestamp_min) if timestamp_min else None,
        "timestamp_max_utc": timestamp_to_iso(timestamp_max) if timestamp_max else None,
        "sequence_distribution": distribution(sequence_lengths),
        "sequence_below_thresholds": {
            str(threshold): sum(length < threshold for length in sequence_lengths)
            for threshold in (5, 10, 15, 20, 30)
        },
        "scope_student_count": len(scope_student_counts),
        "scope_sequence_distribution": distribution(scope_lengths),
        "student_counts": student_counts,
        "scope_student_counts": scope_student_counts,
        "exercise_counts": exercise_counts,
        "samples_path": str(sample_path),
    }


def audit_relation_annotations(
    scans: Mapping[str, Mapping[str, Any]], known_names: set[str]
) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for source_name, scan in scans.items():
        records = scan["records"]
        endpoints: set[str] = set()
        missing_endpoint_rows = 0
        dangling_endpoint_rows = 0
        scores: dict[str, Any] = {}
        for column in ("Similarity_avg", "Difficulty_avg", "Prerequisite_avg"):
            values = [
                value
                for value in (parse_float(record.get(column)) for record in records)
                if value is not None
            ]
            scores[column] = distribution(values)
        for record in records:
            left = record.get("Exercise_A", "")
            right = record.get("Exercise_B", "")
            if is_missing(left) or is_missing(right):
                missing_endpoint_rows += 1
                continue
            endpoints.update((left, right))
            if left not in known_names or right not in known_names:
                dangling_endpoint_rows += 1
        result[source_name] = {
            "row_count": len(records),
            "endpoint_count": len(endpoints),
            "missing_endpoint_rows": missing_endpoint_rows,
            "dangling_endpoint_rows": dangling_endpoint_rows,
            "score_distributions": scores,
            "graph_status": (
                "NOT_DERIVED: scalar annotation scores have no audited operational "
                "edge direction or threshold."
            ),
        }
    return result


def audit_pair_graph(path: Path, directed: bool) -> dict[str, Any]:
    edges: list[tuple[str, str]] = []
    malformed = 0
    with path.open("r", encoding="utf-8", newline="") as handle:
        for line in handle:
            parts = line.rstrip("\r\n").split("\t")
            if len(parts) != 2 or not parts[0] or not parts[1]:
                malformed += 1
                continue
            edges.append((parts[0], parts[1]))
    return {
        "file": file_info(path),
        "malformed_rows": malformed,
        "metrics": graph_metrics(
            {value for edge in edges for value in edge}, edges, directed=directed
        ),
    }


def stratum_index(value: int, boundaries: Sequence[float]) -> int:
    for index, boundary in enumerate(boundaries):
        if value <= boundary:
            return index
    return len(boundaries)


def deterministic_stratified_select(
    counts: Mapping[str, int], target: int, seed: int
) -> list[str]:
    if target <= 0 or not counts:
        return []
    values = list(counts.values())
    boundaries = [
        quantile(values, 0.25) or 0.0,
        quantile(values, 0.50) or 0.0,
        quantile(values, 0.75) or 0.0,
        quantile(values, 0.90) or 0.0,
    ]
    strata: dict[int, list[str]] = defaultdict(list)
    for student, count in counts.items():
        strata[stratum_index(count, boundaries)].append(student)
    population = len(counts)
    target = min(target, population)
    quotas: dict[int, int] = {}
    remainders: list[tuple[float, int]] = []
    allocated = 0
    for index in sorted(strata):
        desired = target * len(strata[index]) / population
        quota = min(len(strata[index]), math.floor(desired))
        quotas[index] = quota
        allocated += quota
        remainders.append((desired - quota, index))
    for _, index in sorted(remainders, key=lambda item: (-item[0], item[1])):
        if allocated >= target:
            break
        if quotas[index] < len(strata[index]):
            quotas[index] += 1
            allocated += 1
    selected: list[str] = []
    for index in sorted(strata):
        candidates = sorted(strata[index])
        random.Random(seed + index).shuffle(candidates)
        selected.extend(candidates[: quotas[index]])
    return selected


def nested_candidates(
    scope_counts: Mapping[str, int], seed: int
) -> tuple[dict[str, list[str]], dict[str, Any]]:
    scope_distribution = distribution(scope_counts.values())
    minimum_interactions = max(5, math.floor(scope_distribution["p25"] or 0.0))
    window_cap = max(1, math.floor(scope_distribution["p75"] or 0.0))
    eligible = {
        student: count
        for student, count in scope_counts.items()
        if count >= minimum_interactions
    }
    large = deterministic_stratified_select(eligible, 15_000, seed)
    large_counts = {student: eligible[student] for student in large}
    medium = deterministic_stratified_select(large_counts, 10_000, seed + 100)
    medium_counts = {student: large_counts[student] for student in medium}
    small = deterministic_stratified_select(medium_counts, 5_000, seed + 200)
    return (
        {"Small": small, "Medium": medium, "Large": large},
        {
            "scope_distribution_before_filter": scope_distribution,
            "minimum_interactions_formula": "max(5, floor(empirical P25))",
            "minimum_interactions_applied": minimum_interactions,
            "window_cap_formula": "max(1, floor(empirical P75))",
            "window_cap_applied": window_cap,
            "eligible_student_count": len(eligible),
        },
    )


def canonical_exercise(record: Mapping[str, str]) -> dict[str, Any]:
    return {
        "exercise_external_id": record["name"],
        "display_name": record["pretty_display_name"],
        "topic": record["topic"],
        "area": record["area"],
        "live": parse_bool(record["live"]),
        "prerequisite_raw": record["prerequisites"],
    }


def canonical_relation(source: str, target: str) -> dict[str, Any]:
    return {
        "source_external_id": source,
        "target_external_id": target,
        "relation_type": "PREREQUISITE_RAW_UNVERIFIED",
        "score": None,
        "source": "JUNYI_EXERCISE_METADATA",
        "verified": False,
    }


def canonical_interaction(row: Mapping[str, str], timestamp: int) -> dict[str, Any]:
    return {
        "student_external_id": row["user_id"],
        "exercise_external_id": row["exercise"],
        "correct": parse_bool(row["correct"]),
        "attempts": parse_int(row["count_attempts"]),
        "duration_seconds": parse_float(row["time_taken"]),
        "hint_used": parse_bool(row["hint_used"]),
        "count_hints": parse_int(row["count_hints"]),
        "earned_proficiency": parse_bool(row["earned_proficiency"]),
        "occurred_at": timestamp_to_iso(timestamp),
    }


def write_jsonl(path: Path, records: Iterable[Mapping[str, Any]]) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for record in records:
            handle.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")))
            handle.write("\n")
            count += 1
    return count


def materialise_candidates(
    log_path: Path,
    scope_exercises: set[str],
    candidates: Mapping[str, Sequence[str]],
    sampling_policy: Mapping[str, Any],
    exercise_records: Mapping[str, Mapping[str, str]],
    parsed_edges: Sequence[tuple[str, str]],
    scope_graph: Mapping[str, Any],
    generated_root: Path,
    seed: int,
    work_dir: Path,
) -> tuple[dict[str, Any], dict[str, Any]]:
    started = time.monotonic()
    selected_students = {
        student for students in candidates.values() for student in students
    }
    selected_by_candidate = {
        name: set(students) for name, students in candidates.items()
    }
    per_student: dict[str, list[tuple[int, int, dict[str, Any]]]] = defaultdict(list)
    headers, rows = read_csv(log_path)
    if headers != LOG_COLUMNS:
        raise ValueError("ProblemLog schema changed between audit and materialisation")
    for row_number, raw_row in enumerate(rows, start=1):
        row = {column: normalise(raw_row.get(column)) for column in headers}
        student = row["user_id"]
        if student not in selected_students or row["exercise"] not in scope_exercises:
            continue
        timestamp = parse_timestamp_us(row["time_done"])
        if timestamp is None or parse_bool(row["correct"]) is None:
            continue
        per_student[student].append(
            (timestamp, row_number, canonical_interaction(row, timestamp))
        )
        if row_number % 500_000 == 0:
            write_json(
                work_dir / "progress.json",
                {
                    "phase": "candidate_materialisation",
                    "rows_completed": row_number,
                    "selected_students_seen": len(per_student),
                },
            )
    window_cap = int(sampling_policy["window_cap_applied"])
    for records in per_student.values():
        records.sort(key=lambda item: (item[0], item[1]))

    relation_records = [
        canonical_relation(source, target)
        for source, target in parsed_edges
        if source in scope_exercises and target in scope_exercises
    ]
    exercise_payload = [
        canonical_exercise(exercise_records[name]) for name in sorted(scope_exercises)
    ]
    reports: dict[str, Any] = {}
    split_report: dict[str, Any] = {}
    for name, students in candidates.items():
        target = generated_root / name.lower()
        target.mkdir(parents=True, exist_ok=True)
        student_set = selected_by_candidate[name]
        selected_records = {
            student: per_student.get(student, [])[:window_cap] for student in student_set
        }
        all_records = [
            record
            for student in sorted(student_set)
            for _, _, record in selected_records[student]
        ]
        write_jsonl(target / "exercises.jsonl", exercise_payload)
        write_jsonl(target / "relationships.jsonl", relation_records)
        interaction_count = write_jsonl(target / "interactions.jsonl", all_records)
        sequence_lengths = [len(selected_records[student]) for student in student_set]
        reports[name] = {
            "students": len(student_set),
            "exercises": len(
                {
                    record["exercise_external_id"]
                    for record in all_records
                    if record["exercise_external_id"]
                }
            ),
            "interactions": interaction_count,
            "topics": len(
                {
                    exercise_records[exercise]["topic"]
                    for exercise in scope_exercises
                    if not is_missing(exercise_records[exercise]["topic"])
                }
            ),
            "areas": len(
                {
                    exercise_records[exercise]["area"]
                    for exercise in scope_exercises
                    if not is_missing(exercise_records[exercise]["area"])
                }
            ),
            "relation_edges": len(relation_records),
            "largest_component_ratio": scope_graph["largest_component_ratio"],
            "sequence_distribution_after_window": distribution(sequence_lengths),
            "materialised_path": str(target),
            "disk_size_bytes": directory_size(target),
            "materialisation_wall_time_seconds": round(time.monotonic() - started, 3),
            "peak_working_set_bytes": peak_working_set_bytes(),
        }
        if name == "Medium":
            ordered = sorted(student_set)
            random.Random(seed + 500).shuffle(ordered)
            train_end = math.floor(len(ordered) * 0.70)
            valid_end = train_end + math.floor(len(ordered) * 0.10)
            split = {
                "train": ordered[:train_end],
                "valid": ordered[train_end:valid_end],
                "test": ordered[valid_end:],
            }
            overlap = {
                "train_valid": len(set(split["train"]) & set(split["valid"])),
                "train_test": len(set(split["train"]) & set(split["test"])),
                "valid_test": len(set(split["valid"]) & set(split["test"])),
            }
            if any(overlap.values()):
                raise AssertionError(f"student split leakage: {overlap}")
            member_path = target / "split_membership.json"
            write_json(member_path, split)
            split_report = {
                "strategy": "student_level",
                "seed": seed + 500,
                "member_list_path": str(member_path),
                "counts": {key: len(value) for key, value in split.items()},
                "overlap": overlap,
                "frozen_before_model_results": True,
                "content_integrity_evidence": (
                    "No content digest produced. Exact anonymous member lists and "
                    "the deterministic seed remain in the ignored local output."
                ),
            }
    return reports, split_report


def canonical_schema() -> dict[str, Any]:
    return {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "title": "Edu-java DATA-0 Canonical Dataset v1",
        "type": "object",
        "required": ["exercise", "interaction", "relationship"],
        "properties": {
            "exercise": {
                "type": "object",
                "required": [
                    "exercise_external_id",
                    "display_name",
                    "topic",
                    "area",
                    "live",
                    "prerequisite_raw",
                ],
                "properties": {
                    "exercise_external_id": {"type": "string", "minLength": 1},
                    "display_name": {"type": "string"},
                    "topic": {"type": ["string", "null"]},
                    "area": {"type": ["string", "null"]},
                    "live": {"type": ["boolean", "null"]},
                    "prerequisite_raw": {"type": "string"},
                },
            },
            "interaction": {
                "type": "object",
                "required": [
                    "student_external_id",
                    "exercise_external_id",
                    "correct",
                    "attempts",
                    "duration_seconds",
                    "hint_used",
                    "count_hints",
                    "earned_proficiency",
                    "occurred_at",
                ],
                "properties": {
                    "student_external_id": {"type": "string", "minLength": 1},
                    "exercise_external_id": {"type": "string", "minLength": 1},
                    "correct": {"type": "boolean"},
                    "attempts": {"type": ["integer", "null"], "minimum": 0},
                    "duration_seconds": {"type": ["number", "null"], "minimum": 0},
                    "hint_used": {"type": ["boolean", "null"]},
                    "count_hints": {"type": ["integer", "null"], "minimum": 0},
                    "earned_proficiency": {"type": ["boolean", "null"]},
                    "occurred_at": {"type": "string", "format": "date-time"},
                },
            },
            "relationship": {
                "type": "object",
                "required": [
                    "source_external_id",
                    "target_external_id",
                    "relation_type",
                    "score",
                    "source",
                    "verified",
                ],
                "properties": {
                    "source_external_id": {"type": "string", "minLength": 1},
                    "target_external_id": {"type": "string", "minLength": 1},
                    "relation_type": {"type": "string", "minLength": 1},
                    "score": {"type": ["number", "null"]},
                    "source": {"type": "string", "minLength": 1},
                    "verified": {"type": "boolean"},
                },
            },
        },
        "notes": [
            "Raw prerequisite text remains available without silently becoming a final graph.",
            "Research Student does not map implicitly to a platform user.",
            "No KnowledgePoint identifier is invented before an approved domain mapping.",
        ],
    }


def show(value: Any) -> str:
    if value is None:
        return "N/A"
    if isinstance(value, float):
        return f"{value:.6f}".rstrip("0").rstrip(".")
    return str(value)


def markdown_table(headers: Sequence[str], rows: Iterable[Sequence[Any]]) -> str:
    result = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
    ]
    for row in rows:
        result.append("| " + " | ".join(show(value) for value in row) + " |")
    return "\n".join(result)


def source_catalog(
    metadata_dir: Path, problem_log: Path, reference_root: Path
) -> list[dict[str, Any]]:
    return [
        {
            "source_id": "S1_PRIMARY_REGISTRY",
            "source_type": "PRIMARY",
            "status": "NOT_ACQUIRED",
            "source_label": "PSLC DataShop Junyi Academy Math Practicing Log",
            "source_uri": "https://pslcdatashop.web.cmu.edu/DatasetInfo?datasetId=1275",
            "acquired_at": "Not acquired: unauthenticated Files view reported login required.",
            "file_evidence": "No local direct-download artifact.",
            "license_note": "Junyi documentation states non-commercial use only.",
            "local_path": "N/A",
            "purpose": "Authoritative provenance target; not used as a local input.",
        },
        {
            "source_id": "S1_LOCAL_MIRROR",
            "source_type": "THIRD_PARTY_PROCESSED",
            "status": "USED_WITH_PROVENANCE_LIMIT",
            "source_label": "User-provided USTC-mirror Junyi archive and extracted CSV files",
            "source_uri": "USTC mirror URL was not recorded in the local acquisition handoff.",
            "acquired_at": "Local archive observed 2026-09-07; original acquisition time unverified.",
            "file_evidence": json.dumps(
                {
                    "metadata": file_info(metadata_dir / "junyi_Exercise_table.csv"),
                    "problem_log": file_info(problem_log),
                },
                ensure_ascii=False,
            ),
            "license_note": "Mirror distribution does not prove primary provenance; Junyi non-commercial boundary retained.",
            "local_path": str(metadata_dir.parent.parent),
            "purpose": "Actual schema, EDA, graph, sampling, and materialisation input.",
        },
        {
            "source_id": "S2_RCD",
            "source_type": "REFERENCE_CODE",
            "status": "INSPECTED",
            "source_label": "bigdata-ustc/RCD",
            "source_uri": "https://github.com/bigdata-ustc/RCD",
            "acquired_at": "Cloned for local reference during DATA-0.",
            "file_evidence": "Junyi graph pairs and JSON files inspected locally.",
            "license_note": "No root license file declared in the inspected repository.",
            "local_path": str(reference_root / "RCD"),
            "purpose": "RCD graph semantics and model-input comparison.",
        },
        {
            "source_id": "S3_INSCD",
            "source_type": "REFERENCE_CODE",
            "status": "INSPECTED",
            "source_label": "ECNU-ILOG/InsCD",
            "source_uri": "https://github.com/ECNU-ILOG/InsCD",
            "acquired_at": "Cloned for local reference during DATA-0.",
            "file_evidence": "Junyi734 datahub adapter inspected locally.",
            "license_note": "MIT license in inspected repository.",
            "local_path": str(reference_root / "InsCD"),
            "purpose": "NCDM, RCD, and ORCDF interface comparison.",
        },
        {
            "source_id": "S4_GEAR_CD",
            "source_type": "THIRD_PARTY_PROCESSED",
            "status": "INSPECTED_NO_TRAINING",
            "source_label": "Figshare GEAR-CD preprocessed archive",
            "source_uri": "https://figshare.com/articles/journal_contribution/The_preprocessed_dataset_and_code_for_GEAR-CD/29231882",
            "acquired_at": "Downloaded for local schema inspection during DATA-0.",
            "file_evidence": "Junyi processed archive exposes RCD-like JSON and graph pair files.",
            "license_note": "Figshare says CC BY 4.0; embedded Junyi documentation says non-commercial. The stricter boundary controls.",
            "local_path": str(reference_root / "GEAR-CD"),
            "purpose": "GEAR-CD input and cost-readiness audit only; no training.",
        },
        {
            "source_id": "S5_NEURAL_CD",
            "source_type": "REFERENCE_CODE",
            "status": "INSPECTED",
            "source_label": "bigdata-ustc/Neural_Cognitive_Diagnosis-NeuralCD",
            "source_uri": "https://github.com/bigdata-ustc/Neural_Cognitive_Diagnosis-NeuralCD",
            "acquired_at": "Cloned for local reference during DATA-0.",
            "file_evidence": "Response data-loader contract inspected locally.",
            "license_note": "No root license file declared in the inspected repository.",
            "local_path": str(reference_root / "NeuralCD"),
            "purpose": "NCDM baseline input comparison.",
        },
        {
            "source_id": "S6_ORCDF",
            "source_type": "REFERENCE_CODE",
            "status": "INSPECTED",
            "source_label": "ECNU-ILOG/ORCDF",
            "source_uri": "https://github.com/ECNU-ILOG/ORCDF",
            "acquired_at": "Cloned for local reference during DATA-0.",
            "file_evidence": "Junyi response and Q-matrix configuration inspected locally.",
            "license_note": "No root license file declared in the inspected repository.",
            "local_path": str(reference_root / "ORCDF"),
            "purpose": "ORCDF response and Q-matrix readiness comparison.",
        },
    ]


def hierarchy_metrics(
    scope_exercises: set[str], exercise_records: Mapping[str, Mapping[str, str]]
) -> dict[str, Any]:
    nodes: set[str] = set(scope_exercises)
    edges: list[tuple[str, str]] = []
    for exercise in scope_exercises:
        record = exercise_records[exercise]
        topic = record["topic"]
        area = record["area"]
        if not is_missing(topic):
            topic_node = "topic:" + topic
            nodes.add(topic_node)
            edges.append((exercise, topic_node))
        if not is_missing(topic) and not is_missing(area):
            area_node = "area:" + area
            nodes.add(area_node)
            edges.append(("topic:" + topic, area_node))
    return graph_metrics(nodes, edges, directed=True)


def schema_column_rows(
    scan: Mapping[str, Any], parse_types: Mapping[str, str]
) -> list[tuple[Any, ...]]:
    row_count = int(scan["row_count"])
    missing = scan["missing"]
    unique_counts = scan["unique_counts"]
    rows: list[tuple[Any, ...]] = []
    for column in scan["headers"]:
        unique = unique_counts[column]
        if isinstance(unique, dict):
            if unique["status"] == "EXACT":
                unique_display = unique["count"]
            else:
                unique_display = "at least " + str(unique["lower_bound"])
        else:
            unique_display = unique
        missing_count = int(missing.get(column, 0))
        rows.append(
            (
                column,
                "CSV text",
                parse_types.get(column, "unclassified"),
                missing_count,
                missing_count / row_count if row_count else 0.0,
                unique_display,
            )
        )
    return rows


def render_reports(
    reports_dir: Path,
    sources: list[dict[str, Any]],
    metadata_scan: Mapping[str, Any],
    relation_scans: Mapping[str, Mapping[str, Any]],
    exercise: Mapping[str, Any],
    annotations: Mapping[str, Any],
    interactions: Mapping[str, Any],
    graph_sources: Mapping[str, Any],
    scope_exercises: set[str],
    candidates: Mapping[str, Any],
    selection_seed: int,
    sampling_policy: Mapping[str, Any],
    split: Mapping[str, Any],
    cost: Mapping[str, Any],
    final_gate: Mapping[str, Any],
) -> dict[str, Any]:
    reports_dir.mkdir(parents=True, exist_ok=True)
    write_text(
        reports_dir / "environment.md",
        """# DATA-0 Environment

| Item | Observed value |
| --- | --- |
| OS | Microsoft Windows 11 Home, 64-bit, build 26200 |
| CPU | Intel Core i5-10200H; 4 physical cores; 8 logical processors |
| RAM | 17,083,187,200 bytes |
| Disk D free at audit start | 278,797,910,016 bytes |
| Python | 3.12.14 at D:\\Code\\S2\\.venv\\Scripts\\python.exe |
| Java | 21.0.12 LTS |
| Maven | 3.9.16 at D:\\Code\\java\\tools\\apache-maven-3.9.16\\bin\\mvn.cmd |
| Git | 2.55.0.windows.3 |
| Docker client | 29.6.2 installed; daemon unavailable during environment check |
| GPU | NVIDIA GeForce GTX 1650; 4,293,918,720 bytes reported adapter memory |
| CUDA and GPU hours | CUDA not used; 0 GPU hours |

No paid compute was provisioned. Docker daemon unavailability is an environment
observation, not proof that compose configuration cannot be checked separately.
""",
    )
    write_text(
        reports_dir / "data_sources.md",
        "# DATA-0 Source Provenance\n\n"
        + markdown_table(
            ["ID", "Type", "Status", "Label", "License boundary", "Local path"],
            [
                (
                    item["source_id"],
                    item["source_type"],
                    item["status"],
                    item["source_label"],
                    item["license_note"],
                    item["local_path"],
                )
                for item in sources
            ],
        )
        + "\n\n## Integrity evidence policy\n\n"
        + "The governing repository rule prohibits content digests. This audit records "
        + "explicit source URIs, acquisition state, local paths, file sizes, schemas, "
        + "sample records, deterministic selection settings, and local member lists. "
        + "That does not meet the conflicting Issue #2 digest requirement and remains "
        + "a Final Gate condition.\n\n"
        + "## Source detail\n\n"
        + "\n".join(
            "\n".join(
                [
                    "### " + item["source_id"] + " — " + item["source_label"],
                    "",
                    "- URI: " + item["source_uri"],
                    "- Acquired: " + item["acquired_at"],
                    "- File evidence: " + item["file_evidence"],
                    "- Purpose: " + item["purpose"],
                    "",
                ]
            )
            for item in sources
        ),
    )
    schema_rows: list[tuple[Any, ...]] = [
        (
            "Exercise metadata",
            metadata_scan["row_count"],
            metadata_scan["column_count"],
            ", ".join(metadata_scan["headers"]),
            metadata_scan["malformed_row_count"],
        )
    ]
    for name, scan in relation_scans.items():
        schema_rows.append(
            (
                name,
                scan["row_count"],
                scan["column_count"],
                ", ".join(scan["headers"]),
                scan["malformed_row_count"],
            )
        )
    schema_rows.append(
        (
            "ProblemLog original",
            interactions["row_count"],
            interactions["column_count"],
            ", ".join(interactions["headers"]),
            interactions["malformed_row_count"],
        )
    )
    write_text(
        reports_dir / "schema_audit.md",
        "# DATA-0 Schema Audit\n\n"
        + markdown_table(
            ["Source", "Rows", "Columns", "Actual headers", "Malformed rows"],
            schema_rows,
        )
        + "\n\n## Real samples\n\n"
        + "- samples/exercise_10.json\n"
        + "- samples/relationship_training_10.json\n"
        + "- samples/relationship_testing_10.json\n"
        + "- samples/problem_log_10.json\n\n"
        + "All raw files are CSV text. Parsed type below states the DATA-0 validation "
        + "category. High-cardinality fields use an explicit lower-bound marker rather "
        + "than a false exact value.\n\n"
        + "## Exercise metadata columns\n\n"
        + markdown_table(
            ["Column", "Storage", "Parsed type", "Missing", "Missing rate", "Unique"],
            schema_column_rows(metadata_scan, EXERCISE_PARSE_TYPES),
        )
        + "\n\n## Relationship training columns\n\n"
        + markdown_table(
            ["Column", "Storage", "Parsed type", "Missing", "Missing rate", "Unique"],
            schema_column_rows(
                relation_scans["relationship_annotation_training"], RELATION_PARSE_TYPES
            ),
        )
        + "\n\n## Relationship testing columns\n\n"
        + markdown_table(
            ["Column", "Storage", "Parsed type", "Missing", "Missing rate", "Unique"],
            schema_column_rows(
                relation_scans["relationship_annotation_testing"], RELATION_PARSE_TYPES
            ),
        )
        + "\n\n## ProblemLog columns\n\n"
        + markdown_table(
            ["Column", "Storage", "Parsed type", "Missing", "Missing rate", "Unique"],
            schema_column_rows(interactions, LOG_PARSE_TYPES),
        )
        + "\n",
    )
    write_text(
        reports_dir / "data0_quality.md",
        "# DATA-0 Quality Audit\n\n"
        + markdown_table(
            ["Check", "Observed value"],
            [
                ("ProblemLog rows", interactions["row_count"]),
                ("ProblemLog malformed rows", interactions["malformed_row_count"]),
                ("ProblemLog exact duplicate rows", interactions["exact_duplicate_rows"]),
                ("ProblemLog business-key duplicate rows", interactions["business_key_duplicate_rows"]),
                ("Unknown Exercise reference rows", interactions["unknown_exercise_rows"]),
                ("Known Exercise reference rate", interactions["known_exercise_reference_rate"]),
                ("Metadata duplicate external IDs", exercise["duplicate_external_id_count"]),
                ("Metadata prerequisite dangling tokens", len(exercise["dangling_tokens"])),
                ("Metadata prerequisite ambiguous tokens", len(exercise["ambiguous_tokens"])),
            ],
        )
        + "\n\nThe business key is user_id, exercise, problem_number, and time_done. "
        + "Exact duplication compares all original CSV fields. Neither check deletes a row.\n",
    )
    write_text(
        reports_dir / "exercise_eda.md",
        "# Exercise EDA\n\n"
        + markdown_table(
            ["Metric", "Value"],
            [
                ("Metadata rows", exercise["metadata_rows"]),
                ("Distinct external IDs", exercise["distinct_external_ids"]),
                ("Duplicate external ID records", exercise["duplicate_external_id_count"]),
                ("Unique IDs usable for identity-sensitive analysis", exercise["unique_external_ids"]),
                ("Topics excluding missing markers", exercise["topic_count_excluding_missing"]),
                ("Areas excluding missing markers", exercise["area_count_excluding_missing"]),
                ("Missing topic rows", exercise["topic_missing_count"]),
                ("Missing area rows", exercise["area_missing_count"]),
                ("Missing prerequisite rows", exercise["prerequisite_missing_count"]),
                ("Raw prerequisite edge rows", exercise["prerequisite_raw_edge_rows"]),
            ],
        )
        + "\n\n## Exercises by topic\n\n"
        + markdown_table(
            ["Topic", "Exercises"], sorted(exercise["exercise_per_topic"].items())
        )
        + "\n\n## Exercises by area\n\n"
        + markdown_table(
            ["Area", "Exercises"], sorted(exercise["exercise_per_area"].items())
        )
        + "\n\nExercise remains a research capability unit; this audit does not claim a "
        + "complete platform Question bank.\n",
    )
    sequences = interactions["sequence_distribution"]
    correct_total = sum(interactions["correct_distribution"].values())
    write_text(
        reports_dir / "interaction_eda.md",
        "# Interaction and Student-sequence EDA\n\n"
        + markdown_table(
            ["Metric", "Value"],
            [
                ("Students", interactions["student_count"]),
                ("Interactions", interactions["row_count"]),
                ("Referenced Exercises", interactions["referenced_exercise_count"]),
                ("Correct rate", interactions["correct_distribution"].get("true", 0) / max(1, correct_total)),
                ("Timestamp earliest", interactions["timestamp_min_utc"]),
                ("Timestamp latest", interactions["timestamp_max_utc"]),
                ("Invalid timestamps", interactions["timestamp_invalid_rows"]),
            ],
        )
        + "\n\n"
        + markdown_table(
            ["min", "P10", "P25", "P50", "P75", "P90", "P95", "P99", "max", "mean"],
            [[
                sequences["min"],
                sequences["p10"],
                sequences["p25"],
                sequences["p50"],
                sequences["p75"],
                sequences["p90"],
                sequences["p95"],
                sequences["p99"],
                sequences["max"],
                sequences["mean"],
            ]],
        )
        + "\n\n"
        + markdown_table(
            ["Students below 5", "below 10", "below 15", "below 20", "below 30"],
            [[
                interactions["sequence_below_thresholds"]["5"],
                interactions["sequence_below_thresholds"]["10"],
                interactions["sequence_below_thresholds"]["15"],
                interactions["sequence_below_thresholds"]["20"],
                interactions["sequence_below_thresholds"]["30"],
            ]],
        )
        + "\n\nCandidate sequences are parsed and stably sorted by time_done and original row "
        + "number before the empirical sequence window is applied.\n",
    )
    write_text(
        reports_dir / "prerequisite_semantics.md",
        "# Prerequisite Semantics Audit\n\n"
        + "The actual metadata header is prerequisites, while the public README uses the "
        + "singular spelling prerequisite. DATA-0 preserves the complete raw text and "
        + "does not silently freeze a graph schema.\n\n"
        + markdown_table(
            ["Check", "Value"],
            [
                ("Source rows excluded for ambiguous Exercise identity", exercise["source_ambiguous_rows"]),
                ("In-row duplicate prerequisite tokens", len(exercise["duplicate_token_rows"])),
                ("Dangling prerequisite tokens", len(exercise["dangling_tokens"])),
                ("Ambiguous prerequisite tokens", len(exercise["ambiguous_tokens"])),
                ("Analysis-view parsed edges", len(exercise["parsed_edges"])),
                ("Self loops", exercise["graph"]["self_loop_count"]),
            ],
        )
        + "\n\nThe analysis view orients each parsed relation as prerequisite to dependent "
        + "only for topology measurement. A delimiter split is not promoted to a final graph "
        + "relation or V0.2 knowledge decision.\n\n"
        + "Duplicate-token examples: "
        + json.dumps(exercise["duplicate_token_rows"][:10], ensure_ascii=False)
        + "\n\nSelf-loop examples: "
        + json.dumps(exercise["graph"]["self_loops"][:10], ensure_ascii=False)
        + "\n",
    )
    metrics_by_source: dict[str, Mapping[str, Any]] = {
        "Metadata prerequisite analysis view": exercise["graph"],
        "Topic and Area hierarchy": hierarchy_metrics(
            scope_exercises, exercise["records_by_name"]
        ),
    }
    for name, source in graph_sources.items():
        metrics_by_source[name] = source["metrics"]
    write_text(
        reports_dir / "data0_graph.md",
        "# DATA-0 Graph Audit\n\n"
        + markdown_table(
            [
                "Relation source",
                "Nodes",
                "Edges",
                "Components",
                "Largest component ratio",
                "Self loops",
                "Cyclic SCCs",
            ],
            [
                (
                    name,
                    metrics["nodes"],
                    metrics["edges"],
                    metrics["components"],
                    metrics["largest_component_ratio"],
                    metrics["self_loop_count"],
                    metrics["cycle_component_count"],
                )
                for name, metrics in metrics_by_source.items()
            ],
        )
        + "\n\n## Annotation semantics\n\n"
        + "\n".join(
            "- "
            + name
            + ": "
            + item["graph_status"]
            + "; rows="
            + str(item["row_count"])
            + "; dangling endpoint rows="
            + str(item["dangling_endpoint_rows"])
            for name, item in annotations.items()
        )
        + "\n\nMetadata prerequisites, annotation scores, RCD directed pairs, RCD "
        + "undirected pairs, and Topic/Area hierarchy remain separated. RCD numeric concept "
        + "IDs are not mapped to current raw external IDs, so they are not merged.\n\n"
        + "Representative raw-prerequisite cyclic components: "
        + json.dumps(exercise["graph"]["cycle_components"][:10], ensure_ascii=False)
        + "\n",
    )
    write_text(
        reports_dir / "domain_mapping_options.md",
        """# Domain Mapping Options — Evidence, Not a Schema Freeze

| Option | Evidence support | Model implications | Platform implications | DATA-0 conclusion |
| --- | --- | --- | --- | --- |
| A. Exercise equals KnowledgePoint | Metadata carries prerequisite text at Exercise granularity, but duplicate IDs and raw graph cycles exist. | Direct but conflates capability unit and concept. | Cannot be a complete Question bank. | Not safe to freeze. |
| B. Topic equals KnowledgePoint, Exercise maps to Topic | Topic and Area hierarchy exists for most metadata rows. | A provisional Exercise-to-Topic matrix can support NCDM-style adapters. | More explainable aggregation. | Candidate only; Topic semantics require approval. |
| C. ExerciseUnit plus KnowledgePoint plus Topic and Area | Preserves raw Exercise, keeps a future conceptual layer explicit, and retains hierarchy evidence. | Supports adapters without asserting a final concept source. | Aligns with the data-domain boundary ADR. | Recommended comparison baseline, not a V0.2 decision. |

No KnowledgePoint identifier is fabricated in this audit. The Owner and architect
must choose domain meaning after reviewing the evidence.
""",
    )
    write_text(
        reports_dir / "data0_sampling.md",
        "# DATA-0 Reproducible Sampling\n\n"
        + markdown_table(
            [
                "Candidate",
                "Students",
                "Exercises",
                "Interactions",
                "Topics",
                "Areas",
                "Relation edges",
                "Largest component ratio",
                "Disk bytes",
                "Materialisation seconds",
                "Peak RAM bytes",
            ],
            [
                (
                    name,
                    item["students"],
                    item["exercises"],
                    item["interactions"],
                    item["topics"],
                    item["areas"],
                    item["relation_edges"],
                    item["largest_component_ratio"],
                    item["disk_size_bytes"],
                    item["materialisation_wall_time_seconds"],
                    item["peak_working_set_bytes"],
                )
                for name, item in candidates.items()
            ],
        )
        + "\n\n"
        + markdown_table(
            ["Control", "Value"],
            [
                ("Selection seed", selection_seed),
                ("Scope Exercise count", len(scope_exercises)),
                ("Eligible students", sampling_policy["eligible_student_count"]),
                ("Minimum interactions", sampling_policy["minimum_interactions_applied"]),
                ("Minimum interaction rule", sampling_policy["minimum_interactions_formula"]),
                ("Stable sequence window cap", sampling_policy["window_cap_applied"]),
                ("Window cap rule", sampling_policy["window_cap_formula"]),
                ("Medium split strategy", split.get("strategy", "not materialised")),
                ("Medium split membership seed", split.get("seed", "not materialised")),
                ("Train valid test overlap", split.get("overlap", {})),
            ],
        )
        + "\n\nAll candidates are materialised below data-pipeline/.data/generated and "
        + "excluded from Git. The Medium member list is fixed before any model result.\n",
    )
    write_text(
        reports_dir / "data0_model_readiness.md",
        """# DATA-0 Model Readiness

| Model | Status | Evidence and remaining condition |
| --- | --- | --- |
| NCDM | CONDITIONAL | Canonical student, Exercise, and binary response fields exist. A provisional Exercise-to-Topic matrix can be made, but Topic is not approved as the final KnowledgePoint meaning. |
| RCD | CONDITIONAL, reference only | Student-Exercise records exist. RCD reference data contains Exercise-Concept and concept-graph files, but its numeric IDs are not traceably mapped to current raw external IDs. |
| ORCDF | CONDITIONAL | Response rows and a provisional Exercise-to-Topic matrix can be formed; a formal concept mapping and response graph definition remain open. |
| GEAR-CD | NOT READY | A small Figshare preprocessed archive was inspected without training. Provenance, source mapping, and a complete verified runtime path are insufficient for a production route. |

No model was trained or tuned. No model result exists that could influence the
frozen Medium split.
""",
    )
    write_text(
        reports_dir / "data0_cost.md",
        "# DATA-0 Cost and Reproducibility\n\n"
        + markdown_table(
            ["Metric", "Observed value"],
            [
                ("Raw ProblemLog bytes", interactions["file"]["size_bytes"]),
                ("Processed candidate bytes", sum(item["disk_size_bytes"] for item in candidates.values())),
                ("ETL wall time seconds", cost["wall_time_seconds"]),
                ("CPU process time seconds", cost["cpu_time_seconds"]),
                ("Peak working set bytes", cost["peak_working_set_bytes"]),
                ("GPU hours", 0),
                ("Cash cost", 0),
            ],
        )
        + "\n\nReproducible entry point: python -m edu_data.data0 with explicit "
        + "metadata, ProblemLog, reference, reports, generated-output, work, manifest, "
        + "and schema paths. It begins from supplied files and never uploads raw data.\n",
    )
    bad_cases = [
        "The local archive is a USTC mirror with no recorded direct source URI; it is not PRIMARY.",
        "DataShop Files access exposed a login-required marker during this audit.",
        "The actual metadata column is prerequisites, not the singular spelling in public documentation.",
        str(exercise["duplicate_external_id_count"]) + " metadata external IDs are duplicated.",
        str(len(exercise["duplicate_token_rows"])) + " in-row duplicate prerequisite tokens are retained.",
        str(exercise["graph"]["self_loop_count"]) + " prerequisite self loops are retained; no cycle is auto-deleted.",
        "Relationship annotation scores are not converted into graph edges without verified semantics.",
        "High-cardinality log columns may be reported as lower bounds under an explicit memory control.",
        "Docker daemon was unavailable during environment inspection.",
        "Content digests and repository HEAD identifiers are prohibited by the governing repository instruction.",
    ]
    write_text(
        reports_dir / "bad_cases.md",
        "# DATA-0 Bad Cases and Boundaries\n\n"
        + "\n".join(
            str(index) + ". " + item for index, item in enumerate(bad_cases, start=1)
        )
        + "\n",
    )
    write_text(
        reports_dir / "data0_final_gate.md",
        "# DATA-0 Final Gate\n\n"
        + "## "
        + final_gate["status"]
        + "\n\n### Supporting evidence\n\n"
        + "\n".join("- " + item for item in final_gate["supporting_evidence"])
        + "\n\n### Conditions and blockers\n\n"
        + "\n".join("- " + item for item in final_gate["conditions"])
        + "\n\n### Stop condition\n\n"
        + "DATA-0 ends here. No V0.2 schema, Course CRUD, recommendation UI, "
        + "teacher dashboard, full model training, LLM or Agent work, or distributed "
        + "service work was started. Independent Owner and architect review is required.\n",
    )
    summary = {
        "dataset": "JUNYI_DATA0",
        "status": final_gate["status"],
        "provenance": {
            "primary_direct_acquisition": False,
            "local_source_classification": "THIRD_PARTY_PROCESSED",
            "content_integrity_evidence": "No content digest permitted by governing repository instruction.",
        },
        "schema_audit": {
            "exercise_metadata": {
                "row_count": metadata_scan["row_count"],
                "column_count": metadata_scan["column_count"],
                "headers": metadata_scan["headers"],
                "missing": metadata_scan["missing"],
                "unique_counts": metadata_scan["unique_counts"],
                "malformed_row_count": metadata_scan["malformed_row_count"],
            },
            "relationship_annotation_training": {
                "row_count": relation_scans["relationship_annotation_training"]["row_count"],
                "column_count": relation_scans["relationship_annotation_training"]["column_count"],
                "headers": relation_scans["relationship_annotation_training"]["headers"],
                "missing": relation_scans["relationship_annotation_training"]["missing"],
                "unique_counts": relation_scans["relationship_annotation_training"]["unique_counts"],
                "malformed_row_count": relation_scans["relationship_annotation_training"]["malformed_row_count"],
            },
            "relationship_annotation_testing": {
                "row_count": relation_scans["relationship_annotation_testing"]["row_count"],
                "column_count": relation_scans["relationship_annotation_testing"]["column_count"],
                "headers": relation_scans["relationship_annotation_testing"]["headers"],
                "missing": relation_scans["relationship_annotation_testing"]["missing"],
                "unique_counts": relation_scans["relationship_annotation_testing"]["unique_counts"],
                "malformed_row_count": relation_scans["relationship_annotation_testing"]["malformed_row_count"],
            },
            "problem_log": {
                "row_count": interactions["row_count"],
                "column_count": interactions["column_count"],
                "headers": interactions["headers"],
                "missing": interactions["missing"],
                "unique_counts": interactions["unique_counts"],
                "malformed_row_count": interactions["malformed_row_count"],
            },
        },
        "exercise": {
            key: exercise[key]
            for key in (
                "metadata_rows",
                "distinct_external_ids",
                "duplicate_external_id_count",
                "topic_count_excluding_missing",
                "area_count_excluding_missing",
            )
        },
        "interaction": {
            key: interactions[key]
            for key in (
                "row_count",
                "student_count",
                "referenced_exercise_count",
                "known_exercise_reference_rate",
                "exact_duplicate_rows",
                "business_key_duplicate_rows",
                "sequence_distribution",
                "unique_counts",
            )
        },
        "sampling": candidates,
        "split": split,
        "final_gate": final_gate,
    }
    write_json(reports_dir / "data0_summary.json", summary)
    return summary


def run(args: argparse.Namespace) -> int:
    started_monotonic = time.monotonic()
    started_cpu = time.process_time()
    metadata_dir = args.metadata_dir.resolve()
    problem_log = args.problem_log.resolve()
    reference_root = args.reference_root.resolve()
    reports_dir = args.reports_dir.resolve()
    generated_root = args.generated_root.resolve()
    work_dir = args.work_dir.resolve()
    exercise_file = metadata_dir / "junyi_Exercise_table.csv"
    relation_training = metadata_dir / "relationship_annotation_training.csv"
    relation_testing = metadata_dir / "relationship_annotation_testing.csv"
    required = [exercise_file, relation_training, relation_testing, problem_log]
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        raise FileNotFoundError("Required DATA-0 input missing: " + ", ".join(missing))

    samples = reports_dir / "samples"
    metadata_scan = scan_small_csv(
        exercise_file, EXERCISE_COLUMNS, samples / "exercise_10.json"
    )
    relation_scans = {
        "relationship_annotation_training": scan_small_csv(
            relation_training, RELATION_COLUMNS, samples / "relationship_training_10.json"
        ),
        "relationship_annotation_testing": scan_small_csv(
            relation_testing, RELATION_COLUMNS, samples / "relationship_testing_10.json"
        ),
    }
    exercise = audit_exercise(metadata_scan)
    annotations = audit_relation_annotations(
        relation_scans, set(exercise["unique_names"])
    )
    scope_exercises = {
        name
        for name in exercise["unique_names"]
        if not is_missing(exercise["records_by_name"][name]["topic"])
        and not is_missing(exercise["records_by_name"][name]["area"])
    }
    interactions = audit_problem_log(
        problem_log,
        samples / "problem_log_10.json",
        set(exercise["unique_names"]),
        scope_exercises,
        work_dir,
    )
    scope_graph = graph_metrics(
        scope_exercises,
        [
            (source, target)
            for source, target in exercise["parsed_edges"]
            if source in scope_exercises and target in scope_exercises
        ],
        directed=True,
    )
    graph_sources: dict[str, Any] = {}
    rcd_graph = reference_root / "RCD" / "data" / "junyi" / "graph"
    for display_name, filename, directed in (
        ("RCD K_Directed", "K_Directed.txt", True),
        ("RCD K_Undirected", "K_Undirected.txt", False),
    ):
        candidate = rcd_graph / filename
        if candidate.is_file():
            graph_sources[display_name] = audit_pair_graph(candidate, directed)

    selected, sampling_policy = nested_candidates(
        interactions["scope_student_counts"], args.seed
    )
    candidates, split = materialise_candidates(
        problem_log,
        scope_exercises,
        selected,
        sampling_policy,
        exercise["records_by_name"],
        exercise["parsed_edges"],
        scope_graph,
        generated_root,
        args.seed,
        work_dir,
    )
    medium = candidates["Medium"]
    correct_total = sum(interactions["correct_distribution"].values())
    correct_rate = interactions["correct_distribution"].get("true", 0) / max(
        1, correct_total
    )
    conditions = [
        "Primary DataShop files were not directly acquired; the audited local input is a third-party mirror.",
        "The governing repository rule prohibits content digests, so the conflicting Issue #2 provenance and split-integrity field is absent.",
        str(exercise["duplicate_external_id_count"])
        + " duplicated Exercise IDs and "
        + str(exercise["graph"]["self_loop_count"])
        + " raw prerequisite self loops require a domain decision.",
        "Raw prerequisite strings and annotation scores remain evidence, not approved production graph edges.",
        "No final ExerciseUnit or KnowledgePoint mapping has been frozen.",
    ]
    if (
        medium["students"] < 5_000
        or medium["interactions"] < 200_000
        or interactions["known_exercise_reference_rate"] < 0.98
    ):
        status = "NO_GO"
        conditions.append(
            "The materialised Medium candidate misses the minimum behavioral-scale or linkage threshold."
        )
    else:
        status = "CONDITIONAL_GO"
    final_gate = {
        "status": status,
        "supporting_evidence": [
            "Full original ProblemLog audit observed "
            + str(interactions["row_count"])
            + " interaction rows and "
            + str(interactions["student_count"])
            + " anonymous students.",
            "The materialised Medium candidate has "
            + str(medium["students"])
            + " students and "
            + str(medium["interactions"])
            + " leakage-checked interactions.",
            "Exercise-to-interaction reference rate is "
            + f"{interactions['known_exercise_reference_rate']:.6f}"
            + "; correct rate is "
            + f"{correct_rate:.6f}"
            + ".",
            "Metadata provides "
            + str(exercise["topic_count_excluding_missing"])
            + " non-missing Topics and "
            + str(exercise["area_count_excluding_missing"])
            + " non-missing Areas.",
            "Canonical records, deterministic split membership, and source-separated graph audits were materialised locally without importing research users into platform tables.",
        ],
        "conditions": conditions,
    }
    cost = {
        "wall_time_seconds": round(time.monotonic() - started_monotonic, 3),
        "cpu_time_seconds": round(time.process_time() - started_cpu, 3),
        "peak_working_set_bytes": peak_working_set_bytes(),
    }
    summary = render_reports(
        reports_dir,
        source_catalog(metadata_dir, problem_log, reference_root),
        metadata_scan,
        relation_scans,
        exercise,
        annotations,
        interactions,
        graph_sources,
        scope_exercises,
        candidates,
        args.seed,
        sampling_policy,
        split,
        cost,
        final_gate,
    )
    write_json(args.schema_path.resolve(), canonical_schema())
    write_json(
        args.manifest_path.resolve(),
        {
            "dataset_name": "JUNYI_MID",
            "version": "1.0.0-data0-candidate",
            "status": status,
            "source_type": "THIRD_PARTY_PROCESSED",
            "license_note": "Junyi documentation states non-commercial use only.",
            "student_count": medium["students"],
            "exercise_count": medium["exercises"],
            "interaction_count": medium["interactions"],
            "relation_count": medium["relation_edges"],
            "split_strategy": split,
            "selection": {
                "seed": args.seed,
                "scope_exercises": len(scope_exercises),
                **sampling_policy,
            },
            "content_integrity_evidence": (
                "No content digest is present because the governing repository rule forbids it. "
                "Use source URI, file evidence, schema audit, deterministic seed, and exact "
                "local member lists for review."
            ),
            "reports_summary": summary,
        },
    )
    write_json(
        work_dir / "progress.json",
        {
            "phase": "complete",
            "status": status,
            "wall_time_seconds": cost["wall_time_seconds"],
        },
    )
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run the Edu-java DATA-0 Junyi audit.")
    parser.add_argument("--metadata-dir", type=Path, required=True)
    parser.add_argument("--problem-log", type=Path, required=True)
    parser.add_argument("--reference-root", type=Path, required=True)
    parser.add_argument("--reports-dir", type=Path, required=True)
    parser.add_argument("--generated-root", type=Path, required=True)
    parser.add_argument("--work-dir", type=Path, required=True)
    parser.add_argument("--manifest-path", type=Path, required=True)
    parser.add_argument("--schema-path", type=Path, required=True)
    parser.add_argument("--seed", type=int, default=20260907)
    return parser


def main() -> int:
    return run(build_parser().parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
