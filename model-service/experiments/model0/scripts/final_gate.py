"""Compile MODEL-0 evidence and apply the terminal non-production gate."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any

from model0_common import MODEL0_ROOT, read_json, rounded, write_json


REPORT_ROOT = MODEL0_ROOT / "reports"
RUNTIME_ROOT = MODEL0_ROOT / "runtime"


def bytes_text(value: int | None) -> str:
    if value is None:
        return "N/A"
    return f"{value:,} bytes ({value / (1024 * 1024):.2f} MiB)"


def metric_text(value: float | None) -> str:
    return f"{value:.6f}" if value is not None else "N/A"


def load_evidence() -> dict[str, Any]:
    paths = {
        "preflight": MODEL0_ROOT / "manifests/junyi_mid_model_v1.json",
        "ncdm": REPORT_ROOT / "ncdm_result.json",
        "orcdf": REPORT_ROOT / "orcdf_result.json",
        "rcd": REPORT_ROOT / "rcd_result.json",
        "gear": REPORT_ROOT / "gear_cd_smoke_result.json",
        "ledger": RUNTIME_ROOT / "test_evaluation_ledger.json",
    }
    missing = [name for name, path in paths.items() if not path.is_file()]
    if missing:
        raise FileNotFoundError(f"MODEL-0 evidence missing: {missing}")
    evidence = {name: read_json(path) for name, path in paths.items()}
    if evidence["ncdm"].get("status") != "COMPLETED":
        raise RuntimeError("NCDM does not have a completed result")
    if evidence["orcdf"].get("status") != "COMPLETED":
        raise RuntimeError("ORCDF does not have a completed result")
    for model_name in ("NCDM", "ORCDF_NCD"):
        entry = evidence["ledger"].get(model_name)
        if not entry or entry.get("status") != "COMPLETED":
            raise RuntimeError(f"Final test ledger is incomplete for {model_name}")
    return evidence


def final_status(evidence: dict[str, Any]) -> tuple[str, list[str]]:
    reasons: list[str] = []
    preflight = evidence["preflight"]
    if preflight.get("status") == "PASS_WITH_COLD_START_LIMITATION":
        reasons.append(
            "The frozen student-disjoint split has no Owner-approved held-out-student calibration protocol; "
            "evaluation therefore measures only zero-history response prediction, not individually fitted diagnosis."
        )
    else:
        reasons.append(f"Preflight did not pass: {preflight.get('status')}")
    ncdm = evidence["ncdm"]
    orcdf = evidence["orcdf"]
    comparison = orcdf["comparison_to_ncdm"]
    if comparison.get("test_auc_delta", 0.0) <= 0.0:
        reasons.append(
            "ORCDF did not improve the shared cold-start test AUC over NCDM and used materially more peak process RAM."
        )
    if evidence["rcd"].get("status") == "NOT_COMPARABLE_ON_COMMON_GRAPH":
        reasons.append(
            "RCD lacks an auditable common concept and Exercise identity mapping, so it cannot support a fair comparison."
        )
    if evidence["gear"].get("model_smoke_executed") is False:
        reasons.append(
            "GEAR-CD has no verified local executable runtime or common identity mapping; only a blocked smoke audit is available."
        )
    if ncdm["test_metrics"]["doa"] is None or orcdf["test_metrics"]["doa"] is None:
        reasons.append(
            "DOA is not applicable under neutral held-out embeddings, leaving no valid held-out mastery-order evidence."
        )
    return "NO_GO", reasons


def comparison_table(evidence: dict[str, Any]) -> str:
    ncdm = evidence["ncdm"]
    orcdf = evidence["orcdf"]
    rcd = evidence["rcd"]
    gear = evidence["gear"]

    def model_row(result: dict[str, Any], risk: str) -> str:
        metrics = result["test_metrics"]
        resources = result["resources"]
        inference = result["test_inference"]
        return "| {model} | {auc} | {acc} | {rmse} | {doa} | {train} | {infer} | {ram} | {gpu} | {checkpoint} | {risk} |".format(
            model=result["model"],
            auc=metric_text(metrics["auc"]),
            acc=metric_text(metrics["acc"]),
            rmse=metric_text(metrics["rmse"]),
            doa="N/A (zero history)",
            train=f"{result['training_wall_seconds']:.3f} s",
            infer=(
                f"{inference['total_seconds']:.6f} s total; "
                f"{inference['milliseconds_per_interaction']:.6f} ms/row"
            ),
            ram=bytes_text(resources["peak_process_rss_bytes"]),
            gpu=resources["gpu"],
            checkpoint=bytes_text(result["checkpoint"]["size_bytes"]),
            risk=risk,
        )

    return "\n".join(
        [
            "| Model | AUC | ACC | RMSE | DOA | Train Time | Inference | Peak RAM | Peak GPU | Checkpoint | Integration Risk |",
            "| --- | ---: | ---: | ---: | --- | ---: | --- | --- | --- | --- | --- |",
            model_row(ncdm, "High: cold-start only; provisional Topic adapter"),
            model_row(orcdf, "Very high: worse metrics, more RAM, cold-start only"),
            f"| RCD | {rcd['status']} | N/A | N/A | N/A | Not run | N/A | N/A | N/A | N/A | No common graph/identity mapping |",
            f"| GEAR-CD | {gear['status']} | N/A | N/A | N/A | Smoke blocked | N/A | Not measured | N/A | N/A | No executable runtime/common identity mapping |",
        ]
    )


def make_results(evidence: dict[str, Any]) -> str:
    ncdm = evidence["ncdm"]
    orcdf = evidence["orcdf"]
    return f"""# MODEL-0 results

