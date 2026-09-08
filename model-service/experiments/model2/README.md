# MODEL-2: observability-aware mastery and personalization signal gate

This workspace implements Issue #24 as a single controlled diagnostic gate. It
does not train RCD, ORCDF, GEAR-CD, or any new graph model, and it does not
change Java mastery, recommendation, or Published Graph code.

## Execution order

1. Audit the explicitly supplied, read-only MODEL-1 C40 checkpoint and
   derivatives.
2. Freeze `JUNYI_FINAL_HOLDOUT_V1` before any diagnostic metric is generated.
   Its membership is outside both the original Medium 10k and MODEL-1 5k.
3. Run inference-only C40 observability, future-signal, and student-identity
   ablations on the historical MODEL-1 derivative. The MODEL-1 test partition
   is a diagnostic source in this workspace, never a new final holdout.
4. Fit only the configured Rasch/IRT-1PL diagnostic baseline on MODEL-1
   train/history, using validation only for epoch selection.
5. Apply exactly one of the three allowed MODEL-2 terminal gates and stop.

## Local execution

Large data, checkpoints, and runtime ledgers are deliberately ignored. Supply
their local locations explicitly; do not copy them into this repository.

```powershell
$python = "D:\Code\java\Edu-java-submit\model-service\.venv\Scripts\python.exe"
$medium = "D:\Code\java\Edu-java-submit\data-pipeline\.data\generated\medium"
$model1 = "D:\Code\java\Edu-java-model0-junyi-20260907\model-service\experiments\model1"
$metadata = "D:\Code\java\data-pipeline\data\interim\junyi_metadata\junyi_Exercise_table.csv"
$problemLog = "D:\Code\java\data-pipeline\data\interim\junyi_original\junyi_ProblemLog_original.csv"

& $python .\model-service\experiments\model2\scripts\preflight.py `
  --medium-root $medium --model1-asset-root $model1 --metadata $metadata `
  --problem-log $problemLog
& $python .\model-service\experiments\model2\scripts\run_diagnostics.py `
  --model1-asset-root $model1
& $python .\model-service\experiments\model2\scripts\final_gate.py
```

The preflight refuses to replace an existing final-holdout membership. The
diagnostic runner refuses a second completed run. Neither command materialises
or scores `JUNYI_FINAL_HOLDOUT_V1` labels.
