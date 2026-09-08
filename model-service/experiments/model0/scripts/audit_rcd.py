"""Audit whether RCD can be fairly compared on the frozen MODEL-0 graph."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from model0_common import MODEL0_ROOT, assert_preflight_ready, read_json, write_json


REPOSITORY_ROOT = MODEL0_ROOT.parents[2]
RCD_ROOT = REPOSITORY_ROOT / "data-pipeline/.data/reference/RCD"


def graph_summary(path: Path) -> dict[str, int]:
    nodes: set[int] = set()
    edges: set[tuple[int, int]] = set()
    raw_rows = 0
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            values = line.strip().split()
            if not values:
                continue
            if len(values) != 2:
                raise ValueError(f"Unexpected pair at {path}:{line_number}")
            source, target = (int(value) for value in values)
            nodes.update((source, target))
            edges.add((source, target))
            raw_rows += 1
    return {
        "raw_rows": raw_rows,
        "unique_edges": len(edges),
        "numeric_nodes": len(nodes),
        "self_loops": sum(1 for source, target in edges if source == target),
    }


def read_reference_cardinalities(path: Path) -> dict[str, int]:
    with path.open("r", encoding="utf-8") as handle:
        header = next(
            (line.strip() for line in handle if line.strip() and not line.lstrip().startswith("#")),
            "",
        )
    values = header.split(",")
    if len(values) != 3:
        raise ValueError(f"Expected three RCD header cardinalities in {path}")
    student_num, exercise_num, knowledge_num = (int(value) for value in values)
    return {
        "student_num": student_num,
        "exercise_num": exercise_num,
        "knowledge_num": knowledge_num,
    }


def make_markdown(result: dict[str, Any]) -> str:
    directed = result["reference_graph"]["directed"]
    undirected = result["reference_graph"]["undirected"]
    target = result["frozen_model_input"]
    return f"""# MODEL-0 RCD conditional-route audit

## Decision

**NOT_COMPARABLE_ON_COMMON_GRAPH**. No same-dataset RCD training or fair-table
comparison was run. This is a conditional-route result, not an RCD performance
failure and not a claim that RCD itself is invalid.

## Evidence checked

| Item | Reference RCD | Frozen MODEL-0 input |
| --- | ---: | ---: |
| Exercises | {result['reference_cardinalities']['exercise_num']} | {target['exercise_num']} |
| Concepts / Topics | {result['reference_cardinalities']['knowledge_num']} numeric concepts | {target['topic_num']} Topics |
| Directed graph numeric nodes | {directed['numeric_nodes']} | N/A |
| Directed graph edges | {directed['unique_edges']} | N/A |
| Directed self-loops | {directed['self_loops']} | N/A |
| Undirected graph numeric nodes | {undirected['numeric_nodes']} | N/A |
| Undirected graph edges | {undirected['unique_edges']} | N/A |

The audit found the reference `K_Directed` and `K_Undirected` numeric graph
files and RCD's own training header, but no explicit artifact that maps the
numeric concept nodes to the frozen 40-Topic Q-matrix or maps the reference
exercise identities to the frozen derived Exercise indexes. Numeric cardinality
differences alone are not the reason for the decision; the blocking fact is the
absence of a traceable identity join. Row order, matching-looking integers, or
post-hoc inferred mappings would not be auditable and were not used.

## Boundary preserved

- DATA-0/ADR evidence already classifies the RCD numeric graph as reference
  evidence, not a Published Graph.
- No raw prerequisite evidence was transformed into a production graph.
- No frozen student member or test interaction was changed or evaluated by RCD.
- A separate reference-reproduction run, if ever requested, must stay outside
  the NCDM/ORCDF fair-comparison table.

## What would unlock a fair RCD route

1. An audited RCD numeric-concept → frozen Topic mapping covering every graph
   node used in the run.
2. An audited RCD reference-exercise → frozen Exercise identity mapping.
3. Owner approval of the common-graph specification while keeping it distinct
   from the Published Graph lifecycle.
"""


def run(config_path: Path) -> dict[str, Any]:
    config = read_json(config_path)
    preflight = assert_preflight_ready()
    manifest = read_json(MODEL0_ROOT / "manifests/junyi_mid_model_v1.json")
    reference_data = RCD_ROOT / "data/junyi"
    graph_root = reference_data / "graph"
    required_files = {
        "directed_graph": graph_root / "K_Directed.txt",
        "undirected_graph": graph_root / "K_Undirected.txt",
        "exercise_from_concept": graph_root / "e_from_k.txt",
        "concept_from_exercise": graph_root / "k_from_e.txt",
        "reference_train": reference_data / "train_set.json",
        "reference_test": reference_data / "test_set.json",
        "reference_log": reference_data / "log_data.json",
        "reference_config": RCD_ROOT / "RCD/config.txt",
    }
    missing = [name for name, path in required_files.items() if not path.is_file()]
    if missing:
        raise FileNotFoundError(f"RCD reference files missing: {missing}")
    reference_cardinalities = read_reference_cardinalities(required_files["reference_config"])
    target_measurements = manifest["measurements"]
    target = {
        "exercise_num": target_measurements["q_matrix"]["rows"],
        "topic_num": target_measurements["q_matrix"]["columns"],
        "student_num": target_measurements["frozen_split"]["student_count"],
    }
    result = {
        "status": "NOT_COMPARABLE_ON_COMMON_GRAPH",
        "model": "RCD",
        "preflight_status": preflight["status"],
        "route_config": config,
        "reference_cardinalities": reference_cardinalities,
        "frozen_model_input": target,
        "reference_graph": {
            "directed": graph_summary(required_files["directed_graph"]),
            "undirected": graph_summary(required_files["undirected_graph"]),
            "status": "REFERENCE_GRAPH_ONLY",
        },
        "identity_mapping_audit": {
            "numeric_concept_to_frozen_topic_mapping": "NOT_FOUND",
            "reference_exercise_to_frozen_exercise_mapping": "NOT_FOUND",
            "checked_reference_files": {
                name: {
                    "relative_path": str(path.relative_to(REPOSITORY_ROOT)).replace("\\", "/"),
                    "size_bytes": path.stat().st_size,
                }
                for name, path in required_files.items()
            },
            "forbidden_inference": [
                "Do not map by numeric position.",
                "Do not map by coincident identifier values.",
                "Do not invent a Topic graph from raw prerequisite evidence.",
            ],
        },
        "fair_comparison_run": False,
        "test_evaluation_count": 0,
        "production_integration_changed": False,
    }
    return result


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--config-path", type=Path, default=MODEL0_ROOT / "configs/rcd_route.json"
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = run(args.config_path)
    result_path = MODEL0_ROOT / "reports/rcd_result.json"
    write_json(result_path, result)
    report_path = MODEL0_ROOT / "reports/rcd_route.md"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(make_markdown(result), encoding="utf-8", newline="\n")
    print(result["status"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

