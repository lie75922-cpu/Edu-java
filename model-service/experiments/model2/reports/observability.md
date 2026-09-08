# MODEL-2 Observability-aware Mastery Audit

## Semantic boundary

UNKNOWN means train/history exposure equals zero. Its numeric model prior is never treated as observed mastery.

The C40 mastery table is reported twice: the historical full-vector summary is
retained for compatibility, while every observed-only conclusion excludes
UNKNOWN student-Topic pairs.

## Full-vector compatibility summary

| Measure | Value |
| --- | ---: |
| Median Topic standard deviation | 0.008069 |
| Mean pairwise cosine | 0.999544 |
| Pairwise mean absolute difference | 0.009364 |
| Pair sample size | 10,000 |

## Observed-only summary

| Train/history exposure | Pair coverage | UNKNOWN pairs | Retained Topics (n>=50) | Median retained Topic std | Constant retained Topics | Student pairs with >=3 common Topics | Median centered observed MND-RMSE |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| >= 1 | 0.100725 | 179,855 | 35 | 0.028903 | 0 | 1,479,345 | 0.023611 |
| >= 3 | 0.085370 | 182,926 | 33 | 0.031493 | 0 | 986,951 | 0.023262 |
| >= 5 | 0.079785 | 184,043 | 32 | 0.031787 | 0 | 840,893 | 0.023318 |

The structured result retains per-Topic P05/P50/P95, per-student observed
Topic counts, within-student standard deviation/range/IQR, and the sampled
common-observed-pair distributions for each threshold. No numeric prior for
an UNKNOWN pair appears in any observed-only statistic.
