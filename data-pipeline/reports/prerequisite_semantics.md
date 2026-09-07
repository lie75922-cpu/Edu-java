# Prerequisite Semantics Audit

The actual metadata header is prerequisites, while the public README uses the singular spelling prerequisite. DATA-0 preserves the complete raw text and does not silently freeze a graph schema.

| Check | Value |
| --- | --- |
| Source rows excluded for ambiguous Exercise identity | 4 |
| In-row duplicate prerequisite tokens | 1 |
| Dangling prerequisite tokens | 0 |
| Ambiguous prerequisite tokens | 0 |
| Analysis-view parsed edges | 980 |
| Self loops | 2 |

The analysis view orients each parsed relation as prerequisite to dependent only for topology measurement. A delimiter split is not promoted to a final graph relation or V0.2 knowledge decision.

Duplicate-token examples: [{"row_number": 718, "exercise": "ratio", "token": "writing_proportions", "count": 2}]

Self-loop examples: ["number_sense_length_l1", "proportions_1"]
