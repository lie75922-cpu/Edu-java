# MODEL-0 Medium Preflight

## Status

**PASS_WITH_COLD_START_LIMITATION**. `JUNYI_MID_MODEL_V1` was derived without
changing the frozen 10,000-student membership or the 7,000 / 1,000 / 2,000
student split. The derivative removes only later full-record-equal interactions
and excludes unknown or Topic-unmapped exercises from Q-matrix model input.

This is not a production dataset and no research student is imported into a
platform database.

## Immutable split evidence

| Check | Value |
| --- | ---: |
| Frozen train students | 7000 |
| Frozen valid students | 1000 |
| Frozen test students | 2000 |
| Train/valid overlap | 0 |
| Train/test overlap | 0 |
| Valid/test overlap | 0 |
| Member-list comparison | PASS |

## Interaction filtering

| Measurement | Count |
| --- | ---: |
| Source Medium interactions | 284245 |
| Exact duplicate interactions | 17 |
| Rows after exact-duplicate removal | 284228 |
| Unknown-exercise interactions before duplicate removal | 0 |
| Unknown-exercise interactions after duplicate removal | 0 |
| Known but Topic-unmapped interactions after duplicate removal | 0 |
| Retained Q-matrix-eligible interactions | 284228 |
| Observed Exercise IDs before filtering | 624 |
| Q-eligible observed Exercise IDs after filtering | 624 |

Exact duplicates are determined by equality of every documented interaction
field, not by a shortened business key. The first source-order occurrence is
retained in the local derivative; all original DATA-0 artifacts remain intact.

## Exercise to Topic mapping and Q-matrix

| Measurement | Count / value |
| --- | --- |
| Medium metadata rows | 815 |
| Medium metadata distinct external IDs | 815 |
| Metadata duplicate external IDs | 0 |
| Metadata Topic columns | 40 |
| Mapped metadata external IDs | 815 |
| Unmapped metadata external IDs | 0 |
| Conflicting-Topic external IDs | 0 |
| Q-matrix shape | 624 × 40 |
| Q-matrix non-zero entries | 624 |
| Q-matrix density | 0.025000 |
| Observed Exercise IDs excluded as unmapped | 0 |

The 40 columns are the non-missing Topics found in the Medium metadata. An
external ID is mapped only if all of its metadata rows yield one unique
non-missing Topic. Missing or conflicting identity evidence stays `UNMAPPED`.

## Retained split statistics

| Split | Students | Interactions | Correct rate |
| --- | ---: | ---: | ---: |
| train | 7000 | 199392 | 0.821778 |
| valid | 1000 | 27721 | 0.827640 |
| test | 2000 | 57115 | 0.813219 |

## Candidate-specific raw-prerequisite projection

The table below is an induced projection of raw prerequisite evidence, not a
Published Graph and not a production relationship import.

| Measurement | All Medium observed exercises | Q-eligible observed exercises |
| --- | ---: | ---: |
| Nodes | 624 | 624 |
| Raw relation rows in projection | 764 | 764 |
| Unique directed edges | 763 | 763 |
| Weak components | 26 | 26 |
| Largest-component ratio | 0.943910 | 0.943910 |
| Self-loops | 1 | 1 |
| Cyclic SCCs | 2 | 2 |

For comparison only, DATA-0's fixed scope contains 815 nodes
and 979 raw prerequisite edges. Those values are not used to
describe the Medium observed-exercise projection above.

## Evaluation limitation carried into NCDM and ORCDF

The split is deliberately student-disjoint. Standard NCDM and ORCDF contain
per-student parameters, so validation and test students have no training-row
signal. This derivative does **not** create calibration rows from held-out
students. Later model runs must therefore use a documented neutral,
zero-history fallback for held-out student embeddings. Their validation/test
metrics measure cold-start response prediction, not recovery of an individually
fitted held-out student's mastery state. A different temporal-calibration
protocol would require Owner approval and a new gate.

## Output boundaries

- Tracked evidence: this report, the manifest, fixed configuration, scripts,
  and tests.
- Ignored local data: numeric model tables, exact member lists, and index maps
  under `model-service/experiments/model0/data/`.
- No content digest is generated or recorded.

