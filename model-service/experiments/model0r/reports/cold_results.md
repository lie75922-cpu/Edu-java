# MODEL-0R New-Student Cold Results

## Protocol boundary

The global NCDM model was trained only with the frozen 7,000 train students.
Validation and test students remain unseen during global fitting.  For every
eligible new student, the first k chronological responses are calibration only
and all later responses are evaluation only.  Students with fewer than k+1
rows remain frozen members and are reported as `INELIGIBLE_FOR_K`.

NCDM adaptation uses a standalone 40-dimensional student representation as the
only optimizer parameter.  All global parameters are set `requires_grad=False`
during calibration, their complete tensors are compared before and after each
evaluation, and evaluation runs with `torch.no_grad`.

| Global model selection | Value |
| --- | --- |
| Selected epoch | 5 |
| Validation k=5 AUC / RMSE | 0.724266 / 0.376629 |
| Global train time | 393.561 s |
| Checkpoint size | 1,767,420 bytes (1.69 MiB) |
| Calibration learning rate / max steps | 0.05 / 40 |
| Calibration stopping rule | Stop after three consecutive non-improving calibration BCE values, or at max_steps. Improvement requires a strict decrease greater than 0.000001. |

## Validation (selection evidence; k=5 was used only for global checkpoint selection)

| Model | k | Eligible | Coverage | Evaluation rows | AUC | ACC | RMSE | Log Loss | DOA | Calibration mean / P50 / P95 (ms) | Inference |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| global historical rate | 0 | 1000 | 100.00% | 27,721 | 0.500000 | 0.827640 | 0.377739 | 0.459727 | N/A | 0.000 / 0.000 / 0.000 | 0.000554 ms/row |
| exercise historical rate | 0 | 1000 | 100.00% | 27,721 | 0.750655 | 0.834494 | 0.352497 | 0.414846 | N/A | 0.000 / 0.000 / 0.000 | 0.000643 ms/row |
| topic historical rate | 0 | 1000 | 100.00% | 27,721 | 0.684484 | 0.829696 | 0.363465 | 0.426111 | N/A | 0.000 / 0.000 / 0.000 | 0.000743 ms/row |
| NCDM (representation-only adaptation) | 0 | 1000 | 100.00% | 27,721 | 0.742784 | 0.828217 | 0.362709 | 0.420963 | N/A | 0.034 / 0.032 / 0.046 | 0.021328 ms/row |
| global historical rate | 3 | 1000 | 100.00% | 24,721 | 0.500000 | 0.835120 | 0.371312 | 0.448294 | N/A | 0.000 / 0.000 / 0.000 | 0.000561 ms/row |
| exercise historical rate | 3 | 1000 | 100.00% | 24,721 | 0.756862 | 0.841107 | 0.346026 | 0.402687 | N/A | 0.000 / 0.000 / 0.000 | 0.000630 ms/row |
| topic historical rate | 3 | 1000 | 100.00% | 24,721 | 0.688451 | 0.836738 | 0.357209 | 0.414617 | N/A | 0.000 / 0.000 / 0.000 | 0.000577 ms/row |
| NCDM (representation-only adaptation) | 3 | 1000 | 100.00% | 24,721 | 0.707654 | 0.791149 | 0.399625 | 0.594742 | 0.538869 | 33.179 / 38.147 / 56.262 | 0.023838 ms/row |
| global historical rate | 5 | 963 | 96.30% | 22,721 | 0.500000 | 0.834558 | 0.371798 | 0.449153 | N/A | 0.000 / 0.000 / 0.000 | 0.000582 ms/row |
| exercise historical rate | 5 | 963 | 96.30% | 22,721 | 0.759278 | 0.840500 | 0.346262 | 0.401410 | N/A | 0.000 / 0.000 / 0.000 | 0.000629 ms/row |
| topic historical rate | 5 | 963 | 96.30% | 22,721 | 0.687419 | 0.835923 | 0.357929 | 0.415850 | N/A | 0.000 / 0.000 / 0.000 | 0.000573 ms/row |
| NCDM (representation-only adaptation) | 5 | 963 | 96.30% | 22,721 | 0.724266 | 0.814401 | 0.376629 | 0.533001 | 0.551435 | 31.921 / 35.599 / 57.915 | 0.023779 ms/row |
| global historical rate | 10 | 682 | 68.20% | 18,453 | 0.500000 | 0.837262 | 0.369451 | 0.445020 | N/A | 0.000 / 0.000 / 0.000 | 0.000698 ms/row |
| exercise historical rate | 10 | 682 | 68.20% | 18,453 | 0.761726 | 0.842194 | 0.344480 | 0.395350 | N/A | 0.000 / 0.000 / 0.000 | 0.000795 ms/row |
| topic historical rate | 10 | 682 | 68.20% | 18,453 | 0.689988 | 0.838400 | 0.355636 | 0.411549 | N/A | 0.000 / 0.000 / 0.000 | 0.000682 ms/row |
| NCDM (representation-only adaptation) | 10 | 682 | 68.20% | 18,453 | 0.718170 | 0.822685 | 0.369595 | 0.522048 | 0.554447 | 32.361 / 31.446 / 65.850 | 0.022214 ms/row |

