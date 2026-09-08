from __future__ import annotations

import csv
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

specification = importlib.util.spec_from_file_location("model2_preflight_module", SCRIPTS / "preflight.py")
if specification is None or specification.loader is None:  # pragma: no cover - import guard
    raise RuntimeError("Unable to load MODEL-2 preflight module")
preflight = importlib.util.module_from_spec(specification)
sys.modules[specification.name] = preflight
specification.loader.exec_module(preflight)


class Model2PreflightTest(unittest.TestCase):
    def test_selection_scan_does_not_depend_on_correct_values(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "problem_log.csv"
            rows = [
                ["new", "exercise_a", "true", "1"],
                ["new", "exercise_a", "false", "2"],
                ["model1", "exercise_a", "true", "3"],
                ["medium", "exercise_a", "false", "4"],
            ]

            def write_rows() -> None:
                with path.open("w", encoding="utf-8", newline="") as handle:
                    writer = csv.writer(handle)
                    writer.writerow(preflight.RAW_LOG_COLUMNS)
                    for student, exercise, correct, timestamp in rows:
                        row = [""] * len(preflight.RAW_LOG_COLUMNS)
                        row[preflight.RAW_LOG_COLUMNS.index("user_id")] = student
                        row[preflight.RAW_LOG_COLUMNS.index("exercise")] = exercise
                        row[preflight.RAW_LOG_COLUMNS.index("correct")] = correct
                        row[preflight.RAW_LOG_COLUMNS.index("time_done")] = timestamp
                        writer.writerow(row)

            write_rows()
            first, first_evidence = preflight.scan_final_candidates(
                path, {"medium"}, {"model1"}, {"exercise_a"}
            )
            rows[0][2], rows[1][2] = rows[1][2], rows[0][2]
            write_rows()
            second, second_evidence = preflight.scan_final_candidates(
                path, {"medium"}, {"model1"}, {"exercise_a"}
            )
        self.assertEqual(first, second)
        self.assertEqual(first_evidence, second_evidence)
        self.assertEqual(first["new"], 2)

    def test_stratified_selection_is_deterministic_and_requires_target(self) -> None:
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
        first, first_evidence = preflight.select_stratified_members(counts, 5, 2026090802)
        second, second_evidence = preflight.select_stratified_members(counts, 5, 2026090802)
        self.assertEqual(first, second)
        self.assertEqual(first_evidence, second_evidence)
        self.assertEqual(len(first), 5)
        with self.assertRaises(ValueError):
            preflight.select_stratified_members(counts, 11, 2026090802)


if __name__ == "__main__":
    unittest.main()
