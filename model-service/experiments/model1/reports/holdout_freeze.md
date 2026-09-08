# MODEL-1 Fresh External Holdout Freeze

## Status

**FROZEN_BEFORE_MODEL_TRAINING** — membership was selected and frozen before any MODEL-1 model training.

## Membership boundary

| Check | Value |
| --- | --- |
| Target / frozen students | 5,000 / 5,000 |
| Random seed | 20260908 |
| Original Medium member overlap | 0 |
| Minimum eligible chronological interactions | 20 |
| `correct` used to select membership | False |
| Local member list | `model-service/experiments/model1/data/junyi_external_holdout_v1/frozen_membership.json` (ignored; not uploaded) |

The selection pass used only anonymous user identity, the frozen eligible Exercise scope, and valid `time_done`; it did not inspect `correct`.  Binary correctness was checked only after the frozen list existed to materialise model rows.  An invalid selected label would fail preflight rather than replace a member.

## Sequence-length strata

| Eligible length stratum | Eligible students outside Medium | Frozen students | Min length | Max length |
| --- | ---: | ---: | ---: | ---: |
| 20-49 | 32,475 | 1,784 | 20 | 49 |
| 50-99 | 19,005 | 1,044 | 50 | 99 |
| 100-199 | 14,449 | 794 | 100 | 199 |
| 200-499 | 13,730 | 755 | 200 | 499 |
| 500-plus | 11,338 | 623 | 500 | 21910 |

## Chronological partitions

| Partition | Interactions |
| --- | ---: |
| train/history | 768,280 |
| validation | 254,748 |
| final test | 260,693 |

Each selected student sequence is ordered by `time_done`, with original source row as the tie-breaker: first 60% is train/history, next 20% is validation, and remaining 20% is final test.  The final test is not used to fit rates, models, mastery, or validation selection.
