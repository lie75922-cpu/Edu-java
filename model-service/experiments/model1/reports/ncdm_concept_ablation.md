# MODEL-1 NCDM Concept-Granularity Ablation

## Frozen comparison

The same NCDM equations, hidden layers, dropout, learning rate, batch size,
maximum epochs, early stopping, seed, fresh holdout, and chronological
protocol are used for both candidates. Only Q-matrix / ModelConcept definition
changes.

Because each frozen Q row is exactly one-hot, both candidates use an
algebraically equivalent first-layer implementation that selects the sole
nonzero concept input instead of multiplying known-zero dimensions. It changes
neither the NCDM parameters nor its interaction equation.

| Candidate | Q-matrix | ModelConcept columns |
| --- | --- | ---: |
| C40_TOPIC | eligible Exercise -> frozen business Topic | 40 |
| C_FINE_EXERCISE | identity-style eligible Exercise -> sequential ModelConcept | 624 |

Only train/history rows fit models and statistical rates. Validation selected
the non-personalized comparator **exercise** before final test rows were opened. The final-test ledger records one evaluation per candidate.

## Non-personalized baselines

| Baseline | Validation AUC | Test AUC | Test ACC | Test RMSE | Test Log Loss |
| --- | ---: | ---: | ---: | ---: | ---: |
| Global correct-rate baseline | 0.500000 | 0.500000 | 0.809139 | 0.394093 | 0.490563 |
| Exercise historical-rate baseline | 0.713206 | 0.708358 | 0.809772 | 0.377696 | 0.459412 |
| Topic historical-rate baseline | 0.632272 | 0.628825 | 0.809151 | 0.386414 | 0.471503 |

## NCDM results

| Candidate | Selected epoch | Validation AUC | Test AUC | Test ACC | Test RMSE | Test Log Loss | Test AUC delta | Bootstrap lower bound | DOA |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| C40_TOPIC / NCDM | 2 | 0.735339 | 0.719500 | 0.806381 | 0.377036 | 0.444707 | 0.011142 | 0.009695 | 0.576406 |
| C_FINE_EXERCISE / NCDM | 2 | 0.723482 | 0.708524 | 0.797655 | 0.382632 | 0.455675 | 0.000167 | -0.001122 | 0.550057 |

The paired bootstrap is a final-test report-only measurement against the
validation-selected comparator; it was not used for selection. DOA is the
pre-registered Exercise-conditioned future-response agreement and uses test
outcomes only to score mastery fitted from train/history.
