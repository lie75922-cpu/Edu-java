# MODEL-0 results

## Scope

Both completed models use the same `JUNYI_MID_MODEL_V1` derived input, frozen
student split, 624 × 40 Exercise-to-Topic Q-matrix, and neutral zero-history
policy for validation/test students. NCDM and ORCDF are comparable only within
that cold-start protocol. RCD is explicitly outside the fair table; GEAR-CD is
smoke-only and blocked before a model run.

| Model | AUC | ACC | RMSE | DOA | Train Time | Inference | Peak RAM | Peak GPU | Checkpoint | Integration Risk |
| --- | ---: | ---: | ---: | --- | ---: | --- | --- | --- | --- | --- |
| NCDM | 0.756775 | 0.820678 | 0.361951 | N/A (zero history) | 5.590 s | 0.069823 s total; 0.001222 ms/row | 363,937,792 bytes (347.08 MiB) | none | 1,767,380 bytes (1.69 MiB) | High: cold-start only; provisional Topic adapter |
| ORCDF-NCD | 0.638144 | 0.813219 | 0.472493 | N/A (zero history) | 18.843 s | 0.070520 s total; 0.001235 ms/row | 1,227,460,608 bytes (1170.60 MiB) | none | 1,452,602 bytes (1.39 MiB) | Very high: worse metrics, more RAM, cold-start only |
| RCD | NOT_COMPARABLE_ON_COMMON_GRAPH | N/A | N/A | N/A | Not run | N/A | N/A | N/A | N/A | No common graph/identity mapping |
| GEAR-CD | SMOKE_BLOCKED_NO_VERIFIED_RUNTIME_OR_COMMON_IDENTITY_MAPPING | N/A | N/A | N/A | Smoke blocked | N/A | Not measured | N/A | N/A | No executable runtime/common identity mapping |

## Selection integrity

- NCDM selected epoch **3** from validation
  AUC then validation RMSE; its test ledger records one completed evaluation.
- ORCDF selected epoch **8** under the same
  validation-only rule; its test ledger records one completed evaluation.
- No model result changed the frozen 7,000 / 1,000 / 2,000 membership.
- Neither model uses raw prerequisite evidence as a model graph or a Published
  Graph.

## ORCDF response-graph evidence

The ORCDF graph has 10664 nodes: frozen students,
the 624 Q-eligible Exercises, and 40 Topics. It uses
199392 retained train response rows,
with 29468 unique
correct and 16956
unique incorrect student-Exercise pairs after sparse coalescing. It adds
624 Exercise-Topic Q edges and no
raw prerequisite edge. Base graph construction took
0.810 s; flipped
training-view construction took a total of
8.597 s.

## Runtime boundary

The local InsCD 1.3.1 reference source is MIT-licensed but its pinned
PyTorch 2.4.0 wheel could not import on this machine because a required OpenMP
DLL was unavailable. Both runs therefore use the local equation-preserving
adapter with PyTorch 2.3.1+cpu; it preserves the
NCDM/ORCDF source semantics while avoiding the toolkit's random interaction
split. This is reported as a compatibility adaptation, not as an unmodified
InsCD package run.

