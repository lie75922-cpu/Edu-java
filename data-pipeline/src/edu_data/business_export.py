"""Deterministic V1 business-domain export for the local Junyi metadata.

This module deliberately consumes only the small Exercise metadata CSV. It
does not read the research interaction log and never turns a Research Student
or Research Interaction into a platform record. Raw prerequisite text is
emitted as evidence; derived Topic pairs are explicitly review-only candidates.
"""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

from edu_data.data0 import EXERCISE_COLUMNS, graph_metrics


EXPORT_FORMAT_VERSION = "1.0.0"
INPUT_ENCODING = "utf-8-sig"
MISSING_MARKERS = {"", "n/a", "na", "null", "none"}
NON_MATH_AREAS = {"biology"}
OUTPUT_FILENAMES = (
    "manifest.json",
    "areas.jsonl",
    "topics.jsonl",
    "exercises.jsonl",
    "exercise_topic_mapping.jsonl",
    "prerequisite_raw_evidence.jsonl",
    "prerequisite_topic_candidates.jsonl",
    "display_mapping.jsonl",
    "quality_report.json",
)

# These V1 curated display values supplement rather than replace source
# identifiers. They make no assertion that a Topic is a minimum pedagogical
# knowledge concept.
AREA_DISPLAY_ZH = {
    "algebra": "代数",
    "analytic-geometry": "解析几何",
    "arithmetic": "算术",
    "biology": "生物学",
    "calculus": "微积分",
    "geometry": "几何",
    "logics": "逻辑",
    "probability-statistics": "概率与统计",
}
TOPIC_DISPLAY_ZH = {
    "absolute-value": "绝对值",
    "addition-subtraction": "加法与减法",
    "algebra-functions": "代数函数",
    "area-perimeter-and-volume": "面积、周长与体积",
    "basic-geometry": "基础几何",
    "biology": "生物学",
    "calculus": "微积分",
    "circle-properties": "圆的性质",
    "complex-numbers": "复数",
    "congruent-triangles": "全等三角形",
    "conic-sections": "圆锥曲线",
    "decimals": "小数",
    "exponents-radicals": "指数与根式",
    "factors-multiples": "因数与倍数",
    "fractions": "分数",
    "linear-equations-and-inequalitie": "线性方程与不等式",
    "logical-reasoning": "逻辑推理",
    "multiplication-division": "乘法与除法",
    "order-of-operations": "运算顺序",
    "parallel-and-perpendicular-lines": "平行线与垂线",
    "polynomials": "多项式",
    "probability": "概率",
    "pythagorean-theorem": "勾股定理",
    "quadrilaterals-and-polygons": "四边形与多边形",
    "quadtratics": "二次方程与二次函数",
    "quantity-sense": "数感",
    "rates-and-ratios": "比率与比",
    "ratio-percentage": "比例与百分比",
    "segments-and-angles": "线段与角",
    "sequence_and_series": "数列与级数",
    "similar-triangle": "相似三角形",
    "solving-linear-equations-and-inequalities": "解线性方程与不等式",
    "statistics": "统计",
    "systems-of-eq-and-ineq": "方程组与不等式组",
    "telling-time": "认识时间",
    "transformations": "几何变换",
    "triangle-properties": "三角形性质",
    "trigonometry": "三角函数",
    "unit-conversion": "单位换算",
    "vectors-matrix": "向量与矩阵",
}


class DataConsistencyError(ValueError):
    """The metadata input cannot be safely projected under the V1 contract."""


def normalise(value: str | None) -> str:
    return "" if value is None else value.strip()


def is_missing(value: str | None) -> bool:
    return normalise(value).casefold() in MISSING_MARKERS


def external_area_id(raw_area: str) -> str:
    return f"junyi-area:{raw_area}"


def external_topic_id(raw_topic: str) -> str:
    return f"junyi-topic:{raw_topic}"


