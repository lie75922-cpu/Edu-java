# JUNYI_FINAL_HOLDOUT_V1 Freeze

## Status

**FROZEN_UNEVALUATED** — membership was frozen before any MODEL-2
diagnostic metric was generated.

| Check | Value |
| --- | --- |
| Target / frozen students | 5,000 / 5,000 |
| Seed | 2026090802 |
| Minimum eligible interactions | 20 |
| Original Medium overlap | 0 |
| MODEL-1 external-holdout overlap | 0 |
| `correct` used to select membership | False |
| Local member list | `model-service/experiments/model2/data/junyi_final_holdout_v1/frozen_membership.json` (ignored; not uploaded) |
| MODEL-2 labels read from this holdout | False |
| MODEL-2 metrics from this holdout | False |

The selection stream read only `user_id`, `exercise`, and `time_done`. It did
not parse `correct`, materialise response rows, or perform any result-driven
substitution.

## Sequence-length strata

| Eligible length stratum | Eligible students outside both prior cohorts | Frozen students | Min length | Max length |
| --- | ---: | ---: | ---: | ---: |
| 20-49 | 30,691 | 1,785 | 20 | 49 |
| 50-99 | 17,961 | 1,044 | 50 | 99 |
| 100-199 | 13,655 | 794 | 100 | 199 |
| 200-499 | 12,975 | 754 | 200 | 499 |
| 500-plus | 10,715 | 623 | 500 | 21910 |

## Source scan

| Measurement | Count |
| --- | ---: |
| Raw ProblemLog rows scanned | 25,925,992 |
| Q-eligible scope rows | 25,801,342 |
| Rows excluded for original Medium membership | 1,397,963 |
| Rows excluded for MODEL-1 membership | 1,283,721 |
| Rows with empty student | 0 |
| Rows with invalid timestamp | 0 |
