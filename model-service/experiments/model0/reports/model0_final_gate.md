# MODEL-0 Final Gate

## Decision

**NO_GO** for model production integration in MODEL-0.

## Decision basis

1. The frozen student-disjoint split has no Owner-approved held-out-student calibration protocol; evaluation therefore measures only zero-history response prediction, not individually fitted diagnosis.
2. ORCDF did not improve the shared cold-start test AUC over NCDM and used materially more peak process RAM.
3. RCD lacks an auditable common concept and Exercise identity mapping, so it cannot support a fair comparison.
4. GEAR-CD has no verified local executable runtime or common identity mapping; only a blocked smoke audit is available.
5. DOA is not applicable under neutral held-out embeddings, leaving no valid held-out mastery-order evidence.

The NCDM result is a usable controlled baseline measurement, but it is not
sufficient evidence to integrate a diagnosis service: the test students have no
fitted model state. ORCDF is both weaker on the same limited protocol and more
memory intensive. No result justifies changing the Java AI Gateway or starting
recommendation integration.

## Evidence closure

- Preflight: `PASS_WITH_COLD_START_LIMITATION`.
- NCDM final test: exactly one completed ledger entry.
- ORCDF final test: exactly one completed ledger entry.
- RCD: `NOT_COMPARABLE_ON_COMMON_GRAPH`; no test evaluation.
- GEAR-CD: `SMOKE_BLOCKED_NO_VERIFIED_RUNTIME_OR_COMMON_IDENTITY_MAPPING`; no model test evaluation and no full
  Medium training.
- Production Java AI Gateway changes: **none**.
- Recommendation integration: **not started**.
- Raw prerequisite evidence publication: **none**.

## Stop boundary

MODEL-0 ends here. Any later work must be a newly authorized experiment: it
would need an Owner-approved cold-start/calibration protocol that does not tune
against test labels, plus a new gate before considering integration. It must not
reuse this final test result as a tuning signal.

