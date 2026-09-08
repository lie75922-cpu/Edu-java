from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

from preflight import run_preflight  # noqa: E402


class PreflightTest(unittest.TestCase):
    def write_jsonl(self, path: Path, records: list[dict]) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            "".join(json.dumps(record) + "\n" for record in records), encoding="utf-8"
        )

    def test_derivation_removes_only_exact_duplicates_and_preserves_member_lists(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            medium = root / "medium"
            output = root / "derived"
            config_path = root / "preflight.json"
            manifest_path = root / "manifest.json"
            report_path = root / "preflight.md"
            config_path.write_text(
                json.dumps(
                    {
                        "dataset_name": "JUNYI_MID_MODEL_V1",
                        "topic_columns": 2,
                        "exact_duplicate_policy": "fixture",
                        "unknown_exercise_policy": "fixture",
                        "unmapped_topic_policy": "fixture",
                    }
                ),
                encoding="utf-8",
            )
            membership = {"train": ["s1"], "valid": ["s2"], "test": ["s3"]}
            (medium / "split_membership.json").parent.mkdir(parents=True, exist_ok=True)
            (medium / "split_membership.json").write_text(
                json.dumps(membership), encoding="utf-8"
            )
            self.write_jsonl(
                medium / "exercises.jsonl",
                [
                    {
                        "area": "a",
                        "display_name": "one",
                        "exercise_external_id": "e1",
                        "live": True,
                        "prerequisite_raw": "",
                        "topic": "t1",
                    },
                    {
                        "area": "a",
                        "display_name": "two",
                        "exercise_external_id": "e2",
                        "live": True,
                        "prerequisite_raw": "",
                        "topic": "t2",
                    },
                    {
                        "area": "a",
                        "display_name": "three",
                        "exercise_external_id": "e3",
                        "live": True,
                        "prerequisite_raw": "",
                        "topic": "N/A",
                    },
                ],
            )
            interaction = {
                "attempts": 1,
                "correct": True,
                "count_hints": 0,
                "duration_seconds": 1.0,
                "earned_proficiency": True,
                "exercise_external_id": "e1",
                "hint_used": False,
                "occurred_at": "2020-01-01T00:00:00Z",
                "student_external_id": "s1",
            }
            self.write_jsonl(
                medium / "interactions.jsonl",
                [
                    interaction,
                    dict(interaction),
                    {**interaction, "student_external_id": "s2", "exercise_external_id": "e2"},
                    {**interaction, "student_external_id": "s3", "exercise_external_id": "e3"},
                    {**interaction, "student_external_id": "s3", "exercise_external_id": "unknown"},
                ],
            )
            self.write_jsonl(
                medium / "relationships.jsonl",
                [
                    {
                        "relation_type": "PREREQUISITE_RAW_UNVERIFIED",
                        "score": None,
                        "source": "JUNYI_EXERCISE_METADATA",
                        "source_external_id": "e1",
                        "target_external_id": "e2",
                        "verified": False,
                    },
                    {
                        "relation_type": "PREREQUISITE_RAW_UNVERIFIED",
                        "score": None,
                        "source": "JUNYI_EXERCISE_METADATA",
                        "source_external_id": "e2",
                        "target_external_id": "e1",
                        "verified": False,
                    },
                ],
            )

            manifest = run_preflight(
                medium,
                output,
                manifest_path,
                report_path,
                config_path,
                expected_split_counts={"train": 1, "valid": 1, "test": 1},
            )

            measurements = manifest["measurements"]
            self.assertEqual(measurements["interactions"]["source_rows"], 5)
            self.assertEqual(measurements["interactions"]["exact_duplicate_rows"], 1)
            self.assertEqual(measurements["interactions"]["unknown_exercise_rows_source"], 1)
            self.assertEqual(measurements["interactions"]["topic_unmapped_rows_after_dedup"], 1)
            self.assertEqual(measurements["interactions"]["retained_q_eligible_rows"], 2)
            self.assertEqual(
                json.loads((output / "split_membership.json").read_text(encoding="utf-8")),
                membership,
            )
            self.assertEqual(
                measurements["medium_observed_exercise_induced_graph"]["cyclic_sccs"], 1
            )
            self.assertIn("zero-history", report_path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()

