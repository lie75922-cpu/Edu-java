from __future__ import annotations

import sys
import unittest
from pathlib import Path

import torch


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

from inscd_adapter import NCDMAdapter, ORCDFNCDAdapter, build_response_graph  # noqa: E402


class InsCDAdapterTest(unittest.TestCase):
    def test_ncdm_neutralises_held_out_student_embeddings(self) -> None:
        model = NCDMAdapter(3, 2, 2, [4], 0.0)
        model.neutralize_students(torch.tensor([1]))
        self.assertTrue(torch.equal(model.student_embedding.weight[1], torch.zeros(2)))
        output = model(
            torch.tensor([0, 1]),
            torch.tensor([0, 1]),
            torch.tensor([[1.0, 0.0], [0.0, 1.0]]),
        )
        self.assertEqual(tuple(output.shape), (2,))
        self.assertTrue(bool(torch.all(output >= 0)))
        self.assertTrue(bool(torch.all(output <= 1)))

    def test_orcdf_response_graph_uses_train_rows_and_q_edges(self) -> None:
        q_matrix = torch.tensor([[1.0, 0.0], [0.0, 1.0]])
        students = torch.tensor([0, 0, 1, 2])
        exercises = torch.tensor([0, 0, 1, 1])
        outcomes = torch.tensor([1, 1, 0, 1])
        graph = build_response_graph(3, 2, 2, students, exercises, outcomes, q_matrix, torch.device("cpu"))
        self.assertEqual(graph.evidence["train_response_rows"], 4)
        self.assertEqual(graph.evidence["right_response_rows"], 3)
        self.assertEqual(graph.evidence["wrong_response_rows"], 1)
        self.assertEqual(graph.evidence["exercise_topic_edges"], 2)
        model = ORCDFNCDAdapter(3, 2, 2, 3, 1, 1.0, 0.01, [4], 0.0)
        model.neutralize_students(torch.tensor([2]))
        prediction, extra_loss = model(
            torch.tensor([0, 1]),
            torch.tensor([0, 1]),
            q_matrix,
            graph,
            graph,
            torch.tensor([0, 1]),
        )
        self.assertEqual(tuple(prediction.shape), (2,))
        self.assertEqual(tuple(extra_loss.shape), ())
        self.assertTrue(bool(torch.isfinite(extra_loss)))


if __name__ == "__main__":
    unittest.main()

