# MODEL-2 Statistical Report

## Primary confidence intervals

- Method: Student-cluster paired bootstrap: students are resampled with replacement, and every selected student's evaluation rows retain the same cluster multiplicity.
- Confidence level: 95%
- Requested resamples: 500
- Interaction-level bootstrap is intentionally not used as a primary MODEL-2
  confidence interval.

The primary paired comparisons cover C40 identity ablation, observed C40 versus
RuleBeta at exposure >=3, and each simple student signal versus ExerciseRate.
All preserve complete within-student evaluation clusters on each resample.

## Statistical fallacy scan (11/11 checked)

| Check | Finding |
| --- | --- |
| Simpson's paradox | Checked by exposure and within-Topic reporting; no aggregate-only gate claim is used. |
| Ecological fallacy | Student-cluster resampling preserves the individual student as the dependence unit; aggregate metrics are not interpreted as individual causal effects. |
| Berkson's paradox | CAUTION: eligibility requires at least 20 Q-eligible interactions, so conclusions are limited to this observed-student population. |
| Collider bias | No post-outcome covariate adjustment or collider control is used. |
| Base-rate neglect | Outcome rates, Log Loss, Brier, and calibration bins accompany AUC. |
| Regression to the mean | No intervention or selected-extreme pre/post claim is made. |
| Survivorship bias | CAUTION: the external-cohort and >=20-interaction boundary is documented; no claim is extended to excluded students. |
| Look-elsewhere effect | The four ablations and named simple baselines are fixed in the Issue/plan; no unlisted model search occurred. |
| Garden of forking paths | Seeds, membership rule, thresholds, C40 checkpoint, and bootstrap method are fixed before diagnostic outputs. |
| Correlation is not causation | Future-response associations are reported without causal language. |
| Reverse causality | Train/history temporally precedes evaluation rows, but unmeasured ability/confounding remains possible and no causal claim is made. |

No p-values, causal effect sizes, or intervention effects are claimed. The
reported intervals quantify diagnostic prediction/ranking differences under the
frozen cohort and chronology only.
