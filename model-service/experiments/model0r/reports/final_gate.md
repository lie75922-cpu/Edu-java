# MODEL-0R Final Gate

## Decision

**NO_GO**

## Evidence basis

1. No warm candidate cleared all pre-registered response-prediction and mastery-sanity criteria.

| Warm candidate | Test AUC | Test ACC | Test RMSE | Test Log Loss | Bootstrap AUC-delta lower bound | Mastery sanity |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| NCDM | 0.765630 | 0.813623 | 0.368150 | 0.428888 | 0.014948 | FLAGGED |
| ORCDF-NCD | 0.470608 | 0.797690 | 0.433262 | 0.565859 | -0.285131 | FLAGGED |

## Cold readiness at k=5

| Check | Value |
| --- | --- |
| Validation-selected statistical baseline | exercise |
| NCDM test AUC / baseline AUC / delta | 0.712998 / 0.765516 / -0.052518 |
| Global state unchanged during calibration | True |
| Representation unchanged during evaluation | True |
| Calibration P95 | 70.898 ms |
| Cold readiness | False |

## Limitations retained

- RCD: `NOT_COMPARABLE_ON_COMMON_GRAPH`.
- GEAR-CD: smoke only; no full MODEL-0R training was run.
- ORCDF new-student adaptation: `NOT_SUPPORTED_BY_CURRENT_ADAPTER`; no zero-vector result was relabeled as calibration.
- ICDM: `NOT_AUDITABLY_ALIGNED_FOR_CURRENT_40_TOPIC_INPUT`; no ID mapping was invented.
- `MODEL-0A / COLD_ZERO_HISTORY` remains a separate historical negative experiment with its original `NO_GO_FOR_ZERO_HISTORY_PROTOCOL_AS_MODEL_SELECTION`.  It does not establish failure of the full cognitive-diagnosis route.

## Stop boundary

No Java AI Gateway was changed, no Mastery Outbox was consumed, and no
recommendation or website work was started.  This PR contains experiment
evidence only; any product integration remains a separately authorized action.
