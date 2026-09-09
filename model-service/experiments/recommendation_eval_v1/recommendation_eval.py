"""Leakage-safe offline recommendation evaluation on local Junyi Research Data.

The evaluator is intentionally a small, transparent baseline comparison.  It
does not create a Platform User or AnswerRecord, read a Published Graph, or
write raw interactions into the repository.  The only graph input it inspects
is the Foundation V1 candidate export; when that export remains unpublished or
cyclic, graph methods are reported as inconclusive rather than silently being
run on a replacement graph.
"""

from __future__ import annotations

import argparse
import bisect
import csv
import json
import math
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence


SCHEMA_VERSION = "recommendation-eval-v1.0.0"
EXPERIMENT_ROOT = Path(__file__).resolve().parent
TOP_K = 3
COHORT_MODULUS = 257
COHORT_REMAINDER = 17
MINIMUM_ELIGIBLE_INTERACTIONS = 20
HISTORY_NUMERATOR = 3
HISTORY_DENOMINATOR = 5
GRAPH_INCONCLUSIVE_STATUS = "INCONCLUSIVE_GRAPH_SEMANTICS"
RAW_REQUIRED_COLUMNS = {"user_id", "exercise", "time_done", "correct"}


@dataclass(frozen=True)
class Event:
    """One mapped Research Interaction retained only in process memory."""

    student_id: int
    topic_external_id: str
    correct: bool
    timestamp: int
    source_row: int

    @property
    def order_key(self) -> tuple[int, int]:
        return (self.timestamp, self.source_row)


@dataclass(frozen=True)
class Case:
    """A student-local chronological history/future recommendation case."""

    case_id: str
    student_id: int
    history: tuple[Event, ...]
    future: tuple[Event, ...]

    @property
    def cutoff(self) -> tuple[int, int]:
        return self.history[-1].order_key


@dataclass(frozen=True)
class FoundationCatalog:
    topics: tuple[str, ...]
    topic_raw_names: Mapping[str, str]
    eligible_exercise_topics: Mapping[str, str]
    graph_audit: Mapping[str, Any]


@dataclass(frozen=True)
class Recommendation:
    topic_external_id: str
    score: float
    global_popularity_before_cutoff: int


