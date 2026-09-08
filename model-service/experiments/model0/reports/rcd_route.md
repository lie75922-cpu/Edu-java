# MODEL-0 RCD conditional-route audit

## Decision

**NOT_COMPARABLE_ON_COMMON_GRAPH**. No same-dataset RCD training or fair-table
comparison was run. This is a conditional-route result, not an RCD performance
failure and not a claim that RCD itself is invalid.

## Evidence checked

| Item | Reference RCD | Frozen MODEL-0 input |
| --- | ---: | ---: |
| Exercises | 835 | 624 |
| Concepts / Topics | 835 numeric concepts | 40 Topics |
| Directed graph numeric nodes | 776 | N/A |
| Directed graph edges | 981 | N/A |
| Directed self-loops | 2 | N/A |
| Undirected graph numeric nodes | 362 | N/A |
| Undirected graph edges | 1040 | N/A |

The audit found the reference `K_Directed` and `K_Undirected` numeric graph
files and RCD's own training header, but no explicit artifact that maps the
numeric concept nodes to the frozen 40-Topic Q-matrix or maps the reference
exercise identities to the frozen derived Exercise indexes. Numeric cardinality
differences alone are not the reason for the decision; the blocking fact is the
absence of a traceable identity join. Row order, matching-looking integers, or
post-hoc inferred mappings would not be auditable and were not used.

## Boundary preserved

- DATA-0/ADR evidence already classifies the RCD numeric graph as reference
  evidence, not a Published Graph.
- No raw prerequisite evidence was transformed into a production graph.
- No frozen student member or test interaction was changed or evaluated by RCD.
- A separate reference-reproduction run, if ever requested, must stay outside
  the NCDM/ORCDF fair-comparison table.

## What would unlock a fair RCD route

1. An audited RCD numeric-concept → frozen Topic mapping covering every graph
   node used in the run.
2. An audited RCD reference-exercise → frozen Exercise identity mapping.
3. Owner approval of the common-graph specification while keeping it distinct
   from the Published Graph lifecycle.

