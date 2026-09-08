from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))
specification = importlib.util.spec_from_file_location("model1_final_gate_module", SCRIPTS / "final_gate.py")
if specification is None or specification.loader is None:  # pragma: no cover - import guard
    raise RuntimeError("Unable to load MODEL-1 final gate module")
final_gate = importlib.util.module_from_spec(specification)
sys.modules[specification.name] = final_gate
specification.loader.exec_module(final_gate)


def synthetic_result(delta: float, lower_bound: float, cosine: float) -> dict:
    candidate = {
        "test_metrics": {"auc": 0.8, "acc": 0.8, "rmse": 0.3, "log_loss": 0.4},
        "test_auc_vs_selected_nonpersonalized_baseline": {"observed_auc_delta": delta, "lower_bound": lower_bound},
        "mastery_sanity_status": "PASS",
    }
    return {
        "selected_nonpersonalized_baseline_by_validation": "exercise",
        "models": {"C40_TOPIC": candidate, "C_FINE_EXERCISE": candidate},
        "mastery_sanity": {
            "C_FINE_EXERCISE": {
                "student_to_student_concept_variance": {"median_concept_standard_deviation": 0.02},
                "oversmoothing_check": {"mean_pairwise_cosine": cosine},
                "concept_coverage": {"train_coverage_fraction": 1.0},
                "nonfinite_values": {"nan_count": 0, "infinite_count": 0},
                "constant_column_check": {"constant_column_fraction": 0.0},
            }
        },
        "fine_concept_to_topic_aggregation": {"status": "PASS"},
    }


class Model1FinalGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.config = {
            "concept_gate": {
                "require_paired_bootstrap_lower_bound_above_zero": True,
                "minimum_median_concept_standard_deviation": 0.01,
                "maximum_mean_pairwise_cosine": 0.995,
                "minimum_train_coverage_fraction": 0.95,
                "maximum_constant_concept_fraction": 0.05,
                "require_no_nonfinite_mastery_values": True,
                "require_topic_aggregation_stable": True,
            },
            "interpretation": {"go": "go", "conditional": "conditional", "no_go": "no-go"},
        }

    def test_full_fine_gate_passes_only_when_every_condition_passes(self) -> None:
        gate = final_gate.decide(synthetic_result(0.03, 0.01, 0.99), self.config)
        self.assertEqual(gate["status"], "GO_FINE_CONCEPT_GRAPH_MODELS")
        self.assertTrue(gate["concept_gate_passed"])

    def test_prediction_success_with_mastery_failure_is_conditional(self) -> None:
        gate = final_gate.decide(synthetic_result(0.03, 0.01, 0.999), self.config)
        self.assertEqual(gate["status"], "CONDITIONAL_GO")
        self.assertFalse(gate["concept_gate_passed"])

    def test_prediction_failure_is_no_go(self) -> None:
        gate = final_gate.decide(synthetic_result(-0.01, -0.02, 0.99), self.config)
        self.assertEqual(gate["status"], "NO_GO_CONCEPT_LAYER")


if __name__ == "__main__":
    unittest.main()
