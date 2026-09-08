from __future__ import annotations

import sys
import unittest
from pathlib import Path

import numpy as np
import torch


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

from model2_common import (  # noqa: E402
    InteractionSplit,
    exposure_matrices,
    historical_signal_scores,
    observability_summary,
    student_cluster_paired_bootstrap_auc_deltas,
)
from run_diagnostics import (  # noqa: E402
    c40_embedding_variants,
    deterministic_derangement,
    onehot_ncdm_forward_with_student_logits,
)


class Model2CommonTest(unittest.TestCase):
    def test_cluster_bootstrap_resamples_students_and_returns_paired_delta(self) -> None:
        labels = np.asarray([0, 1, 0, 1, 0, 1, 0, 1], dtype=np.int8)
        students = np.asarray([0, 0, 1, 1, 2, 2, 3, 3], dtype=np.int64)
        predictions = {
            "candidate": np.asarray([0.05, 0.95, 0.10, 0.90, 0.20, 0.80, 0.30, 0.70]),
            "baseline": np.asarray([0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50]),
        }
        report = student_cluster_paired_bootstrap_auc_deltas(
            labels,
            predictions,
            {"candidate_minus_baseline": ("candidate", "baseline")},
            students,
            resamples=80,
            confidence_level=0.95,
            seed=17,
        )["candidate_minus_baseline"]
        self.assertEqual(report["student_cluster_count"], 4)
        self.assertEqual(report["resamples_requested"], 80)
        self.assertGreater(report["resamples_valid"], 0)
        self.assertGreater(report["observed_auc_delta"], 0.0)

    def test_observability_excludes_unknown_pairs(self) -> None:
        mastery = np.asarray([[0.1, 0.5], [0.9, 0.7], [0.2, 0.3]], dtype=np.float64)
        exposure = np.asarray([[1, 0], [2, 1], [0, 0]], dtype=np.int32)
        report = observability_summary(
            mastery,
            exposure,
            thresholds=[1],
            minimum_topic_sample_size=1,
            pairwise_common_minimum=1,
            pairwise_summary_sample_size=3,
            pairwise_seed=31,
        )["thresholds"]["1"]
        self.assertEqual(report["unknown_pair_count"], 3)
        topic_one = report["concept_side"]["topic_rows"][1]
        self.assertEqual(topic_one["observed_student_count"], 1)
        self.assertAlmostEqual(topic_one["mean_mastery"], 0.7)

    def test_historical_signal_uses_train_history_only(self) -> None:
        train = InteractionSplit(
            students=np.asarray([0, 0, 1], dtype=np.int64),
            exercises=np.asarray([0, 1, 0], dtype=np.int64),
            topics=np.asarray([0, 1, 0], dtype=np.int64),
            outcomes=np.asarray([1.0, 0.0, 0.0]),
        )
        future = InteractionSplit(
            students=np.asarray([0, 1], dtype=np.int64),
            exercises=np.asarray([0, 1], dtype=np.int64),
            topics=np.asarray([0, 1], dtype=np.int64),
            outcomes=np.asarray([0.0, 1.0]),
        )
        mastery = np.asarray([[0.8, 0.2], [0.4, 0.6]], dtype=np.float64)
        scores, exposure, _, _ = historical_signal_scores(train, future, mastery, 2, 2, 2)
        self.assertTrue(np.allclose(scores["RULE_BETA"], np.asarray([2 / 3, 1 / 2])))
        self.assertTrue(np.array_equal(exposure, np.asarray([1, 0])))

    def test_deterministic_derangement_has_no_fixed_points(self) -> None:
        mapping = deterministic_derangement(20, 2026090803)
        self.assertEqual(sorted(mapping.tolist()), list(range(20)))
        self.assertFalse(np.any(mapping == np.arange(20)))

    def test_explicit_student_table_matches_c40_original_forward(self) -> None:
        """The ORIGINAL ablation must be exactly the frozen C40 forward path."""

        torch.manual_seed(19)
        from inscd_adapter import NCDMAdapter

        model = NCDMAdapter(4, 3, 2, [3], 0.0)
        model.eval()
        students = torch.as_tensor([0, 1, 2, 3], dtype=torch.long)
        exercises = torch.as_tensor([0, 1, 2, 1], dtype=torch.long)
        concepts = torch.as_tensor([0, 1, 0], dtype=torch.long)
        q_mask = torch.nn.functional.one_hot(
            concepts.index_select(0, exercises), num_classes=2
        ).to(dtype=torch.float32)
        original_logits = model.student_embedding.weight.detach().clone()

        with torch.no_grad():
            baseline = model(students, exercises, q_mask)
            explicit = onehot_ncdm_forward_with_student_logits(
                model, students, exercises, concepts, original_logits
            )
        self.assertTrue(torch.allclose(baseline, explicit, rtol=1e-6, atol=1e-7))

        variants, permutation = c40_embedding_variants(original_logits, 2026090803)
        self.assertTrue(torch.equal(variants["ORIGINAL"], original_logits))
        self.assertTrue(torch.equal(model.student_embedding.weight, original_logits))
        self.assertFalse(np.any(permutation == np.arange(4)))


if __name__ == "__main__":
    unittest.main()
