# DATA-0 Reproducible Sampling

| Candidate | Students | Exercises | Interactions | Topics | Areas | Relation edges | Largest component ratio | Disk bytes | Materialisation seconds | Peak RAM bytes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Small | 5000 | 584 | 142162 | 40 | 8 | 979 | 0.909202 | 33563018 | 231.734 | N/A |
| Medium | 10000 | 624 | 284245 | 40 | 8 | 979 | 0.909202 | 66746575 | 233.953 | N/A |
| Large | 15000 | 644 | 427038 | 40 | 8 | 979 | 0.909202 | 100080996 | 237.546 | N/A |

| Control | Value |
| --- | --- |
| Selection seed | 20260907 |
| Scope Exercise count | 815 |
| Eligible students | 175359 |
| Minimum interactions | 5 |
| Minimum interaction rule | max(5, floor(empirical P25)) |
| Stable sequence window cap | 50 |
| Window cap rule | max(1, floor(empirical P75)) |
| Medium split strategy | student_level |
| Medium split membership seed | 20261407 |
| Train valid test overlap | {'train_valid': 0, 'train_test': 0, 'valid_test': 0} |

All candidates are materialised below data-pipeline/.data/generated and excluded from Git. The Medium member list is fixed before any model result.
