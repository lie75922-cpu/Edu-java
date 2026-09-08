# MODEL-0A archive record: zero-history cold start

This is an archival record for Issue #4. It archives the completed first-round
MODEL-0 run; it is not a training run, a retuning record, or a replacement for
any of the historical reports.

## Classification and unchanged conclusion

- Experiment classification: `MODEL-0A / COLD_ZERO_HISTORY`.
- The original final-gate report remains unchanged at
  [`model0/reports/model0_final_gate.md`](model0/reports/model0_final_gate.md),
  including its `NO_GO` conclusion.
- In the Issue #4 architect interpretation, that conclusion is scoped as
  `NO_GO_FOR_ZERO_HISTORY_PROTOCOL_AS_MODEL_SELECTION`.
- It must **not** be represented as
  `NO_GO_FOR_COGNITIVE_DIAGNOSIS`. This negative experiment does not establish
  that the overall cognitive-diagnosis route has failed.

The reason for the scope is protocol-specific: validation and test students are
new, student-disjoint members with a neutral zero-history representation. No
validation or test response is used to calibrate an individual held-out student
embedding. The resulting measurements are therefore zero-history new-student
cold-start results, not evidence of personalized diagnosis quality for fitted
students.

## Archived result summary

| Route | AUC | ACC | RMSE | Archived status |
| --- | ---: | ---: | ---: | --- |
| NCDM | 0.756775 | 0.820678 | 0.361951 | completed under the shared zero-history protocol |
| ORCDF | 0.638144 | 0.813219 | 0.472493 | completed under the shared zero-history protocol |
| RCD | N/A | N/A | N/A | `NOT_COMPARABLE_ON_COMMON_GRAPH` |
| GEAR-CD | N/A | N/A | N/A | smoke only; no full model run |

- `DOA`: N/A under neutral held-out embeddings.
- Execution device: CPU; `GPU=none`.

The reported six-decimal values above are preserved from the original reports;
this archive does not alter the historical metrics, experiment configurations,
or final-gate decision.

## Historical artifact inventory

The following 29 pre-existing files are the archived MODEL-0 experiment
artifacts. This archive record is separate metadata and is not an additional
historical experiment output.

- `model0/README.md`
- `model0/configs/gear_cd_smoke.json`
- `model0/configs/ncdm.json`
- `model0/configs/orcdf.json`
- `model0/configs/preflight.json`
- `model0/configs/rcd_route.json`
- `model0/manifests/junyi_mid_model_v1.json`
- `model0/reports/gear_cd_smoke_result.json`
- `model0/reports/gear_cd_smoke.md`
- `model0/reports/model0_bad_cases.md`
- `model0/reports/model0_cost.md`
- `model0/reports/model0_final_gate.json`
- `model0/reports/model0_final_gate.md`
- `model0/reports/model0_results.md`
- `model0/reports/ncdm_result.json`
- `model0/reports/orcdf_result.json`
- `model0/reports/preflight.md`
- `model0/reports/rcd_result.json`
- `model0/reports/rcd_route.md`
- `model0/scripts/audit_rcd.py`
- `model0/scripts/final_gate.py`
- `model0/scripts/gear_cd_smoke.py`
- `model0/scripts/inscd_adapter.py`
- `model0/scripts/model0_common.py`
- `model0/scripts/preflight.py`
- `model0/scripts/run_ncdm.py`
- `model0/scripts/run_orcdf.py`
- `model0/tests/test_inscd_adapter.py`
- `model0/tests/test_preflight.py`

## Upload boundary

The MODEL-0 archive tracks only the small, reviewable artifacts above and this
archive record. The CPU-only dependency declaration in
`model-service/requirements.txt` enables unit-test collection, but no wheel or
Torch runtime is tracked.
The following local-only material remains excluded by `.gitignore`:

- raw and derived MODEL-0 data: `model-service/experiments/model0/data/`;
- checkpoints: `model-service/experiments/model0/checkpoints/` and model
  checkpoint extensions;
- Torch/runtime material: `model-service/experiments/model0/runtime/`;
- virtual environments: `.venv/`;
- logs: `*.log` and `logs/`;
- environment files and secrets: `.env` and `.env.*`, while the non-secret
  tracked template `.env.example` remains allowed.

No raw dataset, large derived data, checkpoint, Torch runtime, virtual
environment, log, or secret is part of this archive.

## Review boundary

The archive ends with the original MODEL-0A evidence. It does not authorize or
perform MODEL-0R, recommendation work, mastery work, website work, or Java AI
Gateway changes. Further experimentation requires separate approval and review.
