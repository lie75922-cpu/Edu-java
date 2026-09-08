from __future__ import annotations

import csv
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))
specification = importlib.util.spec_from_file_location("model1_preflight_module", SCRIPTS / "preflight.py")
if specification is None or specification.loader is None:  # pragma: no cover - import guard
    raise RuntimeError("Unable to load MODEL-1 preflight module")
preflight = importlib.util.module_from_spec(specification)
sys.modules[specification.name] = preflight
specification.loader.exec_module(preflight)


class Model1PreflightTest(unittest.TestCase):
    def test_stratified_selection_is_deterministic_and_uses_all_strata(self) -> None:
        counts = {
            "s01": 20,
            "s02": 49,
            "s03": 50,
            "s04": 99,
            "s05": 100,
            "s06": 199,
            "s07": 200,
            "s08": 499,
            "s09": 500,
            "s10": 800,
        }
        selected_one, evidence_one = preflight.select_stratified_members(counts, 5, 20260908)
        selected_two, evidence_two = preflight.select_stratified_members(counts, 5, 20260908)
        self.assertEqual(selected_one, selected_two)
        self.assertEqual(evidence_one, evidence_two)
        self.assertEqual(len(selected_one), 5)
        self.assertEqual(len(set(selected_one)), 5)
        self.assertEqual([entry["stratum"] for entry in evidence_one], ["20-49", "50-99", "100-199", "200-499", "500-plus"])

    def test_membership_scan_does_not_change_when_correct_values_change(self) -> None:
        rows = [
            ["u1", "exercise_a", "true", "1"],
            ["u1", "exercise_a", "false", "2"],
            ["u2", "exercise_a", "true", "3"],
            ["old", "exercise_a", "false", "4"],
            ["u1", "exercise_outside_scope", "true", "5"],
        ]
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "problem_log.csv"
            with path.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=preflight.RAW_LOG_COLUMNS)
                writer.writeheader()
                for user_id, exercise, correct, time_done in rows:
                    payload = {field: "" for field in preflight.RAW_LOG_COLUMNS}
                    payload.update({"user_id": user_id, "exercise": exercise, "correct": correct, "time_done": time_done})
                    writer.writerow(payload)
            first_counts, first_evidence = preflight.scan_external_candidate_counts(path, {"old"}, {"exercise_a"})
            rows[0][2] = "false"
            rows[1][2] = "true"
            with path.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=preflight.RAW_LOG_COLUMNS)
                writer.writeheader()
                for user_id, exercise, correct, time_done in rows:
                    payload = {field: "" for field in preflight.RAW_LOG_COLUMNS}
                    payload.update({"user_id": user_id, "exercise": exercise, "correct": correct, "time_done": time_done})
                    writer.writerow(payload)
            second_counts, second_evidence = preflight.scan_external_candidate_counts(path, {"old"}, {"exercise_a"})
        self.assertEqual(first_counts, second_counts)
        self.assertEqual(first_evidence, second_evidence)
        self.assertEqual(first_counts, {"u1": 2, "u2": 1})

    def test_graph_statistics_keeps_self_loop_and_cycle_evidence(self) -> None:
        evidence = preflight.graph_statistics(
            {"a", "b", "c", "d"},
            [("a", "b"), ("b", "a"), ("c", "c"), ("a", "b")],
        )
        self.assertEqual(evidence["nodes"], 4)
        self.assertEqual(evidence["raw_edge_rows"], 4)
        self.assertEqual(evidence["unique_edges"], 3)
        self.assertEqual(evidence["self_loop_count"], 1)
        self.assertEqual(evidence["cyclic_strongly_connected_components"], 2)


if __name__ == "__main__":
    unittest.main()
