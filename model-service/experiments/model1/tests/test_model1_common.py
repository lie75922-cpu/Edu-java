from __future__ import annotations

import sys
import unittest
from pathlib import Path

import numpy as np
import torch


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

from model1_common import InteractionSplit, aggregate_fine_mastery_to_topics, mastery_sanity  # noqa: E402

LEGACY_SCRIPTS = Path(__file__).resolve().parents[2] / "model0/scripts"
sys.path.insert(0, str(LEGACY_SCRIPTS))
from inscd_adapter import NCDMAdapter  # noqa: E402
from run_ncdm_ablation import onehot_ncdm_forward  # noqa: E402


class Model1CommonTest(unittest.TestCase):
    def test_onehot_specialization_matches_dense_ncdm_interaction(self) -> None:
        torch.manual_seed(23)
        model = NCDMAdapter(3, 4, 4, [5, 3], 0.0)
        model.eval()
        students = torch.tensor([0, 2, 1], dtype=torch.long)
        exercises = torch.tensor([3, 1, 0], dtype=torch.long)
        q_matrix = torch.eye(4, dtype=torch.float32)
        dense = model(students, exercises, q_matrix.index_select(0, exercises))
        specialized = onehot_ncdm_forward(model, students, exercises, torch.arange(4, dtype=torch.long))
        self.assertTrue(torch.allclose(dense, specialized, atol=1e-6, rtol=1e-6))

    def test_fine_mastery_reports_coverage_variance_and_topic_projection(self) -> None:
        train = InteractionSplit(
            students=np.asarray([0, 1, 2, 3]),
            exercises=np.asarray([0, 1, 2, 3]),
            topics=np.asarray([0, 0, 1, 1]),
            outcomes=np.asarray([1.0, 0.0, 1.0, 0.0], dtype=np.float32),
        )
        q_fine = np.eye(4, dtype=np.float32)
        mastery = np.asarray(
            [
                [0.1, 0.2, 0.3, 0.4],
                [0.2, 0.4, 0.6, 0.8],
                [0.3, 0.6, 0.9, 0.7],
                [0.4, 0.8, 0.5, 0.9],
            ],
            dtype=np.float32,
        )
        sanity = mastery_sanity(
            "fine",
            mastery,
            q_fine,
            train,
            case_count=2,
            cosine_sample_size=4,
            constant_tolerance=1e-12,
            minimum_median_standard_deviation=0.01,
            maximum_mean_pairwise_cosine=1.0,
        )
        self.assertEqual(sanity["concept_coverage"]["concepts_with_train_coverage"], 4)
        self.assertEqual(sanity["nonfinite_values"]["nan_count"], 0)
        self.assertEqual(sanity["constant_column_check"]["constant_column_count"], 0)
        projection = aggregate_fine_mastery_to_topics(
            mastery,
            [0, 0, 1, 1],
            ["topic_a", "topic_b", "topic_without_fine_concepts"],
            cosine_sample_size=4,
            constant_tolerance=1e-12,
            minimum_median_standard_deviation=0.01,
            maximum_mean_pairwise_cosine=1.0,
        )
        self.assertEqual(projection["topic_count"], 3)
        self.assertEqual(projection["topic_rows"][0]["fine_model_concept_count"], 2)
        self.assertEqual(projection["topics_without_fine_model_concepts"], [2])
        self.assertEqual(projection["topic_rows"][2]["projection_status"], "NO_FINE_MODEL_CONCEPT_IN_CURRENT_ELIGIBLE_SCOPE")
        self.assertEqual(projection["status"], "PASS")


if __name__ == "__main__":
    unittest.main()
