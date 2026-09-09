"""Focused scope and leakage tests for recommendation_eval_v1."""

from __future__ import annotations

import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
EXPERIMENT_ROOT = REPOSITORY_ROOT / "model-service/experiments/recommendation_eval_v1"
sys.path.insert(0, str(EXPERIMENT_ROOT))
import recommendation_eval as evaluation  # noqa: E402


def write_jsonl(path: Path, records: list[dict[str, object]]) -> None:
    path.write_text(
        "".join(json.dumps(record, sort_keys=True) + "\n" for record in records),
        encoding="utf-8",
    )


class RecommendationEvalV1Test(unittest.TestCase):
    def test_catalog_marks_unpublished_candidate_graph_inconclusive(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            write_jsonl(
                root / "topics.jsonl",
                [
                    {"topic_external_id": "topic:a", "raw_topic": "a"},
                    {"topic_external_id": "topic:b", "raw_topic": "b"},
                    {"topic_external_id": "topic:c", "raw_topic": "c"},
                ],
            )
            write_jsonl(
                root / "exercises.jsonl",
                [
                    {
                        "business_mapping_status": "ELIGIBLE_FOR_IMPORT",
                        "exercise_external_id": "exercise-a",
                        "raw_topic": "a",
                    },
                    {
                        "business_mapping_status": "ELIGIBLE_FOR_IMPORT",
                        "exercise_external_id": "exercise-b",
                        "raw_topic": "b",
                    },
                    {
                        "business_mapping_status": "ELIGIBLE_FOR_IMPORT",
                        "exercise_external_id": "exercise-c",
                        "raw_topic": "c",
                    },
                ],
            )
            write_jsonl(
                root / "prerequisite_topic_candidates.jsonl",
                [
                    {
                        "review_status": "REVIEW_REQUIRED_NOT_PUBLISHED",
                        "publication_status": "NOT_PUBLISHED",
                    }
                ],
            )
            (root / "quality_report.json").write_text(
                json.dumps(
                    {
                        "topic_candidate_graph": {
                            "nodes": 3,
                            "edges": 3,
                            "self_loop_count": 0,
                            "cycle_component_count": 1,
                        }
                    }
                ),
                encoding="utf-8",
            )
            catalog = evaluation.load_foundation_catalog(root)
            self.assertEqual(catalog.topics, ("topic:a", "topic:b", "topic:c"))
            self.assertEqual(
                catalog.graph_audit["status"], evaluation.GRAPH_INCONCLUSIVE_STATUS
            )

    def test_history_recommendation_does_not_read_future_outcome(self) -> None:
        history = tuple(
            evaluation.Event(17, "topic:a", index % 2 == 0, index, index)
            for index in range(1, 13)
        )
        future = tuple(
            evaluation.Event(17, "topic:b", False, 100 + index, 100 + index)
            for index in range(8)
        )
        case = evaluation.Case("case-0001", 17, history, future)
        first = evaluation.recommend_m1_mastery(case, ("topic:a", "topic:b", "topic:c"))
        changed_future = tuple(
            evaluation.Event(17, "topic:c", True, event.timestamp, event.source_row)
            for event in future
        )
        second = evaluation.recommend_m1_mastery(
            evaluation.Case("case-0001", 17, history, changed_future),
            ("topic:a", "topic:b", "topic:c"),
        )
        self.assertEqual(first, second)

    def test_bucket_membership_and_chronological_split_ignore_correct_for_selection(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "problem_log.csv"
            fields = ["user_id", "exercise", "time_done", "correct"]
            with path.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=fields)
                writer.writeheader()
                for index in range(20):
                    writer.writerow(
                        {
                            "user_id": "17",
                            "exercise": "exercise-a",
                            "time_done": str(20 - index),
                            "correct": "true" if index % 2 else "false",
                        }
                    )
            events, _ = evaluation.load_bucketed_events(path, {"exercise-a": "topic:a"})
            cases, counts = evaluation.make_cases(events)
            self.assertEqual(counts["cases"], 1)
            self.assertEqual(len(cases[0].history), 12)
            self.assertLess(cases[0].history[-1].timestamp, cases[0].future[0].timestamp)


if __name__ == "__main__":
    unittest.main()
