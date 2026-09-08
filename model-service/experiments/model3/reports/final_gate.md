# MODEL-3 Final Gate

## Decision

**GO_RASCH_ONLY_INTEGRATION**

Rasch robustly clears ExerciseRate, while the hierarchical candidate lacks a frozen GO condition.

## Frozen conditions

| Condition | Result |
| --- | --- |
| rasch_auc_above_exercise_rate | PASS |
| rasch_quality | PASS |
| hierarchical_point_auc_above_rasch | FAIL |
| hierarchical_bootstrap_lower_bound_above_zero | FAIL |
| hierarchical_log_loss_not_materially_worse | FAIL |
| hierarchical_brier_not_materially_worse | FAIL |
| hierarchical_quality | PASS |
| observed_topic_delta_nonempty | PASS |
| observed_topic_delta_future_association | PASS |
| unknown_topic_delta_neutral | PASS |
| twenty_anonymized_cases | PASS |
| final_protocol_global_freeze | PASS |
| evaluation_no_parameter_update | PASS |

## Primary comparisons

| Comparison | AUC delta | Student-cluster 95% CI | Valid / requested resamples |
| --- | ---: | --- | ---: |
| rasch_minus_exercise_rate | 0.017465 | [0.012394, 0.022665] | 1000 / 1000 |
| hierarchical_minus_rasch | -0.014660 | [-0.017391, -0.011977] | 1000 / 1000 |

## Stop boundary

A GO authorizes only a later, separately reviewed integration design. Java MasteryProvider, recommendation, Published Graph, and RuleBeta remain unchanged in this Issue.

This is the terminal output for Issue #30. It does not modify Java
`MasteryProvider`, recommendation, Published Graph, or RuleBeta.