## Final test

| Model | k | Eligible | Coverage | Evaluation rows | AUC | ACC | RMSE | Log Loss | DOA | Calibration mean / P50 / P95 (ms) | Inference |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| global historical rate | 0 | 2000 | 100.00% | 57,115 | 0.500000 | 0.813219 | 0.389830 | 0.481769 | N/A | 0.000 / 0.000 / 0.000 | 0.000498 ms/row |
| exercise historical rate | 0 | 2000 | 100.00% | 57,115 | 0.760025 | 0.825527 | 0.360759 | 0.440756 | N/A | 0.000 / 0.000 / 0.000 | 0.000528 ms/row |
| topic historical rate | 0 | 2000 | 100.00% | 57,115 | 0.688642 | 0.815075 | 0.374025 | 0.444640 | N/A | 0.000 / 0.000 / 0.000 | 0.000503 ms/row |
| NCDM (representation-only adaptation) | 0 | 2000 | 100.00% | 57,115 | 0.752326 | 0.815565 | 0.368304 | 0.430111 | N/A | 0.028 / 0.027 / 0.036 | 0.017315 ms/row |
| ORCDF-NCD | 0 | 2000 | 100.00% | 57,115 | NOT_SUPPORTED_BY_CURRENT_ADAPTER | N/A | N/A | N/A | N/A | N/A | N/A |
| ICDM | 0 | 2000 | 100.00% | 57,115 | NOT_AUDITABLY_ALIGNED_FOR_CURRENT_40_TOPIC_INPUT | N/A | N/A | N/A | N/A | N/A | N/A |
| global historical rate | 3 | 2000 | 100.00% | 51,115 | 0.500000 | 0.819857 | 0.384311 | 0.471623 | N/A | 0.000 / 0.000 / 0.000 | 0.000501 ms/row |
| exercise historical rate | 3 | 2000 | 100.00% | 51,115 | 0.765274 | 0.831380 | 0.355185 | 0.427857 | N/A | 0.000 / 0.000 / 0.000 | 0.000417 ms/row |
| topic historical rate | 3 | 2000 | 100.00% | 51,115 | 0.693870 | 0.821833 | 0.368299 | 0.433740 | N/A | 0.000 / 0.000 / 0.000 | 0.000397 ms/row |
| NCDM (representation-only adaptation) | 3 | 2000 | 100.00% | 51,115 | 0.694576 | 0.775839 | 0.418524 | 0.666173 | 0.539834 | 40.775 / 45.357 / 70.311 | 0.030429 ms/row |
| ORCDF-NCD | 3 | 2000 | 100.00% | 51,115 | NOT_SUPPORTED_BY_CURRENT_ADAPTER | N/A | N/A | N/A | N/A | N/A | N/A |
| ICDM | 3 | 2000 | 100.00% | 51,115 | NOT_AUDITABLY_ALIGNED_FOR_CURRENT_40_TOPIC_INPUT | N/A | N/A | N/A | N/A | N/A | N/A |
| global historical rate | 5 | 1928 | 96.40% | 47,115 | 0.500000 | 0.819781 | 0.384375 | 0.471738 | N/A | 0.000 / 0.000 / 0.000 | 0.000604 ms/row |
| exercise historical rate | 5 | 1928 | 96.40% | 47,115 | 0.765516 | 0.831370 | 0.355141 | 0.425750 | N/A | 0.000 / 0.000 / 0.000 | 0.000617 ms/row |
| topic historical rate | 5 | 1928 | 96.40% | 47,115 | 0.693082 | 0.821798 | 0.368431 | 0.434024 | N/A | 0.000 / 0.000 / 0.000 | 0.000612 ms/row |
| NCDM (representation-only adaptation) | 5 | 1928 | 96.40% | 47,115 | 0.712998 | 0.794206 | 0.394450 | 0.575289 | 0.543552 | 38.333 / 42.361 / 70.898 | 0.040393 ms/row |
| ORCDF-NCD | 5 | 1928 | 96.40% | 47,115 | NOT_SUPPORTED_BY_CURRENT_ADAPTER | N/A | N/A | N/A | N/A | N/A | N/A |
| ICDM | 5 | 1928 | 96.40% | 47,115 | NOT_AUDITABLY_ALIGNED_FOR_CURRENT_40_TOPIC_INPUT | N/A | N/A | N/A | N/A | N/A | N/A |
| global historical rate | 10 | 1388 | 69.40% | 38,563 | 0.500000 | 0.821098 | 0.383271 | 0.469726 | N/A | 0.000 / 0.000 / 0.000 | 0.000696 ms/row |
| exercise historical rate | 10 | 1388 | 69.40% | 38,563 | 0.766850 | 0.832067 | 0.354132 | 0.419106 | N/A | 0.000 / 0.000 / 0.000 | 0.000682 ms/row |
| topic historical rate | 10 | 1388 | 69.40% | 38,563 | 0.698255 | 0.822913 | 0.366886 | 0.430744 | N/A | 0.000 / 0.000 / 0.000 | 0.000684 ms/row |
| NCDM (representation-only adaptation) | 10 | 1388 | 69.40% | 38,563 | 0.726533 | 0.805902 | 0.385002 | 0.541738 | 0.555290 | 50.753 / 42.574 / 117.483 | 0.040935 ms/row |
| ORCDF-NCD | 10 | 1388 | 69.40% | 38,563 | NOT_SUPPORTED_BY_CURRENT_ADAPTER | N/A | N/A | N/A | N/A | N/A | N/A |
| ICDM | 10 | 1388 | 69.40% | 38,563 | NOT_AUDITABLY_ALIGNED_FOR_CURRENT_40_TOPIC_INPUT | N/A | N/A | N/A | N/A | N/A | N/A |

For k=0, NCDM is explicitly the `ZERO_HISTORY_BASELINE`: its standalone
representation is zero and it performs zero optimization steps.  It is not
called calibrated adaptation.  For k>0, DOA is reported only as an
exercise-conditioned score of representations fitted from calibration rows.

## Unsupported or blocked routes

- **ORCDF-NCD:** `NOT_SUPPORTED_BY_CURRENT_ADAPTER` — The archived ORCDF adapter convolves a fixed all-student response graph and does not expose a standalone new-student representation path that can be optimized without changing graph/global state.
- **ICDM:** `NOT_AUDITABLY_ALIGNED_FOR_CURRENT_40_TOPIC_INPUT` — The official repository lacks a declared license and no audited input/Q-matrix semantic alignment to this 40 Topic representation is available; no ID mapping is invented.

No ID mapping was invented for either route.
