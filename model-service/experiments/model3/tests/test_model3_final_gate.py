from __future__ import annotations

import sys
import unittest
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

import final_gate  # noqa: E402


def model(auc: float, log_loss: float = 0.4, brier: float = 0.14) -> dict:
    return {
        "overall": {
            "metrics": {"auc": auc, "log_loss": log_loss, "brier": brier},
            "expected_calibration_error": 0.02,
        },
        "coverage": {"fallback_item_fraction": 0.0},
        "inference": {
            "evaluation_updated_personal_parameters": False,
            "frozen_global_parameters_verified": True,
        },
    }


def comparison(lower: float) -> dict:
    return {
        "observed_auc_delta": lower + 0.01,
        "lower_bound": lower,
        "upper_bound": lower + 0.02,
        "resamples_requested": 1000,
        "resamples_valid": 1000,
        "student_cluster_count": 5000,
    }


def result(rasch_lower: float, hier_lower: float) -> dict:
    return {
        "models": {
            "EXERCISE_RATE_DEV": model(0.65),
            "RASCH_1PL_V1": model(0.70),
            "HIER_RASCH_TOPIC_V1": model(0.72),
        },
        "bootstrap": {
            "rasch_minus_exercise_rate": comparison(rasch_lower),
            "hierarchical_minus_rasch": comparison(hier_lower),
        },
        "topic_deviation_audit": {
            "unknown_delta_exactly_zero": True,
            "thresholds": {
                "3": {
                    "delta_distribution": {"count": 20},
                    "future_response_association": {"delta_rank_auc": 0.55},
                }
            },
        },
        "anonymized_student_cases": [{}] * 20,
        "protocol": {
            "global_parameters_frozen": True,
            "evaluation_updates_personal_parameters": False,
        },
    }


class Model3FinalGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.config = {
            "allowed_statuses": [
                "GO_HIERARCHICAL_MODEL_INTEGRATION",
                "GO_RASCH_ONLY_INTEGRATION",
                "STOP_ML_INTEGRATION_RULE_ONLY",
            ],
            "quality_limits": {
                "maximum_ece": 0.1,
                "maximum_fallback_item_fraction": 0.05,
                "maximum_hierarchical_log_loss_increase_over_rasch": 0.005,
                "maximum_hierarchical_brier_increase_over_rasch": 0.002,
            },
            "hierarchical_interpretability": {
                "minimum_observed_exposure": 3,
                "minimum_delta_future_association_auc": 0.5,
            },
            "product_boundary": "test boundary",
        }

    def test_hierarchical_go_requires_all_frozen_conditions(self) -> None:
        gate = final_gate.decide(result(0.01, 0.01), self.config)
        self.assertEqual(gate["status"], "GO_HIERARCHICAL_MODEL_INTEGRATION")

    def test_rasch_only_when_hierarchical_interval_fails(self) -> None:
        gate = final_gate.decide(result(0.01, -0.01), self.config)
        self.assertEqual(gate["status"], "GO_RASCH_ONLY_INTEGRATION")

    def test_stop_when_rasch_does_not_replicate(self) -> None:
        gate = final_gate.decide(result(-0.01, 0.01), self.config)
        self.assertEqual(gate["status"], "STOP_ML_INTEGRATION_RULE_ONLY")


if __name__ == "__main__":
    unittest.main()
