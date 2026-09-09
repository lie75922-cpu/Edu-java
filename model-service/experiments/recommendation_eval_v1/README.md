# recommendation_eval_v1

This is the R Wave-2 offline recommendation evaluation only. It consumes local Junyi **Research Data** and Foundation's regenerated, review-only business export. It never creates platform records.

Run the Foundation exporter to a worktree-external temporary directory, run this evaluator with that directory, then remove the temporary export. The evaluator writes only the required summarized artifacts to this directory:

```powershell
$python = 'C:\\Users\\26675\\.cache\\codex-runtimes\\codex-primary-runtime\\dependencies\\python\\python.exe'
$env:PYTHONPATH = 'D:\\Code\\java\\Edu-java-recommendation-eval-v1\\data-pipeline\\src'
& $python -m edu_data.business_export --metadata D:\\Code\\java\\data-pipeline\\data\\interim\\junyi_metadata\\junyi_Exercise_table.csv --output D:\\Code\\java\\_tmp_recommendation_eval_foundation_export_20260909
& $python .\\model-service\\experiments\\recommendation_eval_v1\\recommendation_eval.py --problem-log D:\\Code\\java\\data-pipeline\\data\\interim\\junyi_original\\junyi_ProblemLog_original.csv --foundation-export D:\\Code\\java\\_tmp_recommendation_eval_foundation_export_20260909 --output .\\model-service\\experiments\\recommendation_eval_v1
```

M0 and M1 use the same Topic candidate set and the same student-local chronological split. M2 and M3 are deliberately not run when the Foundation export remains an unpublished cyclic candidate graph: their status is `INCONCLUSIVE_GRAPH_SEMANTICS`. Rasch is `DEFERRED`.
