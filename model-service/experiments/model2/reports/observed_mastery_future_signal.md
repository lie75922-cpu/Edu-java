# MODEL-2 Observed Mastery Future-signal Audit

Every score below is fixed from MODEL-1 train/history. No score is updated from
the validation or diagnostic-test response being predicted. The diagnostic-test
partition is historical MODEL-1 evidence, not `JUNYI_FINAL_HOLDOUT_V1`.

| Partition | Topic exposure | Score | Rows | AUC | Log Loss | Brier |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| validation | >= 1 | C40_MASTERY | 207,227 | 0.638091 | 0.658440 | 0.232722 |
| validation | >= 1 | RULE_BETA | 207,227 | 0.656798 | 0.442662 | 0.138687 |
| validation | >= 1 | STUDENT_GLOBAL_BETA | 207,227 | 0.628913 | 0.429645 | 0.131679 |
| validation | >= 1 | TOPIC_RATE | 207,227 | 0.621198 | 0.431292 | 0.132526 |
| validation | >= 3 | C40_MASTERY | 199,954 | 0.637990 | 0.657081 | 0.232045 |
| validation | >= 3 | RULE_BETA | 199,954 | 0.658909 | 0.430502 | 0.133217 |
| validation | >= 3 | STUDENT_GLOBAL_BETA | 199,954 | 0.630571 | 0.425266 | 0.130078 |
| validation | >= 3 | TOPIC_RATE | 199,954 | 0.617761 | 0.428819 | 0.131459 |
| validation | >= 5 | C40_MASTERY | 195,787 | 0.637049 | 0.656490 | 0.231751 |
| validation | >= 5 | RULE_BETA | 195,787 | 0.659285 | 0.426083 | 0.131278 |
| validation | >= 5 | STUDENT_GLOBAL_BETA | 195,787 | 0.630581 | 0.423508 | 0.129364 |
| validation | >= 5 | TOPIC_RATE | 195,787 | 0.616877 | 0.427418 | 0.130861 |
| diagnostic_test | >= 1 | C40_MASTERY | 178,253 | 0.618346 | 0.661665 | 0.234321 |
| diagnostic_test | >= 1 | RULE_BETA | 178,253 | 0.634095 | 0.467952 | 0.147844 |
| diagnostic_test | >= 1 | STUDENT_GLOBAL_BETA | 178,253 | 0.609822 | 0.446824 | 0.137728 |
| diagnostic_test | >= 1 | TOPIC_RATE | 178,253 | 0.617212 | 0.442176 | 0.136796 |
| diagnostic_test | >= 3 | C40_MASTERY | 170,219 | 0.615875 | 0.659951 | 0.233467 |
| diagnostic_test | >= 3 | RULE_BETA | 170,219 | 0.633877 | 0.451612 | 0.140368 |
| diagnostic_test | >= 3 | STUDENT_GLOBAL_BETA | 170,219 | 0.608894 | 0.441224 | 0.135520 |
| diagnostic_test | >= 3 | TOPIC_RATE | 170,219 | 0.613138 | 0.437584 | 0.134793 |
| diagnostic_test | >= 5 | C40_MASTERY | 167,300 | 0.615981 | 0.659367 | 0.233176 |
| diagnostic_test | >= 5 | RULE_BETA | 167,300 | 0.634973 | 0.447155 | 0.138373 |
| diagnostic_test | >= 5 | STUDENT_GLOBAL_BETA | 167,300 | 0.608613 | 0.440438 | 0.135196 |
| diagnostic_test | >= 5 | TOPIC_RATE | 167,300 | 0.612488 | 0.436832 | 0.134454 |

## Primary observed >=3 comparison

| Comparison | AUC delta | Student-cluster 95% CI | Valid / requested resamples |
| --- | ---: | --- | ---: |
| C40 mastery - RuleBeta | -0.018003 | [-0.024807, -0.011203] | 500 / 500 |

At diagnostic-test exposure >=3, C40-versus-RuleBeta Spearman correlation is
**0.867901**. The structured result
contains ten-bin calibration tables and within-Topic rank-agreement summaries
for every listed score and threshold.
