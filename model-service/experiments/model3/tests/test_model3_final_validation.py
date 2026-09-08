from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

import run_final_validation  # noqa: E402


class Model3FinalValidationTest(unittest.TestCase):
    def test_ledger_refuses_a_second_final_claim(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            ledger = Path(directory) / "ledger.json"
            ledger.write_text(
                json.dumps({
                    "dataset_name": "JUNYI_FINAL_HOLDOUT_V1",
                    "status": "FROZEN_UNEVALUATED",
                    "labels_read": False,
                    "metrics_generated": False,
                }),
                encoding="utf-8",
            )
            with mock.patch.object(run_final_validation, "LEDGER_PATH", ledger):
                run_final_validation._claim_final_evaluation()
                claimed = json.loads(ledger.read_text(encoding="utf-8"))
                self.assertEqual(claimed["status"], "FINAL_EVALUATION_CLAIMED")
                with self.assertRaises(RuntimeError):
                    run_final_validation._claim_final_evaluation()

    def test_freeze_precondition_blocks_any_final_materialization(self) -> None:
        with mock.patch.object(run_final_validation, "_assert_ready", side_effect=RuntimeError("freeze missing")):
            with mock.patch.object(run_final_validation, "materialize_final_interactions") as materialize:
                with self.assertRaisesRegex(RuntimeError, "freeze missing"):
                    run_final_validation.run(Path("model1"), Path("model2"), Path("medium"), Path("problem_log"))
                materialize.assert_not_called()


if __name__ == "__main__":
    unittest.main()
