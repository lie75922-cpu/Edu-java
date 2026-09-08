from __future__ import annotations

import sys
import unittest
from pathlib import Path

import numpy as np


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

from model0r_common import (  # noqa: E402
    InteractionSplit,
    binary_metrics,
    doa_from_mastery,
    make_rate_estimates,
    rate_predictions,
)


class MetricTest(unittest.TestCase):
    def test_rate_estimate_uses_train_only_and_metrics_include_log_loss(self) -> None:
        train = InteractionSplit(
            students=np.asarray([0, 1, 1]),
            exercises=np.asarray([0, 0, 1]),
            topics=np.asarray([0, 0, 1]),
            outcomes=np.asarray([1.0, 0.0, 1.0], dtype=np.float32),
        )
        test = InteractionSplit(
            students=np.asarray([2, 2]),
            exercises=np.asarray([0, 1]),
            topics=np.asarray([0, 1]),
            outcomes=np.asarray([0.0, 1.0], dtype=np.float32),
        )
        estimates = make_rate_estimates(train, 2, 2)
        prediction = rate_predictions(estimates, test, "exercise")
        self.assertTrue(np.allclose(prediction, np.asarray([0.5, 1.0])))
        metric = binary_metrics(test.outcomes, prediction)
        self.assertIn("log_loss", metric)
        self.assertIsNotNone(metric["auc"])

    def test_doa_scores_correct_student_above_incorrect_peer_on_same_exercise(self) -> None:
        split = InteractionSplit(
            students=np.asarray([0, 1]),
            exercises=np.asarray([0, 0]),
            topics=np.asarray([0, 0]),
            outcomes=np.asarray([1.0, 0.0], dtype=np.float32),
        )
        mastery = np.asarray([[0.9, 0.1], [0.2, 0.8]], dtype=np.float32)
        result = doa_from_mastery(split, mastery, np.asarray([[1.0, 0.0]], dtype=np.float32))
        self.assertEqual(result["status"], "APPLICABLE_EXERCISE_CONDITIONED_PAIRWISE")
        self.assertEqual(result["doa"], 1.0)


if __name__ == "__main__":
    unittest.main()
