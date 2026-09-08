"""Perform the Issue #4 GEAR-CD smoke-only readiness check."""

from __future__ import annotations

import argparse
import importlib.metadata
import json
from pathlib import Path
from typing import Any

import torch

from model0_common import MODEL0_ROOT, assert_preflight_ready, read_json, write_json


REPOSITORY_ROOT = MODEL0_ROOT.parents[2]
GEAR_ROOT = REPOSITORY_ROOT / "data-pipeline/.data/reference/GEAR-CD"


def graph_summary(path: Path) -> dict[str, int]:
    nodes: set[int] = set()
    edges: set[tuple[int, int]] = set()
    rows = 0
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
            rows += 1
    return {
        "raw_rows": rows,
        "unique_edges": len(edges),
        "numeric_nodes": len(nodes),
        "self_loops": sum(1 for source, target in edges if source == target),
    }


def json_structure(path: Path) -> dict[str, Any]:
    """Read only data shape/key evidence; never surface participant records."""

    with path.open("r", encoding="utf-8") as handle:
        payload = json.load(handle)
    if not isinstance(payload, list) or not payload or not isinstance(payload[0], dict):
        raise ValueError(f"Unexpected reference JSON structure: {path}")
    first = payload[0]
    return {
        "top_level": "list",
        "record_count": len(payload),
        "first_record_keys": sorted(first),
        "first_record_value_types": {key: type(value).__name__ for key, value in sorted(first.items())},
    }


def make_markdown(result: dict[str, Any]) -> str:
    frozen = result["frozen_model_input"]
    inventory = result["reference_inventory"]
    return f"""# MODEL-0 GEAR-CD smoke report

## Scope and result

**SMOKE_BLOCKED_NO_VERIFIED_RUNTIME_OR_COMMON_IDENTITY_MAPPING**. No GEAR-CD
model training, no full Medium run, and no model test evaluation occurred. This
is the required smoke-only route, not a production integration decision.

## Local reference inventory

| Check | Result |
| --- | --- |
| Reference archive present | {inventory['archive_present']} |
| Extracted reference data present | {inventory['extracted_data_present']} |
| Local Python model source files | {inventory['python_source_file_count']} |
| Local dependency declaration files | {inventory['dependency_declaration_count']} |
| Reference train JSON records | {inventory['train_structure']['record_count']} |
| Reference test JSON records | {inventory['test_structure']['record_count']} |
| Reference directed graph numeric nodes | {inventory['directed_graph']['numeric_nodes']} |
| Reference directed graph unique edges | {inventory['directed_graph']['unique_edges']} |
| Reference undirected graph numeric nodes | {inventory['undirected_graph']['numeric_nodes']} |
| Reference undirected graph unique edges | {inventory['undirected_graph']['unique_edges']} |

The local artifact contains an extracted data package, not a verified executable
GEAR-CD model runtime or dependency declaration. Its records use a separate
numeric-concept representation. No auditable mapping from those numeric concepts
or reference exercises to the frozen MODEL-0 identities was found or inferred.

## Frozen input adapter-contract check

| Frozen MODEL-0 property | Value |
| --- | ---: |
| Students | {frozen['student_num']} |
| Q-matrix Exercise rows | {frozen['exercise_num']} |
| Topic columns | {frozen['topic_num']} |
| Student split | 7000 / 1000 / 2000 |

The current local GEAR artifact cannot consume this contract without both a
verifiable GEAR-CD runtime and an approved identity mapping. A tiny model smoke
was therefore correctly blocked before construction; a fabricated adapter or a
full Medium training run would violate Issue #4.

## Environment and cost evidence

- Runtime inspected: Python {result['environment']['python']}, PyTorch
  {result['environment']['torch']}.
- CUDA usable by this isolated runtime: {result['environment']['cuda_available']}.
- Peak RAM/GPU and training time: **not available**, because no GEAR-CD model
  process was executable. They are not inferred from NCDM or ORCDF.

## Next approval boundary

Obtain a verified GEAR-CD code/runtime source, an auditable common identity
mapping, and Owner/architect approval before any full Medium GEAR-CD training.
"""


def run(config_path: Path) -> dict[str, Any]:
    config = read_json(config_path)
    preflight = assert_preflight_ready()
    manifest = read_json(MODEL0_ROOT / "manifests/junyi_mid_model_v1.json")
    extracted = GEAR_ROOT / "junyi/junyi"
    graph_root = extracted / "graph"
    required = {
        "archive": GEAR_ROOT / "junyi.rar",
        "train": extracted / "train_set.json",
        "test": extracted / "test_set.json",
        "log": extracted / "log_data.json",
        "directed": graph_root / "K_Directed.txt",
        "undirected": graph_root / "K_Undirected.txt",
        "exercise_from_concept": graph_root / "e_from_k.txt",
        "concept_from_exercise": graph_root / "k_from_e.txt",
    }
    missing = [name for name, path in required.items() if not path.is_file()]
    if missing:
        raise FileNotFoundError(f"GEAR-CD local reference files missing: {missing}")
    python_sources = list(GEAR_ROOT.rglob("*.py"))
    dependency_files = [
        path
        for pattern in ("requirements*.txt", "pyproject.toml", "setup.py", "environment.yml")
        for path in GEAR_ROOT.rglob(pattern)
    ]
    measurements = manifest["measurements"]
    return {
        "status": "SMOKE_BLOCKED_NO_VERIFIED_RUNTIME_OR_COMMON_IDENTITY_MAPPING",
        "model": "GEAR-CD",
        "preflight_status": preflight["status"],
        "scope": config,
        "frozen_model_input": {
            "student_num": measurements["frozen_split"]["student_count"],
            "exercise_num": measurements["q_matrix"]["rows"],
            "topic_num": measurements["q_matrix"]["columns"],
        },
        "reference_inventory": {
            "archive_present": required["archive"].is_file(),
            "archive_size_bytes": required["archive"].stat().st_size,
            "extracted_data_present": extracted.is_dir(),
            "python_source_file_count": len(python_sources),
            "dependency_declaration_count": len(dependency_files),
            "train_structure": json_structure(required["train"]),
            "test_structure": json_structure(required["test"]),
            "directed_graph": graph_summary(required["directed"]),
            "undirected_graph": graph_summary(required["undirected"]),
        },
        "environment": {
            "python": __import__("sys").version.split()[0],
            "torch": torch.__version__,
            "cuda_available": torch.cuda.is_available(),
            "torch_distribution": importlib.metadata.version("torch"),
        },
        "adapter_contract_smoke": {
            "executed": False,
            "blocking_reasons": [
                "No local executable GEAR-CD model source or dependency declaration was supplied.",
                "No auditable reference numeric-concept to frozen 40-Topic mapping exists.",
                "No auditable reference Exercise identity to frozen 624-Exercise mapping exists.",
            ],
            "forbidden_actions_not_taken": [
                "No full Medium GEAR-CD training.",
                "No inferred identity mapping.",
                "No raw prerequisite publication.",
            ],
        },
        "model_smoke_executed": False,
        "test_evaluation_count": 0,
        "resource_measurement": "NOT_AVAILABLE_NO_EXECUTABLE_MODEL_PROCESS",
        "production_integration_changed": False,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--config-path", type=Path, default=MODEL0_ROOT / "configs/gear_cd_smoke.json"
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = run(args.config_path)
    result_path = MODEL0_ROOT / "reports/gear_cd_smoke_result.json"
    write_json(result_path, result)
    report_path = MODEL0_ROOT / "reports/gear_cd_smoke.md"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(make_markdown(result), encoding="utf-8", newline="\n")
    print(result["status"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

