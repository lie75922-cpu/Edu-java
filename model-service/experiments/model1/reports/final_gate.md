# MODEL-1 Final Gate

## Decision

**NO_GO_CONCEPT_LAYER**

## Fine ModelConcept evidence

| Check | Value |
| --- | --- |
| Validation-selected non-personalized baseline | exercise |
| Fine final-test AUC / ACC / RMSE / Log Loss | 0.708524 / 0.797655 / 0.382632 / 0.455675 |
| Fine final-test AUC delta / bootstrap lower bound | 0.000167 / -0.001122 |
| Fine median concept standard deviation | 0.005194 |
| Fine mean pairwise cosine | 0.999814 |
| Fine train coverage fraction | 0.966346 |
| Fine constant-column fraction | 0.000000 |
| Fine NaN / infinite values | 0 / 0 |
| Fine-to-Topic aggregation | FLAGGED |

| Candidate | Test AUC | Test ACC | Test RMSE | Test Log Loss | Bootstrap lower bound | Mastery sanity |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| C40_TOPIC / NCDM | 0.719500 | 0.806381 | 0.377036 | 0.444707 | 0.009695 | FLAGGED |
| C_FINE_EXERCISE / NCDM | 0.708524 | 0.797655 | 0.382632 | 0.455675 | -0.001122 | FLAGGED |

## Gate failures retained

- fine paired-bootstrap AUC-delta lower bound is not above zero
- fine median per-concept student standard deviation is below the pre-registered minimum
- fine mean pairwise mastery cosine exceeds the pre-registered maximum
- fine-to-Topic aggregation is not stable and non-collapsed

## Stop boundary

No Java MasteryProvider, V0.4 rule mastery, recommendation, Published Graph,
or production model routing changed. MODEL-0A / COLD_ZERO_HISTORY and
MODEL-0R remain untouched historical evidence. If this gate does not fully
pass, no complex graph model is run.