## Scope

Both completed models use the same `JUNYI_MID_MODEL_V1` derived input, frozen
student split, 624 × 40 Exercise-to-Topic Q-matrix, and neutral zero-history
policy for validation/test students. NCDM and ORCDF are comparable only within
that cold-start protocol. RCD is explicitly outside the fair table; GEAR-CD is
smoke-only and blocked before a model run.

{comparison_table(evidence)}

## Selection integrity

- NCDM selected epoch **{ncdm['selection']['selected_epoch']}** from validation
  AUC then validation RMSE; its test ledger records one completed evaluation.
- ORCDF selected epoch **{orcdf['selection']['selected_epoch']}** under the same
  validation-only rule; its test ledger records one completed evaluation.
- No model result changed the frozen 7,000 / 1,000 / 2,000 membership.
- Neither model uses raw prerequisite evidence as a model graph or a Published
  Graph.

## ORCDF response-graph evidence

The ORCDF graph has {orcdf['response_graph']['nodes']} nodes: frozen students,
the 624 Q-eligible Exercises, and 40 Topics. It uses
{orcdf['response_graph']['train_response_rows']} retained train response rows,
with {orcdf['response_graph']['unique_right_student_exercise_pairs']} unique
correct and {orcdf['response_graph']['unique_wrong_student_exercise_pairs']}
unique incorrect student-Exercise pairs after sparse coalescing. It adds
{orcdf['response_graph']['exercise_topic_edges']} Exercise-Topic Q edges and no
raw prerequisite edge. Base graph construction took
{orcdf['response_graph']['base_graph_construction_seconds']:.3f} s; flipped
training-view construction took a total of
{orcdf['response_graph']['total_flip_graph_construction_seconds']:.3f} s.

## Runtime boundary

The local InsCD 1.3.1 reference source is MIT-licensed but its pinned
PyTorch 2.4.0 wheel could not import on this machine because a required OpenMP
DLL was unavailable. Both runs therefore use the local equation-preserving
adapter with PyTorch {ncdm['runtime_versions']['torch']}; it preserves the
NCDM/ORCDF source semantics while avoiding the toolkit's random interaction
split. This is reported as a compatibility adaptation, not as an unmodified
InsCD package run.
"""


def make_bad_cases(evidence: dict[str, Any]) -> str:
    preflight = evidence["preflight"]["measurements"]
    ncdm = evidence["ncdm"]["bad_cases"]
    orcdf = evidence["orcdf"]["bad_cases"]
    return f"""# MODEL-0 bad cases and limitations

## Input-level cases

