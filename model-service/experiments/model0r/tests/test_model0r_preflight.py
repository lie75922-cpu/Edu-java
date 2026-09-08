from __future__ import annotations

import json
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

specification = importlib.util.spec_from_file_location("model0r_preflight", SCRIPTS / "preflight.py")
if specification is None or specification.loader is None:  # pragma: no cover - import guard
    raise RuntimeError("Unable to load MODEL-0R preflight module")
preflight = importlib.util.module_from_spec(specification)
sys.modules[specification.name] = preflight
specification.loader.exec_module(preflight)
load_frozen_split = preflight.load_frozen_split
split_warm_sequence = preflight.split_warm_sequence


class WarmSplitTest(unittest.TestCase):
    def test_short_sequences_keep_members_and_a_test_row(self) -> None:
        self.assertEqual(split_warm_sequence(1), (0, 0, 1))
        self.assertEqual(split_warm_sequence(2), (1, 0, 1))
        self.assertEqual(split_warm_sequence(3), (1, 1, 1))
        self.assertEqual(split_warm_sequence(4), (2, 1, 1))
        self.assertEqual(split_warm_sequence(5), (3, 1, 1))
        self.assertEqual(split_warm_sequence(10), (6, 2, 2))
        for length in range(1, 100):
            train, valid, test = split_warm_sequence(length)
            self.assertEqual(train + valid + test, length)
            self.assertGreaterEqual(test, 1)

    def test_exact_membership_fixture_requires_no_overlap(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "split_membership.json"
            membership = {"train": ["s1", "s2"], "valid": ["s3"], "test": ["s4"]}
            path.write_text(json.dumps(membership), encoding="utf-8")
            copied, lookup, evidence = load_frozen_split(
                path, {"train": 2, "valid": 1, "test": 1}
            )
            self.assertEqual(copied, membership)
            self.assertEqual(lookup["s3"], "valid")
            self.assertEqual(evidence["overlap"], {"train_valid": 0, "train_test": 0, "valid_test": 0})


if __name__ == "__main__":
    unittest.main()
