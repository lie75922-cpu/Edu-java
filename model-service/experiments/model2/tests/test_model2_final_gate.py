from __future__ import annotations

import sys
import importlib.util
import unittest
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

specification = importlib.util.spec_from_file_location("model2_final_gate_module", SCRIPTS / "final_gate.py")
if specification is None or specification.loader is None:  # pragma: no cover - import guard
    raise RuntimeError("Unable to load MODEL-2 final gate module")
final_gate = importlib.util.module_from_spec(specification)
sys.modules[specification.name] = final_gate
specification.loader.exec_module(final_gate)


def bootstrap(lower: float) -> dict:
    return {"lower_bound": lower, "upper_bound": lower + 0.02, "observed_auc_delta": lower + 0.01}


def result(identity_lower: float, c40_rule_lower: float, simple_lower: float) -> dict:
    return {
        "bootstrap": {
            "student_identity_ablation": {
                "ORIGINAL_MINUS_POP_MEAN": bootstrap(identity_lower),
                "ORIGINAL_MINUS_ZERO": bootstrap(identity_lower),
                "ORIGINAL_MINUS_PERMUTED": bootstrap(identity_lower),
            },
            "c40_vs_rule_beta_observed_3": bootstrap(c40_rule_lower),
            "simple_signal_vs_exercise": {
                "STUDENT_GLOBAL_MINUS_EXERCISE": bootstrap(simple_lower),
                "TOPIC_BETA_MINUS_EXERCISE": bootstrap(-0.01),
                "RASCH_MINUS_EXERCISE": bootstrap(simple_lower),
            },
        },
        "observability": {
            "thresholds": {
                "3": {
                    "concept_side": {
                        "retained_topic_count": 40,
                        "aggregate_topic_standard_deviation": {"median": 0.1},
                        "constant_retained_topic_ids": [],
                    }
                }
            }
        },
        "final_holdout": {
            "dataset_name": "JUNYI_FINAL_HOLDOUT_V1",
            "status": "FROZEN_UNEVALUATED",
            "labels_read_for_model2_diagnostics": False,
            "metrics_generated_in_model2": False,
        },
        "product_boundaries": {},
    }


class Model2FinalGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.config = {
            "allowed_statuses": [
                "GO_C40_FINAL_VALIDATION",
                "GO_SIMPLE_HIERARCHICAL_ROUTE",
                "STOP_ML_DIAGNOSIS_RULE_ONLY",
            ],
            "interpretation": {"go_c40": "c40", "go_simple": "simple", "stop": "stop"},
        }

    def test_c40_go_requires_all_c40_conditions(self) -> None:
        gate = final_gate.decide(result(0.01, 0.02, -0.01), self.config)
        self.assertEqual(gate["status"], "GO_C40_FINAL_VALIDATION")

    def test_simple_route_requires_simple_signal_after_c40_failure(self) -> None:
        gate = final_gate.decide(result(-0.01, -0.01, 0.02), self.config)
        self.assertEqual(gate["status"], "GO_SIMPLE_HIERARCHICAL_ROUTE")

    def test_stop_requires_no_c40_and_no_simple_signal(self) -> None:
        gate = final_gate.decide(result(-0.01, -0.01, -0.02), self.config)
        self.assertEqual(gate["status"], "STOP_ML_DIAGNOSIS_RULE_ONLY")


if __name__ == "__main__":
    unittest.main()
