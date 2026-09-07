import argparse
import csv
import os
from pathlib import Path

from edu_data.data0 import (
    EXERCISE_COLUMNS,
    LOG_COLUMNS,
    audit_exercise,
    audit_pair_graph,
    audit_problem_log,
    canonical_schema,
    deterministic_stratified_select,
    graph_metrics,
    materialise_candidates,
    nested_candidates,
    parse_timestamp_us,
    peak_working_set_bytes,
    run,
    scan_small_csv,
)


def write_csv(path: Path, headers: list[str], rows: list[list[str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(headers)
        writer.writerows(rows)


def test_peak_working_set_is_observable_on_windows() -> None:
    value = peak_working_set_bytes()
    if os.name == "nt":
        assert isinstance(value, int)
        assert value > 0
    else:
        assert value is None


def test_graph_keeps_cycle_and_duplicate_edges_visible() -> None:
    metrics = graph_metrics(
        {"a", "b", "c"},
        [("a", "b"), ("b", "a"), ("a", "b"), ("c", "c")],
        directed=True,
    )
    assert metrics["duplicate_edge_count"] == 1
    assert metrics["self_loop_count"] == 1
    assert metrics["cycle_component_count"] == 2


def test_exercise_audit_preserves_duplicate_and_dangling_values(tmp_path: Path) -> None:
    file = tmp_path / "exercise.csv"
    write_csv(
        file,
        EXERCISE_COLUMNS,
        [
            ["a", "TRUE", "b,b,missing", "0", "0", "date", "1", "a", "a", "t", "area"],
            ["b", "TRUE", "a", "0", "0", "date", "1", "b", "b", "t", "area"],
            ["b", "TRUE", "", "0", "0", "date", "1", "b2", "b2", "t", "area"],
        ],
    )
    scan = scan_small_csv(file, EXERCISE_COLUMNS, tmp_path / "samples.json")
    result = audit_exercise(scan)
    assert result["duplicate_external_id_count"] == 1
    assert len(result["duplicate_token_rows"]) == 1
    assert len(result["dangling_tokens"]) == 1
    assert result["prerequisite_missing_count"] == 1
    assert result["parsed_edges"] == []


def test_problem_log_reports_exact_and_business_duplicates(tmp_path: Path) -> None:
    log = tmp_path / "log.csv"
    row = [
        "student",
        "exercise",
        "0",
        "1",
        "false",
        "false",
        "false",
        "1420714810324490",
        "4",
        "4",
        "true",
        "1",
        "false",
        "0",
        "",
        "false",
        "0",
    ]
    later = row.copy()
    later[7] = "1420714810324491"
    write_csv(log, LOG_COLUMNS, [row, row, later])
    result = audit_problem_log(
        log,
        tmp_path / "samples.json",
        {"exercise"},
        {"exercise"},
        tmp_path / "work",
    )
    assert result["row_count"] == 3
    assert result["exact_duplicate_rows"] == 1
    assert result["business_key_duplicate_rows"] == 1
    assert result["scope_student_count"] == 1


def test_problem_log_keeps_invalid_timestamp_and_unknown_exercise_visible(
    tmp_path: Path,
) -> None:
    invalid = [
        "student",
        "unknown",
        "0",
        "1",
        "false",
        "false",
        "false",
        "not-a-timestamp",
        "4",
        "4",
        "true",
        "1",
        "false",
        "0",
        "",
        "false",
        "0",
    ]
    log = tmp_path / "log.csv"
    write_csv(log, LOG_COLUMNS, [invalid])
    result = audit_problem_log(
        log,
        tmp_path / "samples.json",
        {"known"},
        {"known"},
        tmp_path / "work",
    )
    assert result["unknown_exercise_rows"] == 1
    assert result["invalid_values"]["time_done"] == 1
    assert parse_timestamp_us("not-a-timestamp") is None


def test_pair_graph_preserves_invalid_relation_row(tmp_path: Path) -> None:
    graph = tmp_path / "graph.txt"
    graph.write_text("a\tb\nnot-a-pair\n", encoding="utf-8")
    result = audit_pair_graph(graph, directed=True)
    assert result["malformed_rows"] == 1
    assert result["metrics"]["edges"] == 1


def test_sampling_is_deterministic_and_nested() -> None:
    counts = {f"student-{index}": 5 + index % 30 for index in range(100)}
    assert deterministic_stratified_select(counts, 20, 7) == deterministic_stratified_select(
        counts, 20, 7
    )
    candidates, policy = nested_candidates(counts, 9)
    assert set(candidates["Small"]) <= set(candidates["Medium"])
    assert set(candidates["Medium"]) <= set(candidates["Large"])
    assert policy["minimum_interactions_applied"] >= 5


def test_canonical_schema_has_no_invented_knowledgepoint_field() -> None:
    schema = canonical_schema()
    interaction = schema["properties"]["interaction"]["properties"]
    assert "student_external_id" in interaction
    assert all("knowledgepoint" not in key.casefold() for key in interaction)


def test_raw_prerequisite_relation_and_student_split_remain_separate(
    tmp_path: Path,
) -> None:
    log = tmp_path / "log.csv"
    rows = []
    for student in ("s1", "s2", "s3"):
        for timestamp in ("1420714810324490", "1420714810324491"):
            rows.append(
                [
                    student,
                    "e1",
                    "0",
                    "1",
                    "false",
                    "false",
                    "false",
                    timestamp,
                    "4",
                    "4",
                    "true",
                    "1",
                    "false",
                    "0",
                    "",
                    "false",
                    "0",
                ]
            )
    write_csv(log, LOG_COLUMNS, rows)
    candidates = {"Small": ["s1"], "Medium": ["s1", "s2"], "Large": ["s1", "s2", "s3"]}
    reports, split = materialise_candidates(
        log,
        {"e1"},
        candidates,
        {"window_cap_applied": 2},
        {
            "e1": {
                "name": "e1",
                "pretty_display_name": "E1",
                "topic": "topic",
                "area": "area",
                "live": "true",
                "prerequisites": "",
            }
        },
        [("e1", "e1")],
        graph_metrics({"e1"}, [("e1", "e1")], directed=True),
        tmp_path / "generated",
        9,
        tmp_path / "work",
    )
    assert reports["Medium"]["interactions"] == 4
    assert split["overlap"] == {"train_valid": 0, "train_test": 0, "valid_test": 0}


def test_run_writes_required_gate_artifacts(tmp_path: Path) -> None:
    metadata = tmp_path / "metadata"
    metadata.mkdir()
    write_csv(
        metadata / "junyi_Exercise_table.csv",
        EXERCISE_COLUMNS,
        [["e1", "true", "", "0", "0", "date", "1", "E1", "E1", "topic", "area"]],
    )
    relation_row = ["e1", "e1", "1", "1_1", "1", "1_1", "1", "1_1"]
    write_csv(
        metadata / "relationship_annotation_training.csv",
        [
            "Exercise_A",
            "Exercise_B",
            "Similarity_avg",
            "Similarity_raw",
            "Difficulty_avg",
            "Difficulty_raw",
            "Prerequisite_avg",
            "Prerequisite_raw",
        ],
        [relation_row],
    )
    write_csv(
        metadata / "relationship_annotation_testing.csv",
        [
            "Exercise_A",
            "Exercise_B",
            "Similarity_avg",
            "Similarity_raw",
            "Difficulty_avg",
            "Difficulty_raw",
            "Prerequisite_avg",
            "Prerequisite_raw",
        ],
        [relation_row],
    )
    log = tmp_path / "log.csv"
    write_csv(
        log,
        LOG_COLUMNS,
        [[
            "student",
            "e1",
            "0",
            "1",
            "false",
            "false",
            "false",
            "1420714810324490",
            "4",
            "4",
            "true",
            "1",
            "false",
            "0",
            "",
            "false",
            "0",
        ]],
    )
    reports = tmp_path / "reports"
    manifest = tmp_path / "manifests" / "junyi_mid_v1.json"
    schema = tmp_path / "schemas" / "canonical-v1.json"
    args = argparse.Namespace(
        metadata_dir=metadata,
        problem_log=log,
        reference_root=tmp_path / "reference",
        reports_dir=reports,
        generated_root=tmp_path / "generated",
        work_dir=tmp_path / "work",
        manifest_path=manifest,
        schema_path=schema,
        seed=20260907,
    )
    assert run(args) == 0
    assert (reports / "data0_final_gate.md").is_file()
    assert (reports / "data0_sampling.md").is_file()
    sampling_report = (reports / "data0_sampling.md").read_text(encoding="utf-8")
    assert "| Selection seed | 20260907 |" in sampling_report
    assert "| Medium split membership seed | 20261407 |" in sampling_report
    assert manifest.is_file()
    assert schema.is_file()
