# MODEL-3 Development-only Model Selection

## Status

**DEVELOPMENT_SELECTION_COMPLETED_FINAL_LABELS_UNREAD**

Only MODEL-1 train/history fitted candidates and MODEL-1 validation selected
the Rasch epoch and the small pre-registered `lambda_delta` grid. The historical
MODEL-1 diagnostic partition was not scored in this selection. Final holdout
labels remain unread: `False`.

| Candidate | lambda_delta | Selected epoch | AUC | ACC | RMSE | Log Loss | Brier |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| EXERCISE_RATE_DEV | N/A | N/A | 0.713473 | 0.826691 | 0.363614 | 0.421608 | 0.132215 |
| RASCH_1PL_V1 | N/A | 5 | 0.737412 | 0.829365 | 0.358275 | 0.411089 | 0.128361 |
| HIER_RASCH_TOPIC_V1 **selected** | 0.03 | 2 | 0.739293 | 0.829910 | 0.357995 | 0.410306 | 0.128160 |
| HIER_RASCH_TOPIC_V1 | 0.10 | 2 | 0.738916 | 0.829973 | 0.358048 | 0.410445 | 0.128198 |
| HIER_RASCH_TOPIC_V1 | 0.30 | 2 | 0.738423 | 0.829965 | 0.358120 | 0.410621 | 0.128250 |
| HIER_RASCH_TOPIC_V1 | 1.00 | 1 | 0.737997 | 0.829797 | 0.358198 | 0.411040 | 0.128306 |

The frozen hierarchical selection is `lambda_delta=0.03`.
No architecture search, graph model, neural hidden layer, or final-holdout
metric informed this choice.
