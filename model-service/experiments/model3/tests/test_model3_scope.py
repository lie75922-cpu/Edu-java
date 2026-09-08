from __future__ import annotations

import shutil
import subprocess
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[4]


class Model3ScopeTest(unittest.TestCase):
    def test_historical_model_workspaces_are_unchanged_against_origin_main(self) -> None:
        if shutil.which("git") is None:
            self.skipTest("git is unavailable for the historical-boundary check")
        completed = subprocess.run(
            [
                "git",
                "diff",
                "--quiet",
                "origin/main",
                "--",
                "model-service/experiments/model0",
                "model-service/experiments/model0r",
                "model-service/experiments/model1",
                "model-service/experiments/model2",
            ],
            cwd=REPOSITORY_ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        if completed.returncode == 128:
            self.skipTest("origin/main is unavailable in this checkout")
        self.assertEqual(completed.returncode, 0, completed.stderr)


if __name__ == "__main__":
    unittest.main()
