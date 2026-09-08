# MODEL-1: Junyi ModelConcept Resolution Gate

This workspace implements Issue #19 as a controlled research gate, not a
production-model change.  It first audits the local Junyi identity and concept
semantics, freezes a fresh external holdout outside the original DATA-0 Medium
10,000 students, and then compares the same NCDM implementation under two
Q-matrix definitions:

- `C40_TOPIC`: each eligible Exercise maps to one of the 40 business Topics.
- `C_FINE_EXERCISE`: each eligible Exercise maps to its own sequential,
  auditable algorithm-layer `model_concept_id`.

`ModelConcept` remains separate from platform `KnowledgePoint`, Question, and
any published prerequisite graph.  The raw Exercise prerequisite view is
research-only evidence and is never written to product graph storage.

## Required inputs and local execution

The data is deliberately supplied by explicit local paths and is never copied
into this repository.  The commands below create ignored local numeric
derivatives, checkpoints, and runtime ledgers while tracking only small
manifests, configurations, scripts, tests, and review reports.

```powershell
$python = ".\model-service\.venv\Scripts\python.exe"
$medium = "D:\path\to\data-pipeline\.data\generated\medium"
$metadata = "D:\path\to\junyi_Exercise_table.csv"
$problemLog = "D:\path\to\junyi_ProblemLog_original.csv"
$reference = "D:\path\to\data-pipeline\.data\reference"

& $python .\model-service\experiments\model1\scripts\preflight.py `
  --medium-root $medium --metadata $metadata --problem-log $problemLog `
  --reference-root $reference
& $python .\model-service\experiments\model1\scripts\run_ncdm_ablation.py
& $python .\model-service\experiments\model1\scripts\final_gate.py
```

The preflight command refuses to replace an already frozen local membership or
manifest.  It selects membership from raw user identity, the fixed eligible
Exercise scope, valid chronology, and sequence length only; `correct` is not
read by the selection pass.  The NCDM runner opens the fresh test rows only
after both configured candidates and the non-personalized comparator have been
selected using validation data.  Its ignored ledger rejects a second final-test
evaluation for a recorded candidate.

## Gate boundary

The only terminal decisions are:

- `GO_FINE_CONCEPT_GRAPH_MODELS`
- `CONDITIONAL_GO`
- `NO_GO_CONCEPT_LAYER`

Graph-model work is not started unless the fine-concept NCDM clears every
pre-registered Concept Gate condition.  If it does not clear the gate, this
workspace stops without an RCD, ORCDF, or GEAR-CD training run.

`model-service/experiments/model0/` and `model-service/experiments/model0r/`
remain historical evidence and are not rewritten or used as MODEL-1 final
test data.
