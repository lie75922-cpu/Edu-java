# MODEL-1 Concept Provenance Audit

## Local Exercise identity and current scope

| Check | Value |
| --- | ---: |
| Raw metadata rows | 837 |
| Raw unique Exercise external IDs | 835 |
| Identity-unambiguous raw Exercises | 833 |
| Duplicate external IDs | 2 |
| Missing Topic rows | 20 |
| Raw non-missing Topics | 40 |
| Frozen Medium Exercise metadata rows | 815 |
| Medium observed Exercises | 624 |
| Current Q-eligible Exercises | 624 |
| One-to-one Exercise-to-Topic coverage | 624 |
| Frozen Topic display columns | 40 |
| Topics represented by current eligible Exercises | 39 |
| Fine ModelConcept candidates | 624 |

The tracked catalog is [`model_concept_catalog_v1.json`](../manifests/model_concept_catalog_v1.json).  It supplies the local, auditable `Exercise external ID -> model_concept_id` relation.  A fine ModelConcept remains an algorithm-layer identity and does not overwrite a business Topic KnowledgePoint.

## Raw prerequisite research graph

| Graph | Nodes | Unique edges | Raw edge rows | Self loops | Components | Cyclic SCCs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Identity-unambiguous raw Exercise view | 833 | 979 | 980 | 2 | 69 | 3 |
| Current Q-eligible induced view | 624 | 763 | 764 | 1 | 26 | 2 |

Prerequisite text is retained only as `RESEARCH_MODEL_GRAPH` evidence.  Self loops and cycles are reported rather than repaired, and this audit does not publish or modify any product graph.

## Reference Junyi concept semantics

| Route | Verified local evidence | Alignment result |
| --- | --- | --- |
| RCD | config declares 10000 students / 835 exercises / 835 knowledge concepts | `NOT_COMPARABLE_UNTIL_PROJECT_COMMON_GRAPH_IS_BUILT` |
| InsCD | `Junyi734` adapter loads archive-defined response/Q arrays | `REFERENCE_NCDM_SEMANTICS_AVAILABLE_BUT_JUNYI734_IDS_NOT_ALIGNED_TO_LOCAL_SCOPE` |
| GEAR-CD | numeric Exercise-concept pair file: 706 rows; distinct numeric IDs by file column: 706 / 706 | `SMOKE_OR_COMPATIBILITY_ONLY` |

RCD and GEAR-CD local files use opaque numeric identifiers with no supplied external-ID relation to this repository's current Q-eligible Exercise scope.  No mapping is invented.  InsCD provides the NCDM reference semantics but its `Junyi734` archive identifiers are not locally auditable against the present 624-Exercise catalog.
