# MODEL-2 Bad Cases and Boundaries

| C40 diagnostic-test error type | Count |
| --- | ---: |
| False positives | 40,913 |
| False negatives | 9,562 |
| UNKNOWN Topic rows (exposure=0) | 82,440 |
| OBSERVED_1 Topic rows | 178,253 |
| OBSERVED_3 Topic rows | 170,219 |
| OBSERVED_5 Topic rows | 167,300 |

UNKNOWN rows remain excluded from observed-only mastery claims; a model value
near 0.5 is not recoded as observed mastery.

## Anonymized original-C40 false-positive cases

| Case | Topic ID | Train/history Topic exposure | C40 prediction | RuleBeta | Future outcome |
| --- | ---: | ---: | ---: | ---: | ---: |
| false-positive-001 | 28 | 24 | 0.699148 | 0.923077 | 0 |
| false-positive-002 | 3 | 0 | 0.930910 | 0.500000 | 0 |
| false-positive-003 | 3 | 0 | 0.930910 | 0.500000 | 0 |
| false-positive-004 | 30 | 0 | 0.608465 | 0.500000 | 0 |
| false-positive-005 | 30 | 0 | 0.608465 | 0.500000 | 0 |
| false-positive-006 | 26 | 0 | 0.743608 | 0.500000 | 0 |
| false-positive-007 | 27 | 12 | 0.569027 | 0.928571 | 0 |
| false-positive-008 | 27 | 12 | 0.569027 | 0.928571 | 0 |
| false-positive-009 | 27 | 12 | 0.569027 | 0.928571 | 0 |
| false-positive-010 | 27 | 12 | 0.569027 | 0.928571 | 0 |

No external student ID, Exercise external ID, timestamp, or raw response is included.
