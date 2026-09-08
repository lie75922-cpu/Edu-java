# MODEL-0 experiment workspace

This directory contains the controlled, non-production MODEL-0 experiment
artifacts for Issue #4.

Tracked content is limited to executable experiment code, fixed configurations,
small manifests, tests, and human-reviewable reports. Derived interaction
tables, member lists, index maps, checkpoints, and runtime telemetry stay in
the ignored `data/`, `checkpoints/`, and `runtime/` directories. No content
digest is generated or recorded by this workspace.

Run the preflight before any training:

```powershell
& .\model-service\.venv\Scripts\python.exe .\model-service\experiments\model0\scripts\preflight.py
```

The preflight preserves the DATA-0 10,000-student membership and 70/10/20
student split exactly. Since the split is student-disjoint, later model runs
must use the documented zero-history cold-start policy for validation and test
students; they must not fit a held-out student's embedding from their own
responses.