def provenance(row_number: int | None = None) -> dict[str, Any]:
    result: dict[str, Any] = {
        "dataset": "JUNYI_EXERCISE_METADATA",
        "source_type": "THIRD_PARTY_PROCESSED",
        "input_encoding": "UTF-8 with optional BOM",
    }
    if row_number is not None:
        result["source_metadata_row_number"] = row_number
    return result


def stable_write_json(path: Path, payload: Mapping[str, Any]) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def stable_write_jsonl(path: Path, records: Iterable[Mapping[str, Any]]) -> int:
    count = 0
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for record in records:
            handle.write(
                json.dumps(record, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
            )
            handle.write("\n")
            count += 1
    return count


def read_metadata(path: Path) -> list[dict[str, Any]]:
    if not path.is_file():
        raise FileNotFoundError(f"Missing required metadata file: {path}")
    with path.open("r", encoding=INPUT_ENCODING, newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != EXERCISE_COLUMNS:
            raise DataConsistencyError(
                "Exercise metadata columns do not match the frozen V1 input schema: "
                f"expected {EXERCISE_COLUMNS!r}, got {reader.fieldnames!r}"
            )
        records: list[dict[str, Any]] = []
        for source_row_number, source_row in enumerate(reader, start=2):
            fields = {column: source_row.get(column, "") or "" for column in EXERCISE_COLUMNS}
            external_id = normalise(fields["name"])
            if not external_id:
                raise DataConsistencyError(
                    f"Metadata row {source_row_number} has no Exercise external ID"
                )
            records.append(
                {
                    "source_row_number": source_row_number,
                    "fields": fields,
                    "external_id": external_id,
                    "raw_area": normalise(fields["area"]),
                    "raw_topic": normalise(fields["topic"]),
                }
            )
    if not records:
        raise DataConsistencyError("Exercise metadata contains no data rows")
    return records


def classify_exercise(record: Mapping[str, Any], duplicate_ids: set[str]) -> str:
    if record["external_id"] in duplicate_ids:
        return "QUARANTINED_DUPLICATE_EXTERNAL_ID"
    if is_missing(record["raw_area"]) or is_missing(record["raw_topic"]):
        return "QUARANTINED_MISSING_AREA_OR_TOPIC"
    if record["raw_area"] in NON_MATH_AREAS:
        return "QUARANTINED_NON_MATH_AREA"
    return "ELIGIBLE_FOR_IMPORT"


def source_display_status(mapping_status: str) -> str:
    return "QUARANTINED" if mapping_status.startswith("QUARANTINED") else "UNREVIEWED"


def prepare_output_root(output_root: Path) -> None:
    output_root.mkdir(parents=True, exist_ok=True)
    for filename in OUTPUT_FILENAMES:
        target = output_root / filename
        if target.exists():
            target.unlink()


def build_export(metadata_path: Path) -> dict[str, Any]:
    """Build the complete export representation without writing output files."""

    records = read_metadata(metadata_path)
    identifier_counts = Counter(record["external_id"] for record in records)
    duplicate_ids = {
        external_id for external_id, count in identifier_counts.items() if count > 1
    }
    records_by_id: dict[str, dict[str, Any]] = {
        record["external_id"]: record
        for record in records
        if record["external_id"] not in duplicate_ids
    }
    for record in records:
        record["business_mapping_status"] = classify_exercise(record, duplicate_ids)

    raw_areas = {
        record["raw_area"] for record in records if not is_missing(record["raw_area"])
    }
    raw_topics = {
        record["raw_topic"] for record in records if not is_missing(record["raw_topic"])
    }
    unmapped_areas = sorted(raw_areas - set(AREA_DISPLAY_ZH))
    unmapped_topics = sorted(raw_topics - set(TOPIC_DISPLAY_ZH))
    if unmapped_areas or unmapped_topics:
        raise DataConsistencyError(
            "V1 Chinese mapping is incomplete: "
            f"areas={unmapped_areas!r}, topics={unmapped_topics!r}"
        )

    topic_areas: dict[str, set[str]] = defaultdict(set)
    for record in records:
        if not is_missing(record["raw_area"]) and not is_missing(record["raw_topic"]):
            topic_areas[record["raw_topic"]].add(record["raw_area"])
    multi_area_topics = {
        topic: sorted(areas) for topic, areas in topic_areas.items() if len(areas) != 1
    }
    if multi_area_topics:
        raise DataConsistencyError(
            "A Topic has no single Area parent: " + repr(multi_area_topics)
        )

    area_records = []
    for raw_area in sorted(raw_areas):
        status = (
            "QUARANTINED_NON_MATH_AREA"
            if raw_area in NON_MATH_AREAS
            else "ELIGIBLE_FOR_IMPORT"
        )
        area_records.append(
            {
                "area_external_id": external_area_id(raw_area),
                "business_mapping_status": status,
                "display_mapping_status": "REVIEWED",
                "display_name_zh": AREA_DISPLAY_ZH[raw_area],
                "provenance": provenance(),
                "raw_area": raw_area,
                "record_type": "AREA",
            }
        )

    topic_records = []
    for raw_topic in sorted(raw_topics):
        raw_area = next(iter(topic_areas[raw_topic]))
        status = (
            "QUARANTINED_NON_MATH_AREA"
            if raw_area in NON_MATH_AREAS
            else "ELIGIBLE_FOR_IMPORT"
        )
        topic_records.append(
            {
                "area_external_id": external_area_id(raw_area),
                "business_mapping_status": status,
                "display_mapping_status": "REVIEWED",
                "display_name_zh": TOPIC_DISPLAY_ZH[raw_topic],
                "provenance": provenance(),
                "raw_topic": raw_topic,
                "record_type": "TOPIC",
                "topic_external_id": external_topic_id(raw_topic),
            }
        )

    exercise_records = []
    mapping_records = []
    display_records = []
    for area_record in area_records:
        display_records.append(
            {
                "display_mapping_status": area_record["display_mapping_status"],
                "display_name_zh": area_record["display_name_zh"],
                "entity_external_id": area_record["area_external_id"],
                "entity_type": "AREA",
                "provenance": area_record["provenance"],
                "raw_value": area_record["raw_area"],
            }
        )
    for topic_record in topic_records:
        display_records.append(
            {
                "display_mapping_status": topic_record["display_mapping_status"],
                "display_name_zh": topic_record["display_name_zh"],
                "entity_external_id": topic_record["topic_external_id"],
                "entity_type": "TOPIC",
                "provenance": topic_record["provenance"],
                "raw_value": topic_record["raw_topic"],
            }
        )
    for record in records:
        status = record["business_mapping_status"]
        fields = record["fields"]
        pretty_display = fields["pretty_display_name"] or None
        exercise_records.append(
            {
                "business_mapping_status": status,
                "display_mapping_status": source_display_status(status),
                "display_name_zh": pretty_display if status == "ELIGIBLE_FOR_IMPORT" else None,
                "exercise_external_id": record["external_id"],
                "provenance": provenance(record["source_row_number"]),
                "question_content_status": "NOT_PRESENT_METADATA_ONLY",
                "raw_area": record["raw_area"] or None,
                "raw_source_fields": fields,
                "raw_topic": record["raw_topic"] or None,
                "record_type": "EXERCISE",
                "source_metadata_row_number": record["source_row_number"],
            }
        )
        mapping_records.append(
            {
                "area_external_id": (
                    external_area_id(record["raw_area"])
                    if not is_missing(record["raw_area"])
                    else None
                ),
                "business_mapping_status": status,
                "exercise_external_id": record["external_id"],
                "mapping_id": f"junyi-exercise-topic-row:{record['source_row_number']}",
                "provenance": provenance(record["source_row_number"]),
                "raw_area": record["raw_area"] or None,
                "raw_topic": record["raw_topic"] or None,
                "source_metadata_row_number": record["source_row_number"],
                "topic_external_id": (
                    external_topic_id(record["raw_topic"])
                    if not is_missing(record["raw_topic"])
                    else None
                ),
            }
        )
        display_records.append(
            {
                "display_mapping_status": source_display_status(status),
                "display_name_zh": pretty_display,
                "entity_external_id": record["external_id"],
                "entity_type": "EXERCISE",
                "provenance": provenance(record["source_row_number"]),
                "raw_value": fields["name"],
                "source_metadata_row_number": record["source_row_number"],
            }
        )

    raw_evidence = []
    candidate_sources: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    same_topic_collapsed = 0
    topic_projection_statuses: Counter[str] = Counter()
    for dependent_record in records:
        dependent_id = dependent_record["external_id"]
        tokens = [token.strip() for token in dependent_record["fields"]["prerequisites"].split(",")]
        for token_position, prerequisite_id in enumerate(tokens, start=1):
            if is_missing(prerequisite_id):
                continue
            evidence_id = (
                f"junyi-prerequisite-row:{dependent_record['source_row_number']}:"
                f"token:{token_position}"
            )
            identity_status = "RESOLVED_EXERCISE_ENDPOINTS"
            topic_status = "PENDING"
            if dependent_id in duplicate_ids:
                identity_status = "AMBIGUOUS_DEPENDENT_EXERCISE_ID"
                topic_status = "UNRESOLVED_AMBIGUOUS_EXERCISE_ID"
            elif prerequisite_id not in identifier_counts:
                identity_status = "UNRESOLVED_PREREQUISITE_EXERCISE_ID"
                topic_status = "UNRESOLVED_PREREQUISITE_EXERCISE_ID"
            elif prerequisite_id in duplicate_ids:
                identity_status = "AMBIGUOUS_PREREQUISITE_EXERCISE_ID"
                topic_status = "UNRESOLVED_AMBIGUOUS_EXERCISE_ID"
            else:
                prerequisite_record = records_by_id[prerequisite_id]
                prerequisite_status = prerequisite_record["business_mapping_status"]
                dependent_status = dependent_record["business_mapping_status"]
                if (
                    prerequisite_status == "QUARANTINED_MISSING_AREA_OR_TOPIC"
                    or dependent_status == "QUARANTINED_MISSING_AREA_OR_TOPIC"
                ):
                    topic_status = "UNRESOLVED_MISSING_AREA_OR_TOPIC"
                elif (
                    prerequisite_status == "QUARANTINED_NON_MATH_AREA"
                    or dependent_status == "QUARANTINED_NON_MATH_AREA"
                ):
                    topic_status = "QUARANTINED_NON_MATH_AREA"
                else:
                    prerequisite_topic = prerequisite_record["raw_topic"]
                    dependent_topic = dependent_record["raw_topic"]
                    if prerequisite_topic == dependent_topic:
                        topic_status = "COLLAPSED_SAME_TOPIC"
                        same_topic_collapsed += 1
                    else:
                        topic_status = "MAPPED_TOPIC_CANDIDATE"
                        candidate_sources[(prerequisite_topic, dependent_topic)].append(
                            {
                                "evidence_id": evidence_id,
                                "prerequisite_exercise_external_id": prerequisite_id,
                                "dependent_exercise_external_id": dependent_id,
                            }
                        )
            topic_projection_statuses[topic_status] += 1
            raw_evidence.append(
                {
                    "dependent_exercise_external_id": dependent_id,
                    "evidence_id": evidence_id,
                    "identity_resolution_status": identity_status,
                    "prerequisite_exercise_external_id": prerequisite_id,
                    "provenance": provenance(dependent_record["source_row_number"]),
                    "raw_prerequisites_cell": dependent_record["fields"]["prerequisites"],
                    "relation_type": "PREREQUISITE_RAW_UNVERIFIED",
                    "source_metadata_row_number": dependent_record["source_row_number"],
                    "token_position": token_position,
                    "topic_projection_status": topic_status,
                    "verified": False,
                }
            )

    eligible_topics = sorted(
        topic["raw_topic"]
        for topic in topic_records
        if topic["business_mapping_status"] == "ELIGIBLE_FOR_IMPORT"
    )
    candidate_records = []
    candidate_pairs = []
    for (prerequisite_topic, dependent_topic), sources in sorted(candidate_sources.items()):
        candidate_pairs.append((prerequisite_topic, dependent_topic))
        candidate_records.append(
            {
                "candidate_id": (
                    "junyi-topic-prerequisite:"
                    f"{prerequisite_topic}->{dependent_topic}"
                ),
                "candidate_status": "REVIEW_REQUIRED_NOT_PUBLISHED",
                "dependent_topic_external_id": external_topic_id(dependent_topic),
                "derivation_policy_version": "junyi-topic-projection-v1",
                "evidence_count": len(sources),
                "raw_evidence_ids": [source["evidence_id"] for source in sources],
                "prerequisite_topic_external_id": external_topic_id(prerequisite_topic),
                "published_graph_status": "NOT_PUBLISHED",
                "relation_type": "PREREQUISITE_TOPIC_CANDIDATE",
            }
        )
    candidate_graph = graph_metrics(eligible_topics, candidate_pairs, directed=True)

    source_stat = metadata_path.stat()
    status_counts = Counter(record["business_mapping_status"] for record in records)
    exercise_chinese_display_count = sum(
        bool(record["fields"]["pretty_display_name"]) for record in records
    )
    raw_edge_tokens = len(raw_evidence)
    identity_resolved_edges = sum(
        item["identity_resolution_status"] == "RESOLVED_EXERCISE_ENDPOINTS"
        for item in raw_evidence
    )
    resolved_exercise_pairs = [
        (
            item["prerequisite_exercise_external_id"],
            item["dependent_exercise_external_id"],
        )
        for item in raw_evidence
        if item["identity_resolution_status"] == "RESOLVED_EXERCISE_ENDPOINTS"
    ]
    raw_exercise_graph = graph_metrics(
        records_by_id.keys(), resolved_exercise_pairs, directed=True
    )
    candidate_input_edges = topic_projection_statuses["MAPPED_TOPIC_CANDIDATE"]
    topic_projection_unresolved_edges = sum(
        count
        for status, count in topic_projection_statuses.items()
        if status.startswith("UNRESOLVED")
    )
    quality_report = {
        "business_boundary": {
            "area_is_not_topic": True,
            "exercise_is_not_question": True,
            "raw_prerequisite_is_not_published_graph": True,
            "research_interactions_read": False,
            "research_students_exported": False,
        },
        "counts": {
            "area_records": len(area_records),
            "business_mapping_status": dict(sorted(status_counts.items())),
            "exercise_metadata_rows": len(records),
            "exercise_topic_mapping_records": len(mapping_records),
            "topic_records": len(topic_records),
            "unique_exercise_external_ids": len(identifier_counts),
            "unique_import_eligible_exercise_external_ids": sum(
                1
                for record in records
                if record["business_mapping_status"] == "ELIGIBLE_FOR_IMPORT"
            ),
        },
        "display_mapping": {
            "area_chinese_mapping_count": len(area_records),
            "exercise_source_chinese_display_count": exercise_chinese_display_count,
            "exercise_source_chinese_display_missing_count": len(records)
            - exercise_chinese_display_count,
            "topic_chinese_mapping_count": len(topic_records),
        },
        "gate": {
            "status": "GO_FOUNDATION_V1",
            "basis": [
                "Every non-missing Area and Topic has an explicit Chinese display mapping.",
                "Each non-missing Topic has exactly one Area parent.",
                "Duplicate IDs, missing classification, and non-math records are quarantined rather than imported as business identities.",
                "Raw prerequisite evidence and derived Topic candidates remain separate from any Published Graph.",
            ],
        },
        "input": {
            "columns": EXERCISE_COLUMNS,
            "encoding": "UTF-8 with optional BOM",
            "metadata_path": str(metadata_path.resolve()),
            "row_count": len(records),
            "size_bytes": source_stat.st_size,
        },
        "prerequisite_projection": {
            "all_raw_prerequisite_token_rows": raw_edge_tokens,
            "candidate_topic_pair_count": len(candidate_records),
            "collapsed_same_topic_edge_rows": same_topic_collapsed,
            "duplicate_topic_pair_collapsed_edge_rows": candidate_input_edges
            - len(candidate_records),
            "identity_resolved_exercise_edge_rows": identity_resolved_edges,
            "identity_unresolved_exercise_edge_rows": raw_edge_tokens - identity_resolved_edges,
            "mapped_topic_candidate_input_edge_rows": candidate_input_edges,
            "raw_identity_resolved_exercise_graph": {
                key: raw_exercise_graph[key]
                for key in (
                    "nodes",
                    "edges",
                    "raw_edge_rows",
                    "self_loop_count",
                    "duplicate_edge_count",
                    "cycle_component_count",
                    "cycle_components",
                )
            },
            "topic_projection_unresolved_edge_rows": topic_projection_unresolved_edges,
            "topic_projection_status": dict(sorted(topic_projection_statuses.items())),
        },
        "topic_candidate_graph": {
            key: candidate_graph[key]
            for key in (
                "nodes",
                "edges",
                "raw_edge_rows",
                "self_loop_count",
                "duplicate_edge_count",
                "components",
                "largest_component_size",
                "cycle_component_count",
                "cycle_components",
            )
        },
    }
    manifest = {
        "business_boundary": quality_report["business_boundary"],
        "dataset": "JUNYI_EXERCISE_METADATA",
        "deterministic_ordering": {
            "areas": "raw_area ascending",
            "exercises": "source_metadata_row_number ascending",
            "prerequisite_evidence": "source_metadata_row_number then token_position ascending",
            "topic_candidates": "prerequisite_topic then dependent_topic ascending",
            "topics": "raw_topic ascending",
        },
        "export_format_version": EXPORT_FORMAT_VERSION,
        "input": quality_report["input"],
        "output_record_counts": {
            "areas.jsonl": len(area_records),
            "display_mapping.jsonl": len(display_records),
            "exercise_topic_mapping.jsonl": len(mapping_records),
            "exercises.jsonl": len(exercise_records),
            "prerequisite_raw_evidence.jsonl": len(raw_evidence),
            "prerequisite_topic_candidates.jsonl": len(candidate_records),
            "topics.jsonl": len(topic_records),
        },
        "status": "GO_FOUNDATION_V1",
    }
    return {
        "areas": area_records,
        "candidates": candidate_records,
        "display_mappings": display_records,
        "exercises": exercise_records,
        "manifest": manifest,
        "mappings": mapping_records,
        "quality_report": quality_report,
        "raw_evidence": raw_evidence,
        "topics": topic_records,
    }


def export_business(metadata_path: Path, output_root: Path) -> dict[str, Any]:
    """Build and write every V1 business-export artifact in a stable order."""

    payload = build_export(metadata_path)
    output_root = output_root.resolve()
    prepare_output_root(output_root)
    stable_write_json(output_root / "manifest.json", payload["manifest"])
    stable_write_jsonl(output_root / "areas.jsonl", payload["areas"])
    stable_write_jsonl(output_root / "topics.jsonl", payload["topics"])
    stable_write_jsonl(output_root / "exercises.jsonl", payload["exercises"])
    stable_write_jsonl(output_root / "exercise_topic_mapping.jsonl", payload["mappings"])
    stable_write_jsonl(output_root / "prerequisite_raw_evidence.jsonl", payload["raw_evidence"])
    stable_write_jsonl(output_root / "prerequisite_topic_candidates.jsonl", payload["candidates"])
    stable_write_jsonl(output_root / "display_mapping.jsonl", payload["display_mappings"])
    stable_write_json(output_root / "quality_report.json", payload["quality_report"])
    return payload["quality_report"]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Export Junyi Exercise metadata as a deterministic V1 business baseline."
    )
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    report = export_business(args.metadata, args.output)
    print(
        json.dumps(
            {
                "gate": report["gate"]["status"],
                "output": str(args.output.resolve()),
                "topic_candidate_edges": report["topic_candidate_graph"]["edges"],
            },
            ensure_ascii=False,
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