def _read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def _read_jsonl(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for row_number, line in enumerate(handle, start=1):
            if not line.strip():
                raise ValueError(f"Blank JSONL line is not allowed: {path}:{row_number}")
            value = json.loads(line)
            if not isinstance(value, dict):
                raise ValueError(f"Expected a JSON object: {path}:{row_number}")
            records.append(value)
    return records


def _candidate_record_is_unpublished(record: Mapping[str, Any]) -> bool:
    values = {str(value) for value in record.values() if isinstance(value, str)}
    return "REVIEW_REQUIRED_NOT_PUBLISHED" in values or "NOT_PUBLISHED" in values


def load_foundation_catalog(export_root: Path) -> FoundationCatalog:
    """Load only Foundation's frozen export records needed by the evaluation."""

    required = (
        "topics.jsonl",
        "exercises.jsonl",
        "prerequisite_topic_candidates.jsonl",
        "quality_report.json",
    )
    missing = [name for name in required if not (export_root / name).is_file()]
    if missing:
        raise FileNotFoundError(
            f"Foundation export is incomplete at {export_root}: missing {', '.join(missing)}"
        )

    topic_records = _read_jsonl(export_root / "topics.jsonl")
    exercise_records = _read_jsonl(export_root / "exercises.jsonl")
    candidate_records = _read_jsonl(export_root / "prerequisite_topic_candidates.jsonl")
    quality_report = _read_json(export_root / "quality_report.json")

    topic_raw_names: dict[str, str] = {}
    topic_external_ids_by_raw_name: dict[str, str] = {}
    for record in topic_records:
        external_id = record.get("topic_external_id")
        raw_topic = record.get("raw_topic")
        if not isinstance(external_id, str) or not isinstance(raw_topic, str):
            raise ValueError("Foundation topics export lacks topic external identity or raw Topic")
        topic_raw_names[external_id] = raw_topic
        topic_external_ids_by_raw_name[raw_topic] = external_id

    eligible_exercise_topics: dict[str, str] = {}
    for record in exercise_records:
        if record.get("business_mapping_status") != "ELIGIBLE_FOR_IMPORT":
            continue
        external_id = record.get("exercise_external_id")
        raw_topic = record.get("raw_topic")
        topic_external_id = topic_external_ids_by_raw_name.get(raw_topic)
        if not isinstance(external_id, str) or not isinstance(topic_external_id, str):
            raise ValueError("Eligible Foundation Exercise lacks external identity or Topic identity")
        if topic_external_id not in topic_raw_names:
            raise ValueError("Eligible Foundation Exercise references an unknown Topic")
        if external_id in eligible_exercise_topics:
            raise ValueError("Foundation marked a duplicate Exercise identity eligible for import")
        eligible_exercise_topics[external_id] = topic_external_id

    topics = tuple(sorted(set(eligible_exercise_topics.values())))
    if not topics:
        raise ValueError("Foundation export has no eligible math Topics")

    # The Foundation contract calls these relations review-only candidates, not a
    # Published Graph.  A cyclic SCC is an independent blocker.  The evaluator
    # deliberately treats either condition as a semantic stop for M2/M3.
    unpublished_records = sum(
        1 for record in candidate_records if _candidate_record_is_unpublished(record)
    )
    candidate_graph = quality_report.get("topic_candidate_graph")
    if not isinstance(candidate_graph, dict):
        raise ValueError("Foundation quality report lacks topic_candidate_graph")
    cycle_component_count = candidate_graph.get("cycle_component_count")
    if not isinstance(cycle_component_count, int) or cycle_component_count < 0:
        raise ValueError("Foundation quality report has invalid candidate cyclic SCC count")
    graph_audit = {
        "foundation_candidate_record_count": len(candidate_records),
        "foundation_candidate_unpublished_record_count": unpublished_records,
        "foundation_candidate_graph_nodes": candidate_graph.get("nodes"),
        "foundation_candidate_graph_edges": candidate_graph.get("edges"),
        "foundation_candidate_graph_self_loop_count": candidate_graph.get("self_loop_count"),
        "foundation_candidate_graph_cyclic_scc_count": cycle_component_count,
        "status": GRAPH_INCONCLUSIVE_STATUS,
        "reason": (
            "Foundation Topic relations are review-only candidates, not a Published Graph; "
            "Foundation also reports cyclic candidate SCCs. M2/M3 are not run."
        ),
    }
    return FoundationCatalog(
        topics=topics,
        topic_raw_names=topic_raw_names,
        eligible_exercise_topics=eligible_exercise_topics,
        graph_audit=graph_audit,
    )


def _parse_positive_int(value: str | None, field: str, row_number: int) -> int:
    try:
        parsed = int(value or "")
    except ValueError as exc:
        raise ValueError(f"Invalid {field} at ProblemLog row {row_number}") from exc
    if parsed < 0:
        raise ValueError(f"Negative {field} at ProblemLog row {row_number}")
    return parsed


def _parse_correct(value: str | None, row_number: int) -> bool:
    normalized = (value or "").strip().casefold()
    if normalized == "true":
        return True
    if normalized == "false":
        return False
    raise ValueError(f"Invalid correct value at ProblemLog row {row_number}")


def load_bucketed_events(
    problem_log: Path, eligible_exercise_topics: Mapping[str, str]
) -> tuple[dict[int, list[Event]], dict[str, int]]:
    """Read one fixed, label-independent Research Student bucket.

    Membership is determined only by numeric user_id, mapped Exercise, and a
    valid timestamp.  The correctness column is parsed after that membership
    predicate solely as an evaluation outcome.  No values are written out.
    """

    selected: dict[int, list[Event]] = defaultdict(list)
    counters: Counter[str] = Counter()
    with problem_log.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        header = set(reader.fieldnames or [])
        if not RAW_REQUIRED_COLUMNS.issubset(header):
            raise ValueError(
                "ProblemLog misses required columns: "
                + ", ".join(sorted(RAW_REQUIRED_COLUMNS - header))
            )
        for row_number, row in enumerate(reader, start=2):
            counters["raw_problem_log_rows_scanned"] += 1
            raw_user_id = (row.get("user_id") or "").strip()
            try:
                student_id = int(raw_user_id)
            except ValueError:
                counters["rows_excluded_non_numeric_student_id"] += 1
                continue
            exercise_id = (row.get("exercise") or "").strip()
            topic_external_id = eligible_exercise_topics.get(exercise_id)
            if topic_external_id is None:
                counters["rows_excluded_non_eligible_exercise"] += 1
                continue
            try:
                timestamp = _parse_positive_int(row.get("time_done"), "time_done", row_number)
            except ValueError:
                counters["rows_excluded_invalid_timestamp"] += 1
                continue
            if student_id % COHORT_MODULUS != COHORT_REMAINDER:
                counters["rows_excluded_outside_fixed_bucket"] += 1
                continue
            correct = _parse_correct(row.get("correct"), row_number)
            selected[student_id].append(
                Event(
                    student_id=student_id,
                    topic_external_id=topic_external_id,
                    correct=correct,
                    timestamp=timestamp,
                    source_row=row_number,
                )
            )
            counters["selected_bucket_mapped_rows"] += 1
    return dict(selected), dict(sorted(counters.items()))


def make_cases(events_by_student: Mapping[int, Sequence[Event]]) -> tuple[list[Case], dict[str, int]]:
    """Apply the fixed chronological split without inspecting outcomes."""

    cases: list[Case] = []
    counters: Counter[str] = Counter()
    for student_id in sorted(events_by_student):
        ordered = tuple(sorted(events_by_student[student_id], key=lambda event: event.order_key))
        if len(ordered) < MINIMUM_ELIGIBLE_INTERACTIONS:
            counters["students_excluded_below_minimum_interactions"] += 1
            continue
        history_size = (len(ordered) * HISTORY_NUMERATOR) // HISTORY_DENOMINATOR
        if history_size == 0 or history_size == len(ordered):
            counters["students_excluded_no_nonempty_chronological_split"] += 1
            continue
        cases.append(
            Case(
                case_id=f"case-{len(cases) + 1:04d}",
                student_id=student_id,
                history=ordered[:history_size],
                future=ordered[history_size:],
            )
        )
    counters["cases"] = len(cases)
    counters["selected_bucket_students"] = len(events_by_student)
    if not cases:
        raise ValueError("The fixed bucket yielded no chronological evaluation cases")
    return cases, dict(sorted(counters.items()))


def build_population_index(cases: Iterable[Case]) -> dict[str, list[tuple[int, int]]]:
    """Create timestamp/source-order indices for population frequency as of a cutoff."""

    result: dict[str, list[tuple[int, int]]] = defaultdict(list)
    for case in cases:
        for event in (*case.history, *case.future):
            result[event.topic_external_id].append(event.order_key)
    for keys in result.values():
        keys.sort()
    return dict(result)


def history_topic_stats(case: Case) -> dict[str, dict[str, int]]:
    stats: dict[str, dict[str, int]] = defaultdict(lambda: {"correct": 0, "incorrect": 0})
    for event in case.history:
        stats[event.topic_external_id]["correct" if event.correct else "incorrect"] += 1
    return dict(stats)


def population_count_before_cutoff(
    topic_external_id: str,
    case: Case,
    population_index: Mapping[str, Sequence[tuple[int, int]]],
    stats: Mapping[str, Mapping[str, int]],
) -> int:
    # All target-student history rows are present in the index; subtracting them
    # prevents the non-personalized M0 baseline from borrowing the target's own
    # history. Future rows are after the chronological cutoff and never count.
    keys = population_index.get(topic_external_id, ())
    all_students_before = bisect.bisect_right(keys, case.cutoff)
    target_history_count = sum(stats.get(topic_external_id, {}).values())
    return max(0, all_students_before - target_history_count)


def recommend_m0_popularity(
    case: Case,
    topics: Sequence[str],
    population_index: Mapping[str, Sequence[tuple[int, int]]],
    stats: Mapping[str, Mapping[str, int]],
) -> list[Recommendation]:
    ranked = [
        Recommendation(
            topic_external_id=topic,
            score=float(population_count_before_cutoff(topic, case, population_index, stats)),
            global_popularity_before_cutoff=population_count_before_cutoff(
                topic, case, population_index, stats
            ),
        )
        for topic in topics
    ]
    return sorted(ranked, key=lambda item: (-item.score, item.topic_external_id))[:TOP_K]


def recommend_m1_mastery(case: Case, topics: Sequence[str]) -> list[Recommendation]:
    """Rank posterior Topic error rates from this case's history only."""

    stats = history_topic_stats(case)
    ranked: list[Recommendation] = []
    for topic in topics:
        correct = stats.get(topic, {}).get("correct", 0)
        incorrect = stats.get(topic, {}).get("incorrect", 0)
        observations = correct + incorrect
        # Beta(1, 1) posterior mean of Topic error.  This is a transparent
        # RuleBeta-style weakness score, not a Rasch theta or a platform mastery.
        posterior_error = (incorrect + 1) / (observations + 2)
        ranked.append(
            Recommendation(
                topic_external_id=topic,
                score=posterior_error,
                global_popularity_before_cutoff=0,
            )
        )
    return sorted(ranked, key=lambda item: (-item.score, item.topic_external_id))[:TOP_K]


def _future_topic_sets(case: Case) -> tuple[set[str], set[str]]:
    future_topics = {event.topic_external_id for event in case.future}
    future_error_topics = {event.topic_external_id for event in case.future if not event.correct}
    return future_topics, future_error_topics


def _weighted_future_recall(
    recommendations: Sequence[Recommendation],
    future_topics: set[str],
    popularity: Mapping[str, int],
) -> float:
    if not future_topics:
        return 0.0
    weights = {
        topic: 1.0 / math.log2(2.0 + popularity.get(topic, 0)) for topic in future_topics
    }
    recommendation_topics = {item.topic_external_id for item in recommendations}
    numerator = sum(weight for topic, weight in weights.items() if topic in recommendation_topics)
    return numerator / sum(weights.values())


def aggregate_metrics(
    recommendations_by_case: Mapping[str, Sequence[Recommendation]],
    cases: Sequence[Case],
    topics: Sequence[str],
    population_index: Mapping[str, Sequence[tuple[int, int]]],
) -> dict[str, Any]:
    case_lookup = {case.case_id: case for case in cases}
    hits = 0
    error_eligible_cases = 0
    error_hits = 0
    popularity_adjusted_recall_values: list[float] = []
    covered_topics: set[str] = set()
    recommendation_count = 0
    for case_id, recommendations in recommendations_by_case.items():
        case = case_lookup[case_id]
        recommendation_topics = {item.topic_external_id for item in recommendations}
        future_topics, future_error_topics = _future_topic_sets(case)
        hits += int(bool(recommendation_topics & future_topics))
        if future_error_topics:
            error_eligible_cases += 1
            error_hits += int(bool(recommendation_topics & future_error_topics))
        stats = history_topic_stats(case)
        popularity = {
            topic: population_count_before_cutoff(topic, case, population_index, stats)
            for topic in topics
        }
        popularity_adjusted_recall_values.append(
            _weighted_future_recall(recommendations, future_topics, popularity)
        )
        covered_topics.update(recommendation_topics)
        recommendation_count += len(recommendations)
    cases_evaluated = len(recommendations_by_case)
    return {
        "cases_evaluated": cases_evaluated,
        "future_topic_hit_at_3": hits / cases_evaluated if cases_evaluated else None,
        "future_error_topic_hit_at_3": (
            error_hits / error_eligible_cases if error_eligible_cases else None
        ),
        "future_error_eligible_cases": error_eligible_cases,
        "coverage_at_3": len(covered_topics) / len(topics) if topics else None,
        "covered_topic_count": len(covered_topics),
        "topic_universe_count": len(topics),
        "popularity_adjusted_future_recall_at_3": (
            sum(popularity_adjusted_recall_values) / len(popularity_adjusted_recall_values)
            if popularity_adjusted_recall_values
            else None
        ),
        "mean_recommendation_count": recommendation_count / cases_evaluated if cases_evaluated else None,
    }


def _method_rows(
    cases: Sequence[Case],
    recommendations: Mapping[str, Sequence[Recommendation]],
    method: str,
    method_status: str,
    population_index: Mapping[str, Sequence[tuple[int, int]]],
    topic_raw_names: Mapping[str, str],
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for case in cases:
        future_topics, future_error_topics = _future_topic_sets(case)
        method_recommendations = recommendations.get(case.case_id, ())
        if not method_recommendations:
            rows.append(
                {
                    "case_id": case.case_id,
                    "method": method,
                    "method_status": method_status,
                    "history_interaction_count": len(case.history),
                    "future_interaction_count": len(case.future),
                    "future_topic_count": len(future_topics),
                    "future_error_topic_count": len(future_error_topics),
                    "recommendation_rank": "",
                    "topic_external_id": "",
                    "topic_raw_name": "",
                    "score": "",
                    "global_popularity_before_cutoff": "",
                    "future_topic_hit": "",
                    "future_error_topic_hit": "",
                }
            )
            continue
        stats = history_topic_stats(case)
        for rank, recommendation in enumerate(method_recommendations, start=1):
            popularity = recommendation.global_popularity_before_cutoff
            if method == "M1_MASTERY_ONLY":
                popularity = population_count_before_cutoff(
                    recommendation.topic_external_id, case, population_index, stats
                )
            rows.append(
                {
                    "case_id": case.case_id,
                    "method": method,
                    "method_status": method_status,
                    "history_interaction_count": len(case.history),
                    "future_interaction_count": len(case.future),
                    "future_topic_count": len(future_topics),
                    "future_error_topic_count": len(future_error_topics),
                    "recommendation_rank": rank,
                    "topic_external_id": recommendation.topic_external_id,
                    "topic_raw_name": topic_raw_names[recommendation.topic_external_id],
                    "score": f"{recommendation.score:.12f}",
                    "global_popularity_before_cutoff": popularity,
                    "future_topic_hit": str(
                        recommendation.topic_external_id in future_topics
                    ).lower(),
                    "future_error_topic_hit": str(
                        recommendation.topic_external_id in future_error_topics
                    ).lower(),
                }
            )
    return rows


def _write_json(path: Path, value: Mapping[str, Any]) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def _write_method_rows(path: Path, rows: Sequence[Mapping[str, Any]]) -> None:
    fieldnames = [
        "case_id",
        "method",
        "method_status",
        "history_interaction_count",
        "future_interaction_count",
        "future_topic_count",
        "future_error_topic_count",
        "recommendation_rank",
        "topic_external_id",
        "topic_raw_name",
        "score",
        "global_popularity_before_cutoff",
        "future_topic_hit",
        "future_error_topic_hit",
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def _write_metrics(path: Path, metrics: Mapping[str, Mapping[str, Any]]) -> None:
    fieldnames = [
        "method",
        "method_status",
        "cases_evaluated",
        "future_topic_hit_at_3",
        "future_error_topic_hit_at_3",
        "future_error_eligible_cases",
        "coverage_at_3",
        "covered_topic_count",
        "topic_universe_count",
        "popularity_adjusted_future_recall_at_3",
        "mean_recommendation_count",
        "interpretation",
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        for method in ("M0_POPULARITY", "M1_MASTERY_ONLY", "M2_GRAPH_ONLY", "M3_MASTERY_GRAPH"):
            item = dict(metrics[method])
            item["method"] = method
            writer.writerow({field: item.get(field, "") for field in fieldnames})


def _write_bad_cases(
    path: Path,
    cases: Sequence[Case],
    m0: Mapping[str, Sequence[Recommendation]],
    m1: Mapping[str, Sequence[Recommendation]],
    topic_raw_names: Mapping[str, str],
) -> int:
    records: list[dict[str, Any]] = []
    for case in cases:
        future_topics, future_error_topics = _future_topic_sets(case)
        m0_topics = [item.topic_external_id for item in m0[case.case_id]]
        m1_topics = [item.topic_external_id for item in m1[case.case_id]]
        m0_hit = bool(set(m0_topics) & future_topics)
        m1_hit = bool(set(m1_topics) & future_topics)
        if m0_hit and not m1_hit:
            reason = "M0_FUTURE_TOPIC_HIT_M1_MISS"
        elif not m0_hit and not m1_hit:
            reason = "BOTH_METHODS_FUTURE_TOPIC_MISS"
        elif future_error_topics and not (set(m1_topics) & future_error_topics):
            reason = "M1_FUTURE_ERROR_TOPIC_MISS"
        else:
            continue
        stats = history_topic_stats(case)
        records.append(
            {
                "case_id": case.case_id,
                "selection_reason": reason,
                "history_topic_outcomes": [
                    {
                        "topic_external_id": topic,
                        "topic_raw_name": topic_raw_names[topic],
                        "correct": values["correct"],
                        "incorrect": values["incorrect"],
                    }
                    for topic, values in sorted(stats.items())
                ],
                "future_topic_external_ids": sorted(future_topics),
                "future_error_topic_external_ids": sorted(future_error_topics),
                "m0_recommendations": m0_topics,
                "m1_recommendations": m1_topics,
            }
        )
    records.sort(key=lambda record: (record["selection_reason"], record["case_id"]))
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for record in records[:25]:
            handle.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")
    return min(25, len(records))


def _metric_text(value: Any) -> str:
    return f"{value:.6f}" if isinstance(value, float) else str(value)


def _write_report(
    path: Path,
    summary: Mapping[str, Any],
    metrics: Mapping[str, Mapping[str, Any]],
) -> None:
    m0 = metrics["M0_POPULARITY"]
    m1 = metrics["M1_MASTERY_ONLY"]
    protocol = summary["protocol"]
    counts = summary["counts"]
    report = f"""# R：推荐/路径离线评测

## Gate 与结论

- Gate：`{summary['evaluation_gate']}`
- 结论：`{summary['conclusion']}`
- Rasch：`DEFERRED`

本评测的 M0/M1 是有效的离线 proxy 比较，但不是学习增益或线上产品证据。M2/M3 均为 `{GRAPH_INCONCLUSIVE_STATUS}`：Foundation 图是未发布、待审核的 Topic candidate，且 Foundation 已报告 cyclic SCC；没有将其称为 Published Graph、没有删边或换图。因此本轮不能对“图是否带来增益”作有效结论。

## 冻结方法

- Research Student 仅按 `user_id % {COHORT_MODULUS} == {COHORT_REMAINDER}` 选择；选择与 outcome 无关。
- 仅保留 Foundation `ELIGIBLE_FOR_IMPORT` Exercise 到 Topic 的映射；每位学生按 `time_done` 升序、原始 ProblemLog 行号打破时间相同的并列。
- 至少 {MINIMUM_ELIGIBLE_INTERACTIONS} 条映射交互；前 {HISTORY_NUMERATOR}/{HISTORY_DENOMINATOR} 为 history，剩余为 future。推荐时未读取该学生 future。
- M0 是截至该 student cutoff 的其他 Research Student Topic frequency；M1 是该 student history 的 Beta(1,1) posterior Topic error rate。二者相同候选集和 `@{TOP_K}`。
- `future_topic_hit`、`future_error_topic_hit`、coverage 和 popularity-adjusted recall 都是离线 proxy。future error 仅用作评测目标，未用于推荐；相关性不是由 candidate graph 标注。

## 实际样本

| 项目 | 数值 |
| --- | ---: |
| 原始 ProblemLog 扫描行 | {counts['source_scan']['raw_problem_log_rows_scanned']} |
| 固定 bucket 映射交互 | {counts['source_scan']['selected_bucket_mapped_rows']} |
| 可评 Research Student case | {counts['case_construction']['cases']} |
| Topic 候选集 | {counts['topic_universe']} |
| bad case 记录 | {counts['bad_case_records_written']} |

## M0/M1 指标

| 方法 | future Topic hit@{TOP_K} | future error Topic hit@{TOP_K} | coverage@{TOP_K} | popularity-adjusted future recall@{TOP_K} |
| --- | ---: | ---: | ---: | ---: |
| M0 Popularity | {_metric_text(m0['future_topic_hit_at_3'])} | {_metric_text(m0['future_error_topic_hit_at_3'])} | {_metric_text(m0['coverage_at_3'])} | {_metric_text(m0['popularity_adjusted_future_recall_at_3'])} |
| M1 Mastery-only | {_metric_text(m1['future_topic_hit_at_3'])} | {_metric_text(m1['future_error_topic_hit_at_3'])} | {_metric_text(m1['coverage_at_3'])} | {_metric_text(m1['popularity_adjusted_future_recall_at_3'])} |

完整逐 case 输出在 `method_by_case.csv`，指标在 `metrics.csv`，反例在 `bad_cases.jsonl`。这些文件只用重编号 case，不包含原始 user_id、原始交互或 Platform business records。

## 限制和后续边界

- `INCONCLUSIVE_GRAPH_SEMANTICS` 不等同于“图无价值”；它表示 Foundation candidate 目前不具备可用于图方法的已发布语义。
- `NO_PROVEN_INCREMENT` 未被声明，因为组合方法没有合法图输入可供检验；总体结论必须是 `INCONCLUSIVE`。
- 本轮未做 Rasch，也未做任何平台推荐接入、AnswerRecord 写入或 Question 映射。
"""
    path.write_text(report, encoding="utf-8", newline="\n")


def run(problem_log: Path, foundation_export: Path, output: Path) -> dict[str, Any]:
    if output.resolve() != EXPERIMENT_ROOT.resolve():
        raise ValueError(f"Output must be the isolated experiment directory: {EXPERIMENT_ROOT}")
    catalog = load_foundation_catalog(foundation_export)
    events_by_student, scan_counts = load_bucketed_events(
        problem_log, catalog.eligible_exercise_topics
    )
    cases, case_counts = make_cases(events_by_student)
    population_index = build_population_index(cases)

    m0: dict[str, list[Recommendation]] = {}
    m1: dict[str, list[Recommendation]] = {}
    for case in cases:
        stats = history_topic_stats(case)
        m0[case.case_id] = recommend_m0_popularity(
            case, catalog.topics, population_index, stats
        )
        m1[case.case_id] = recommend_m1_mastery(case, catalog.topics)

    m0_metrics = aggregate_metrics(m0, cases, catalog.topics, population_index)
    m1_metrics = aggregate_metrics(m1, cases, catalog.topics, population_index)
    m0_metrics.update(
        {
            "method_status": "EVALUATED",
            "interpretation": "Population-frequency offline proxy baseline.",
        }
    )
    m1_metrics.update(
        {
            "method_status": "EVALUATED",
            "interpretation": "Student-history RuleBeta-style Topic weakness proxy.",
        }
    )
    graph_metrics = {
        "method_status": GRAPH_INCONCLUSIVE_STATUS,
        "cases_evaluated": 0,
        "future_topic_hit_at_3": None,
        "future_error_topic_hit_at_3": None,
        "future_error_eligible_cases": 0,
        "coverage_at_3": None,
        "covered_topic_count": 0,
        "topic_universe_count": len(catalog.topics),
        "popularity_adjusted_future_recall_at_3": None,
        "mean_recommendation_count": None,
        "interpretation": catalog.graph_audit["reason"],
    }
    metrics: dict[str, dict[str, Any]] = {
        "M0_POPULARITY": m0_metrics,
        "M1_MASTERY_ONLY": m1_metrics,
        "M2_GRAPH_ONLY": dict(graph_metrics),
        "M3_MASTERY_GRAPH": dict(graph_metrics),
    }

    method_rows = []
    method_rows.extend(
        _method_rows(cases, m0, "M0_POPULARITY", "EVALUATED", population_index, catalog.topic_raw_names)
    )
    method_rows.extend(
        _method_rows(cases, m1, "M1_MASTERY_ONLY", "EVALUATED", population_index, catalog.topic_raw_names)
    )
    method_rows.extend(
        _method_rows(cases, {}, "M2_GRAPH_ONLY", GRAPH_INCONCLUSIVE_STATUS, population_index, catalog.topic_raw_names)
    )
    method_rows.extend(
        _method_rows(cases, {}, "M3_MASTERY_GRAPH", GRAPH_INCONCLUSIVE_STATUS, population_index, catalog.topic_raw_names)
    )
    method_rows.sort(
        key=lambda item: (
            item["case_id"],
            item["method"],
            int(item["recommendation_rank"]) if item["recommendation_rank"] else 0,
        )
    )

    bad_case_count = _write_bad_cases(
        output / "bad_cases.jsonl", cases, m0, m1, catalog.topic_raw_names
    )
    summary: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "evaluation_gate": "EVAL_INCONCLUSIVE",
        "conclusion": "INCONCLUSIVE",
        "completion_status": "COMPLETED_WITH_GRAPH_SEMANTICS_LIMITATION",
        "input_boundary": {
            "research_data_only": True,
            "platform_user_created": False,
            "answer_record_created": False,
            "raw_interactions_written_to_repository": False,
            "problem_log_path": str(problem_log),
            "foundation_export_path": str(foundation_export),
        },
        "protocol": {
            "cohort_selector": {
                "rule": f"numeric user_id % {COHORT_MODULUS} == {COHORT_REMAINDER}",
                "membership_fields": ["user_id", "exercise", "time_done"],
                "correct_used_for_membership": False,
            },
            "sequence_order": "time_done ascending; original ProblemLog source row ascending as tie-breaker",
            "minimum_eligible_interactions": MINIMUM_ELIGIBLE_INTERACTIONS,
            "history_fraction": f"{HISTORY_NUMERATOR}/{HISTORY_DENOMINATOR}",
            "recommendation_count": TOP_K,
            "topic_candidate_set": "Foundation ELIGIBLE_FOR_IMPORT Exercise-to-Topic projection",
            "methods": {
                "M0_POPULARITY": "Other Research Students' Topic frequency observed no later than each focal cutoff.",
                "M1_MASTERY_ONLY": "Focal history Beta(1,1) posterior Topic error rate; no Rasch theta.",
                "M2_GRAPH_ONLY": GRAPH_INCONCLUSIVE_STATUS,
                "M3_MASTERY_GRAPH": GRAPH_INCONCLUSIVE_STATUS,
            },
            "rasch": "DEFERRED",
            "future_data_used_for_recommendation": False,
            "offline_proxy_metrics": [
                "future_topic_hit_at_3",
                "future_error_topic_hit_at_3",
                "coverage_at_3",
                "popularity_adjusted_future_recall_at_3",
            ],
        },
        "foundation_graph_audit": dict(catalog.graph_audit),
        "counts": {
            "source_scan": scan_counts,
            "case_construction": case_counts,
            "topic_universe": len(catalog.topics),
            "method_by_case_rows": len(method_rows),
            "bad_case_records_written": bad_case_count,
        },
        "methods": metrics,
        "deferred_or_blocked": [
            "Rasch is DEFERRED by R1.",
            "M2/M3 are INCONCLUSIVE_GRAPH_SEMANTICS; no replacement graph is used.",
            "No product, Platform User, AnswerRecord, Question, or Published Graph mutation is in scope.",
        ],
    }
    _write_method_rows(output / "method_by_case.csv", method_rows)
    _write_metrics(output / "metrics.csv", metrics)
    _write_json(output / "summary.json", summary)
    _write_report(output / "report.md", summary, metrics)
    return summary


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--problem-log", type=Path, required=True)
    parser.add_argument("--foundation-export", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=EXPERIMENT_ROOT)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    summary = run(args.problem_log, args.foundation_export, args.output)
    print(
        "recommendation-eval "
        f"gate={summary['evaluation_gate']} conclusion={summary['conclusion']} "
        f"cases={summary['counts']['case_construction']['cases']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
