from __future__ import annotations

import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

from model3_common import (  # noqa: E402
    InteractionSplit,
    RAW_LOG_COLUMNS,
    ResourceTracker,
    assert_final_membership_disjoint,
    fit_exercise_rate,
    fit_personal_calibration,
    materialize_final_interactions,
    predict_exercise_rate,
    predict_personal,
    student_cluster_paired_bootstrap_auc_deltas,
    topic_exposure_matrix,
    topic_statuses,
)


class Model3CommonTest(unittest.TestCase):
    def test_membership_overlap_guard_rejects_prior_cohort(self) -> None:
        with self.assertRaises(RuntimeError):
            assert_final_membership_disjoint({"final-a", "shared"}, {"shared"}, {"medium-a"})
        result = assert_final_membership_disjoint({"final-a"}, {"model1-a"}, {"medium-a"})
        self.assertEqual(result["model1_overlap"], 0)
        self.assertEqual(result["original_medium_overlap"], 0)

    def test_chronological_split_is_stable_and_nonoverlapping(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            membership = root / "members.json"
            membership.write_text(
                json.dumps({"dataset_name": "JUNYI_FINAL_HOLDOUT_V1", "members": ["student-b", "student-a"]}),
                encoding="utf-8",
            )
            log = root / "problem_log.csv"
            with log.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.writer(handle)
                writer.writerow(RAW_LOG_COLUMNS)
                for student in ("student-a", "student-b"):
                    for number in range(5):
                        row = [""] * len(RAW_LOG_COLUMNS)
                        row[RAW_LOG_COLUMNS.index("user_id")] = student
                        row[RAW_LOG_COLUMNS.index("exercise")] = "exercise-a" if number < 3 else "exercise-b"
                        # Equal first timestamps prove source-row tie-breaking is deterministic.
                        row[RAW_LOG_COLUMNS.index("time_done")] = "10" if number < 2 else str(10 + number)
                        row[RAW_LOG_COLUMNS.index("correct")] = "true" if number % 2 else "false"
                        writer.writerow(row)
            first = materialize_final_interactions(
                log,
                membership,
                {"exercise-a": (0, 0), "exercise-b": (1, 1)},
                expected_students=2,
                minimum_interactions=5,
                calibration_fraction=0.6,
            )
            second = materialize_final_interactions(
                log,
                membership,
                {"exercise-a": (0, 0), "exercise-b": (1, 1)},
                expected_students=2,
                minimum_interactions=5,
                calibration_fraction=0.6,
            )
        self.assertEqual(first.calibration.size, 6)
        self.assertEqual(first.evaluation.size, 4)
        self.assertTrue(np.array_equal(first.calibration.outcomes, second.calibration.outcomes))
        self.assertTrue(np.array_equal(first.evaluation.outcomes, second.evaluation.outcomes))
        self.assertTrue(np.array_equal(first.calibration_interactions_by_student, np.asarray([3, 3])))
        self.assertTrue(np.array_equal(first.evaluation_interactions_by_student, np.asarray([2, 2])))

    def test_fallback_rows_are_retained_and_scored(self) -> None:
        train = InteractionSplit(
            students=np.asarray([0, 0, 1]),
            exercises=np.asarray([0, 0, 1]),
            topics=np.asarray([0, 0, 1]),
            outcomes=np.asarray([1.0, 0.0, 1.0]),
        )
        parameters = fit_exercise_rate(train, exercise_count=3, alpha=1.0, beta=1.0, minimum_exposure=2)
        prediction, known = predict_exercise_rate(parameters, np.asarray([0, 1, 2]))
        self.assertTrue(np.array_equal(known, np.asarray([True, False, False])))
        self.assertEqual(len(prediction), 3)
        self.assertAlmostEqual(prediction[1], parameters.global_rate)
        self.assertAlmostEqual(prediction[2], parameters.global_rate)

    def test_personal_fit_neutral_unknown_and_evaluation_no_update(self) -> None:
        calibration = InteractionSplit(
            students=np.asarray([0, 0, 1, 1]),
            exercises=np.asarray([0, 0, 1, 1]),
            topics=np.asarray([0, 0, 1, 1]),
            outcomes=np.asarray([1.0, 1.0, 0.0, 0.0]),
        )
        evaluation = InteractionSplit(
            students=np.asarray([0, 1]),
            exercises=np.asarray([1, 0]),
            topics=np.asarray([1, 0]),
            outcomes=np.asarray([1.0, 0.0]),
        )
        tracker = ResourceTracker.start()
        model, evidence = fit_personal_calibration(
            calibration,
            student_count=2,
            topic_count=2,
            global_mu=0.0,
            global_difficulty=np.asarray([0.0, 0.0]),
            hierarchical=True,
            delta_lambda=0.1,
            learning_rate=0.05,
            batch_size=4,
            epochs=3,
            theta_l2=0.01,
            seed=7,
            tracker=tracker,
        )
        prediction, known, inference = predict_personal(
            model,
            evaluation,
            known_exercises=np.asarray([True, True]),
            fallback_rate=0.5,
            batch_size=2,
            tracker=tracker,
        )
        exposure = topic_exposure_matrix(calibration, 2, 2)
        status = topic_statuses(exposure, evaluation.students, evaluation.topics)
        delta = model.delta.weight.detach().cpu().numpy().reshape(2, 2)
        self.assertTrue(np.array_equal(status, np.asarray(["UNKNOWN", "UNKNOWN"])))
        self.assertEqual(delta[0, 1], 0.0)
        self.assertEqual(delta[1, 0], 0.0)
        self.assertTrue(np.all(known))
        self.assertEqual(len(prediction), 2)
        self.assertTrue(evidence["frozen_global_parameters_verified"])
        self.assertFalse(inference["evaluation_updated_personal_parameters"])

    def test_cluster_bootstrap_resamples_whole_students(self) -> None:
        labels = np.asarray([0, 1, 0, 1, 0, 1, 0, 1], dtype=np.int8)
        students = np.asarray([0, 0, 1, 1, 2, 2, 3, 3], dtype=np.int64)
        report = student_cluster_paired_bootstrap_auc_deltas(
            labels,
            {
                "candidate": np.asarray([0.1, 0.9, 0.15, 0.85, 0.2, 0.8, 0.25, 0.75]),
                "baseline": np.full(8, 0.5),
            },
            {"candidate_minus_baseline": ("candidate", "baseline")},
            students,
            resamples=80,
            confidence_level=0.95,
            seed=11,
        )["candidate_minus_baseline"]
        self.assertEqual(report["student_cluster_count"], 4)
        self.assertEqual(report["resamples_requested"], 80)
        self.assertGreater(report["resamples_valid"], 0)
        self.assertGreater(report["observed_auc_delta"], 0.0)


if __name__ == "__main__":
    unittest.main()
