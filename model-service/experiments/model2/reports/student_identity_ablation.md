# MODEL-2 Student-identity Inference Ablation

All four columns use the same selected MODEL-1 C40 NCDM checkpoint. Only the
student embedding table differs. Exercise difficulty, discrimination,
interaction MLP, Q matrix, batch order, and evaluation rows are unchanged.

| Student embedding | Validation AUC | Diagnostic-test AUC | ACC | RMSE | Log Loss |
| --- | ---: | ---: | ---: | ---: | ---: |
| ORIGINAL | 0.735339 | 0.719500 | 0.806381 | 0.377036 | 0.444707 |
| POP_MEAN | 0.710418 | 0.706657 | 0.805300 | 0.380077 | 0.451361 |
| ZERO | 0.708583 | 0.704477 | 0.804908 | 0.382443 | 0.456362 |
| PERMUTED | 0.701419 | 0.697232 | 0.800654 | 0.383557 | 0.459121 |

The deterministic permutation uses seed 2026090803 and has
0 fixed points (required: zero).

| Paired comparison | AUC delta | Student-cluster 95% CI | Valid / requested resamples |
| --- | ---: | --- | ---: |
| ORIGINAL_MINUS_POP_MEAN | 0.012844 | [0.008400, 0.017457] | 500 / 500 |
| ORIGINAL_MINUS_ZERO | 0.015023 | [0.010617, 0.019536] | 500 / 500 |
| ORIGINAL_MINUS_PERMUTED | 0.022268 | [0.016763, 0.027972] | 500 / 500 |
