# MODEL-3 Statistical Report

## Primary comparisons

Method: student-cluster paired bootstrap. Each resample draws whole students
with replacement and gives all of each sampled student's evaluation rows the
same multiplicity. Interaction-level bootstrap is not used as the primary
interval.

| Comparison | Student clusters | Valid / requested resamples | AUC delta | 95% CI |
| --- | ---: | ---: | ---: | --- |
| rasch_minus_exercise_rate | 5000 | 1000 / 1000 | 0.017465 | [0.012394, 0.022665] |
| hierarchical_minus_rasch | 5000 | 1000 / 1000 | -0.014660 | [-0.017391, -0.011977] |

## Statistical fallacy scan (11/11 checked)

| Check | Finding |
| --- | --- |
| Simpson's paradox | Checked with exposure-stratified Topic reporting; no aggregate-only Gate claim is used. |
| Ecological fallacy | Student-cluster resampling keeps the student as the dependence unit; no individual causal claim is made. |
| Berkson's paradox | CAUTION: the frozen cohort requires at least 20 eligible interactions, so claims are limited to this observed population. |
| Collider bias | No post-outcome covariate adjustment is performed. |
| Base-rate neglect | AUC is accompanied by ACC, RMSE, Log Loss, Brier, and calibration. |
| Regression to the mean | No selected-extreme pre/post intervention claim is made. |
| Survivorship bias | CAUTION: excluded students are outside the frozen cohort claim. |
| Look-elsewhere effect | Only the pre-registered three candidates and four-value lambda grid were considered. |
| Garden of forking paths | Chronology, fallback, metric set, bootstrap, Gate, and lambda grid were frozen before final labels. |
| Correlation is not causation | Topic-deviation future association is descriptive and non-causal. |
| Reverse causality | Calibration precedes evaluation chronologically, but no causal interpretation is asserted. |
