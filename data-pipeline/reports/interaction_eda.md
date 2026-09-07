# Interaction and Student-sequence EDA

| Metric | Value |
| --- | --- |
| Students | 247606 |
| Interactions | 25925992 |
| Referenced Exercises | 722 |
| Correct rate | 0.827874 |
| Timestamp earliest | 2012-10-12T01:03:55.131230Z |
| Timestamp latest | 2015-01-11T18:14:25.650430Z |
| Invalid timestamps | 0 |

| min | P10 | P25 | P50 | P75 | P90 | P95 | P99 | max | mean |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 1 | 3 | 10 | 50 | 220 | 485 | 1670.95 | 22067 | 104.706639 |

| Students below 5 | below 10 | below 15 | below 20 | below 30 |
| --- | --- | --- | --- | --- |
| 72247 | 122724 | 135836 | 151085 | 166432 |

Candidate sequences are parsed and stably sorted by time_done and original row number before the empirical sequence window is applied.
