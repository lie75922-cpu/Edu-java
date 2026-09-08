# MODEL-2 Simple Interpretable Personalization Baselines

| Score | Diagnostic-test AUC | ACC | RMSE | Log Loss | Brier |
| --- | ---: | ---: | ---: | ---: | ---: |
| EXERCISE_RATE | 0.708358 | 0.809772 | 0.377696 | 0.459412 | 0.142655 |
| STUDENT_GLOBAL_BETA | 0.604293 | 0.810578 | 0.389156 | 0.482821 | 0.151442 |
| TOPIC_BETA | 0.614596 | 0.791417 | 0.424440 | 0.539167 | 0.180149 |
| RASCH_1PL | 0.728041 | 0.818626 | 0.372206 | 0.438628 | 0.138537 |

| Paired comparison | AUC delta | Student-cluster 95% CI |
| --- | ---: | --- |
| STUDENT_GLOBAL_MINUS_EXERCISE | -0.104064 | [-0.116955, -0.092436] |
| TOPIC_BETA_MINUS_EXERCISE | -0.093762 | [-0.103540, -0.084246] |
| RASCH_MINUS_EXERCISE | 0.019684 | [0.013889, 0.025986] |

## Rasch / IRT-1PL

`logit P(correct) = student_ability - exercise_difficulty`. It was fitted on
train/history only, with validation-only epoch selection. Selected epoch:
1. Student ability distribution median / standard
deviation: -0.002251 /
0.164163. Exercise
difficulty distribution median / standard deviation:
-0.267923 /
0.666527.
