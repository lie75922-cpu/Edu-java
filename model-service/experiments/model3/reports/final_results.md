# MODEL-3 Final Results

## One-time final evaluation

**FINAL_EVALUATION_COMPLETED_ONCE**

| Candidate | AUC | ACC | RMSE | Log Loss | Brier | ECE | Known-item fraction | Fallback-item fraction |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| EXERCISE_RATE_DEV | 0.707210 | 0.820778 | 0.368169 | 0.429899 | 0.135549 | 0.008947 | 0.999621 | 0.000379 |
| RASCH_1PL_V1 | 0.724675 | 0.826387 | 0.362658 | 0.420147 | 0.131521 | 0.006393 | 0.999621 | 0.000379 |
| HIER_RASCH_TOPIC_V1 | 0.710015 | 0.811279 | 0.374733 | 0.442709 | 0.140425 | 0.023577 | 0.999621 | 0.000379 |

## Primary student-cluster paired bootstrap

| Comparison | AUC delta | 95% CI | Valid / requested resamples |
| --- | ---: | --- | ---: |
| rasch_minus_exercise_rate | 0.017465 | [0.012394, 0.022665] | 1000 / 1000 |
| hierarchical_minus_rasch | -0.014660 | [-0.017391, -0.011977] | 1000 / 1000 |

All evaluation rows were retained. Rows whose Exercise lacked the frozen
development parameter coverage used the pre-registered global ExerciseRate
fallback and are reported separately above.
