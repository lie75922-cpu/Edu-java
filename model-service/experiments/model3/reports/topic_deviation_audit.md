# MODEL-3 Topic-deviation Audit

## Semantic boundary

delta is a local logit-space deviation. UNKNOWN means calibration exposure=0, delta=0, and is never mastery=0.5.

Observed delta distribution: count=20370,
P05=-2.759707, P50=0.000927,
P95=0.948395, std=1.083328.
Unknown delta exactly zero: `True`.

| Calibration Topic exposure | Student-Topic pairs | Fraction of all pairs | Evaluation rows |
| --- | ---: | ---: | ---: |
| >= 1 | 20370 | 0.101850 | 382178 |
| >= 3 | 17176 | 0.085880 | 365428 |
| >= 5 | 16040 | 0.080200 | 357705 |

| Calibration Topic exposure | Delta count | Future delta rank AUC | Delta/outcome correlation | Hierarchical minus Rasch AUC | Delta/RuleBeta Spearman |
| --- | ---: | ---: | ---: | ---: | ---: |
| >= 1 | 382178 | 0.586012 | 0.109447 | -0.018490 | 0.664655 |
| >= 3 | 365428 | 0.579526 | 0.104367 | -0.015031 | 0.666106 |
| >= 5 | 357705 | 0.579475 | 0.107633 | -0.013210 | 0.663141 |

The RuleBeta relationship is an offline calibration-only Beta(1,1) comparator,
not a Java provider output. UNKNOWN rows are excluded rather than assigned a
numeric mastery value.

## 20 anonymized student cases

| Case | Calibration rows | Evaluation rows | Observed Topics | UNKNOWN Topics | Hierarchical theta | Observed delta median |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| student-001 | 13 | 10 | 2 | 38 | -0.096336 | -0.298030 |
| student-002 | 19 | 14 | 4 | 36 | -1.104798 | -1.627611 |
| student-003 | 30 | 21 | 3 | 37 | -0.212794 | -0.432223 |
| student-004 | 132 | 88 | 2 | 38 | 0.207392 | 0.116886 |
| student-005 | 42 | 29 | 2 | 38 | 0.672965 | 0.110714 |
| student-006 | 21 | 15 | 3 | 37 | 0.183656 | -0.059585 |
| student-007 | 43 | 30 | 3 | 37 | -0.082057 | -0.152171 |
| student-008 | 13 | 9 | 4 | 36 | 0.034490 | -0.194126 |
| student-009 | 127 | 86 | 4 | 36 | 0.059422 | 0.026980 |
| student-010 | 12 | 8 | 1 | 39 | -2.758792 | -0.919597 |
| student-011 | 144 | 97 | 6 | 34 | -0.047493 | 0.234963 |
| student-012 | 28 | 19 | 1 | 39 | 0.010816 | -0.136978 |
| student-013 | 67 | 45 | 1 | 39 | -0.130747 | 0.116104 |
| student-014 | 51 | 34 | 5 | 35 | -0.861735 | -0.265211 |
| student-015 | 91 | 62 | 5 | 35 | 1.328304 | 0.271279 |
| student-016 | 60 | 40 | 4 | 36 | 0.362503 | 0.230454 |
| student-017 | 1305 | 871 | 18 | 22 | -0.168410 | -0.186661 |
| student-018 | 395 | 264 | 8 | 32 | 0.104664 | 0.058448 |
| student-019 | 180 | 120 | 4 | 36 | 0.256553 | 0.308539 |
| student-020 | 116 | 78 | 4 | 36 | 0.182493 | 0.300554 |