- The Medium source contains {preflight['interactions']['exact_duplicate_rows']}
  exact duplicate interaction rows. They were removed only from the local
  derivative; DATA-0 materialisation remains unchanged.
- Unknown Exercise interactions after duplicate removal:
  {preflight['interactions']['unknown_exercise_rows_after_dedup']}.
- Known-but-Topic-unmapped interactions after duplicate removal:
  {preflight['interactions']['topic_unmapped_rows_after_dedup']}.
- The induced raw-prerequisite evidence graph still has
  {preflight['medium_observed_exercise_induced_graph']['self_loops']} self-loop
  and {preflight['medium_observed_exercise_induced_graph']['cyclic_sccs']} cyclic
  SCCs. It remains evidence only and was not published or fed into ORCDF.

## Evaluation-level limitation

All 3,000 validation/test students are held out at the student level. Their
model embeddings remain neutral and no held-out response is used for
calibration. Consequently, `DOA` is intentionally N/A, and the reported AUC,
ACC, and RMSE do not establish individualized diagnostic quality.

## NCDM test cases

| Case | Count / result |
| --- | --- |
| False positives | {ncdm['false_positive_count']} |
| False negatives | {ncdm['false_negative_count']} |
| Test rows on Exercises unseen in train | {ncdm['test_rows_on_exercises_unseen_in_train']} |
| Unseen-Exercise AUC | {metric_text(ncdm['unseen_exercise_metrics']['auc']) if ncdm['unseen_exercise_metrics'] else 'N/A'} |
| Unseen-Exercise ACC | {metric_text(ncdm['unseen_exercise_metrics']['acc']) if ncdm['unseen_exercise_metrics'] else 'N/A'} |
| Unseen-Exercise RMSE | {metric_text(ncdm['unseen_exercise_metrics']['rmse']) if ncdm['unseen_exercise_metrics'] else 'N/A'} |

## ORCDF test cases

| Case | Count / result |
| --- | --- |
| False positives | {orcdf['false_positive_count']} |
| False negatives | {orcdf['false_negative_count']} |
| Test rows on Exercises unseen in train | {orcdf['test_rows_on_exercises_unseen_in_train']} |
| Unseen-Exercise AUC | {metric_text(orcdf['unseen_exercise_metrics']['auc']) if orcdf['unseen_exercise_metrics'] else 'N/A'} |
| Unseen-Exercise ACC | {metric_text(orcdf['unseen_exercise_metrics']['acc']) if orcdf['unseen_exercise_metrics'] else 'N/A'} |
| Unseen-Exercise RMSE | {metric_text(orcdf['unseen_exercise_metrics']['rmse']) if orcdf['unseen_exercise_metrics'] else 'N/A'} |

ORCDF's zero false-negative / {orcdf['false_positive_count']} false-positive
pattern shows that its selected cold-start output classified every test response
as correct at the 0.5 threshold. This is a failure mode, not a favorable
accuracy interpretation.

## Routes not eligible for a fair result

- **RCD:** `NOT_COMPARABLE_ON_COMMON_GRAPH`; no numeric concept or Exercise
  identity mapping was inferred.
- **GEAR-CD:** smoke blocked because the local artifact has data but no verified
  executable runtime or common identity mapping. No resource estimate is
  invented from other models.
"""


def make_cost(evidence: dict[str, Any]) -> str:
    ncdm = evidence["ncdm"]
    orcdf = evidence["orcdf"]
    delta = orcdf["comparison_to_ncdm"]
    return f"""# MODEL-0 cost and runtime evidence

## Measured runs

| Model | Device | Train wall time | Valid inference | Test inference | Peak process RSS | Peak GPU | Checkpoint |
| --- | --- | ---: | --- | --- | --- | --- | --- |
| NCDM | {ncdm['device']} | {ncdm['training_wall_seconds']:.3f} s | {ncdm['validation_inference']['milliseconds_per_interaction']:.6f} ms/row | {ncdm['test_inference']['milliseconds_per_interaction']:.6f} ms/row | {bytes_text(ncdm['resources']['peak_process_rss_bytes'])} | {ncdm['resources']['gpu']} | {bytes_text(ncdm['checkpoint']['size_bytes'])} |
| ORCDF-NCD | {orcdf['device']} | {orcdf['training_wall_seconds']:.3f} s | {orcdf['validation_inference']['milliseconds_per_interaction']:.6f} ms/row | {orcdf['test_inference']['milliseconds_per_interaction']:.6f} ms/row | {bytes_text(orcdf['resources']['peak_process_rss_bytes'])} | {orcdf['resources']['gpu']} | {bytes_text(orcdf['checkpoint']['size_bytes'])} |

