# MODEL-2 Asset Audit

## Status

**REUSE_LOCAL_MODEL1_C40**

The local asset location was supplied explicitly for this diagnostic run. The
checkpoint and derivatives remain external ignored artifacts; MODEL-2 neither
copies nor modifies them.

| Asset | Present | Size bytes |
| --- | --- | ---: |
| manifest | True | 4692 |
| membership | True | 67961 |
| student_index | True | 86713 |
| exercise_index | True | 21105 |
| topic_index | True | 1077 |
| q_c40 | True | 49920 |
| train | True | 10108858 |
| valid | True | 3374085 |
| test | True | 3454126 |
| checkpoint | True | 967322 |
| historical_result | True | 327953 |

| Check | Value |
| --- | --- |
| Missing assets | none |
| MODEL-1 manifest status | FROZEN_BEFORE_MODEL_TRAINING |
| MODEL-1 frozen students | 5000 |
| MODEL-1 / original Medium overlap | 0 |
| MODEL-1 selection read `correct` | False |
| Historical MODEL-1 test role | Diagnostic source only. MODEL-2 does not rerun or rewrite the MODEL-1 historical result, and it does not treat this partition as a new final holdout. |
| New final holdout role | JUNYI_FINAL_HOLDOUT_V1 is frozen only. Its labels and metrics are prohibited in MODEL-2. |

No new C40 training, RCD, ORCDF, GEAR-CD, Java MasteryProvider,
recommendation, or Published Graph work is authorized by this audit.
