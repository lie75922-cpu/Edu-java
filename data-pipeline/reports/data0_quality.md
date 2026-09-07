# DATA-0 Quality Audit

| Check | Observed value |
| --- | --- |
| ProblemLog rows | 25925992 |
| ProblemLog malformed rows | 0 |
| ProblemLog exact duplicate rows | 1439 |
| ProblemLog business-key duplicate rows | 1439 |
| Unknown Exercise reference rows | 11 |
| Known Exercise reference rate | 1 |
| Metadata duplicate external IDs | 2 |
| Metadata prerequisite dangling tokens | 0 |
| Metadata prerequisite ambiguous tokens | 0 |

The business key is user_id, exercise, problem_number, and time_done. Exact duplication compares all original CSV fields. Neither check deletes a row.
