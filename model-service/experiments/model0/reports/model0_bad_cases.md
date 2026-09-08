# MODEL-0 bad cases and limitations

## Input-level cases

- The Medium source contains 17
  exact duplicate interaction rows. They were removed only from the local
  derivative; DATA-0 materialisation remains unchanged.
- Unknown Exercise interactions after duplicate removal:
  0.
- Known-but-Topic-unmapped interactions after duplicate removal:
  0.
- The induced raw-prerequisite evidence graph still has
  1 self-loop
  and 2 cyclic
  SCCs. It remains evidence only and was not published or fed into ORCDF.

## Evaluation-level limitation

All 3,000 validation/test students are held out at the student level. Their
model embeddings remain neutral and no held-out response is used for
calibration. Consequently, `DOA` is intentionally N/A, and the reported AUC,
ACC, and RMSE do not establish individualized diagnostic quality.

## NCDM test cases

| Case | Count / result |
| --- | --- |
| False positives | 8827 |
| False negatives | 1415 |
| Test rows on Exercises unseen in train | 119 |
| Unseen-Exercise AUC | 0.548474 |
| Unseen-Exercise ACC | 0.512605 |
| Unseen-Exercise RMSE | 0.567549 |

## ORCDF test cases

| Case | Count / result |
| --- | --- |
| False positives | 10668 |
| False negatives | 0 |
| Test rows on Exercises unseen in train | 119 |
| Unseen-Exercise AUC | 0.404042 |
| Unseen-Exercise ACC | 0.512605 |
| Unseen-Exercise RMSE | 0.500945 |

ORCDF's zero false-negative / 10668 false-positive
pattern shows that its selected cold-start output classified every test response
as correct at the 0.5 threshold. This is a failure mode, not a favorable
accuracy interpretation.

## Routes not eligible for a fair result

- **RCD:** `NOT_COMPARABLE_ON_COMMON_GRAPH`; no numeric concept or Exercise
  identity mapping was inferred.
- **GEAR-CD:** smoke blocked because the local artifact has data but no verified
  executable runtime or common identity mapping. No resource estimate is
  invented from other models.

