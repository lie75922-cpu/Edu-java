# MODEL-0R Cold Preflight

## Status

**PASS** — `JUNYI_MID_COLD_V1` preserves DATA-0's exact
student membership: 7,000 train, 1,000 validation, and 2,000 test.  No member
is exchanged, resampled, or moved between groups.

## Input boundary

| Check | Value |
| --- | --- |
| Exact member-list comparison | PASS |
| Membership overlap | all zero |
| Train / valid / test source-role rows | 199,392 / 27,721 / 57,115 |
| Q-matrix | 624 Exercises × 40 Topics |

## Calibration and evaluation boundary

Per student: occurred_at ascending, with source-order as a stable tie-breaker.

For each k, first k chronological retained rows are calibration only; later rows are evaluation only. Members with fewer than k+1 retained rows remain in the frozen split and are marked INELIGIBLE_FOR_K.

| Frozen split | k | Eligible students | `INELIGIBLE_FOR_K` | Coverage | Evaluation interactions |
| --- | ---: | ---: | ---: | ---: | ---: |
| valid | 0 | 1000 | 0 | 100.00% | 27,721 |
| valid | 3 | 1000 | 0 | 100.00% | 24,721 |
| valid | 5 | 963 | 37 | 96.30% | 22,721 |
| valid | 10 | 682 | 318 | 68.20% | 18,453 |
| test | 0 | 2000 | 0 | 100.00% | 57,115 |
| test | 3 | 2000 | 0 | 100.00% | 51,115 |
| test | 5 | 1928 | 72 | 96.40% | 47,115 |
| test | 10 | 1388 | 612 | 69.40% | 38,563 |

The first k responses and all later evaluation responses are disjoint by
construction.  No evaluation label can update a student representation.
