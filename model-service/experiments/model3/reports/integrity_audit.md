# MODEL-3 Integrity Audit

## Status

**INTEGRITY_AUDIT_PASSED_NO_FINAL_LABEL_READ**

This audit reads frozen membership, archived manifest, development identity, and
raw Exercise metadata only. It does **not** open the final ProblemLog
`correct` column or materialize final response rows.

| Check | Value |
| --- | --- |
| Dataset | JUNYI_FINAL_HOLDOUT_V1 |
| Archived status | FROZEN_UNEVALUATED |
| MODEL-3 ledger status | FROZEN_UNEVALUATED |
| Frozen final students | 5000 |
| Final / MODEL-1 overlap | 0 |
| Final / original Medium overlap | 0 |
| MODEL-2 labels read | False |
| MODEL-2 metrics generated | False |
| MODEL-3 labels read | False |
| MODEL-3 metrics generated | False |

## Identity consistency

| Check | Value |
| --- | ---: |
| Development Exercises | 624 |
| Development Topic catalog | 40 |
| Final eligible Exercises | 624 |
| Active final eligible Topics | 39 |
| Metadata identity/Topic mismatches | 0 |

## Frozen protocol before final labels

- Candidates: `EXERCISE_RATE_DEV, RASCH_1PL_V1, HIER_RASCH_TOPIC_V1`
- Pre-registered `lambda_delta`: `[0.03, 0.1, 0.3, 1.0]`
- Student-cluster bootstrap resamples: `1000`
- Allowed final Gate statuses: `GO_HIERARCHICAL_MODEL_INTEGRATION, GO_RASCH_ONLY_INTEGRATION, STOP_ML_INTEGRATION_RULE_ONLY`

No final label or metric artifact existed when this audit completed. The next
allowed phase is development-only model selection and global refit.
