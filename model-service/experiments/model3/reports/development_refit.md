# MODEL-3 Development Global Refit

## Status

**DEVELOPMENT_GLOBAL_REFIT_COMPLETED_FINAL_LABELS_UNREAD**

The refit used the historical MODEL-1 development cohort only after model form,
the selected `lambda_delta`, fallback policy, and final Gate were frozen. It
did not open any `JUNYI_FINAL_HOLDOUT_V1` label.

| Check | Value |
| --- | --- |
| Development interaction rows | 1283721 |
| Known Exercises | 614 |
| Fallback Exercises | 10 |
| Rasch refit epochs | 5 |
| Hierarchical refit epochs | 2 |
| Selected lambda_delta | 0.03 |
| Final labels read | False |

The local parameter artifact is ignored by Git and contains only development
global parameters. The final runner may optimize only per-final-student theta
and, for the hierarchical candidate, per-final-student Topic deviation.