## Incremental ORCDF cost versus NCDM

- Training wall time: {delta['additional_training_wall_seconds']:.3f} s.
- Peak process RSS: {bytes_text(delta['additional_peak_process_rss_bytes'])}.
- Checkpoint size delta: {bytes_text(delta['additional_checkpoint_bytes'])}.
- Base graph construction: {orcdf['response_graph']['base_graph_construction_seconds']:.3f} s.
- Total flipped graph construction during training:
  {orcdf['response_graph']['total_flip_graph_construction_seconds']:.3f} s.

## Runtime limitations

- Both measured runs use Python {ncdm['runtime_versions']['python']} and
  PyTorch {ncdm['runtime_versions']['torch']} on CPU. `GPU=none` describes the
  actual model runtime; it does not claim that no graphics hardware exists.
- The metric is sampled process RSS at each training/evaluation step and GPU
  allocator peak where available. No unmeasured GPU or GEAR-CD cost is inferred.
- GEAR-CD has no executable local model process, so its training time, peak RAM,
  peak GPU, and checkpoint size are correctly `NOT_AVAILABLE`, not zero.
"""


def make_final_gate(status: str, reasons: list[str], evidence: dict[str, Any]) -> str:
    numbered_reasons = "\n".join(f"{index}. {reason}" for index, reason in enumerate(reasons, start=1))
    return f"""# MODEL-0 Final Gate

## Decision

**{status}** for model production integration in MODEL-0.

## Decision basis

{numbered_reasons}

The NCDM result is a usable controlled baseline measurement, but it is not
sufficient evidence to integrate a diagnosis service: the test students have no
fitted model state. ORCDF is both weaker on the same limited protocol and more
memory intensive. No result justifies changing the Java AI Gateway or starting
recommendation integration.

## Evidence closure

- Preflight: `{evidence['preflight']['status']}`.
- NCDM final test: exactly one completed ledger entry.
- ORCDF final test: exactly one completed ledger entry.
- RCD: `{evidence['rcd']['status']}`; no test evaluation.
- GEAR-CD: `{evidence['gear']['status']}`; no model test evaluation and no full
  Medium training.
- Production Java AI Gateway changes: **none**.
- Recommendation integration: **not started**.
- Raw prerequisite evidence publication: **none**.

## Stop boundary

MODEL-0 ends here. Any later work must be a newly authorized experiment: it
would need an Owner-approved cold-start/calibration protocol that does not tune
against test labels, plus a new gate before considering integration. It must not
reuse this final test result as a tuning signal.
"""


def run() -> dict[str, Any]:
    evidence = load_evidence()
    status, reasons = final_status(evidence)
    result = {
        "status": status,
        "reasons": reasons,
        "test_ledger": evidence["ledger"],
        "production_integration_changed": False,
        "recommendation_integration_started": False,
    }
    REPORT_ROOT.mkdir(parents=True, exist_ok=True)
    (REPORT_ROOT / "model0_results.md").write_text(
        make_results(evidence), encoding="utf-8", newline="\n"
    )
    (REPORT_ROOT / "model0_bad_cases.md").write_text(
        make_bad_cases(evidence), encoding="utf-8", newline="\n"
    )
    (REPORT_ROOT / "model0_cost.md").write_text(
        make_cost(evidence), encoding="utf-8", newline="\n"
    )
    (REPORT_ROOT / "model0_final_gate.md").write_text(
        make_final_gate(status, reasons, evidence), encoding="utf-8", newline="\n"
    )
    write_json(REPORT_ROOT / "model0_final_gate.json", result)
    return result


def main() -> int:
    argparse.ArgumentParser(description=__doc__).parse_args()
    result = run()
    print(result["status"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

