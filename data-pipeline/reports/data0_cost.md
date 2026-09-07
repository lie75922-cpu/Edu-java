# DATA-0 Cost and Reproducibility

| Metric | Observed value |
| --- | --- |
| Raw ProblemLog bytes | 2668566436 |
| Processed candidate bytes | 200390589 |
| ETL wall time seconds | 5149.531 |
| CPU process time seconds | 3652.812 |
| Peak working set bytes | N/A |
| GPU hours | 0 |
| Cash cost | 0 |

Reproducible entry point: python -m edu_data.data0 with explicit metadata, ProblemLog, reference, reports, generated-output, work, manifest, and schema paths. It begins from supplied files and never uploads raw data.
