# MODEL-0R Warm Results

## Scope and leakage controls

`JUNYI_MID_TRANS_V1` uses the exact frozen 10,000 Medium students, but the
unit of split is each student's chronology.  Only earlier warm-train responses
fit models and rates; validation selected both neural candidates before the
test file was read.  The response graph contains only warm-train response
edges plus the frozen Exercise-to-Topic Q-matrix.  It contains no raw
prerequisite edge and no validation/test response edge.

The selected non-personalized comparator was **exercise**, selected by validation AUC then lower RMSE before test evaluation.

| Model | AUC | ACC | RMSE | Log Loss | DOA | Train Time | Test Inference | Peak RAM | GPU | Checkpoint |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- |
| Majority / Always-correct-rate baseline | 0.500000 | 0.797690 | 0.449789 | 3.260858 | N/A | 0.006 s shared fit | 0.027797 s | 1,156,829,184 bytes (1103.24 MiB) | none | N/A |
| Global correct-rate baseline | 0.500000 | 0.797690 | 0.402696 | 0.506202 | N/A | 0.006 s shared fit | 0.027811 s | 1,156,829,184 bytes (1103.24 MiB) | none | N/A |
| Exercise historical-rate baseline | 0.748487 | 0.810038 | 0.373471 | 0.466488 | N/A | 0.006 s shared fit | 0.030663 s | 1,156,829,184 bytes (1103.24 MiB) | none | N/A |
| Topic historical-rate baseline | 0.676567 | 0.801243 | 0.387517 | 0.470733 | N/A | 0.006 s shared fit | 0.030128 s | 1,156,829,184 bytes (1103.24 MiB) | none | N/A |
| NCDM | 0.765630 | 0.813623 | 0.368150 | 0.428888 | 0.575220 | 6.723 s | 0.214868 s | 1,157,201,920 bytes (1103.59 MiB) | none | 1,767,322 bytes (1.69 MiB) |
| ORCDF-NCD | 0.470608 | 0.797690 | 0.433262 | 0.565859 | 0.514449 | 22.239 s | 0.087673 s | 1,160,790,016 bytes (1107.02 MiB) | none | 1,452,584 bytes (1.39 MiB) |

## Model-selection evidence

| Candidate | Selected epoch | Validation AUC | Validation RMSE | Test AUC delta versus validation-selected baseline | Paired bootstrap lower bound |
| --- | ---: | ---: | ---: | ---: | ---: |
| NCDM | 4 | 0.779754 | 0.341623 | 0.017143 | 0.014948 |
| ORCDF-NCD | 8 | 0.458897 | 0.422505 | -0.277879 | -0.285131 |

DOA is the pre-registered exercise-conditioned pairwise diagnosis agreement.
It uses later test outcomes only to score mastery vectors that were fitted from
earlier warm-train histories.

## Routes outside the fair neural table

- **RCD:** `NOT_COMPARABLE_ON_COMMON_GRAPH` — No audited common Concept Graph and no audited Exercise identity mapping exist between the frozen 40 Topic Q-matrix and an RCD graph input.
- **GEAR-CD:** `SMOKE_ONLY` — MODEL-0A compatibility/smoke conclusion is retained. Full GEAR-CD training is not authorized for MODEL-0R.

## Historical table kept separate — MODEL-0A / COLD_ZERO_HISTORY

| Model | AUC | ACC | RMSE | Interpretation |
| --- | ---: | ---: | ---: | --- |
| NCDM | 0.756775 | 0.820678 | 0.361951 | Historical MODEL-0A zero-history unseen-student compatibility result only. |
| ORCDF-NCD | 0.638144 | 0.813219 | 0.472493 | Historical MODEL-0A zero-history unseen-student compatibility result only. |

The archived MODEL-0A gate remains
`NO_GO_FOR_ZERO_HISTORY_PROTOCOL_AS_MODEL_SELECTION`.  It is not part of this
warm model-selection table and is not evidence that cognitive diagnosis as a
whole failed.
