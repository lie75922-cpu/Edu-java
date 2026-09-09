import csv
import json
from pathlib import Path

import pytest

from edu_data.business_export import (
    DataConsistencyError,
    EXERCISE_COLUMNS,
    OUTPUT_FILENAMES,
    build_export,
    export_business,
)


def write_metadata(path: Path, rows: list[list[str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(EXERCISE_COLUMNS)
        writer.writerows(rows)


def exercise(
    external_id: str,
    prerequisites: str,
    topic: str = "fractions",
    area: str = "arithmetic",
    display: str = "分數",
) -> list[str]:
    return [
        external_id,
        "TRUE",
        prerequisites,
        "0",
        "0",
        "date",
        "10",
        display,
        display,
        topic,
        area,
    ]


def read_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]


def test_export_is_complete_deterministic_and_preserves_raw_values(tmp_path: Path) -> None:
    metadata = tmp_path / "junyi_Exercise_table.csv"
    write_metadata(
        metadata,
        [
            exercise("pre", "", display="前置"),
            exercise("dependent", "pre,pre", display="相依"),
            exercise("other", "pre", topic="decimals", display="小數"),
        ],
    )
    output = tmp_path / "business_export"
    first = export_business(metadata, output)
    first_contents = {name: (output / name).read_bytes() for name in OUTPUT_FILENAMES}
    second = export_business(metadata, output)
    second_contents = {name: (output / name).read_bytes() for name in OUTPUT_FILENAMES}

    assert first == second
    assert first_contents == second_contents
    assert set(path.name for path in output.iterdir()) == set(OUTPUT_FILENAMES)
    exercises = read_jsonl(output / "exercises.jsonl")
    assert exercises[1]["raw_source_fields"]["pretty_display_name"] == "相依"
    assert exercises[1]["display_name_zh"] == "相依"
    mappings = read_jsonl(output / "display_mapping.jsonl")
    assert any(item["raw_value"] == "dependent" for item in mappings)
    candidates = read_jsonl(output / "prerequisite_topic_candidates.jsonl")
    assert len(candidates) == 1
    assert candidates[0]["evidence_count"] == 1
    report = json.loads((output / "quality_report.json").read_text(encoding="utf-8"))
    assert report["prerequisite_projection"]["collapsed_same_topic_edge_rows"] == 2
    assert report["topic_candidate_graph"]["self_loop_count"] == 0
    assert report["prerequisite_projection"]["raw_identity_resolved_exercise_graph"]["edges"] == 2


def test_duplicate_missing_and_non_math_rows_are_quarantined(tmp_path: Path) -> None:
    metadata = tmp_path / "junyi_Exercise_table.csv"
    write_metadata(
        metadata,
        [
            exercise("ok", "", display="可用"),
            exercise("duplicate", "ok", display="重複一"),
            exercise("duplicate", "ok", display="重複二"),
            exercise("unclassified", "ok", topic="N/A", area="N/A", display="未分類"),
            exercise("bio", "ok", topic="biology", area="biology", display="生物"),
        ],
    )
    payload = build_export(metadata)
    statuses = [item["business_mapping_status"] for item in payload["exercises"]]
    assert statuses == [
        "ELIGIBLE_FOR_IMPORT",
        "QUARANTINED_DUPLICATE_EXTERNAL_ID",
        "QUARANTINED_DUPLICATE_EXTERNAL_ID",
        "QUARANTINED_MISSING_AREA_OR_TOPIC",
        "QUARANTINED_NON_MATH_AREA",
    ]
    evidence = payload["raw_evidence"]
    assert evidence[0]["identity_resolution_status"] == "AMBIGUOUS_DEPENDENT_EXERCISE_ID"
    assert evidence[1]["identity_resolution_status"] == "AMBIGUOUS_DEPENDENT_EXERCISE_ID"
    assert evidence[2]["topic_projection_status"] == "UNRESOLVED_MISSING_AREA_OR_TOPIC"
    assert evidence[3]["topic_projection_status"] == "QUARANTINED_NON_MATH_AREA"


def test_cycle_is_detected_in_topic_candidates_but_never_published(tmp_path: Path) -> None:
    metadata = tmp_path / "junyi_Exercise_table.csv"
    write_metadata(
        metadata,
        [
            exercise("fraction", "decimal", topic="fractions", display="分數"),
            exercise("decimal", "fraction", topic="decimals", display="小數"),
        ],
    )
    payload = build_export(metadata)
    graph = payload["quality_report"]["topic_candidate_graph"]
    assert graph["cycle_component_count"] == 1
    assert graph["self_loop_count"] == 0
    assert all(
        candidate["published_graph_status"] == "NOT_PUBLISHED"
        and candidate["candidate_status"] == "REVIEW_REQUIRED_NOT_PUBLISHED"
        for candidate in payload["candidates"]
    )


def test_unknown_area_or_topic_fails_closed(tmp_path: Path) -> None:
    metadata = tmp_path / "junyi_Exercise_table.csv"
    write_metadata(metadata, [exercise("e1", "", topic="unknown-topic")])

    with pytest.raises(DataConsistencyError, match="Chinese mapping is incomplete"):
        build_export(metadata)
