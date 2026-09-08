# MODEL-2 Final Gate

## Decision

**GO_SIMPLE_HIERARCHICAL_ROUTE**

A simple student-ability signal remains after C40 fails its diagnostic requirements. Stop graph cognitive diagnosis and permit only a separately designed simple hierarchical ability plus Topic-deviation research route.

## C40 conditions

| Condition | Result |
| --- | --- |
| identity_signal_vs_pop_mean_or_permuted | PASS |
| identity_signal_vs_pop_mean | PASS |
| identity_signal_vs_permuted | PASS |
| observed_exposure_3_nonconstant | PASS |
| observed_c40_incremental_over_rule_beta | FAIL |
| final_holdout_frozen_and_unused | PASS |

| C40 comparison | AUC delta | Student-cluster 95% CI |
| --- | ---: | --- |
| ORIGINAL - POP_MEAN | 0.012844 | [0.008400, 0.017457] |
| ORIGINAL - PERMUTED | 0.022268 | [0.016763, 0.027972] |
| C40 observed >=3 - RuleBeta | -0.018003 | [-0.024807, -0.011203] |

## Simple student-signal conditions

| Condition | Result |
| --- | --- |
| student_global_beta_over_exercise | FAIL |
| rasch_1pl_over_exercise | PASS |

| Simple comparison | AUC delta | Student-cluster 95% CI |
| --- | ---: | --- |
| StudentGlobalBeta - ExerciseRate | -0.104064 | [-0.116955, -0.092436] |
| Rasch/IRT-1PL - ExerciseRate | 0.019684 | [0.013889, 0.025986] |

## C40 requirements not met

- observed_c40_incremental_over_rule_beta

## Stop boundary

The final holdout remains `FROZEN_UNEVALUATED` with labels read:
`False` and metrics
generated: `False`. No Java
MasteryProvider, recommendation, Published Graph, RCD, ORCDF, or GEAR-CD work
was added. This gate is terminal for this Issue; do not start another ML model
from this result.
