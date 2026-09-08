# MODEL-3: Simple Hierarchical Ability Final Validation

This workspace implements GitHub Issue #30 as one controlled final-validation
run. It contains exactly three candidates:

- `EXERCISE_RATE_DEV`
- `RASCH_1PL_V1`
- `HIER_RASCH_TOPIC_V1`

`HIER_RASCH_TOPIC_V1` is the regularized model
`logit P(correct) = mu + theta_student - beta_exercise + delta_student_topic`.
The Topic deviation is a local logit-space deviation, not a platform mastery
percentage. A final student with no calibration history for a Topic receives
`delta=0` and status `UNKNOWN`.

The frozen catalog has 40 Topics. The 624 observed eligible Exercises used by
the final protocol represent 39 of them; the remaining catalog Topic has no
eligible response row and is not silently converted into observed evidence.

## Mandatory order

1. Run the integrity audit. It reads membership and development identity only;
   it never opens final correctness labels.
2. Run development selection and development-only global refit. This writes the
   frozen selection evidence and an ignored local global-parameter artifact.
3. Run final validation once. The ledger is claimed before labels are opened and
   rejects every subsequent final evaluation.
4. Run the final gate and stop. It never changes Java production code.

## Local execution

The data and parameter artifacts are local ignored files. The paths below are
explicit inputs rather than repository content.

```powershell
$python = "D:\Code\java\Edu-java-submit\model-service\.venv\Scripts\python.exe"
$model1 = "D:\Code\java\Edu-java-model0-junyi-20260907\model-service\experiments\model1"
$model2 = "D:\Code\java\Edu-java-model2-signal-20260908\model-service\experiments\model2"
$medium = "D:\Code\java\Edu-java-submit\data-pipeline\.data\generated\medium"
$metadata = "D:\Code\java\data-pipeline\data\interim\junyi_metadata\junyi_Exercise_table.csv"
$problemLog = "D:\Code\java\data-pipeline\data\interim\junyi_original\junyi_ProblemLog_original.csv"

& $python .\model-service\experiments\model3\scripts\integrity_audit.py `
  --model1-asset-root $model1 --model2-asset-root $model2 `
  --medium-root $medium --metadata $metadata
& $python .\model-service\experiments\model3\scripts\run_development.py `
  --model1-asset-root $model1
& $python .\model-service\experiments\model3\scripts\run_final_validation.py `
  --model1-asset-root $model1 --model2-asset-root $model2 `
  --medium-root $medium --problem-log $problemLog
& $python .\model-service\experiments\model3\scripts\final_gate.py
```

The final runner must not be retried: a claimed ledger is deliberately
fail-closed even if an interruption occurs after the claim. Runtime derivatives,
global parameters, and any raw response rows remain ignored.

## Boundary

No NCDM, RCD, ORCDF, GEAR-CD, GNN, or Transformer is trained or evaluated here.
No Java `MasteryProvider`, recommendation, Published Graph, or RuleBeta source
is modified. A final Gate result is evidence only and never an automatic model
integration.
