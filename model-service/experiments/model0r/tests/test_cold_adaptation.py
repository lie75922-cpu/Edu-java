from __future__ import annotations

import sys
import unittest
from pathlib import Path

import numpy as np
import torch


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

from model0r_common import InteractionSplit, ResourceTracker  # noqa: E402
from run_cold import evaluate_new_students  # noqa: E402

LEGACY_SCRIPTS = Path(__file__).resolve().parents[2] / "model0/scripts"
sys.path.insert(0, str(LEGACY_SCRIPTS))
from inscd_adapter import NCDMAdapter  # noqa: E402


class ColdAdaptationTest(unittest.TestCase):
    def test_only_standalone_representation_is_optimized_and_evaluation_is_immutable(self) -> None:
        torch.manual_seed(7)
        model = NCDMAdapter(4, 2, 2, [4], 0.0)
        groups = {
            2: InteractionSplit(
                students=np.asarray([2, 2, 2]),
                exercises=np.asarray([0, 1, 0]),
                topics=np.asarray([0, 1, 0]),
                outcomes=np.asarray([1.0, 0.0, 1.0], dtype=np.float32),
            ),
            3: InteractionSplit(
                students=np.asarray([3, 3, 3]),
                exercises=np.asarray([1, 0, 1]),
                topics=np.asarray([1, 0, 1]),
                outcomes=np.asarray([0.0, 1.0, 0.0], dtype=np.float32),
            ),
        }
        result = evaluate_new_students(
            model,
            groups,
            1,
            torch.tensor([[1.0, 0.0], [0.0, 1.0]]),
            {"learning_rate": 0.05, "max_steps": 8},
            torch.device("cpu"),
            ResourceTracker.start(torch.device("cpu")),
            include_doa=True,
            student_num=4,
        )
        self.assertEqual(result["eligible_students"], 2)
        self.assertEqual(result["evaluation_interactions"], 4)
        self.assertTrue(result["global_parameters_frozen_during_calibration"])
        self.assertTrue(result["global_state_unchanged"])
        self.assertTrue(result["evaluation_representation_unchanged"])
        self.assertEqual(result["optimizer_parameter_count"], 2)
        self.assertTrue(all(parameter.requires_grad for parameter in model.parameters()))


if __name__ == "__main__":
    unittest.main()
