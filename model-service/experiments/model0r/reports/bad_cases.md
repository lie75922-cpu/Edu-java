# MODEL-0R Bad Cases and Boundaries

## Warm errors retained

| Model | False positives | False negatives | Test rows on Exercises unseen in warm train |
| --- | ---: | ---: | ---: |
| NCDM | 9,824 | 1,873 | 143 |
| ORCDF-NCD | 12,697 | 0 | 143 |

## Cold eligibility and invariant checks retained

| k | `INELIGIBLE_FOR_K` test students | Eligible test students | Evaluation interactions | Global state unchanged | Evaluation representation unchanged |
| ---: | ---: | ---: | ---: | --- | --- |
| 0 | 0 | 2000 | 57,115 | True | True |
| 3 | 0 | 2000 | 51,115 | True | True |
| 5 | 72 | 1928 | 47,115 | True | True |
| 10 | 612 | 1388 | 38,563 | True | True |

No bad case was removed by changing membership, chronology, Topic semantics,
configuration, seed, or test membership.  RCD is not fairly comparable,
GEAR-CD is smoke only, ORCDF cold adaptation is unsupported by the current
adapter, and ICDM lacks audited input compatibility.
