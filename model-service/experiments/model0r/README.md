# MODEL-0R: separated warm diagnosis and new-student cold start

This is the Issue #8 controlled re-evaluation workspace.  It answers two
different questions on the same frozen DATA-0 Medium population without
rewriting the archived first experiment:

- `JUNYI_MID_TRANS_V1`: warm/transductive diagnosis.  The same 10,000 students
  are retained; each individual sequence is chronological 60/20/20.
- `JUNYI_MID_COLD_V1`: new-student cold start.  DATA-0's exact 7,000 / 1,000 /
  2,000 student membership remains frozen, with k = 0, 3, 5, and 10 initial
  responses reserved exclusively for calibration.

The reviewed experiment plans and semantic boundary are
[`docs/MODEL-0R_PLAN.md`](../../../../docs/MODEL-0R_PLAN.md),
[`ADR-0004`](../../../../docs/ADR/0004-cognitive-diagnosis-evaluation-protocol.md),
and [`ADR-0002`](../../../../docs/ADR/0002-post-data0-domain-model.md).

## Historical boundary

`model-service/experiments/model0/` is the separate archived
`MODEL-0A / COLD_ZERO_HISTORY` experiment.  Its original terminal decision,
`NO_GO_FOR_ZERO_HISTORY_PROTOCOL_AS_MODEL_SELECTION`, remains untouched.  Its
reported NCDM (AUC 0.756775, ACC 0.820678, RMSE 0.361951) and ORCDF-NCD (AUC
0.638144, ACC 0.813219, RMSE 0.472493) values are historical zero-history
unseen-student compatibility results, not this workspace's standard diagnosis
selection table and not evidence that cognitive diagnosis as a whole failed.

## Reproducible local run

Use an environment that satisfies `model-service/requirements.txt`, then pass
the existing local Medium materialization explicitly; it is never copied into
the repository:

```powershell
$python = ".\\model-service\\.venv\\Scripts\\python.exe"
$medium = "D:\\path\\to\\data-pipeline\\.data\\generated\\medium"
& $python .\\model-service\\experiments\\model0r\\scripts\\preflight.py --medium-root $medium
& $python .\\model-service\\experiments\\model0r\\scripts\\run_warm.py
& $python .\\model-service\\experiments\\model0r\\scripts\\run_cold.py
& $python .\\model-service\\experiments\\model0r\\scripts\\final_gate.py
```

`preflight.py` checks the exact member lists, schemas, 40 Topic Q-matrix,
full-record duplicate rule, and split boundaries before training.  The warm
runner selects NCDM and ORCDF only on validation data, then evaluates test once.
The cold runner freezes every global NCDM parameter while fitting only a
standalone new-student representation; it audits global-state and evaluation
representation immutability.

## Deliberate route limits

- RCD is `NOT_COMPARABLE_ON_COMMON_GRAPH` because no audited common Concept
  Graph and Exercise identity mapping exists.
- GEAR-CD is smoke only; no full MODEL-0R training is authorized.
- ORCDF cold calibration is `NOT_SUPPORTED_BY_CURRENT_ADAPTER`, because its
  response-graph implementation does not expose a standalone safe
  new-student-representation path.  It is not substituted with a zero vector.
- ICDM is `NOT_AUDITABLY_ALIGNED_FOR_CURRENT_40_TOPIC_INPUT`: no declared
  official license and no audited input/Q-matrix semantic mapping were found;
  no ID mapping is invented.

## Tracking boundary

Tracked files are configurations, scripts, small manifests, unit tests, and
human-reviewable reports.  Raw data, large derived tables, checkpoints, Torch
runtime files, virtual environments, logs, and secrets remain ignored.  This
workspace creates no content digest; review is based on fixed paths, schemas,
exact member lists, counts, and deep state comparison.
