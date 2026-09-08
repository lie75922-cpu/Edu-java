# MODEL-0R Warm Preflight

## Status

**PASS** — `JUNYI_MID_TRANS_V1` retains exactly the frozen
10,000 Medium members and creates a separate chronological interaction split.
It does not overwrite the DATA-0 student-level 7,000 / 1,000 / 2,000 split.

## Frozen input and Q-matrix

| Check | Value |
| --- | --- |
| Frozen train / validation / test members | 7,000 / 1,000 / 2,000 |
| Membership overlap | all zero |
| Exact member-list comparison | PASS |
| Retained Q-eligible interactions | 284,228 |
| Exact full-record duplicates removed locally | 17 |
| Q-matrix | 624 Exercises × 40 Topics |

The first source-order occurrence of an exact full-record duplicate is retained.
Unknown or Topic-unmapped exercises are excluded under the unchanged MODEL-0A
derivation rule.  Source data and local numeric derivatives are not tracked.

## Chronological rule

Per student: occurred_at ascending, with source-order as a stable tie-breaker.

For length >= 5, floor(0.60*n), floor(0.20*n), and remaining rows for test. Short sequences use the documented retained-member exception.

Training uses only each student's earlier partition; validation and test labels do not enter training, rate estimates, response graphs, selection, or mastery fitting.

| Warm partition | Interactions |
| --- | ---: |
| train | 167,388 |
| valid | 54,080 |
| test | 62,760 |

## Short sequences

n=1 -> 0/0/1; n=2 -> 1/0/1; n=3 or 4 -> at least 1/1/1 while retaining all rows.

| Sequence length | train / validation / test | Students |
| ---: | --- | ---: |
| none | N/A | 0 |

No student is silently deleted because a sequence is short.  Every retained
student has at least one final test interaction under this protocol.
